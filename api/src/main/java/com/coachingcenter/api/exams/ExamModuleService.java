package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.courses.CourseRepository;

import tools.jackson.databind.JsonNode;

@Service
public class ExamModuleService {

	private static final Set<String> STATUSES = Set.of("DRAFT", "PUBLISHED", "ARCHIVED");

	private static final Set<String> ACCESS_MODES = Set.of("OPEN", "ENROLLED_ONLY", "PURCHASE_REQUIRED");

	private final ExamModuleRepository modules;

	private final CourseRepository courses;

	private final AuditRecorder audit;

	public ExamModuleService(ExamModuleRepository modules, CourseRepository courses, AuditRecorder audit) {
		this.modules = modules;
		this.courses = courses;
		this.audit = audit;
	}

	@Transactional
	public ModuleView create(JsonNode body, boolean canSetPrice) {
		ExamModule module = new ExamModule();
		module.setTitleEn(requiredText(body, "titleEn"));
		module.setTitleBn(requiredText(body, "titleBn"));
		module.setDescriptionEn(requiredText(body, "descriptionEn"));
		module.setDescriptionBn(requiredText(body, "descriptionBn"));
		module.setStatus("DRAFT");
		module.setPrice(readPrice(body, canSetPrice, true));
		return view(modules.save(module));
	}

	@Transactional
	public ModuleView update(UUID id, JsonNode body, boolean canSetPrice) {
		ExamModule module = requireModule(id);
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
		if (body.has("price")) {
			if (!canSetPrice) {
				throw denied();
			}
			if (body.get("price").isNull()) {
				module.setPrice(null);
			}
			else {
				module.setPrice(parsePrice(body.get("price")));
			}
		}
		if (body.has("status") && !body.get("status").isNull()) {
			String status = requiredText(body, "status");
			validateStatus(status);
			module.setStatus(status);
		}
		return view(module);
	}

	@Transactional(readOnly = true)
	public Page<ModuleView> list(Pageable pageable) {
		return modules.findByDeletedAtIsNull(pageable).map(this::view);
	}

	@Transactional(readOnly = true)
	public ModuleView get(UUID id) {
		return view(requireModule(id));
	}

	@Transactional
	public void delete(UUID id) {
		ExamModule module = requireModule(id);
		module.setDeletedAt(Instant.now());
		audit.record(AuditRecorder.EXAM_MODULE_DELETE, AuditRecorder.EXAM_MODULE_ENTITY, module.getId());
	}

	@Transactional
	public void archive(UUID id) {
		ExamModule module = requireModule(id);
		module.setStatus("ARCHIVED");
		audit.record(AuditRecorder.EXAM_MODULE_ARCHIVE, AuditRecorder.EXAM_MODULE_ENTITY, module.getId());
	}

	@Transactional
	public ModuleView patchAccess(UUID id, JsonNode body) {
		ExamModule module = requireModule(id);
		String accessMode = requiredText(body, "accessMode");
		if (!ACCESS_MODES.contains(accessMode)) {
			throw validationFailed();
		}
		UUID courseId = readCourseId(body);
		switch (accessMode) {
			case "OPEN" -> {
				if (courseId != null) {
					throw linkMismatch();
				}
				if (module.getPrice() != null) {
					throw priceNotAllowed();
				}
			}
			case "PURCHASE_REQUIRED" -> {
				if (courseId != null) {
					throw linkMismatch();
				}
				if (module.getPrice() == null) {
					throw new ApiException(ErrorCode.PRICE_REQUIRED, HttpStatus.CONFLICT, "Price is required.");
				}
			}
			case "ENROLLED_ONLY" -> {
				if (courseId == null) {
					throw linkMismatch();
				}
				if (module.getPrice() != null) {
					throw priceNotAllowed();
				}
				courses.findByIdAndDeletedAtIsNull(courseId).orElseThrow(this::notFound);
			}
			default -> throw validationFailed();
		}
		module.setAccessMode(accessMode);
		module.setCourseId(courseId);
		return view(module);
	}

	private ExamModule requireModule(UUID id) {
		return modules.findByIdAndDeletedAtIsNull(id).orElseThrow(this::notFound);
	}

	private BigDecimal readPrice(JsonNode body, boolean canSetPrice, boolean onCreate) {
		if (!body.has("price") || body.get("price").isNull()) {
			return null;
		}
		if (!canSetPrice) {
			throw denied();
		}
		return parsePrice(body.get("price"));
	}

	private static UUID readCourseId(JsonNode body) {
		if (!body.has("courseId") || body.get("courseId").isNull()) {
			return null;
		}
		return UUID.fromString(body.get("courseId").asText());
	}

	private static ApiException linkMismatch() {
		return new ApiException(ErrorCode.ACCESS_MODE_LINK_MISMATCH, HttpStatus.CONFLICT,
				"Access mode and course link do not match.");
	}

	private static ApiException priceNotAllowed() {
		return new ApiException(ErrorCode.PRICE_NOT_ALLOWED, HttpStatus.CONFLICT, "Price is not allowed for this mode.");
	}

	private ModuleView view(ExamModule module) {
		return new ModuleView(module.getId(), module.getTitleEn(), module.getTitleBn(), module.getDescriptionEn(),
				module.getDescriptionBn(), module.getPrice(), module.getStatus(), module.getAccessMode(),
				module.getCourseId());
	}

	private static String requiredText(JsonNode body, String field) {
		JsonNode node = body.get(field);
		if (node == null || node.isNull() || node.asText().isBlank()) {
			throw validationFailed();
		}
		return node.asText().trim();
	}

	private static BigDecimal parsePrice(JsonNode node) {
		if (node.isNumber()) {
			return node.decimalValue();
		}
		try {
			return new BigDecimal(node.asText().trim());
		}
		catch (NumberFormatException exception) {
			throw validationFailed();
		}
	}

	private static void validateStatus(String status) {
		if (!STATUSES.contains(status)) {
			throw validationFailed();
		}
	}

	private static ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
	}

	private ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	private static ApiException denied() {
		return new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
	}

	public record ModuleView(UUID id, String titleEn, String titleBn, String descriptionEn, String descriptionBn,
			BigDecimal price, String status, String accessMode, UUID courseId) {
	}

}
