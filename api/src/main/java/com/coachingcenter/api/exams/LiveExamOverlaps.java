package com.coachingcenter.api.exams;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * The exams and packages a student already holds that overlap an enrolment (plan 14.3, D23). Read only: an overlap never blocks.
 * Each item says how the student holds it. Plan figures are added only for a caller who may see money. Access is judged as
 * {@link LiveExamAccess} judges it: a revoked grant, an ended one, a free exam and an exam removed from the package do not count.
 */
@Component
class LiveExamOverlaps {

	/** The kind of an item: an exam or a package. */
	private static final String KIND_EXAM = "EXAM";

	private static final String KIND_PACKAGE = "PACKAGE";

	/** How the student holds an item. A package reached through a package grant, or an exam reached through one, is PACKAGE. */
	private static final String VIA_PACKAGE = "PACKAGE";

	private static final String VIA_ROSTER = "ROSTER";

	private final LiveExamGrantRepository grants;

	private final LiveExamRosterRepository roster;

	private final LiveExamPackageExamRepository memberships;

	private final LiveExamRepository exams;

	private final LiveExamModuleRepository modules;

	private final InstallmentPlanReader plans;

	LiveExamOverlaps(LiveExamGrantRepository grants, LiveExamRosterRepository roster,
			LiveExamPackageExamRepository memberships, LiveExamRepository exams, LiveExamModuleRepository modules,
			InstallmentPlanReader plans) {
		this.grants = grants;
		this.roster = roster;
		this.memberships = memberships;
		this.exams = exams;
		this.modules = modules;
		this.plans = plans;
	}

	/**
	 * The exams inside a package that the student holds by their own grant, or through another package, for an enrolment on the
	 * package. The package's own roster and grant are not listed here, because they are the same enrolment (plan 14.3).
	 */
	List<LiveExamGrantAdminService.QuoteOverlap> forPackage(UUID studentId, UUID moduleId, Instant now, boolean withMoney) {
		List<Hit> hits = new ArrayList<>();
		for (LiveExamPackageExam membership : memberships.findActiveByModuleId(moduleId)) {
			LiveExam exam = exams.findByIdAndDeletedAtIsNull(membership.getExamId()).orElse(null);
			if (exam == null || exam.isFree()) {
				continue;
			}
			Hit hit = examRoute(studentId, exam, moduleId, now);
			if (hit != null) {
				hits.add(hit);
			}
		}
		return withPlans(hits, withMoney);
	}

	/** The packages that hold the exam and that the student holds by a grant or the roster, for an enrolment on the exam. */
	List<LiveExamGrantAdminService.QuoteOverlap> forExam(UUID studentId, LiveExam exam, Instant now, boolean withMoney) {
		List<Hit> hits = new ArrayList<>();
		for (LiveExamPackageExam membership : memberships.findActiveByExamId(exam.getId())) {
			Optional<LiveExamModule> module = openPackage(membership.getModuleId());
			if (module.isEmpty()) {
				continue;
			}
			Hit hit = packageRoute(studentId, exam, module.get(), now);
			if (hit != null) {
				hits.add(hit);
			}
		}
		return withPlans(hits, withMoney);
	}

	/**
	 * How the student holds this exam outside the package {@code excludedModuleId}: by the exam's own grant first, then through
	 * another open package. Null when the student holds it no other way.
	 */
	private Hit examRoute(UUID studentId, LiveExam exam, UUID excludedModuleId, Instant now) {
		for (LiveExamGrant grant : grants.findByStudentIdAndScopeAndExamId(studentId, LiveExamOrder.EXAM, exam.getId())) {
			if (grant.getRevokedAt() == null && !AccessWindow.expired(exam, grant.getCreatedAt(), now)) {
				return examHit(exam, via(grant), grant.getId(), null);
			}
		}
		for (LiveExamPackageExam membership : memberships.findByExamId(exam.getId())) {
			UUID moduleId = membership.getModuleId();
			Optional<LiveExamModule> pkg = openPackage(moduleId);
			if (moduleId.equals(excludedModuleId) || pkg.isEmpty()) {
				continue;
			}
			Instant removedAt = membership.getRemovedAt();
			Optional<LiveExamRoster> row = roster.findById(new LiveExamRoster.Key(moduleId, studentId));
			if (row.isPresent() && LiveExamAccess.covers(row.get().getCreatedAt(), removedAt)
					&& !AccessWindow.expired(exam, row.get().getCreatedAt(), now)) {
				// The package names the route: "on the roster of the package {name}" (plan 14.11, P54).
				return examHit(exam, VIA_ROSTER, null, pkg.get());
			}
			for (LiveExamGrant grant : grants.findByStudentIdAndScopeAndModuleId(studentId, LiveExamOrder.MODULE, moduleId)) {
				if (grant.getRevokedAt() == null && LiveExamAccess.covers(grant.getCreatedAt(), removedAt)
						&& !AccessWindow.expired(exam, grant.getCreatedAt(), now)) {
					return examHit(exam, VIA_PACKAGE, grant.getId(), pkg.get());
				}
			}
		}
		return null;
	}

	/** How the student holds the package for this exam: a package grant first, then the roster. Null when neither gives access. */
	private Hit packageRoute(UUID studentId, LiveExam exam, LiveExamModule module, Instant now) {
		for (LiveExamGrant grant : grants.findByStudentIdAndScopeAndModuleId(studentId, LiveExamOrder.MODULE, module.getId())) {
			if (grant.getRevokedAt() == null && !AccessWindow.expired(exam, grant.getCreatedAt(), now)) {
				return packageHit(module, via(grant), grant.getId());
			}
		}
		Optional<LiveExamRoster> row = roster.findById(new LiveExamRoster.Key(module.getId(), studentId));
		if (row.isPresent() && !AccessWindow.expired(exam, row.get().getCreatedAt(), now)) {
			return packageHit(module, VIA_ROSTER, null);
		}
		return null;
	}

	/** The package when it is published. A package that is not published gives no access, so it is not an overlap. */
	private Optional<LiveExamModule> openPackage(UUID moduleId) {
		return modules.findByIdAndDeletedAtIsNull(moduleId).filter(module -> LiveExamModule.PUBLISHED.equals(module.getStatus()));
	}

	/** Adds the plan figures to each item, when the caller may see money. An item with no plan keeps them null. */
	private List<LiveExamGrantAdminService.QuoteOverlap> withPlans(List<Hit> hits, boolean withMoney) {
		Map<UUID, InstallmentPlanReader.PlanRow> byGrant = withMoney
				? plans.byGrantIds(hits.stream().map(Hit::grantId).filter(Objects::nonNull).toList())
				: Map.of();
		return hits.stream().map(hit -> {
			InstallmentPlanReader.PlanRow plan = hit.grantId() == null ? null : byGrant.get(hit.grantId());
			if (plan == null) {
				return new LiveExamGrantAdminService.QuoteOverlap(hit.kind(), hit.id(), hit.titleEn(), hit.titleBn(), hit.via(),
						null, null, null, hit.packageTitleEn(), hit.packageTitleBn());
			}
			return new LiveExamGrantAdminService.QuoteOverlap(hit.kind(), hit.id(), hit.titleEn(), hit.titleBn(), hit.via(),
					plan.planId(), plan.paid(), plan.due(), hit.packageTitleEn(), hit.packageTitleBn());
		}).toList();
	}

	/** How a grant reached the student: a manual grant is a direct enrolment, a bundle is a bundle, and anything else is an online purchase. */
	private static String via(LiveExamGrant grant) {
		if (LiveExamGrant.SOURCE_MANUAL.equals(grant.getSource())) {
			return "DIRECT";
		}
		return LiveExamGrant.SOURCE_BUNDLE.equals(grant.getSource()) ? "BUNDLE" : "ONLINE";
	}

	/** An exam item. {@code pkg} is the package that gives the route, or null when the exam's own grant gives it. */
	private static Hit examHit(LiveExam exam, String via, UUID grantId, LiveExamModule pkg) {
		return new Hit(KIND_EXAM, exam.getId(), exam.getTitleEn(), exam.getTitleBn(), via, grantId,
				pkg == null ? null : pkg.getTitleEn(), pkg == null ? null : pkg.getTitleBn());
	}

	/** A package item names itself, so it carries no separate package title. */
	private static Hit packageHit(LiveExamModule module, String via, UUID grantId) {
		return new Hit(KIND_PACKAGE, module.getId(), module.getTitleEn(), module.getTitleBn(), via, grantId, null, null);
	}

	/**
	 * One overlapping item before its plan figures are added. {@code grantId} is null for a roster route. {@code packageTitleEn}
	 * and {@code packageTitleBn} name the package that gives an exam its route, and are null otherwise.
	 */
	private record Hit(String kind, UUID id, String titleEn, String titleBn, String via, UUID grantId, String packageTitleEn,
			String packageTitleBn) {
	}

}
