package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

import tools.jackson.databind.ObjectMapper;

/**
 * Staged edits to a published package (NR-01, E34 and B4). The published row stays live until the Master Admin publishes
 * the revision after reading its change list. A revision holds the whole proposed package: its fields and its exams in
 * order. Staged membership changes (B1) go through here too.
 */
@Service
public class PackageRevisionService {

	private static final Logger log = LoggerFactory.getLogger(PackageRevisionService.class);

	private final LiveExamModuleRepository modules;

	private final LiveExamModuleRevisionRepository revisions;

	private final LiveExamPackageExamRepository memberships;

	private final LiveExamRepository exams;

	private final LiveExamChangeLogRepository changeLogs;

	private final LiveExamMemberships membership;

	private final LiveExamCatalogVisibility visibility;

	private final AuditRecorder audit;

	private final ObjectMapper json;

	private final Clock clock;

	public PackageRevisionService(LiveExamModuleRepository modules, LiveExamModuleRevisionRepository revisions,
			LiveExamPackageExamRepository memberships, LiveExamRepository exams, LiveExamChangeLogRepository changeLogs,
			LiveExamMemberships membership, LiveExamCatalogVisibility visibility, AuditRecorder audit, ObjectMapper json,
			Clock clock) {
		this.modules = modules;
		this.revisions = revisions;
		this.memberships = memberships;
		this.exams = exams;
		this.changeLogs = changeLogs;
		this.membership = membership;
		this.visibility = visibility;
		this.audit = audit;
		this.json = json;
		this.clock = clock;
	}

	/** What a package looks like with its staged edits applied: its fields, and its exams in order. */
	public record Draft(String titleEn, String titleBn, String descriptionEn, String descriptionBn, UUID categoryId,
			int position, BigDecimal price, Integer discountPercent, Instant discountEndsAt, List<UUID> examIds) {

		/** The package as it is published now, with the given exams in order. */
		public static Draft of(LiveExamModule module, List<UUID> examIds) {
			return new Draft(module.getTitleEn(), module.getTitleBn(), module.getDescriptionEn(), module.getDescriptionBn(),
					module.getCategoryId(), module.getPosition(), module.getPrice(), module.getDiscountPercent(),
					module.getDiscountEndsAt(), List.copyOf(examIds));
		}

		public Draft withExamIds(List<UUID> next) {
			return new Draft(titleEn, titleBn, descriptionEn, descriptionBn, categoryId, position, price, discountPercent,
					discountEndsAt, List.copyOf(next));
		}

	}

	/** The package as staff edit it: the staged draft when there is one, else the published row with its exams. */
	@Transactional(readOnly = true)
	public Draft workingCopy(LiveExamModule module) {
		return revisions.findByModuleId(module.getId()).map(this::read)
			.orElseGet(() -> Draft.of(module, activeExamIds(module.getId())));
	}

	/** The staged draft of every package in {@code moduleIds} that has one. Staff reads only. */
	Map<UUID, Draft> stagedDrafts(Collection<UUID> moduleIds) {
		Map<UUID, Draft> byModule = new HashMap<>();
		if (moduleIds.isEmpty()) {
			return byModule;
		}
		for (LiveExamModuleRevision row : revisions.findByModuleIdIn(moduleIds)) {
			byModule.put(row.getModuleId(), read(row));
		}
		return byModule;
	}

	boolean hasRevision(UUID moduleId) {
		return revisions.findByModuleId(moduleId).isPresent();
	}

	/** Writes {@code draft} as the package's revision, creating the revision on the first staged edit. */
	void stage(LiveExamModule module, Draft draft) {
		Instant now = clock.instant();
		UUID actor = actorId();
		LiveExamModuleRevision row = new LiveExamModuleRevision();
		row.setModuleId(module.getId());
		row.setCreatedBy(actor);
		row.setCreatedAt(now);
		revisions.findByModuleId(module.getId()).ifPresent(previous -> {
			// A new edit gets a new revision id, so a publish that names the old one is refused as stale (E31).
			row.setCreatedBy(previous.getCreatedBy());
			row.setCreatedAt(previous.getCreatedAt());
			revisions.delete(previous);
			revisions.flush();
		});
		row.setPayload(write(draft));
		row.setUpdatedBy(actor);
		row.setUpdatedAt(now);
		revisions.save(row);
	}

	/** The change list the Master Admin reads before publishing (E31). 404 when nothing is staged. */
	@Transactional(readOnly = true)
	public ChangeList pendingChanges(UUID moduleId) {
		LiveExamModule module = requireModule(moduleId);
		LiveExamModuleRevision row = revisions.findByModuleId(moduleId)
			.orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found."));
		return changes(module, row);
	}

	@Transactional
	public void discard(UUID moduleId) {
		requireModule(moduleId);
		LiveExamModuleRevision row = revisions.findByModuleId(moduleId).orElseThrow(PackageRevisionService::nothingStaged);
		revisions.delete(row);
		audit.record(AuditRecorder.LIVE_EXAM_MODULE_REVISION_DISCARD, AuditRecorder.LIVE_EXAM_MODULE_ENTITY, moduleId);
	}

	/**
	 * Publishes the staged draft onto the package and its memberships, then logs the change list. {@code revisionId} must
	 * be the current revision; null is the web's compatibility path, which the caller has already limited to the Master
	 * Admin (contracts §7 item 4).
	 */
	@Transactional
	public void publish(LiveExamModule module, String revisionId) {
		LiveExamModuleRevision row = revisions.findByModuleId(module.getId())
			.orElseThrow(PackageRevisionService::nothingStaged);
		if (revisionId != null && !revisionId.equals(row.getId().toString())) {
			throw new ApiException(ErrorCode.PENDING_CHANGES_STALE, HttpStatus.CONFLICT,
					"The staged changes have changed since they were read.");
		}
		if (revisionId == null) {
			log.warn("Package {} was published without a revisionId", module.getId());
		}
		UUID moduleId = module.getId();
		Instant now = clock.instant();
		ChangeList list = changes(module, row);
		Draft draft = read(row);
		ExamPricing.validate(draft.price(), false, draft.discountPercent(), draft.discountEndsAt());
		visibility.requireOpen(draft.categoryId());
		applyFields(module, draft);

		Set<UUID> wanted = new HashSet<>(draft.examIds());
		Set<UUID> present = new HashSet<>();
		for (LiveExamPackageExam link : memberships.findActiveByModuleId(moduleId)) {
			present.add(link.getExamId());
			if (!wanted.contains(link.getExamId())) {
				link.setRemovedAt(now);
			}
		}
		for (UUID examId : draft.examIds()) {
			if (!present.contains(examId)) {
				membership.attach(moduleId, examId, now);
			}
		}
		Map<UUID, LiveExamPackageExam> active = new HashMap<>();
		for (LiveExamPackageExam link : memberships.findActiveByModuleId(moduleId)) {
			active.put(link.getExamId(), link);
		}
		for (int position = 0; position < draft.examIds().size(); position++) {
			LiveExamPackageExam link = active.get(draft.examIds().get(position));
			if (link != null) {
				link.setPosition(position);
			}
		}

		LiveExamChangeLog entry = new LiveExamChangeLog();
		entry.setModuleId(moduleId);
		entry.setChanges(write(list));
		entry.setRescored(0);
		entry.setPublishedBy(actorId());
		entry.setPublishedAt(now);
		changeLogs.save(entry);
		revisions.delete(row);
		audit.record(AuditRecorder.LIVE_EXAM_MODULE_REVISION_PUBLISH, AuditRecorder.LIVE_EXAM_MODULE_ENTITY, moduleId);
	}

	/** Copies a draft's fields onto the package row. Memberships are not touched here. */
	static void applyFields(LiveExamModule module, Draft draft) {
		module.setTitleEn(draft.titleEn());
		module.setTitleBn(draft.titleBn());
		module.setDescriptionEn(draft.descriptionEn());
		module.setDescriptionBn(draft.descriptionBn());
		module.setCategoryId(draft.categoryId());
		module.setPosition(draft.position());
		module.setPrice(draft.price());
		module.setDiscountPercent(draft.discountPercent());
		module.setDiscountEndsAt(draft.discountEndsAt());
	}

	private ChangeList changes(LiveExamModule module, LiveExamModuleRevision row) {
		Draft live = Draft.of(module, activeExamIds(module.getId()));
		Draft staged = read(row);
		List<FieldChange> fields = new ArrayList<>();
		addChange(fields, "titleEn", live.titleEn(), staged.titleEn());
		addChange(fields, "titleBn", live.titleBn(), staged.titleBn());
		addChange(fields, "descriptionEn", live.descriptionEn(), staged.descriptionEn());
		addChange(fields, "descriptionBn", live.descriptionBn(), staged.descriptionBn());
		addChange(fields, "categoryId", live.categoryId(), staged.categoryId());
		addChange(fields, "position", live.position(), staged.position());
		addChange(fields, "price", live.price(), staged.price());
		addChange(fields, "discountPercent", live.discountPercent(), staged.discountPercent());
		addChange(fields, "discountEndsAt", live.discountEndsAt(), staged.discountEndsAt());
		List<UUID> liveCommon = live.examIds().stream().filter(staged.examIds()::contains).toList();
		List<UUID> stagedCommon = staged.examIds().stream().filter(live.examIds()::contains).toList();
		if (!liveCommon.equals(stagedCommon)) {
			fields.add(new FieldChange("examOrder", liveCommon, stagedCommon));
		}

		Set<UUID> examIds = new HashSet<>(live.examIds());
		examIds.addAll(staged.examIds());
		Map<UUID, LiveExam> titles = new HashMap<>();
		for (LiveExam exam : exams.findAllById(examIds)) {
			titles.put(exam.getId(), exam);
		}
		List<MembershipChange> members = new ArrayList<>();
		for (UUID examId : staged.examIds()) {
			if (!live.examIds().contains(examId)) {
				members.add(membershipChange(examId, titles, "ADDED"));
			}
		}
		for (UUID examId : live.examIds()) {
			if (!staged.examIds().contains(examId)) {
				members.add(membershipChange(examId, titles, "REMOVED"));
			}
		}
		return new ChangeList(row.getId().toString(), fields, List.of(), null, members, new Impact(0, false, 0));
	}

	private static void addChange(List<FieldChange> fields, String field, Object before, Object after) {
		boolean same = before instanceof BigDecimal left && after instanceof BigDecimal right
				? left.compareTo(right) == 0
				: Objects.equals(before, after);
		if (!same) {
			fields.add(new FieldChange(field, before, after));
		}
	}

	private static MembershipChange membershipChange(UUID examId, Map<UUID, LiveExam> titles, String change) {
		LiveExam exam = titles.get(examId);
		return new MembershipChange(examId, exam == null ? null : exam.getTitleEn(),
				exam == null ? null : exam.getTitleBn(), change);
	}

	private List<UUID> activeExamIds(UUID moduleId) {
		return memberships.findActiveByModuleId(moduleId).stream().map(LiveExamPackageExam::getExamId).toList();
	}

	private LiveExamModule requireModule(UUID moduleId) {
		return modules.findByIdAndDeletedAtIsNull(moduleId)
			.orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found."));
	}

	private static ApiException nothingStaged() {
		return new ApiException(ErrorCode.NO_PENDING_CHANGES, HttpStatus.CONFLICT, "Nothing is staged for this package.");
	}

	private Draft read(LiveExamModuleRevision row) {
		return json.readValue(row.getPayload(), Draft.class);
	}

	private String write(Object value) {
		return json.writeValueAsString(value);
	}

	private static UUID actorId() {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor) {
			return actor.userId();
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

}
