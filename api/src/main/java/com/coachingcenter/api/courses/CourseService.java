package com.coachingcenter.api.courses;

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

import tools.jackson.databind.JsonNode;

@Service
public class CourseService {

	private static final Set<String> CONTENT_LANGUAGES = Set.of("BN", "EN");

	private static final Set<String> DELIVERY_MODES = Set.of("ONLINE", "OFFLINE", "HYBRID");

	private static final Set<String> ACCESS_TYPES = Set.of("FREEMIUM", "FULL_FREE");

	private static final Set<String> STATUSES = Set.of("DRAFT", "PUBLISHED", "ARCHIVED");

	private final CourseRepository courses;

	private final CourseCategoryService categories;

	private final AuditRecorder audit;

	public CourseService(CourseRepository courses, CourseCategoryService categories, AuditRecorder audit) {
		this.courses = courses;
		this.categories = categories;
		this.audit = audit;
	}

	@Transactional
	public CourseView create(JsonNode body, boolean canSetPrice) {
		String titleEn = requiredText(body, "titleEn");
		String titleBn = requiredText(body, "titleBn");
		String contentLanguage = requiredText(body, "contentLanguage");
		String description = requiredText(body, "description");
		String deliveryMode = requiredText(body, "deliveryMode");
		String accessType = requiredText(body, "accessType");
		validateContentLanguage(contentLanguage);
		validateDeliveryMode(deliveryMode);
		validateAccessType(accessType);
		rejectUnknownDescriptionFields(body);
		BigDecimal price = null;
		if (body.has("price") && !body.get("price").isNull()) {
			if (!canSetPrice) {
				throw denied();
			}
			price = parsePrice(body.get("price"));
		}
		if ("FULL_FREE".equals(accessType)) {
			price = null;
		}
		Course course = new Course();
		course.setTitleEn(titleEn);
		course.setTitleBn(titleBn);
		course.setContentLanguage(contentLanguage);
		course.setDescription(description);
		course.setDeliveryMode(deliveryMode);
		course.setAccessType(accessType);
		course.setStatus("DRAFT");
		course.setPrice(price);
		course.setClosed(false);
		course.setOpeningNumber(0);
		return view(courses.save(course));
	}

	@Transactional
	public CourseView update(UUID id, JsonNode body, boolean canSetPrice) {
		Course course = requireCourse(id);
		if (body.has("titleEn") && !body.get("titleEn").isNull()) {
			course.setTitleEn(requiredText(body, "titleEn"));
		}
		if (body.has("titleBn") && !body.get("titleBn").isNull()) {
			course.setTitleBn(requiredText(body, "titleBn"));
		}
		if (body.has("contentLanguage") && !body.get("contentLanguage").isNull()) {
			String language = requiredText(body, "contentLanguage");
			validateContentLanguage(language);
			course.setContentLanguage(language);
		}
		if (body.has("description") && !body.get("description").isNull()) {
			course.setDescription(requiredText(body, "description"));
		}
		rejectUnknownDescriptionFields(body);
		if (body.has("deliveryMode") && !body.get("deliveryMode").isNull()) {
			String mode = requiredText(body, "deliveryMode");
			validateDeliveryMode(mode);
			course.setDeliveryMode(mode);
		}
		if (body.has("accessType") && !body.get("accessType").isNull()) {
			String accessType = requiredText(body, "accessType");
			validateAccessType(accessType);
			if (!accessType.equals(course.getAccessType())) {
				throw conflict(ErrorCode.ACCESS_TYPE_FIXED, "Course access type cannot be changed.");
			}
		}
		if (body.has("price")) {
			if (!canSetPrice) {
				throw denied();
			}
			if (body.get("price").isNull()) {
				course.setPrice(null);
			}
			else {
				course.setPrice(parsePrice(body.get("price")));
			}
		}
		if ("FULL_FREE".equals(course.getAccessType())) {
			course.setPrice(null);
		}
		if (body.has("status") && !body.get("status").isNull()) {
			String status = requiredText(body, "status");
			validateStatus(status);
			if (!status.equals(course.getStatus())) {
				if ("PUBLISHED".equals(status)) {
					validatePublishable(course);
					if (!"PUBLISHED".equals(course.getStatus())) {
						course.setOpeningNumber(1);
						course.setClosed(false);
					}
				}
				course.setStatus(status);
			}
		}
		if (body.has("categoryId") || body.has("categoryPosition")) {
			categories.assign(course, body);
		}
		return view(course);
	}

	@Transactional
	public CourseView close(UUID id) {
		Course course = requireCourse(id);
		if (!"PUBLISHED".equals(course.getStatus())) {
			throw conflict(ErrorCode.COURSE_NOT_PUBLISHED, "Only a published course can be closed.");
		}
		if (course.isClosed()) {
			return view(course);
		}
		course.setClosed(true);
		audit.record(AuditRecorder.COURSE_CLOSE, AuditRecorder.COURSE_ENTITY, course.getId());
		return view(course);
	}

	@Transactional
	public CourseView reopen(UUID id) {
		Course course = requireCourse(id);
		if (!"PUBLISHED".equals(course.getStatus()) || !course.isClosed()) {
			throw conflict(ErrorCode.COURSE_NOT_PUBLISHED, "Only a closed published course can be reopened.");
		}
		course.setOpeningNumber(course.getOpeningNumber() + 1);
		course.setClosed(false);
		audit.record(AuditRecorder.COURSE_REOPEN, AuditRecorder.COURSE_ENTITY, course.getId());
		return view(course);
	}

	@Transactional(readOnly = true)
	public Page<CourseView> list(Pageable pageable) {
		return courses.findByDeletedAtIsNull(pageable).map(this::view);
	}

	@Transactional(readOnly = true)
	public CourseView get(UUID id) {
		return view(requireCourse(id));
	}

	@Transactional
	public void delete(UUID id) {
		Course course = requireCourse(id);
		course.setDeletedAt(Instant.now());
		audit.record(AuditRecorder.COURSE_DELETE, AuditRecorder.COURSE_ENTITY, course.getId());
	}

	@Transactional
	public void archive(UUID id) {
		Course course = requireCourse(id);
		course.setStatus("ARCHIVED");
		audit.record(AuditRecorder.COURSE_ARCHIVE, AuditRecorder.COURSE_ENTITY, course.getId());
	}

	private void validatePublishable(Course course) {
		if ("FREEMIUM".equals(course.getAccessType()) && course.getPrice() == null) {
			throw conflict(ErrorCode.PRICE_REQUIRED, "A price is required before publishing a freemium course.");
		}
	}

	private Course requireCourse(UUID id) {
		return courses.findByIdAndDeletedAtIsNull(id).orElseThrow(this::notFound);
	}

	private CourseView view(Course course) {
		return new CourseView(course.getId(), course.getTitleEn(), course.getTitleBn(), course.getContentLanguage(),
				course.getDescription(), course.getPrice(), course.getDeliveryMode(), course.getAccessType(),
				course.getStatus(), course.isClosed(), course.getOpeningNumber(), course.getCoverFileId(),
				course.getCategoryId(), course.getCategoryPosition());
	}

	private static String requiredText(JsonNode body, String field) {
		JsonNode node = body.get(field);
		if (node == null || node.isNull() || node.asText().isBlank()) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
		}
		return node.asText().trim();
	}

	private static void rejectUnknownDescriptionFields(JsonNode body) {
		if (body.has("descriptionEn") || body.has("descriptionBn")) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
		}
	}

	private static BigDecimal parsePrice(JsonNode node) {
		if (node.isNumber()) {
			return node.decimalValue();
		}
		try {
			return new BigDecimal(node.asText().trim());
		}
		catch (NumberFormatException exception) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
		}
	}

	private static void validateContentLanguage(String language) {
		if (!CONTENT_LANGUAGES.contains(language)) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
		}
	}

	private static void validateDeliveryMode(String mode) {
		if (!DELIVERY_MODES.contains(mode)) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
		}
	}

	private static void validateAccessType(String accessType) {
		if (!ACCESS_TYPES.contains(accessType)) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
		}
	}

	private static void validateStatus(String status) {
		if (!STATUSES.contains(status)) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
		}
	}

	private static ApiException conflict(ErrorCode code, String message) {
		return new ApiException(code, HttpStatus.CONFLICT, message);
	}

	private ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	private static ApiException denied() {
		return new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
	}

	public record CourseView(UUID id, String titleEn, String titleBn, String contentLanguage, String description,
			BigDecimal price, String deliveryMode, String accessType, String status, boolean closed, int openingNumber,
			UUID coverFileId, UUID categoryId, Integer categoryPosition) {
	}

}
