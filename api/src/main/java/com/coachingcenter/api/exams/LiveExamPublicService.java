package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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

	@Transactional(readOnly = true)
	public List<PublicCategory> categories() {
		Set<UUID> hidden = visibility.hiddenCategoryIds();
		Map<UUID, List<PublicModule>> modulesByCategory = new HashMap<>();
		for (LiveExamModule module : modules.findByDeletedAtIsNull()) {
			if (!LiveExamModule.PUBLISHED.equals(module.getStatus()) || hidden.contains(module.getCategoryId())) {
				continue;
			}
			modulesByCategory.computeIfAbsent(module.getCategoryId(), ignored -> new ArrayList<>()).add(moduleView(module));
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
		sort(roots);
		return roots;
	}

	@Transactional(readOnly = true)
	public Page<PublicModule> modules(UUID categoryId, Pageable pageable) {
		Set<UUID> hidden = visibility.hiddenCategoryIds();
		Pageable ordered = orderedModules(pageable);
		if (categoryId != null && hidden.contains(categoryId)) {
			return Page.empty(ordered);
		}
		Page<LiveExamModule> page;
		if (categoryId != null) {
			page = modules.findByCategoryIdAndStatusAndDeletedAtIsNull(categoryId, LiveExamModule.PUBLISHED, ordered);
		}
		else if (hidden.isEmpty()) {
			page = modules.findByStatusAndDeletedAtIsNull(LiveExamModule.PUBLISHED, ordered);
		}
		else {
			page = modules.findByStatusAndDeletedAtIsNullAndCategoryIdNotIn(LiveExamModule.PUBLISHED, hidden, ordered);
		}
		return page.map(this::moduleView);
	}

	@Transactional(readOnly = true)
	public PublicModule module(UUID id) {
		return moduleView(requireModule(id));
	}

	@Transactional(readOnly = true)
	public Page<PublicExam> exams(UUID moduleId, Pageable pageable) {
		requireModule(moduleId);
		return exams
			.findByModuleIdAndStatusAndDeletedAtIsNull(moduleId, LiveExam.PUBLISHED, orderedExams(pageable))
			.map(this::examView);
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

	private PublicModule moduleView(LiveExamModule module) {
		return new PublicModule(module.getId(), module.getCategoryId(), module.getTitleEn(), module.getTitleBn(),
				module.getDescriptionEn(), module.getDescriptionBn(), module.getPosition(), module.getPrice());
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
			String descriptionBn, int position, BigDecimal price) {
	}

	public record PublicExam(UUID id, UUID moduleId, String examType, String titleEn, String titleBn,
			String descriptionEn, String descriptionBn, String rulesEn, String rulesBn, String paperLanguage,
			String liveAt, String endAt, int durationMinutes, BigDecimal price) {
	}

}
