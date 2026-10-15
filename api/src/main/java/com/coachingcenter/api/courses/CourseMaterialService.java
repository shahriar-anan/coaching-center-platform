package com.coachingcenter.api.courses;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.files.LocalFileStorage;
import com.coachingcenter.api.files.LocalFileStorage.StoredFile;

import tools.jackson.databind.JsonNode;

@Service
public class CourseMaterialService {

	private static final Set<String> KINDS = Set.of("HANDOUT", "EXERCISE", "SLIDE", "OTHER");

	private final CourseRepository courses;

	private final ContentNodeRepository nodes;

	private final MaterialRepository materials;

	private final LocalFileStorage fileStorage;

	public CourseMaterialService(CourseRepository courses, ContentNodeRepository nodes, MaterialRepository materials,
			LocalFileStorage fileStorage) {
		this.courses = courses;
		this.nodes = nodes;
		this.materials = materials;
		this.fileStorage = fileStorage;
	}

	@Transactional
	public Material storeCourseFile(UUID courseId, MultipartFile file) {
		return store(requireCourse(courseId), null, null, file, false, null, null, null);
	}

	@Transactional
	public void discard(UUID courseId, UUID materialId) {
		Material material = requireMaterial(courseId, materialId);
		fileStorage.delete(material.getStoredFileName());
		material.setDeletedAt(Instant.now());
	}

	@Transactional
	public MaterialView uploadCover(UUID courseId, MultipartFile file) {
		Course course = requireCourse(courseId);
		Material material = store(course, null, null, file, false, null, null, null);
		course.setCoverFileId(material.getId());
		return view(material);
	}

	@Transactional
	public MaterialView uploadMaterial(UUID courseId, UUID lectureNodeId, UUID sectionNodeId, boolean free,
			String title, String description, String kind, MultipartFile file) {
		Course course = requireCourse(courseId);
		Attachment attachment = attachment(courseId, lectureNodeId, sectionNodeId);
		boolean markFree = attachment.lectureNodeId() == null && free && "FREEMIUM".equals(course.getAccessType());
		return view(store(course, attachment.lectureNodeId(), attachment.sectionNodeId(), file, markFree, title,
				description, kind));
	}

	@Transactional
	public List<MaterialView> uploadBatch(UUID courseId, UUID lectureNodeId, UUID sectionNodeId, List<MultipartFile> files,
			List<String> titles, List<String> descriptions, List<String> kinds) {
		Course course = requireCourse(courseId);
		Attachment attachment = attachment(courseId, lectureNodeId, sectionNodeId);
		if (files == null || files.isEmpty()) {
			throw validationFailed();
		}
		for (int index = 0; index < files.size(); index++) {
			kindAt(kinds, index);
		}
		List<StoredFile> stored = new ArrayList<>();
		try {
			for (MultipartFile file : files) {
				stored.add(saveFile(file));
			}
			List<MaterialView> created = new ArrayList<>();
			for (int index = 0; index < stored.size(); index++) {
				Material material = persist(course, attachment.lectureNodeId(), attachment.sectionNodeId(),
						stored.get(index), false, textAt(titles, index), textAt(descriptions, index), kindAt(kinds, index));
				created.add(view(material));
			}
			return created;
		}
		catch (RuntimeException exception) {
			for (StoredFile file : stored) {
				fileStorage.delete(file.storedFileName());
			}
			throw exception;
		}
	}

	@Transactional
	public MaterialView replace(UUID courseId, MultipartFile file, UUID materialId) {
		requireCourse(courseId);
		Material material = requireMaterial(courseId, materialId);
		rejectCover(courseId, material);
		if (file == null || file.isEmpty()) {
			throw validationFailed();
		}
		String previousName = material.getStoredFileName();
		StoredFile stored = saveFile(file);
		material.setStoredFileName(stored.storedFileName());
		material.setOriginalFileName(stored.originalFileName());
		material.setByteSize(stored.byteSize());
		material.setContentType(stored.contentType());
		fileStorage.delete(previousName);
		return view(material);
	}

	@Transactional
	public void delete(UUID courseId, UUID materialId) {
		Course course = requireCourse(courseId);
		Material material = requireMaterial(courseId, materialId);
		rejectCover(course, material);
		fileStorage.delete(material.getStoredFileName());
		material.setDeletedAt(Instant.now());
	}

	@Transactional
	public void deleteForLecture(UUID courseId, UUID lectureNodeId) {
		requireCourse(courseId);
		for (Material material : materials.findByLectureNodeIdAndDeletedAtIsNull(lectureNodeId)) {
			if (!courseId.equals(material.getCourseId())) {
				continue;
			}
			fileStorage.delete(material.getStoredFileName());
			material.setDeletedAt(Instant.now());
		}
	}

	@Transactional
	public void deleteForSection(UUID courseId, UUID sectionNodeId) {
		requireCourse(courseId);
		for (Material material : materials.findBySectionNodeIdAndDeletedAtIsNull(sectionNodeId)) {
			if (!courseId.equals(material.getCourseId())) {
				continue;
			}
			fileStorage.delete(material.getStoredFileName());
			material.setDeletedAt(Instant.now());
		}
	}

	@Transactional
	public MaterialView update(UUID courseId, UUID materialId, JsonNode body) {
		Course course = requireCourse(courseId);
		Material material = requireMaterial(courseId, materialId);
		if (body.has("free")) {
			applyFree(course, material, body.get("free").asBoolean());
		}
		if (body.has("title") && !body.get("title").isNull()) {
			material.setTitle(requiredText(body, "title"));
		}
		if (body.has("description")) {
			if (body.get("description").isNull()) {
				material.setDescription(null);
			}
			else {
				material.setDescription(body.get("description").asText());
			}
		}
		if (body.has("kind") && !body.get("kind").isNull()) {
			material.setKind(requiredKind(body.get("kind").asText()));
		}
		if (body.has("position") && !body.get("position").isNull()) {
			place(material, body.get("position").asInt());
		}
		return view(material);
	}

	@Transactional(readOnly = true)
	public Material requireReadable(UUID materialId) {
		return materials.findByIdAndDeletedAtIsNull(materialId).orElseThrow(this::notFound);
	}

	@Transactional(readOnly = true)
	public List<MaterialView> list(UUID courseId) {
		requireCourse(courseId);
		return materials.findByCourseIdAndDeletedAtIsNull(courseId)
			.stream()
			.sorted(Comparator.comparing(Material::getLectureNodeId, Comparator.nullsFirst(UUID::compareTo))
				.thenComparing(Material::getSectionNodeId, Comparator.nullsFirst(UUID::compareTo))
				.thenComparingInt(Material::getPosition))
			.map(CourseMaterialService::view)
			.toList();
	}

	private void applyFree(Course course, Material material, boolean free) {
		if (material.getLectureNodeId() != null) {
			throw validationFailed();
		}
		if (!"FREEMIUM".equals(course.getAccessType())) {
			material.setFree(false);
			return;
		}
		if (material.isFree() && !free) {
			throw new ApiException(ErrorCode.FREE_CONTENT_FIXED, HttpStatus.CONFLICT, "Free content cannot be made paid.");
		}
		material.setFree(free);
	}

	private void place(Material material, int position) {
		if (position < 0) {
			throw validationFailed();
		}
		List<Material> ordered = new ArrayList<>(group(material));
		ordered.removeIf(item -> item.getId().equals(material.getId()));
		int index = Math.min(position, ordered.size());
		ordered.add(index, material);
		for (int i = 0; i < ordered.size(); i++) {
			ordered.get(i).setPosition(i);
		}
	}

	private List<Material> group(Material material) {
		if (material.getLectureNodeId() != null) {
			return materials.findByLectureNodeIdAndDeletedAtIsNullOrderByPositionAsc(material.getLectureNodeId());
		}
		if (material.getSectionNodeId() != null) {
			return materials.findBySectionNodeIdAndDeletedAtIsNullOrderByPositionAsc(material.getSectionNodeId());
		}
		return materials.findByCourseIdAndLectureNodeIdIsNullAndSectionNodeIdIsNullAndDeletedAtIsNullOrderByPositionAsc(
				material.getCourseId());
	}

	private Material store(Course course, UUID lectureNodeId, UUID sectionNodeId, MultipartFile file, boolean free,
			String title, String description, String kind) {
		StoredFile stored = saveFile(file);
		try {
			return persist(course, lectureNodeId, sectionNodeId, stored, free, title, description, kind);
		}
		catch (RuntimeException exception) {
			fileStorage.delete(stored.storedFileName());
			throw exception;
		}
	}

	private Material persist(Course course, UUID lectureNodeId, UUID sectionNodeId, StoredFile stored, boolean free,
			String title, String description, String kind) {
		Material material = new Material();
		material.setCourseId(course.getId());
		material.setLectureNodeId(lectureNodeId);
		material.setSectionNodeId(sectionNodeId);
		material.setStoredFileName(stored.storedFileName());
		material.setOriginalFileName(stored.originalFileName());
		material.setByteSize(stored.byteSize());
		material.setContentType(stored.contentType());
		material.setTitle(title == null || title.isBlank() ? stored.originalFileName() : title.trim());
		material.setDescription(description == null || description.isBlank() ? null : description);
		material.setKind(kind == null || kind.isBlank() ? "OTHER" : requiredKind(kind));
		material.setPosition(nextPosition(course.getId(), lectureNodeId, sectionNodeId));
		material.setFree(free);
		return materials.save(material);
	}

	private int nextPosition(UUID courseId, UUID lectureNodeId, UUID sectionNodeId) {
		List<Material> existing;
		if (lectureNodeId != null) {
			existing = materials.findByLectureNodeIdAndDeletedAtIsNullOrderByPositionAsc(lectureNodeId);
		}
		else if (sectionNodeId != null) {
			existing = materials.findBySectionNodeIdAndDeletedAtIsNullOrderByPositionAsc(sectionNodeId);
		}
		else {
			existing = materials
				.findByCourseIdAndLectureNodeIdIsNullAndSectionNodeIdIsNullAndDeletedAtIsNullOrderByPositionAsc(courseId);
		}
		return existing.size();
	}

	private Attachment attachment(UUID courseId, UUID lectureNodeId, UUID sectionNodeId) {
		if (lectureNodeId != null && sectionNodeId != null) {
			throw validationFailed();
		}
		if (lectureNodeId != null) {
			ContentNode lecture = nodes.findByIdAndCourseIdAndDeletedAtIsNull(lectureNodeId, courseId)
				.orElseThrow(CourseMaterialService::validationFailed);
			if (!"LECTURE".equals(lecture.getNodeType())) {
				throw validationFailed();
			}
			return new Attachment(lectureNodeId, null);
		}
		if (sectionNodeId != null) {
			ContentNode section = nodes.findByIdAndCourseIdAndDeletedAtIsNull(sectionNodeId, courseId)
				.orElseThrow(CourseMaterialService::validationFailed);
			if (!"SECTION".equals(section.getNodeType())) {
				throw validationFailed();
			}
			return new Attachment(null, sectionNodeId);
		}
		return new Attachment(null, null);
	}

	private StoredFile saveFile(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw validationFailed();
		}
		String originalName = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
		String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
		try {
			return fileStorage.save(originalName, contentType, file.getInputStream(), file.getSize());
		}
		catch (ApiException exception) {
			throw exception;
		}
		catch (Exception exception) {
			throw new IllegalStateException("Could not read upload.", exception);
		}
	}

	private static String textAt(List<String> values, int index) {
		if (values == null || index >= values.size()) {
			return null;
		}
		return values.get(index);
	}

	private static String kindAt(List<String> kinds, int index) {
		String kind = textAt(kinds, index);
		if (kind == null || kind.isBlank()) {
			return null;
		}
		return requiredKind(kind);
	}

	private static String requiredKind(String kind) {
		String normalized = kind.trim();
		if (!KINDS.contains(normalized)) {
			throw validationFailed();
		}
		return normalized;
	}

	private static String requiredText(JsonNode body, String field) {
		JsonNode node = body.get(field);
		if (node == null || node.isNull() || node.asText().isBlank()) {
			throw validationFailed();
		}
		return node.asText().trim();
	}

	private void rejectCover(UUID courseId, Material material) {
		rejectCover(requireCourse(courseId), material);
	}

	private static void rejectCover(Course course, Material material) {
		if (material.getId().equals(course.getCoverFileId())) {
			throw validationFailed();
		}
	}

	private Course requireCourse(UUID courseId) {
		return courses.findByIdAndDeletedAtIsNull(courseId).orElseThrow(this::notFound);
	}

	private Material requireMaterial(UUID courseId, UUID materialId) {
		return materials.findByIdAndCourseIdAndDeletedAtIsNull(materialId, courseId).orElseThrow(this::notFound);
	}

	private static MaterialView view(Material material) {
		return new MaterialView(material.getId(), material.getCourseId(), material.getLectureNodeId(),
				material.getSectionNodeId(), material.getTitle(), material.getDescription(), material.getKind(),
				material.getPosition(), material.getOriginalFileName(), material.getByteSize(), material.getContentType(),
				material.isFree());
	}

	private static ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
	}

	private ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	private record Attachment(UUID lectureNodeId, UUID sectionNodeId) {
	}

	public record MaterialView(UUID id, UUID courseId, UUID lectureNodeId, UUID sectionNodeId, String title,
			String description, String kind, int position, String originalFileName, long byteSize, String contentType,
			boolean free) {
	}

}
