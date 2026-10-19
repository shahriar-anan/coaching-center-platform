package com.coachingcenter.api.exams;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

import tools.jackson.databind.JsonNode;

@Service
public class LiveExamModuleService {

	private static final UUID NO_HIDDEN_CATEGORY = new UUID(0L, 0L);

	private final LiveExamModuleRepository modules;

	private final LiveExamRosterRepository roster;

	private final LiveExamCatalogVisibility visibility;

	private final AuditRecorder audit;

	public LiveExamModuleService(LiveExamModuleRepository modules, LiveExamRosterRepository roster,
			LiveExamCatalogVisibility visibility, AuditRecorder audit) {
		this.modules = modules;
		this.roster = roster;
		this.visibility = visibility;
		this.audit = audit;
	}

	@Transactional
	public ModuleView create(JsonNode body) {
		rejectLockedFields(body);
		UUID categoryId = readCategoryId(body);
		visibility.requireOpen(categoryId);
		LiveExamModule module = new LiveExamModule();
		module.setCategoryId(categoryId);
		module.setTitleEn(requiredText(body, "titleEn"));
		module.setTitleBn(requiredText(body, "titleBn"));
		module.setDescriptionEn(requiredText(body, "descriptionEn"));
		module.setDescriptionBn(requiredText(body, "descriptionBn"));
		module.setAccessMode(LiveExamModule.MANUAL);
		module.setStatus(LiveExamModule.DRAFT);
		module.setFinallySaved(false);
		module.setPosition(readPosition(body, categoryId, null));
		return view(modules.save(module));
	}

	@Transactional
	public ModuleView update(UUID id, JsonNode body) {
		rejectLockedFields(body);
		LiveExamModule module = requireOpen(id);
		if (body.has("titleEn") && !body.get("titleEn").isNull()) {
			module.setTitleEn(requiredText(body, "titleEn"));
		}
		if (body.has("titleBn") && !body.get("titleBn").isNull()) {
			module.setTitleBn(requiredText(body, "titleBn"));
		}
		if (body.has("descriptionEn") && !body.get("descriptionEn").isNull()) {
			module.setDescriptionEn(requiredText(body, "descriptionEn"));
		}
		if (body.has("descriptionBn") && !body.get("descriptionBn").isNull()) {
			module.setDescriptionBn(requiredText(body, "descriptionBn"));
		}
		boolean categoryChanged = false;
		if (body.has("categoryId")) {
			UUID categoryId = readCategoryId(body);
			visibility.requireOpen(categoryId);
			module.setCategoryId(categoryId);
			categoryChanged = true;
		}
		if (body.has("position") && !body.get("position").isNull()) {
			module.setPosition(readPosition(body, module.getCategoryId(), id));
		}
		else if (categoryChanged) {
			module.setPosition(nextPosition(module.getCategoryId(), id));
		}
		module.setStatus(LiveExamModule.DRAFT);
		module.setFinallySaved(false);
		return view(module);
	}

	@Transactional
	public ModuleView finalSave(UUID id) {
		LiveExamModule module = requireOpen(id);
		module.setFinallySaved(true);
		return view(module);
	}

	@Transactional
	public ModuleView publish(UUID id) {
		LiveExamModule module = requireOpen(id);
		if (!module.isFinallySaved()) {
			throw new ApiException(ErrorCode.FINAL_SAVE_REQUIRED, HttpStatus.CONFLICT,
					"Publish requires a final save.");
		}
		visibility.requireOpen(module.getCategoryId());
		module.setStatus(LiveExamModule.PUBLISHED);
		return view(module);
	}

	@Transactional(readOnly = true)
	public Page<ModuleView> list(UUID categoryId, Pageable pageable, boolean publishedOnly, UUID studentId) {
		Pageable ordered = ordered(pageable);
		Page<LiveExamModule> page;
		if (publishedOnly) {
			Set<UUID> hidden = hiddenCategories();
			page = categoryId == null ? modules.findVisibleToStudent(studentId, hidden, ordered)
					: modules.findVisibleToStudentInCategory(studentId, categoryId, hidden, ordered);
		}
		else if (categoryId == null) {
			page = modules.findByDeletedAtIsNull(ordered);
		}
		else {
			page = modules.findByCategoryIdAndDeletedAtIsNull(categoryId, ordered);
		}
		return page.map(this::view);
	}

	@Transactional(readOnly = true)
	public Page<ModuleView> listForInstructor(UUID instructorId, Pageable pageable) {
		return modules.findAssignedToInstructor(instructorId, ordered(pageable)).map(this::view);
	}

	@Transactional(readOnly = true)
	public ModuleView get(UUID id) {
		return view(require(id));
	}

	@Transactional(readOnly = true)
	public ModuleView getPublished(UUID id, UUID studentId) {
		LiveExamModule module = require(id);
		if (!LiveExamModule.PUBLISHED.equals(module.getStatus()) || visibility.branchHidden(module.getCategoryId())
				|| !roster.existsByModuleIdAndStudentId(id, studentId)) {
			throw notFound();
		}
		return view(module);
	}

	@Transactional
	public void delete(UUID id) {
		LiveExamModule module = require(id);
		module.setDeletedAt(Instant.now());
		audit.record(AuditRecorder.LIVE_EXAM_MODULE_DELETE, AuditRecorder.LIVE_EXAM_MODULE_ENTITY, module.getId());
	}

	@Transactional
	public void archive(UUID id) {
		LiveExamModule module = require(id);
		if (LiveExamModule.ARCHIVED.equals(module.getStatus())) {
			return;
		}
		module.setStatus(LiveExamModule.ARCHIVED);
		audit.record(AuditRecorder.LIVE_EXAM_MODULE_ARCHIVE, AuditRecorder.LIVE_EXAM_MODULE_ENTITY, module.getId());
	}

	private int readPosition(JsonNode body, UUID categoryId, UUID ignoreId) {
		int position = body.has("position") && !body.get("position").isNull() ? body.get("position").asInt()
				: nextPosition(categoryId, ignoreId);
		if (positionTaken(categoryId, position, ignoreId)) {
			throw new ApiException(ErrorCode.CATEGORY_POSITION_TAKEN, HttpStatus.CONFLICT,
					"That position is already used under this category.");
		}
		return position;
	}

	private int nextPosition(UUID categoryId, UUID ignoreId) {
		int max = -1;
		for (LiveExamModule sibling : modules.findByCategoryIdAndDeletedAtIsNull(categoryId)) {
			if (ignoreId == null || !sibling.getId().equals(ignoreId)) {
				max = Math.max(max, sibling.getPosition());
			}
		}
		return max + 1;
	}

	private boolean positionTaken(UUID categoryId, int position, UUID ignoreId) {
		for (LiveExamModule sibling : modules.findByCategoryIdAndDeletedAtIsNull(categoryId)) {
			if (sibling.getPosition() == position && (ignoreId == null || !sibling.getId().equals(ignoreId))) {
				return true;
			}
		}
		return false;
	}

	private LiveExamModule requireOpen(UUID id) {
		LiveExamModule module = require(id);
		if (LiveExamModule.ARCHIVED.equals(module.getStatus())) {
			throw new ApiException(ErrorCode.MODULE_ARCHIVED, HttpStatus.CONFLICT, "An archived module cannot be changed.");
		}
		return module;
	}

	private LiveExamModule require(UUID id) {
		return modules.findByIdAndDeletedAtIsNull(id).orElseThrow(this::notFound);
	}

	private Set<UUID> hiddenCategories() {
		Set<UUID> hidden = visibility.hiddenCategoryIds();
		if (hidden.isEmpty()) {
			return Set.of(NO_HIDDEN_CATEGORY);
		}
		return hidden;
	}

	private static Pageable ordered(Pageable pageable) {
		if (pageable.getSort().isSorted()) {
			return pageable;
		}
		return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
				Sort.by(Sort.Order.asc("position"), Sort.Order.asc("id")));
	}

	private static void rejectLockedFields(JsonNode body) {
		if (body.has("price") || body.has("courseId")) {
			throw validationFailed();
		}
		if (body.has("accessMode")) {
			JsonNode mode = body.get("accessMode");
			if (mode.isNull() || !LiveExamModule.MANUAL.equals(mode.asText().trim())) {
				throw validationFailed();
			}
		}
	}

	private static UUID readCategoryId(JsonNode body) {
		JsonNode node = body.get("categoryId");
		if (node == null || node.isNull() || node.asText().isBlank()) {
			throw validationFailed();
		}
		return UUID.fromString(node.asText());
	}

	private static String requiredText(JsonNode body, String field) {
		JsonNode node = body.get(field);
		if (node == null || node.isNull() || node.asText().isBlank()) {
			throw validationFailed();
		}
		return node.asText().trim();
	}

	private ModuleView view(LiveExamModule module) {
		return new ModuleView(module.getId(), module.getCategoryId(), module.getTitleEn(), module.getTitleBn(),
				module.getDescriptionEn(), module.getDescriptionBn(), module.getAccessMode(), module.getStatus(),
				module.isFinallySaved(), module.getPosition());
	}

	private static ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
	}

	private ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	public record ModuleView(UUID id, UUID categoryId, String titleEn, String titleBn, String descriptionEn,
			String descriptionBn, String accessMode, String status, boolean finallySaved, int position) {
	}

}
