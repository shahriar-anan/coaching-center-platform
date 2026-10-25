package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
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

	private final LiveExamCategoryRepository categories;

	private final LiveExamModuleRepository modules;

	private final LiveExamRepository exams;

	private final LiveExamCatalogVisibility visibility;

	public LiveExamPublicService(LiveExamCategoryRepository categories, LiveExamModuleRepository modules,
			LiveExamRepository exams, LiveExamCatalogVisibility visibility) {
		this.categories = categories;
		this.modules = modules;
		this.exams = exams;
		this.visibility = visibility;
	}

	/**
	 * The category tree. With an {@code examType} (as {@link LiveExamTypeFilter} parsed it) a module stays only when it
	 * has a published exam of that type, and a category stays when its branch keeps a module or has no module at all
	 * (shown as coming soon in both doors).
	 */
	@Transactional(readOnly = true)
	public List<PublicCategory> categories(String examType) {
		Set<UUID> hidden = visibility.hiddenCategoryIds();
		List<LiveExamModule> visible = new ArrayList<>();
		for (LiveExamModule module : modules.findByDeletedAtIsNull()) {
			if (LiveExamModule.PUBLISHED.equals(module.getStatus()) && !hidden.contains(module.getCategoryId())) {
				visible.add(module);
			}
		}
		Map<UUID, Counts> counts = counts(visible.stream().map(LiveExamModule::getId).toList());
		boolean filtered = !LiveExamTypeFilter.ANY.equals(examType);
		Map<UUID, List<PublicModule>> modulesByCategory = new HashMap<>();
		Set<UUID> categoriesWithModules = new HashSet<>();
		for (LiveExamModule module : visible) {
			categoriesWithModules.add(module.getCategoryId());
			Counts count = counts.getOrDefault(module.getId(), Counts.NONE);
			if (filtered && count.of(examType) == 0) {
				continue;
			}
			modulesByCategory.computeIfAbsent(module.getCategoryId(), ignored -> new ArrayList<>())
				.add(moduleView(module, count));
		}
		for (List<PublicModule> placed : modulesByCategory.values()) {
			placed.sort(Comparator.comparingInt(PublicModule::position).thenComparing(PublicModule::id));
		}
		Map<UUID, PublicCategory> nodes = new HashMap<>();
		List<PublicCategory> roots = new ArrayList<>();
		for (LiveExamCategory category : categories.findByDeletedAtIsNull()) {
			if (hidden.contains(category.getId())) {
				continue;
			}
			PublicCategory node = new PublicCategory(category.getId(), category.getParentId(), category.getTitleEn(),
					category.getTitleBn(), category.getPosition(), new ArrayList<>(),
					new ArrayList<>(modulesByCategory.getOrDefault(category.getId(), List.of())));
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
			roots = prune(roots, categoriesWithModules);
		}
		sort(roots);
		return roots;
	}

	@Transactional(readOnly = true)
	public Page<PublicModule> modules(UUID categoryId, String examType, Pageable pageable) {
		Set<UUID> hidden = visibility.hiddenCategoryIds();
		Pageable ordered = orderedModules(pageable);
		if (categoryId != null && hidden.contains(categoryId)) {
			return Page.empty(ordered);
		}
		Page<LiveExamModule> page;
		if (!LiveExamTypeFilter.ANY.equals(examType)) {
			page = categoryId != null ? modules.findPublishedInCategoryWithExamType(categoryId, examType, ordered)
					: modules.findPublishedWithExamType(examType, hidden.isEmpty() ? Set.of(NO_HIDDEN_CATEGORY) : hidden,
							ordered);
		}
		else if (categoryId != null) {
			page = modules.findByCategoryIdAndStatusAndDeletedAtIsNull(categoryId, LiveExamModule.PUBLISHED, ordered);
		}
		else if (hidden.isEmpty()) {
			page = modules.findByStatusAndDeletedAtIsNull(LiveExamModule.PUBLISHED, ordered);
		}
		else {
			page = modules.findByStatusAndDeletedAtIsNullAndCategoryIdNotIn(LiveExamModule.PUBLISHED, hidden, ordered);
		}
		Map<UUID, Counts> counts = counts(page.getContent().stream().map(LiveExamModule::getId).toList());
		return page.map(module -> moduleView(module, counts.getOrDefault(module.getId(), Counts.NONE)));
	}

	@Transactional(readOnly = true)
	public PublicModule module(UUID id) {
		LiveExamModule module = requireModule(id);
		return moduleView(module, counts(List.of(module.getId())).getOrDefault(module.getId(), Counts.NONE));
	}

	@Transactional(readOnly = true)
	public Page<PublicExam> exams(UUID moduleId, String examType, Pageable pageable) {
		requireModule(moduleId);
		Pageable ordered = orderedExams(pageable);
		Page<LiveExam> page = LiveExamTypeFilter.ANY.equals(examType)
				? exams.findByModuleIdAndStatusAndDeletedAtIsNull(moduleId, LiveExam.PUBLISHED, ordered)
				: exams.findByModuleIdAndStatusAndExamTypeAndDeletedAtIsNull(moduleId, LiveExam.PUBLISHED, examType,
						ordered);
		return page.map(this::examView);
	}

	@Transactional(readOnly = true)
	public PublicExam exam(UUID id) {
		LiveExam exam = exams.findByIdAndDeletedAtIsNull(id).orElseThrow(this::notFound);
		if (!LiveExam.PUBLISHED.equals(exam.getStatus())) {
			throw notFound();
		}
		requireModule(exam.getModuleId());
		return examView(exam);
	}

	private LiveExamModule requireModule(UUID id) {
		LiveExamModule module = modules.findByIdAndDeletedAtIsNull(id).orElseThrow(this::notFound);
		if (!LiveExamModule.PUBLISHED.equals(module.getStatus()) || visibility.branchHidden(module.getCategoryId())) {
			throw notFound();
		}
		return module;
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

	/**
	 * Keeps a category whose branch still holds a module after the filter, or whose branch never had a module. A
	 * category whose modules are all of the other type drops out with its branch.
	 */
	private static List<PublicCategory> prune(List<PublicCategory> nodes, Set<UUID> categoriesWithModules) {
		List<PublicCategory> kept = new ArrayList<>();
		for (PublicCategory node : nodes) {
			if (keptModules(node) || !hadModules(node, categoriesWithModules)) {
				List<PublicCategory> children = prune(node.children(), categoriesWithModules);
				node.children().clear();
				node.children().addAll(children);
				kept.add(node);
			}
		}
		return kept;
	}

	private static boolean keptModules(PublicCategory node) {
		if (!node.modules().isEmpty()) {
			return true;
		}
		for (PublicCategory child : node.children()) {
			if (keptModules(child)) {
				return true;
			}
		}
		return false;
	}

	private static boolean hadModules(PublicCategory node, Set<UUID> categoriesWithModules) {
		if (categoriesWithModules.contains(node.id())) {
			return true;
		}
		for (PublicCategory child : node.children()) {
			if (hadModules(child, categoriesWithModules)) {
				return true;
			}
		}
		return false;
	}

	private static PublicModule moduleView(LiveExamModule module, Counts counts) {
		return new PublicModule(module.getId(), module.getCategoryId(), module.getTitleEn(), module.getTitleBn(),
				module.getDescriptionEn(), module.getDescriptionBn(), module.getPosition(), module.getPrice(),
				counts.mcq(), counts.written());
	}

	private PublicExam examView(LiveExam exam) {
		return new PublicExam(exam.getId(), exam.getModuleId(), exam.getExamType(), exam.getTitleEn(), exam.getTitleBn(),
				exam.getDescriptionEn(), exam.getDescriptionBn(), exam.getRulesEn(), exam.getRulesBn(),
				exam.getPaperLanguage(), format(exam.getLiveAt()), format(end(exam)), exam.getDurationMinutes(),
				exam.getPrice());
	}

	private static Pageable orderedModules(Pageable pageable) {
		if (pageable.getSort().isSorted()) {
			return pageable;
		}
		return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
				Sort.by(Sort.Order.asc("position"), Sort.Order.asc("id")));
	}

	private static Pageable orderedExams(Pageable pageable) {
		if (pageable.getSort().isSorted()) {
			return pageable;
		}
		return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
				Sort.by(Sort.Order.asc("liveAt").with(Sort.NullHandling.NULLS_LAST)));
	}

	private static void sort(List<PublicCategory> nodes) {
		nodes.sort(Comparator.comparingInt(PublicCategory::position).thenComparing(PublicCategory::id));
		for (PublicCategory node : nodes) {
			sort(node.children());
		}
	}

	private static Instant end(LiveExam exam) {
		if (exam.getLiveAt() == null) {
			return null;
		}
		return exam.getLiveAt().plus(Duration.ofMinutes(exam.getDurationMinutes()));
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

	public record PublicCategory(UUID id, UUID parentId, String titleEn, String titleBn, int position,
			List<PublicCategory> children, List<PublicModule> modules) {
	}

	public record PublicModule(UUID id, UUID categoryId, String titleEn, String titleBn, String descriptionEn,
			String descriptionBn, int position, BigDecimal price, int mcqCount, int writtenCount) {
	}

	private record Counts(int mcq, int written) {

		static final Counts NONE = new Counts(0, 0);

		int of(String examType) {
			return LiveExam.MCQ.equals(examType) ? mcq : written;
		}

	}

	public record PublicExam(UUID id, UUID moduleId, String examType, String titleEn, String titleBn,
			String descriptionEn, String descriptionBn, String rulesEn, String rulesBn, String paperLanguage,
			String liveAt, String endAt, int durationMinutes, BigDecimal price) {
	}

}
