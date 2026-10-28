package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

@Service
public class LiveExamPublicService {

	private static final DateTimeFormatter DHAKA_TIME = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

	private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

	private static final UUID NO_HIDDEN_CATEGORY = new UUID(0L, 0L);

	/** Dated exams soonest first; undated exams after them. */
	private static final Comparator<LiveExam> START_ORDER = Comparator
		.comparing(LiveExam::getLiveAt, Comparator.nullsLast(Comparator.<Instant>naturalOrder()))
		.thenComparing(LiveExam::getId);

	private final LiveExamCategoryRepository categories;

	private final LiveExamModuleRepository modules;

	private final LiveExamRepository exams;

	private final LiveExamPackageExamRepository links;

	private final LiveExamMemberships memberships;

	private final LiveExamCatalogVisibility visibility;

	private final Clock clock;

	public LiveExamPublicService(LiveExamCategoryRepository categories, LiveExamModuleRepository modules,
			LiveExamRepository exams, LiveExamCatalogVisibility visibility, Clock clock,
			LiveExamMemberships memberships, LiveExamPackageExamRepository links) {
		this.clock = clock;
		this.categories = categories;
		this.modules = modules;
		this.exams = exams;
		this.visibility = visibility;
		this.memberships = memberships;
		this.links = links;
	}

	/**
	 * The category tree. Each category holds its packages and the exams whose home it is (NR-01, B2). With an
	 * {@code examType} (as {@link LiveExamTypeFilter} parsed it) a package stays only when it has a published exam of that
	 * type, and a category stays when its branch keeps something or never had anything (shown as coming soon in both doors).
	 */
	@Transactional(readOnly = true)
	public List<PublicCategory> categories(String examType) {
		Instant now = clock.instant();
		Set<UUID> hidden = visibility.hiddenCategoryIds();
		List<LiveExamModule> visible = new ArrayList<>();
		for (LiveExamModule module : modules.findByDeletedAtIsNull()) {
			if (LiveExamModule.PUBLISHED.equals(module.getStatus()) && !hidden.contains(module.getCategoryId())) {
				visible.add(module);
			}
		}
		List<LiveExam> visibleExams = new ArrayList<>(
				exams.findByStatusAndDeletedAtIsNullAndCategoryIdNotIn(LiveExam.PUBLISHED, catalogHidden(hidden)));
		visibleExams.sort(START_ORDER);
		Map<UUID, Counts> counts = counts(visible.stream().map(LiveExamModule::getId).toList());
		Map<UUID, LiveExamCategory> categoriesById = LiveExamStaffModuleService.categoriesById(categories);
		Map<UUID, List<LiveExamModule>> packagesByExam = visiblePackages(
				visibleExams.stream().map(LiveExam::getId).toList(), hidden);
		boolean filtered = !LiveExamTypeFilter.ANY.equals(examType);
		Map<UUID, List<PublicModule>> modulesByCategory = new HashMap<>();
		Map<UUID, List<CategoryExam>> examsByCategory = new HashMap<>();
		Set<UUID> categoriesWithContent = new HashSet<>();
		for (LiveExamModule module : visible) {
			categoriesWithContent.add(module.getCategoryId());
			Counts count = counts.getOrDefault(module.getId(), Counts.NONE);
			if (filtered && count.of(examType) == 0) {
				continue;
			}
			modulesByCategory.computeIfAbsent(module.getCategoryId(), ignored -> new ArrayList<>())
				.add(moduleView(module, count, categoriesById, null));
		}
		for (LiveExam exam : visibleExams) {
			categoriesWithContent.add(exam.getCategoryId());
			if (filtered && !examType.equals(exam.getExamType())) {
				continue;
			}
			examsByCategory.computeIfAbsent(exam.getCategoryId(), ignored -> new ArrayList<>())
				.add(categoryExam(exam, now, packagesByExam.getOrDefault(exam.getId(), List.of())));
		}
		for (List<PublicModule> placed : modulesByCategory.values()) {
			placed.sort(Comparator.comparingInt(PublicModule::position).thenComparing(PublicModule::id));
		}
		Map<UUID, PublicCategory> nodes = new HashMap<>();
		List<PublicCategory> roots = new ArrayList<>();
		for (LiveExamCategory category : categoriesById.values()) {
			if (hidden.contains(category.getId())) {
				continue;
			}
			PublicCategory node = new PublicCategory(category.getId(), category.getParentId(), category.getTitleEn(),
					category.getTitleBn(), category.getPosition(), new ArrayList<>(),
					new ArrayList<>(modulesByCategory.getOrDefault(category.getId(), List.of())),
					new ArrayList<>(examsByCategory.getOrDefault(category.getId(), List.of())));
			nodes.put(category.getId(), node);
		}
		for (PublicCategory node : nodes.values()) {
			if (node.parentId() == null) {
				roots.add(node);
			}
			else {
				PublicCategory parent = nodes.get(node.parentId());
				if (parent != null) {
					parent.children().add(node);
				}
			}
		}
		if (filtered) {
			roots = prune(roots, categoriesWithContent);
		}
		sort(roots);
		return roots;
	}

	/** Published packages, with a title pattern, a category, popular and exam type filters (NR-01, B2 and B5). */
	@Transactional(readOnly = true)
	public Page<PublicModule> modules(UUID categoryId, String examType, String q, boolean popularOnly, Pageable pageable,
			StudentOwnAccessService.Viewer viewer) {
		Set<UUID> hidden = visibility.hiddenCategoryIds();
		Pageable ordered = orderedModules(pageable);
		if (categoryId != null && hidden.contains(categoryId)) {
			return Page.empty(ordered);
		}
		String pattern = q == null || q.isBlank() ? "%" : "%" + q.trim().toLowerCase() + "%";
		Page<LiveExamModule> page = modules.findPublishedCatalog(catalogHidden(hidden), categoryId == null,
				categoryId == null ? NO_HIDDEN_CATEGORY : categoryId, popularOnly, pattern,
				LiveExamTypeFilter.ANY.equals(examType), examType, ordered);
		Map<UUID, Counts> counts = counts(page.getContent().stream().map(LiveExamModule::getId).toList());
		Map<UUID, LiveExamCategory> categoriesById = LiveExamStaffModuleService.categoriesById(categories);
		return page.map(module -> moduleView(module, counts.getOrDefault(module.getId(), Counts.NONE), categoriesById,
				viewer));
	}

	/**
	 * Dated exams that have not ended, soonest first, in categories outside the hidden branches. An exam joins the catalog
	 * through its home category, so a standalone exam is listed too (NR-01, B2). Public like the catalog: no questions and
	 * no PDFs. Practice exams have no date and are not listed.
	 */
	@Transactional(readOnly = true)
	public Page<UpcomingExam> upcoming(String examType, Pageable pageable) {
		Instant now = clock.instant();
		Set<UUID> hidden = visibility.hiddenCategoryIds();
		List<LiveExam> live = new ArrayList<>(exams.findUpcomingInCatalog(now, catalogHidden(hidden),
				LiveExamTypeFilter.ANY.equals(examType), examType));
		live.sort(START_ORDER);
		List<LiveExam> slice = page(live, pageable).getContent();
		Map<UUID, List<LiveExamModule>> packagesByExam = visiblePackages(
				slice.stream().map(LiveExam::getId).toList(), hidden);
		List<UpcomingExam> content = slice.stream()
			.map(exam -> upcomingRow(exam, now, packagesByExam.getOrDefault(exam.getId(), List.of())))
			.toList();
		return new PageImpl<>(content, pageable, live.size());
	}

	@Transactional(readOnly = true)
	public PublicModule module(UUID id, StudentOwnAccessService.Viewer viewer) {
		return moduleRow(requireModule(id), viewer);
	}

	/** One package's public row. Also used for a student's favourites (NR-01, B6). */
	PublicModule moduleRow(LiveExamModule module, StudentOwnAccessService.Viewer viewer) {
		return moduleView(module, counts(List.of(module.getId())).getOrDefault(module.getId(), Counts.NONE),
				LiveExamStaffModuleService.categoriesById(categories), viewer);
	}

	/**
	 * The exams of one package, in membership order (NR-01, B2). Only exams whose home branch is visible are listed, and a
	 * package shows only its own members.
	 */
	@Transactional(readOnly = true)
	public Page<PublicExam> exams(UUID moduleId, String examType, boolean popularOnly, Pageable pageable,
			StudentOwnAccessService.Viewer viewer) {
		requireModule(moduleId);
		Set<UUID> hidden = visibility.hiddenCategoryIds();
		List<UUID> memberIds = links.findActiveByModuleId(moduleId).stream().map(LiveExamPackageExam::getExamId).toList();
		Map<UUID, LiveExam> byId = new HashMap<>();
		for (LiveExam exam : exams.findAllById(memberIds)) {
			byId.put(exam.getId(), exam);
		}
		List<LiveExam> visible = new ArrayList<>();
		for (UUID examId : memberIds) {
			LiveExam exam = byId.get(examId);
			if (exam != null && visibleExam(exam, hidden)
					&& (LiveExamTypeFilter.ANY.equals(examType) || examType.equals(exam.getExamType()))
					&& (!popularOnly || exam.isPopular())) {
				visible.add(exam);
			}
		}
		Page<LiveExam> slice = page(visible, pageable);
		Map<UUID, List<LiveExamModule>> packagesByExam = visiblePackages(
				slice.getContent().stream().map(LiveExam::getId).toList(), hidden);
		return slice.map(exam -> examRow(exam, packagesByExam.getOrDefault(exam.getId(), List.of()), viewer));
	}

	@Transactional(readOnly = true)
	public PublicExam exam(UUID id, StudentOwnAccessService.Viewer viewer) {
		LiveExam exam = exams.findByIdAndDeletedAtIsNull(id).orElseThrow(this::notFound);
		if (!LiveExam.PUBLISHED.equals(exam.getStatus()) || visibility.branchHidden(exam.getCategoryId())) {
			throw notFound();
		}
		return examRowFor(exam, viewer);
	}

	/** One exam's public row with its visible packages. Also used for a student's favourites (NR-01, B6). */
	PublicExam examRowFor(LiveExam exam, StudentOwnAccessService.Viewer viewer) {
		Set<UUID> hidden = visibility.hiddenCategoryIds();
		return examRow(exam, visiblePackages(List.of(exam.getId()), hidden).getOrDefault(exam.getId(), List.of()), viewer);
	}

	/** The published exams of one category, for the category's exam list (NR-01, B2). */
	@Transactional(readOnly = true)
	public Page<CategoryExam> categoryExams(UUID categoryId, String examType, String mode, String phase,
			boolean popularOnly, Pageable pageable) {
		categories.findByIdAndDeletedAtIsNull(categoryId).orElseThrow(this::notFound);
		if (visibility.branchHidden(categoryId)) {
			return Page.empty(pageable);
		}
		Set<UUID> hidden = visibility.hiddenCategoryIds();
		Instant now = clock.instant();
		List<LiveExam> candidates = new ArrayList<>(
				exams.findByCategoryIdAndStatusAndDeletedAtIsNull(categoryId, LiveExam.PUBLISHED));
		candidates.sort(START_ORDER);
		Map<UUID, List<LiveExamModule>> packagesByExam = visiblePackages(
				candidates.stream().map(LiveExam::getId).toList(), hidden);
		List<CategoryExam> rows = new ArrayList<>();
		for (LiveExam exam : candidates) {
			if (!LiveExamTypeFilter.ANY.equals(examType) && !examType.equals(exam.getExamType())) {
				continue;
			}
			if (!LiveExamTypeFilter.ANY.equals(mode) && !mode.equals(exam.getMode())) {
				continue;
			}
			if (!LiveExamTypeFilter.ANY.equals(phase) && !phase.equals(ExamWindow.phase(exam, now))) {
				continue;
			}
			if (popularOnly && !exam.isPopular()) {
				continue;
			}
			rows.add(categoryExam(exam, now, packagesByExam.getOrDefault(exam.getId(), List.of())));
		}
		return page(rows, pageable);
	}

	private LiveExamModule requireModule(UUID id) {
		LiveExamModule module = modules.findByIdAndDeletedAtIsNull(id).orElseThrow(this::notFound);
		if (!LiveExamModule.PUBLISHED.equals(module.getStatus()) || visibility.branchHidden(module.getCategoryId())) {
			throw notFound();
		}
		return module;
	}

	/** An exam is visible when it is published, not deleted, and its home branch is not hidden. Packages do not hide it. */
	private static boolean visibleExam(LiveExam exam, Set<UUID> hidden) {
		return exam.getDeletedAt() == null && LiveExam.PUBLISHED.equals(exam.getStatus())
				&& !hidden.contains(exam.getCategoryId());
	}

	/**
	 * The packages each exam is in, by exam id: published, not deleted, outside the hidden branches. A student sees only
	 * the packages they could open (NR-01, B2). Packages come oldest membership first.
	 */
	private Map<UUID, List<LiveExamModule>> visiblePackages(Collection<UUID> examIds, Set<UUID> hidden) {
		Map<UUID, List<LiveExamModule>> byExam = new HashMap<>();
		if (examIds.isEmpty()) {
			return byExam;
		}
		List<LiveExamPackageExam> active = links.findActiveByExamIdIn(examIds).stream()
			.sorted(Comparator.comparing(LiveExamPackageExam::getAddedAt).thenComparing(LiveExamPackageExam::getId))
			.toList();
		Map<UUID, LiveExamModule> byId = new HashMap<>();
		for (LiveExamModule module : modules.findAllById(active.stream().map(LiveExamPackageExam::getModuleId).toList())) {
			byId.put(module.getId(), module);
		}
		for (LiveExamPackageExam link : active) {
			LiveExamModule module = byId.get(link.getModuleId());
			if (module != null && module.getDeletedAt() == null && LiveExamModule.PUBLISHED.equals(module.getStatus())
					&& !hidden.contains(module.getCategoryId())) {
				byExam.computeIfAbsent(link.getExamId(), ignored -> new ArrayList<>()).add(module);
			}
		}
		return byExam;
	}

	/** Free exams and the own price: an exam with no own price is bought with a priced package that holds it. */
	private static boolean moduleOnly(LiveExam exam, List<LiveExamModule> packages) {
		return exam.getPrice() == null && !exam.isFree()
				&& packages.stream().anyMatch(module -> module.getPrice() != null);
	}

	private static List<LiveExamModuleService.PackageRef> packageRefs(List<LiveExamModule> packages) {
		return packages.stream()
			.map(module -> new LiveExamModuleService.PackageRef(module.getId(), module.getTitleEn(), module.getTitleBn()))
			.toList();
	}

	/** {@code viewer} is the signed-in student, or null; only a student's view carries {@code access}. */
	private PublicModule moduleView(LiveExamModule module, Counts counts, Map<UUID, LiveExamCategory> categoriesById,
			StudentOwnAccessService.Viewer viewer) {
		return new PublicModule(module.getId(), module.getCategoryId(),
				LiveExamStaffModuleService.pathOf(module.getCategoryId(), categoriesById), module.getTitleEn(),
				module.getTitleBn(), module.getDescriptionEn(), module.getDescriptionBn(), module.getPosition(), module.getPrice(),
				counts.mcq(), counts.written(), viewer == null ? null : viewer.module(module), ModuleCoverService.coverPath(module),
				module.isPopular(), ExamPricing.view(module, clock.instant()), null);
	}

	private PublicExam examRow(LiveExam exam, List<LiveExamModule> packages, StudentOwnAccessService.Viewer viewer) {
		Instant now = clock.instant();
		UUID primary = memberships.primaryModuleId(exam.getId()).orElse(null);
		return new PublicExam(exam.getId(), primary, exam.getExamType(), exam.getTitleEn(), exam.getTitleBn(),
				exam.getDescriptionEn(), exam.getDescriptionBn(), exam.getRulesEn(), exam.getRulesBn(),
				exam.getPaperLanguage(), format(exam.getLiveAt()), format(exam.getEndsAt()), exam.getDurationMinutes(),
				exam.getPrice(), viewer == null ? null : viewer.exam(exam), exam.getCategoryId(), exam.getMode(),
				ExamWindow.phase(exam, now), exam.isPopular(), ExamPricing.view(exam, now, moduleOnly(exam, packages)),
				packageRefs(packages), null);
	}

	private CategoryExam categoryExam(LiveExam exam, Instant now, List<LiveExamModule> packages) {
		return new CategoryExam(exam.getId(), exam.getCategoryId(), exam.getExamType(), exam.getMode(),
				ExamWindow.phase(exam, now), exam.getTitleEn(), exam.getTitleBn(), format(exam.getLiveAt()),
				format(exam.getEndsAt()), exam.isPopular(), ExamPricing.view(exam, now, moduleOnly(exam, packages)),
				packageRefs(packages), null);
	}

	private UpcomingExam upcomingRow(LiveExam exam, Instant now, List<LiveExamModule> packages) {
		UUID primary = memberships.primaryModuleId(exam.getId()).orElse(null);
		LiveExamModule module = primary == null ? null : modules.findByIdAndDeletedAtIsNull(primary).orElse(null);
		boolean moduleOnly = moduleOnly(exam, packages);
		return new UpcomingExam(exam.getId(), exam.getExamType(), exam.getTitleEn(), exam.getTitleBn(), primary,
				module == null ? null : module.getTitleEn(), module == null ? null : module.getTitleBn(), exam.getLiveAt(),
				exam.getEndsAt(), exam.getPrice(), moduleOnly, exam.getCategoryId(), exam.getMode(),
				ExamWindow.phase(exam, now), exam.isPopular(), ExamPricing.view(exam, now, moduleOnly),
				packageRefs(packages), null);
	}

	private Map<UUID, Counts> counts(List<UUID> moduleIds) {
		Map<UUID, Counts> byModule = new HashMap<>();
		if (moduleIds.isEmpty()) {
			return byModule;
		}
		for (Object[] row : exams.countPublishedByModuleAndType(moduleIds)) {
			UUID moduleId = (UUID) row[0];
			int total = ((Number) row[2]).intValue();
			Counts current = byModule.getOrDefault(moduleId, Counts.NONE);
			byModule.put(moduleId, LiveExam.MCQ.equals(row[1]) ? new Counts(total, current.written())
					: new Counts(current.mcq(), total));
		}
		return byModule;
	}

	private static Set<UUID> catalogHidden(Set<UUID> hidden) {
		return hidden.isEmpty() ? Set.of(NO_HIDDEN_CATEGORY) : hidden;
	}

	/**
	 * Keeps a category whose branch still holds something after the filter, or whose branch never had anything. A category
	 * whose content is all of the other type drops out with its branch.
	 */
	private static List<PublicCategory> prune(List<PublicCategory> nodes, Set<UUID> categoriesWithContent) {
		List<PublicCategory> kept = new ArrayList<>();
		for (PublicCategory node : nodes) {
			if (keptContent(node) || !hadContent(node, categoriesWithContent)) {
				List<PublicCategory> children = prune(node.children(), categoriesWithContent);
				node.children().clear();
				node.children().addAll(children);
				kept.add(node);
			}
		}
		return kept;
	}

	private static boolean keptContent(PublicCategory node) {
		if (!node.modules().isEmpty() || !node.exams().isEmpty()) {
			return true;
		}
		for (PublicCategory child : node.children()) {
			if (keptContent(child)) {
				return true;
			}
		}
		return false;
	}

	private static boolean hadContent(PublicCategory node, Set<UUID> categoriesWithContent) {
		if (categoriesWithContent.contains(node.id())) {
			return true;
		}
		for (PublicCategory child : node.children()) {
			if (hadContent(child, categoriesWithContent)) {
				return true;
			}
		}
		return false;
	}

	private static Pageable orderedModules(Pageable pageable) {
		if (pageable.getSort().isSorted()) {
			return pageable;
		}
		return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
				Sort.by(Sort.Order.asc("position"), Sort.Order.asc("id")));
	}

	/** An in-memory page of a list the caller has already filtered and ordered. */
	private static <T> Page<T> page(List<T> rows, Pageable pageable) {
		int from = (int) Math.min(pageable.getOffset(), rows.size());
		int to = Math.min(from + pageable.getPageSize(), rows.size());
		return new PageImpl<>(rows.subList(from, to), pageable, rows.size());
	}

	private static void sort(List<PublicCategory> nodes) {
		nodes.sort(Comparator.comparingInt(PublicCategory::position).thenComparing(PublicCategory::id));
		for (PublicCategory node : nodes) {
			sort(node.children());
		}
	}

	private static String format(Instant instant) {
		if (instant == null) {
			return null;
		}
		return DHAKA_TIME.format(instant.atZone(DHAKA));
	}

	private ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	/** {@code categoryPath} runs from the root category down to the module's own, the same shape as the staff read. */
	public record PublicCategory(UUID id, UUID parentId, String titleEn, String titleBn, int position,
			List<PublicCategory> children, List<PublicModule> modules, List<CategoryExam> exams) {
	}

	/** A package's public row. {@code favourite} is set only for a signed-in student (NR-01, B6). */
	public record PublicModule(UUID id, UUID categoryId, List<LiveExamModuleService.CategoryRef> categoryPath,
			String titleEn, String titleBn, String descriptionEn, String descriptionBn, int position, BigDecimal price,
			int mcqCount, int writtenCount, StudentOwnAccessService.Access access, String coverUrl, boolean popular,
			PriceView pricing, Boolean favourite) {

		public PublicModule withFavourite(boolean marked) {
			return new PublicModule(id, categoryId, categoryPath, titleEn, titleBn, descriptionEn, descriptionBn, position,
					price, mcqCount, writtenCount, access, coverUrl, popular, pricing, marked);
		}

	}

	/** An exam in its category's list: the public row shape without the descriptions and rules. */
	public record CategoryExam(UUID id, UUID categoryId, String examType, String mode, String phase, String titleEn,
			String titleBn, String liveAt, String endAt, boolean popular, PriceView pricing,
			List<LiveExamModuleService.PackageRef> packages, Boolean favourite) {

		public CategoryExam withFavourite(boolean marked) {
			return new CategoryExam(id, categoryId, examType, mode, phase, titleEn, titleBn, liveAt, endAt, popular,
					pricing, packages, marked);
		}

	}

	/** One upcoming exam, with its primary package's titles for display. {@code moduleOnly} is the buy-with-package rule. */
	public record UpcomingExam(UUID id, String examType, String titleEn, String titleBn, UUID moduleId,
			String moduleTitleEn, String moduleTitleBn, Instant liveAt, Instant endAt, BigDecimal price, boolean moduleOnly,
			UUID categoryId, String mode, String phase, boolean popular, PriceView pricing,
			List<LiveExamModuleService.PackageRef> packages, Boolean favourite) {

		public UpcomingExam withFavourite(boolean marked) {
			return new UpcomingExam(id, examType, titleEn, titleBn, moduleId, moduleTitleEn, moduleTitleBn, liveAt, endAt,
					price, moduleOnly, categoryId, mode, phase, popular, pricing, packages, marked);
		}

	}

	private record Counts(int mcq, int written) {

		static final Counts NONE = new Counts(0, 0);

		int of(String examType) {
			return LiveExam.MCQ.equals(examType) ? mcq : written;
		}

	}

	/** The public exam row. {@code moduleId} is the primary package, for display only; null for a standalone exam. */
	public record PublicExam(UUID id, UUID moduleId, String examType, String titleEn, String titleBn,
			String descriptionEn, String descriptionBn, String rulesEn, String rulesBn, String paperLanguage,
			String liveAt, String endAt, int durationMinutes, BigDecimal price,
			StudentOwnAccessService.Access access, UUID categoryId, String mode, String phase, boolean popular,
			PriceView pricing, List<LiveExamModuleService.PackageRef> packages, Boolean favourite) {

		public PublicExam withFavourite(boolean marked) {
			return new PublicExam(id, moduleId, examType, titleEn, titleBn, descriptionEn, descriptionBn, rulesEn, rulesBn,
					paperLanguage, liveAt, endAt, durationMinutes, price, access, categoryId, mode, phase, popular, pricing,
					packages, marked);
		}

	}

}
