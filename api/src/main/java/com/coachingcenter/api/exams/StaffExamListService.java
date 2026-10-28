package com.coachingcenter.api.exams;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.users.AdminProfileRepository;

/**
 * The staff list of exams across packages, for the later Exams page (NR-01, B3). One query per page for the exams, then
 * batched lookups for their packages, question counts, staged revisions and instructors.
 */
@Service
public class StaffExamListService {

	private static final UUID NONE = new UUID(0L, 0L);

	private final LiveExamRepository exams;

	private final LiveExamPackageExamRepository links;

	private final LiveExamModuleRepository modules;

	private final LiveExamQuestionRepository questions;

	private final LiveExamRevisionRepository revisions;

	private final AdminProfileRepository adminProfiles;

	private final Clock clock;

	public StaffExamListService(LiveExamRepository exams, LiveExamPackageExamRepository links,
			LiveExamModuleRepository modules, LiveExamQuestionRepository questions, LiveExamRevisionRepository revisions,
			AdminProfileRepository adminProfiles, Clock clock) {
		this.exams = exams;
		this.links = links;
		this.modules = modules;
		this.questions = questions;
		this.revisions = revisions;
		this.adminProfiles = adminProfiles;
		this.clock = clock;
	}

	/**
	 * {@code instructorId} is set for an instructor, who sees only the exams assigned to them. Every other filter is
	 * optional; the controller has already parsed the type, mode and phase.
	 */
	@Transactional(readOnly = true)
	public Page<StaffExamRow> list(ListQuery query, UUID instructorId, Pageable pageable) {
		Instant now = clock.instant();
		String status = status(query.status());
		String text = query.text() == null ? "" : query.text().trim().toLowerCase(Locale.ROOT);
		Page<LiveExam> page = exams.searchForStaff(query.categoryId() == null,
				query.categoryId() == null ? NONE : query.categoryId(),
				query.packageId() == null, query.packageId() == null ? NONE : query.packageId(),
				query.standalone(), LiveExamTypeFilter.ANY.equals(query.examType()), query.examType(),
				LiveExamTypeFilter.ANY.equals(query.mode()), query.mode(), status.isEmpty(), status,
				query.pendingOnly(), query.popularOnly(), instructorId != null,
				instructorId == null ? NONE : instructorId, text.isEmpty(), "%" + text + "%", query.phase(), now,
				ordered(pageable));
		List<UUID> ids = page.getContent().stream().map(LiveExam::getId).toList();
		Map<UUID, List<LiveExamModuleService.PackageRef>> packages = packagesOf(ids);
		Map<UUID, Integer> questionCounts = new HashMap<>();
		Set<UUID> pending = new HashSet<>();
		if (!ids.isEmpty()) {
			for (Object[] row : questions.countByExamIds(ids)) {
				questionCounts.put((UUID) row[0], ((Number) row[1]).intValue());
			}
			pending.addAll(revisions.findExamIdsIn(ids));
		}
		Map<UUID, String> names = instructorNames(page.getContent());
		return page.map(exam -> row(exam, now, packages, questionCounts, pending, names));
	}

	/** Every live package of each exam, oldest membership first. A deleted package is left out. */
	private Map<UUID, List<LiveExamModuleService.PackageRef>> packagesOf(List<UUID> examIds) {
		Map<UUID, List<LiveExamModuleService.PackageRef>> byExam = new HashMap<>();
		if (examIds.isEmpty()) {
			return byExam;
		}
		List<LiveExamPackageExam> active = links.findActiveByExamIdIn(examIds).stream()
			.sorted(Comparator.comparing(LiveExamPackageExam::getAddedAt).thenComparing(LiveExamPackageExam::getId))
			.toList();
		Map<UUID, LiveExamModule> moduleById = new HashMap<>();
		for (LiveExamModule module : modules.findAllById(active.stream().map(LiveExamPackageExam::getModuleId).toList())) {
			moduleById.put(module.getId(), module);
		}
		for (LiveExamPackageExam link : active) {
			LiveExamModule module = moduleById.get(link.getModuleId());
			if (module != null && module.getDeletedAt() == null) {
				byExam.computeIfAbsent(link.getExamId(), ignored -> new ArrayList<>())
					.add(new LiveExamModuleService.PackageRef(module.getId(), module.getTitleEn(), module.getTitleBn()));
			}
		}
		return byExam;
	}

	private Map<UUID, String> instructorNames(List<LiveExam> rows) {
		Set<UUID> instructors = rows.stream().map(LiveExam::getInstructorId).filter(Objects::nonNull)
			.collect(Collectors.toSet());
		Map<UUID, String> names = new HashMap<>();
		if (!instructors.isEmpty()) {
			adminProfiles.findAllById(instructors).forEach(profile -> names.put(profile.getUserId(), profile.getFullName()));
		}
		return names;
	}

	private StaffExamRow row(LiveExam exam, Instant now, Map<UUID, List<LiveExamModuleService.PackageRef>> packages,
			Map<UUID, Integer> questionCounts, Set<UUID> pending, Map<UUID, String> names) {
		Instructor instructor = exam.getInstructorId() == null ? null
				: new Instructor(exam.getInstructorId(), names.get(exam.getInstructorId()));
		return new StaffExamRow(exam.getId(), exam.getTitleEn(), exam.getTitleBn(), exam.getExamType(), exam.getMode(),
				ExamWindow.phase(exam, now), exam.getStatus(), exam.getLiveAt(), exam.getEndsAt(), exam.getDurationMinutes(),
				ExamPricing.view(exam, now, false), exam.isPopular(), pending.contains(exam.getId()),
				packages.getOrDefault(exam.getId(), List.of()), exam.getCategoryId(),
				questionCounts.getOrDefault(exam.getId(), 0),
				exam.getQuestionPaperId() != null || exam.getQuestionStoredFileName() != null, instructor);
	}

	private static String status(String value) {
		if (value == null || value.isBlank()) {
			return "";
		}
		String status = value.trim().toUpperCase(Locale.ROOT);
		if (!Set.of(LiveExam.DRAFT, LiveExam.PUBLISHED, LiveExam.ARCHIVED).contains(status)) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
		}
		return status;
	}

	/** Sort keys the list accepts: {@code liveAt}, {@code title} (the English title) and {@code updatedAt}. */
	private static Pageable ordered(Pageable pageable) {
		List<Sort.Order> orders = new ArrayList<>();
		for (Sort.Order order : pageable.getSort()) {
			orders.add(new Sort.Order(order.getDirection(), property(order.getProperty())));
		}
		if (orders.isEmpty()) {
			orders.add(Sort.Order.desc("updatedAt"));
		}
		orders.add(Sort.Order.asc("id"));
		return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(orders));
	}

	private static String property(String name) {
		return switch (name) {
			case "liveAt" -> "liveAt";
			case "title" -> "titleEn";
			case "updatedAt" -> "updatedAt";
			default -> throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
					"Request validation failed.");
		};
	}

	/** The filters of the list. The "any" values are the empty strings of {@link LiveExamTypeFilter}. */
	public record ListQuery(UUID packageId, UUID categoryId, boolean standalone, String examType, String mode,
			String status, String phase, String text, boolean pendingOnly, boolean popularOnly) {
	}

	public record Instructor(UUID id, String name) {
	}

	/** One exam in the staff list. {@code questionCount} counts MCQ questions; {@code hasPaper} is the written paper. */
	public record StaffExamRow(UUID id, String titleEn, String titleBn, String examType, String mode, String phase,
			String status, Instant liveAt, Instant endAt, int durationMinutes, PriceView pricing, boolean popular,
			boolean pendingChanges, List<LiveExamModuleService.PackageRef> packages, UUID categoryId,
			int questionCount, boolean hasPaper, Instructor instructor) {
	}

}
