package com.coachingcenter.api.exams;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

/**
 * Which exams a package holds, and in what order (NR-01, R2 and B1). A published package takes these changes as a staged
 * revision; a draft applies them at once. Access is not decided here: a student keeps an exam removed later (R4) through
 * {@link LiveExamAccess}.
 */
@Service
public class PackageMembershipService {

	private final LiveExamModuleRepository modules;

	private final LiveExamPackageExamRepository memberships;

	private final LiveExamRepository exams;

	private final LiveExamMemberships membership;

	private final PackageRevisionService revisions;

	private final AuditRecorder audit;

	private final Clock clock;

	public PackageMembershipService(LiveExamModuleRepository modules, LiveExamPackageExamRepository memberships,
			LiveExamRepository exams, LiveExamMemberships membership, PackageRevisionService revisions,
			AuditRecorder audit, Clock clock) {
		this.modules = modules;
		this.memberships = memberships;
		this.exams = exams;
		this.membership = membership;
		this.revisions = revisions;
		this.audit = audit;
		this.clock = clock;
	}

	/** Adds an existing exam at the end of the package. Any category may hold any package (B1). */
	@Transactional
	public MembershipView add(UUID moduleId, UUID examId) {
		LiveExamModule module = requireOpen(moduleId);
		LiveExam exam = exams.findByIdAndDeletedAtIsNull(examId).orElseThrow(this::notFound);
		if (LiveExam.ARCHIVED.equals(exam.getStatus())) {
			throw new ApiException(ErrorCode.EXAM_ARCHIVED, HttpStatus.CONFLICT, "A withdrawn exam cannot join a package.");
		}
		if (LiveExamModule.PUBLISHED.equals(module.getStatus())) {
			PackageRevisionService.Draft draft = revisions.workingCopy(module);
			if (draft.examIds().contains(examId)) {
				throw alreadyInPackage();
			}
			List<UUID> next = new ArrayList<>(draft.examIds());
			next.add(examId);
			revisions.stage(module, draft.withExamIds(next));
			audit.record(AuditRecorder.LIVE_EXAM_PACKAGE_EXAM_ADD, AuditRecorder.LIVE_EXAM_MODULE_ENTITY, moduleId);
			return new MembershipView(moduleId, next, true);
		}
		if (memberships.existsActive(moduleId, examId)) {
			throw alreadyInPackage();
		}
		membership.attach(moduleId, examId, clock.instant());
		audit.record(AuditRecorder.LIVE_EXAM_PACKAGE_EXAM_ADD, AuditRecorder.LIVE_EXAM_MODULE_ENTITY, moduleId);
		return view(moduleId, false);
	}

	/**
	 * Adds several exams at once (B1). The package row is locked first, so a concurrent add cannot race it. Each id is
	 * added or skipped with a reason, in the order the caller sent them. A published package stages the additions as one
	 * revision.
	 */
	@Transactional
	public BulkResult bulkAdd(UUID moduleId, List<UUID> examIds) {
		LiveExamModule module = modules.lockById(moduleId).filter(row -> row.getDeletedAt() == null).orElseThrow(this::notFound);
		if (LiveExamModule.ARCHIVED.equals(module.getStatus())) {
			throw new ApiException(ErrorCode.MODULE_ARCHIVED, HttpStatus.CONFLICT, "An archived module cannot be changed.");
		}
		List<UUID> requested = new ArrayList<>(new LinkedHashSet<>(examIds));
		Map<UUID, LiveExam> loaded = new HashMap<>();
		for (LiveExam exam : exams.findAllById(requested)) {
			loaded.put(exam.getId(), exam);
		}
		boolean published = LiveExamModule.PUBLISHED.equals(module.getStatus());
		PackageRevisionService.Draft draft = published ? revisions.workingCopy(module) : null;
		List<UUID> next = published ? new ArrayList<>(draft.examIds()) : null;
		Set<UUID> inDraft = published ? new HashSet<>(draft.examIds()) : null;
		List<UUID> added = new ArrayList<>();
		List<Skipped> skipped = new ArrayList<>();
		for (UUID examId : requested) {
			LiveExam exam = loaded.get(examId);
			if (exam == null || exam.getDeletedAt() != null) {
				skipped.add(new Skipped(examId, "NOT_FOUND"));
				continue;
			}
			if (LiveExam.ARCHIVED.equals(exam.getStatus())) {
				skipped.add(new Skipped(examId, "ARCHIVED"));
				continue;
			}
			boolean already = published ? inDraft.contains(examId) : memberships.existsActive(moduleId, examId);
			if (already) {
				skipped.add(new Skipped(examId, "ALREADY_IN_PACKAGE"));
				continue;
			}
			if (published) {
				next.add(examId);
				inDraft.add(examId);
			}
			else {
				membership.attach(moduleId, examId, clock.instant());
			}
			added.add(examId);
			audit.record(AuditRecorder.LIVE_EXAM_PACKAGE_EXAM_ADD, AuditRecorder.LIVE_EXAM_MODULE_ENTITY, moduleId);
		}
		if (published && !added.isEmpty()) {
			revisions.stage(module, draft.withExamIds(next));
		}
		boolean pending = published && revisions.hasRevision(moduleId);
		return new BulkResult(added, skipped, pending);
	}

	/** Removes an exam from the package. Students who held the package keep the exam while their access lasts (R4). */
	@Transactional
	public void remove(UUID moduleId, UUID examId) {
		LiveExamModule module = requireOpen(moduleId);
		if (LiveExamModule.PUBLISHED.equals(module.getStatus())) {
			PackageRevisionService.Draft draft = revisions.workingCopy(module);
			if (!draft.examIds().contains(examId)) {
				throw notFound();
			}
			List<UUID> next = new ArrayList<>(draft.examIds());
			next.remove(examId);
			revisions.stage(module, draft.withExamIds(next));
		}
		else {
			LiveExamPackageExam link = memberships.findActiveByModuleId(moduleId).stream()
				.filter(row -> row.getExamId().equals(examId))
				.findFirst()
				.orElseThrow(this::notFound);
			link.setRemovedAt(clock.instant());
			renumber(moduleId);
		}
		audit.record(AuditRecorder.LIVE_EXAM_PACKAGE_EXAM_REMOVE, AuditRecorder.LIVE_EXAM_MODULE_ENTITY, moduleId);
	}

	/**
	 * Sets the exam order. {@code examIds} must name exactly the package's exams, each once. A published package stages the
	 * order.
	 */
	@Transactional
	public MembershipView reorder(UUID moduleId, List<UUID> examIds) {
		LiveExamModule module = requireOpen(moduleId);
		if (LiveExamModule.PUBLISHED.equals(module.getStatus())) {
			PackageRevisionService.Draft draft = revisions.workingCopy(module);
			requireSameMembers(draft.examIds(), examIds);
			revisions.stage(module, draft.withExamIds(examIds));
			return new MembershipView(moduleId, List.copyOf(examIds), true);
		}
		List<UUID> current = activeExamIds(moduleId);
		requireSameMembers(current, examIds);
		for (LiveExamPackageExam link : memberships.findActiveByModuleId(moduleId)) {
			link.setPosition(examIds.indexOf(link.getExamId()));
		}
		return view(moduleId, false);
	}

	/** The package's exams as staff see them: a published package shows its staged draft, so staged changes are included. */
	@Transactional(readOnly = true)
	public Members members(UUID moduleId) {
		LiveExamModule module = modules.findByIdAndDeletedAtIsNull(moduleId).orElseThrow(this::notFound);
		if (LiveExamModule.PUBLISHED.equals(module.getStatus())) {
			return new Members(revisions.workingCopy(module).examIds(), revisions.hasRevision(moduleId));
		}
		return new Members(activeExamIds(moduleId), false);
	}

	private MembershipView view(UUID moduleId, boolean pending) {
		return new MembershipView(moduleId, activeExamIds(moduleId), pending);
	}

	/** Positions run 0 to n-1 again, so the next add lands at the end. */
	private void renumber(UUID moduleId) {
		int position = 0;
		for (LiveExamPackageExam link : memberships.findActiveByModuleId(moduleId)) {
			link.setPosition(position++);
		}
	}

	private List<UUID> activeExamIds(UUID moduleId) {
		return memberships.findActiveByModuleId(moduleId).stream().map(LiveExamPackageExam::getExamId).toList();
	}

	private static void requireSameMembers(List<UUID> current, List<UUID> requested) {
		Set<UUID> wanted = Set.copyOf(requested);
		if (requested.size() != wanted.size() || !wanted.equals(Set.copyOf(current))) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
		}
	}

	private LiveExamModule requireOpen(UUID moduleId) {
		LiveExamModule module = modules.findByIdAndDeletedAtIsNull(moduleId).orElseThrow(this::notFound);
		if (LiveExamModule.ARCHIVED.equals(module.getStatus())) {
			throw new ApiException(ErrorCode.MODULE_ARCHIVED, HttpStatus.CONFLICT, "An archived module cannot be changed.");
		}
		return module;
	}

	private static ApiException alreadyInPackage() {
		return new ApiException(ErrorCode.EXAM_ALREADY_IN_PACKAGE, HttpStatus.CONFLICT, "The exam is already in this package.");
	}

	private ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	/** The package's exams in order, and whether a staged revision is waiting. */
	public record MembershipView(UUID moduleId, List<UUID> examIds, boolean pendingChanges) {
	}

	/** A bulk add's outcome: the exams added, the ones skipped with a reason, and whether a revision is waiting. */
	public record BulkResult(List<UUID> added, List<Skipped> skipped, boolean pendingChanges) {
	}

	/** An exam a bulk add did not add. The reason is NOT_FOUND, ARCHIVED or ALREADY_IN_PACKAGE. */
	public record Skipped(UUID examId, String reason) {
	}

	/** The package's exam ids in order, including staged changes on a published package, and whether a revision is staged. */
	public record Members(List<UUID> examIds, boolean pendingChanges) {
	}

}
