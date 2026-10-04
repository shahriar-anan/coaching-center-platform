package com.coachingcenter.api.courses;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.files.LocalFileStorage;
import com.coachingcenter.api.files.LocalFileStorage.StoredFile;

@Service
public class CourseMaterialService {

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
	public MaterialView uploadCover(UUID courseId, MultipartFile file) {
		Course course = requireCourse(courseId);
		Material material = store(course, null, file, false);
		course.setCoverFileId(material.getId());
		return view(material);
	}

	@Transactional
	public MaterialView uploadMaterial(UUID courseId, UUID lectureNodeId, boolean free, MultipartFile file) {
		Course course = requireCourse(courseId);
		if (lectureNodeId != null) {
			ContentNode lecture = nodes.findByIdAndCourseIdAndDeletedAtIsNull(lectureNodeId, courseId)
				.orElseThrow(this::notFound);
			if (!"LECTURE".equals(lecture.getNodeType())) {
				throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
			}
			return view(store(course, lectureNodeId, file, false));
		}
		boolean courseFree = free && "FREEMIUM".equals(course.getAccessType());
		return view(store(course, null, file, courseFree));
	}

	@Transactional
	public MaterialView replace(UUID courseId, MultipartFile file, UUID materialId) {
		Course course = requireCourse(courseId);
		Material material = requireMaterial(courseId, materialId);
		rejectCover(course, material);
		if (file == null || file.isEmpty()) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
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
	public MaterialView updateFree(UUID courseId, UUID materialId, boolean free) {
		Course course = requireCourse(courseId);
		Material material = requireMaterial(courseId, materialId);
		if (material.getLectureNodeId() != null) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
		}
		if (!"FREEMIUM".equals(course.getAccessType())) {
			material.setFree(false);
			return view(material);
		}
		if (material.isFree() && !free) {
			throw new ApiException(ErrorCode.FREE_CONTENT_FIXED, HttpStatus.CONFLICT, "Free content cannot be made paid.");
		}
		material.setFree(free);
		return view(material);
	}

	@Transactional(readOnly = true)
	public Material requireReadable(UUID materialId) {
		return materials.findByIdAndDeletedAtIsNull(materialId).orElseThrow(this::notFound);
	}

	@Transactional(readOnly = true)
	public java.util.List<MaterialView> list(UUID courseId) {
		requireCourse(courseId);
		return materials.findByCourseIdAndDeletedAtIsNullOrderByCreatedAtAsc(courseId)
			.stream()
			.map(CourseMaterialService::view)
			.toList();
	}

	private Material store(Course course, UUID lectureNodeId, MultipartFile file, boolean free) {
		StoredFile stored = saveFile(file);
		Material material = new Material();
		material.setCourseId(course.getId());
		material.setLectureNodeId(lectureNodeId);
		material.setStoredFileName(stored.storedFileName());
		material.setOriginalFileName(stored.originalFileName());
		material.setByteSize(stored.byteSize());
		material.setContentType(stored.contentType());
		material.setFree(free);
		return materials.save(material);
	}

	private StoredFile saveFile(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
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

	private static void rejectCover(Course course, Material material) {
		if (material.getId().equals(course.getCoverFileId())) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
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
				material.getOriginalFileName(), material.getByteSize(), material.getContentType(), material.isFree());
	}

	private ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	public record MaterialView(UUID id, UUID courseId, UUID lectureNodeId, String originalFileName, long byteSize,
			String contentType, boolean free) {
	}

}
