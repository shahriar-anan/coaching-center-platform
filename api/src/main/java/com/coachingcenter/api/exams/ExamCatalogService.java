package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

@Service
public class ExamCatalogService {

	private final ExamModuleRepository modules;

	private final ExamNodeRepository nodes;

	private final PaperComponentRepository components;

	private final ComponentQuestionRepository links;

	private final QuestionRepository questions;

	private final ExamAccessService access;

	public ExamCatalogService(ExamModuleRepository modules, ExamNodeRepository nodes,
			PaperComponentRepository components, ComponentQuestionRepository links, QuestionRepository questions,
			ExamAccessService access) {
		this.modules = modules;
		this.nodes = nodes;
		this.components = components;
		this.links = links;
		this.questions = questions;
		this.access = access;
	}

	@Transactional(readOnly = true)
	public Page<ExamModuleSummary> list(String query, Pageable pageable, UUID studentId) {
		String q = query == null ? "" : query.trim();
		return modules.findCatalogModules(q, pageable).map(module -> summary(module, studentId));
	}

	@Transactional(readOnly = true)
	public ExamModuleDetail get(UUID moduleId, UUID studentId) {
		ExamModule module = requireCatalogModule(moduleId);
		return detail(module, studentId);
	}

	@Transactional(readOnly = true)
	public StudentComponentView getComponent(UUID moduleId, UUID componentId, UUID studentId) {
		ExamModule module = requireCatalogModule(moduleId);
		access.requireAccess(module, studentId);
		PaperComponent component = components.findByIdAndDeletedAtIsNull(componentId).orElseThrow(this::notFound);
		ExamNode paper = nodes.findByIdAndDeletedAtIsNull(component.getPaperId()).orElseThrow(this::notFound);
		if (!moduleId.equals(paper.getModuleId())) {
			throw notFound();
		}
		return studentComponentView(component);
	}

	private ExamModuleSummary summary(ExamModule module, UUID studentId) {
		return new ExamModuleSummary(module.getId(), module.getTitleEn(), module.getTitleBn(), module.getPrice(),
				module.getAccessMode(), access.accessGranted(module, studentId));
	}

	private ExamModuleDetail detail(ExamModule module, UUID studentId) {
		List<TreeNode> tree = nodes.findByModuleIdAndDeletedAtIsNullOrderByPositionAsc(module.getId())
			.stream()
			.map(node -> new TreeNode(node.getId(), node.getParentId(), node.getNodeType(), node.getTitleEn(),
					node.getTitleBn(), node.getPosition()))
			.toList();
		return new ExamModuleDetail(module.getId(), module.getTitleEn(), module.getTitleBn(), module.getDescriptionEn(),
				module.getDescriptionBn(), module.getPrice(), module.getAccessMode(),
				access.accessGranted(module, studentId), tree);
	}

	private StudentComponentView studentComponentView(PaperComponent component) {
		List<ComponentQuestion> rows = links.findByComponentIdAndDeletedAtIsNullOrderByPositionAsc(component.getId());
		int questionCount = rows.size();
		BigDecimal maxMarks = BigDecimal.ZERO;
		List<WrittenQuestionPreview> writtenQuestions = new ArrayList<>();
		for (ComponentQuestion row : rows) {
			Question question = questions.findByIdAndDeletedAtIsNull(row.getQuestionId()).orElseThrow(this::notFound);
			maxMarks = maxMarks.add(question.getMarks());
			if ("WRITTEN".equals(component.getComponentType())) {
				writtenQuestions.add(new WrittenQuestionPreview(question.getId(), question.getStem(), question.getMarks()));
			}
		}
		return new StudentComponentView(component.getId(), component.getComponentType(), component.getDescriptionEn(),
				component.getDescriptionBn(), component.getStartsAt(), component.getEndsAt(),
				component.getDurationSeconds(), component.isImmediateResults(), component.getDeadlineAt(),
				deriveLifecycle(component), questionCount, maxMarks, writtenQuestions);
	}

	private static String deriveLifecycle(PaperComponent component) {
		if (!component.isScheduled()) {
			return "DRAFT";
		}
		if (component.getStartsAt() == null || component.getEndsAt() == null) {
			return "DRAFT";
		}
		Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
		if (now.isBefore(component.getStartsAt())) {
			return "SCHEDULED";
		}
		if (now.isBefore(component.getEndsAt())) {
			return "ACTIVE";
		}
		return "ENDED";
	}

	private ExamModule requireCatalogModule(UUID moduleId) {
		return modules.findCatalogModule(moduleId).orElseThrow(this::notFound);
	}

	private ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	public record ExamModuleSummary(UUID id, String titleEn, String titleBn, BigDecimal price, String accessMode,
			boolean accessGranted) {
	}

	public record ExamModuleDetail(UUID id, String titleEn, String titleBn, String descriptionEn, String descriptionBn,
			BigDecimal price, String accessMode, boolean accessGranted, List<TreeNode> nodes) {
	}

	public record TreeNode(UUID id, UUID parentId, String nodeType, String titleEn, String titleBn, int position) {
	}

	public record WrittenQuestionPreview(UUID id, String stem, BigDecimal marks) {
	}

	public record StudentComponentView(UUID id, String type, String descriptionEn, String descriptionBn,
			Instant startsAt, Instant endsAt, Integer durationSeconds, boolean immediateResults, Instant deadlineAt,
			String lifecycle, int questionCount, BigDecimal maxMarks, List<WrittenQuestionPreview> questions) {
	}

}
