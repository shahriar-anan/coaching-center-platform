package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
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

	private final LiveExamCatalogVisibility visibility;

	private final LiveExamAccess access;

	private final AuditRecorder audit;

	private final ExamMailService examMail;

	private final AfterCommit afterCommit;

	private final PackageRevisionService revisions;

	private final LiveExamPackageExamRepository memberships;

	private final Clock clock;

	private final RichTextSanitizer sanitizer;

	public LiveExamModuleService(LiveExamModuleRepository modules, LiveExamCatalogVisibility visibility,
			LiveExamAccess access, AuditRecorder audit, ExamMailService examMail, AfterCommit afterCommit,
			PackageRevisionService revisions, LiveExamPackageExamRepository memberships, Clock clock,
			RichTextSanitizer sanitizer) {
		this.modules = modules;
		this.visibility = visibility;
		this.access = access;
		this.audit = audit;
		this.examMail = examMail;
		this.afterCommit = afterCommit;
		this.revisions = revisions;
		this.memberships = memberships;
		this.clock = clock;
		this.sanitizer = sanitizer;
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
		module.setDescriptionEn(sanitizer.clean(requiredText(body, "descriptionEn")));
		module.setDescriptionBn(sanitizer.clean(requiredText(body, "descriptionBn")));
		module.setAccessMode(LiveExamModule.MANUAL);
		module.setStatus(LiveExamModule.DRAFT);
		module.setFinallySaved(false);
		module.setPosition(readPosition(body, categoryId, null));
		if (body.has("price")) {
			module.setPrice(readPrice(body.get("price")));
		}
		module.setDiscountPercent(readDiscountPercent(body));
		module.setDiscountEndsAt(readDiscountEndsAt(body));
		ExamPricing.validate(module.getPrice(), false, module.getDiscountPercent(), module.getDiscountEndsAt());
		return view(modules.save(module));
	}

	/**
	 * A draft is edited in place. A published package takes the edit as a staged revision (E34): the published row keeps
	 * its values until the Master Admin publishes, and its status stays PUBLISHED.
	 */
	@Transactional
	public ModuleView update(UUID id, JsonNode body) {
		rejectLockedFields(body);
		LiveExamModule module = requireOpen(id);
		if (LiveExamModule.PUBLISHED.equals(module.getStatus())) {
			PackageRevisionService.Draft staged = edit(module, revisions.workingCopy(module), body);
			revisions.stage(module, staged);
			return view(module, staged, true);
		}
		PackageRevisionService.Draft next = edit(module, PackageRevisionService.Draft.of(module, List.of()), body);
		PackageRevisionService.applyFields(module, next);
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

	/** A first publish of a draft, or a publish without a revision id (the compatibility path, contracts §7 item 4). */
	@Transactional
	public ModuleView publish(UUID id) {
		return publish(id, null);
	}

	/**
	 * First publish of a draft, as before. On a published package the call publishes its staged revision, and
	 * {@code revisionId} must be the current one.
	 */
	@Transactional
	public ModuleView publish(UUID id, String revisionId) {
		LiveExamModule module = requireOpen(id);
		if (LiveExamModule.PUBLISHED.equals(module.getStatus())) {
			revisions.publish(module, revisionId);
			return view(module);
		}
		if (!module.isFinallySaved()) {
			throw new ApiException(ErrorCode.FINAL_SAVE_REQUIRED, HttpStatus.CONFLICT,
					"Publish requires a final save.");
		}
		visibility.requireOpen(module.getCategoryId());
		module.setStatus(LiveExamModule.PUBLISHED);
		UUID moduleId = module.getId();
		afterCommit.run(() -> examMail.onModulePublished(moduleId));
		return view(module);
	}

	/**
	 * {@code examType} applies to the student read only, as {@link LiveExamTypeFilter} parsed it. {@code popularOnly} keeps
	 * the popular packages (NR-01, B5).
	 */
	@Transactional(readOnly = true)
	public Page<ModuleView> list(UUID categoryId, Pageable pageable, boolean publishedOnly, UUID studentId,
			String examType, boolean popularOnly) {
		Pageable ordered = ordered(pageable);
		Page<LiveExamModule> page;
		if (publishedOnly) {
			Set<UUID> hidden = hiddenCategories();
			page = modules.findVisibleToStudentCatalog(studentId, hidden, categoryId == null,
					categoryId == null ? NO_HIDDEN_CATEGORY : categoryId, popularOnly,
					LiveExamTypeFilter.ANY.equals(examType), examType, ordered);
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
	public ModuleView getPublished(UUID id, UUID studentId) {
		LiveExamModule module = require(id);
		if (!LiveExamModule.PUBLISHED.equals(module.getStatus()) || visibility.branchHidden(module.getCategoryId())
				|| !access.hasModule(studentId, id)) {
			throw notFound();
		}
		return view(module);
	}

	/** Soft delete. The package's memberships end now; the exams stay in their categories (NR-01, B1). */
	@Transactional
	public void delete(UUID id) {
		LiveExamModule module = require(id);
		Instant now = clock.instant();
		module.setDeletedAt(now);
		for (LiveExamPackageExam link : memberships.findActiveByModuleId(module.getId())) {
			link.setRemovedAt(now);
		}
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

	/** The package with the body's fields applied. Every check runs before anything is written. */
	private PackageRevisionService.Draft edit(LiveExamModule module, PackageRevisionService.Draft current, JsonNode body) {
		String titleEn = optionalText(body, "titleEn", current.titleEn());
		String titleBn = optionalText(body, "titleBn", current.titleBn());
		String descriptionEn = sanitizer.clean(optionalText(body, "descriptionEn", current.descriptionEn()));
		String descriptionBn = sanitizer.clean(optionalText(body, "descriptionBn", current.descriptionBn()));
		UUID categoryId = current.categoryId();
		boolean categoryChanged = false;
		if (body.has("categoryId")) {
			categoryId = readCategoryId(body);
			visibility.requireOpen(categoryId);
			categoryChanged = true;
		}
		int position = current.position();
		if (body.has("position") && !body.get("position").isNull()) {
			position = checkPosition(categoryId, body.get("position").asInt(), module.getId());
		}
		else if (categoryChanged) {
			position = nextPosition(categoryId, module.getId());
		}
		BigDecimal price = body.has("price") ? readPrice(body.get("price")) : current.price();
		Integer discountPercent = body.has("discountPercent") ? readDiscountPercent(body) : current.discountPercent();
		Instant discountEndsAt = body.has("discountEndsAt") ? readDiscountEndsAt(body) : current.discountEndsAt();
		ExamPricing.validate(price, false, discountPercent, discountEndsAt);
		return new PackageRevisionService.Draft(titleEn, titleBn, descriptionEn, descriptionBn, categoryId, position,
				price, discountPercent, discountEndsAt, current.examIds());
	}

	private int checkPosition(UUID categoryId, int position, UUID ignoreId) {
		if (positionTaken(categoryId, position, ignoreId)) {
			throw new ApiException(ErrorCode.CATEGORY_POSITION_TAKEN, HttpStatus.CONFLICT,
					"That position is already used under this category.");
		}
		return position;
	}

	private int readPosition(JsonNode body, UUID categoryId, UUID ignoreId) {
		int position = body.has("position") && !body.get("position").isNull() ? body.get("position").asInt()
				: nextPosition(categoryId, ignoreId);
		return checkPosition(categoryId, position, ignoreId);
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

	static Pageable ordered(Pageable pageable) {
		if (pageable.getSort().isSorted()) {
			return pageable;
		}
		return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
				Sort.by(Sort.Order.asc("position"), Sort.Order.asc("id")));
	}

	private static void rejectLockedFields(JsonNode body) {
		if (body.has("courseId")) {
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

	private static String optionalText(JsonNode body, String field, String current) {
		return body.has(field) && !body.get(field).isNull() ? requiredText(body, field) : current;
	}

	private static String requiredText(JsonNode body, String field) {
		JsonNode node = body.get(field);
		if (node == null || node.isNull() || node.asText().isBlank()) {
			throw validationFailed();
		}
		return node.asText().trim();
	}

	private static BigDecimal readPrice(JsonNode node) {
		if (node == null || node.isNull()) {
			return null;
		}
		BigDecimal price;
		if (node.isNumber()) {
			price = node.decimalValue();
		}
		else {
			try {
				price = new BigDecimal(node.asText().trim());
			}
			catch (NumberFormatException exception) {
				throw validationFailed();
			}
		}
		if (price.compareTo(BigDecimal.ZERO) <= 0 || price.scale() > 2) {
			throw validationFailed();
		}
		return price.setScale(2, RoundingMode.UNNECESSARY);
	}

	/** A whole percentage, or null for none. The range is checked by {@link ExamPricing#validate}. */
	private static Integer readDiscountPercent(JsonNode body) {
		JsonNode node = body.get("discountPercent");
		if (node == null || node.isNull()) {
			return null;
		}
		if (!node.isNumber()) {
			throw validationFailed();
		}
		return node.intValue();
	}

	private static Instant readDiscountEndsAt(JsonNode body) {
		JsonNode node = body.get("discountEndsAt");
		if (node == null || node.isNull() || node.asText().isBlank()) {
			return null;
		}
		try {
			return Instant.parse(node.asText().trim());
		}
		catch (DateTimeParseException exception) {
			throw validationFailed();
		}
	}

	/** A student's or public read: the published row, with no staged change. */
	private ModuleView view(LiveExamModule module) {
		return build(module, PackageRevisionService.Draft.of(module, List.of()), false, null, null, null, null);
	}

	/** A staff read of a package with a staged revision: the working copy, flagged as pending. */
	ModuleView view(LiveExamModule module, PackageRevisionService.Draft working, boolean pending) {
		return build(module, working, pending, null, null, null, null);
	}

	/** Staff reads only: the category path, the teacher and the exam counts. Student and public reads never get these. */
	ModuleView staffView(LiveExamModule module, PackageRevisionService.Draft working, List<CategoryRef> categoryPath,
			TeacherRef instructor, int mcqCount, int writtenCount) {
		PackageRevisionService.Draft shown = working == null ? PackageRevisionService.Draft.of(module, List.of()) : working;
		return build(module, shown, working != null, categoryPath, instructor, mcqCount, writtenCount);
	}

	private ModuleView build(LiveExamModule module, PackageRevisionService.Draft shown, boolean pending,
			List<CategoryRef> categoryPath, TeacherRef instructor, Integer mcqCount, Integer writtenCount) {
		LiveExamModule priced = new LiveExamModule();
		priced.setPrice(shown.price());
		priced.setDiscountPercent(shown.discountPercent());
		priced.setDiscountEndsAt(shown.discountEndsAt());
		return new ModuleView(module.getId(), shown.categoryId(), shown.titleEn(), shown.titleBn(), shown.descriptionEn(),
				shown.descriptionBn(), module.getAccessMode(), module.getStatus(), module.isFinallySaved(), shown.position(),
				shown.price(), categoryPath, instructor, mcqCount, writtenCount, ModuleCoverService.coverPath(module),
				shown.discountPercent(), shown.discountEndsAt(), module.isPopular(), ExamPricing.view(priced, clock.instant()),
				pending);
	}

	private static ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
	}

	private ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	/**
	 * The category path and teacher and counts are filled on staff reads only; they are null everywhere else. The discount,
	 * popular and pricing fields are the package's own. {@code pendingChanges} is true when a staged revision exists, and
	 * the other fields then show the working copy.
	 */
	public record ModuleView(UUID id, UUID categoryId, String titleEn, String titleBn, String descriptionEn,
			String descriptionBn, String accessMode, String status, boolean finallySaved, int position,
			BigDecimal price, List<CategoryRef> categoryPath, TeacherRef instructor, Integer mcqCount,
			Integer writtenCount, String coverUrl, Integer discountPercent, Instant discountEndsAt, boolean popular,
			PriceView pricing, boolean pendingChanges) {
	}

	/** One step of a module's category path, root first. */
	public record CategoryRef(UUID id, String titleEn, String titleBn) {
	}

	/** A package an exam is in: the shape every exam row carries (NR-01, B2 and B3). */
	public record PackageRef(UUID id, String titleEn, String titleBn) {
	}

	/** The module's current teacher. Phone is shown to staff only. */
	public record TeacherRef(UUID id, String name, String phone) {
	}

}
