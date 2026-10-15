package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

import tools.jackson.databind.JsonNode;

@Service
public class ExamComponentService {

	private static final Set<String> COMPONENT_TYPES = Set.of("MCQ", "WRITTEN");

	private final ExamNodeRepository nodes;

	private final PaperComponentRepository components;

	private final QuestionRepository questions;

	private final QuestionOptionRepository options;

	private final ComponentQuestionRepository links;

	public ExamComponentService(ExamNodeRepository nodes, PaperComponentRepository components,
			QuestionRepository questions, QuestionOptionRepository options, ComponentQuestionRepository links) {
		this.nodes = nodes;
		this.components = components;
		this.questions = questions;
		this.options = options;
		this.links = links;
	}

	@Transactional
	public ComponentView createComponent(UUID paperId, JsonNode body) {
		ExamNode paper = requirePaper(paperId);
		String type = requiredText(body, "type");
		if (!COMPONENT_TYPES.contains(type)) {
			throw validationFailed();
		}
		if (components.findByPaperIdAndComponentTypeAndDeletedAtIsNull(paperId, type).isPresent()) {
			throw new ApiException(ErrorCode.COMPONENT_EXISTS, HttpStatus.CONFLICT,
					"This paper already has that component type.");
		}
		PaperComponent component = new PaperComponent();
		component.setPaperId(paper.getId());
		component.setComponentType(type);
		return view(components.save(component), List.of());
	}

	@Transactional(readOnly = true)
	public ComponentView getComponent(UUID componentId) {
		PaperComponent component = requireComponent(componentId);
		return view(component, loadQuestions(component.getId()));
	}

	@Transactional
	public ComponentView patchComponent(UUID componentId, JsonNode body) {
		PaperComponent component = requireComponent(componentId);
		if ("MCQ".equals(component.getComponentType())) {
			patchMcq(component, body);
		}
		else {
			patchWritten(component, body);
		}
		return view(component, loadQuestions(component.getId()));
	}

	@Transactional
	public ComponentView scheduleComponent(UUID componentId) {
		PaperComponent component = requireComponent(componentId);
		if (!"MCQ".equals(component.getComponentType())) {
			throw validationFailed();
		}
		requirePassPercent(component.getPaperId());
		if (isBlank(component.getDescriptionEn()) || isBlank(component.getDescriptionBn())) {
			throw validationFailed();
		}
		if (component.getStartsAt() == null || component.getEndsAt() == null || component.getDurationSeconds() == null) {
			throw validationFailed();
		}
		component.setScheduled(true);
		return view(component, loadQuestions(component.getId()));
	}

	@Transactional
	public PaperView patchPaper(UUID paperId, JsonNode body) {
		ExamNode paper = requirePaperForPassPatch(paperId);
		if (!body.has("passPercent") || body.get("passPercent").isNull()) {
			throw validationFailed();
		}
		int passPercent = parsePassPercent(body.get("passPercent"));
		paper.setPassPercent(passPercent);
		return paperView(paper);
	}

	@Transactional(readOnly = true)
	public PaperView getPaper(UUID paperId) {
		return paperView(requirePaper(paperId));
	}

	@Transactional
	public QuestionView createQuestion(UUID componentId, JsonNode body) {
		PaperComponent component = requireComponent(componentId);
		Question question = buildQuestion(component.getComponentType(), body);
		questions.save(question);
		if ("MCQ".equals(component.getComponentType())) {
			saveOptions(question.getId(), body.get("options"));
		}
		attach(component, question.getId());
		return questionView(question);
	}

	@Transactional
	public void attachQuestion(UUID componentId, UUID questionId) {
		PaperComponent component = requireComponent(componentId);
		Question question = requireQuestion(questionId);
		if (!component.getComponentType().equals(question.getQuestionType())) {
			throw typeMismatch();
		}
		if (links.findByComponentIdAndQuestionIdAndDeletedAtIsNull(componentId, questionId).isPresent()) {
			return;
		}
		attach(component, questionId);
	}

	@Transactional
	public QuestionView updateQuestion(UUID questionId, JsonNode body) {
		Question question = requireQuestion(questionId);
		if (body.has("stem") && !body.get("stem").isNull()) {
			question.setStem(requiredText(body, "stem"));
		}
		if (body.has("explanation")) {
			question.setExplanation(body.get("explanation").isNull() ? null : body.get("explanation").asText());
		}
		if (body.has("marks") && !body.get("marks").isNull()) {
			question.setMarks(parseMarks(body.get("marks")));
		}
		if (body.has("subject") && !body.get("subject").isNull()) {
			question.setSubject(requiredText(body, "subject"));
		}
		if ("MCQ".equals(question.getQuestionType()) && body.has("options") && body.get("options").isArray()) {
			softDeleteOptions(question.getId());
			saveOptions(question.getId(), body.get("options"));
		}
		return questionView(question);
	}

	@Transactional(readOnly = true)
	public QuestionView getQuestion(UUID questionId) {
		return questionView(requireQuestion(questionId));
	}

	@Transactional
	public void detachQuestion(UUID componentId, UUID questionId) {
		requireComponent(componentId);
		ComponentQuestion link = links.findByComponentIdAndQuestionIdAndDeletedAtIsNull(componentId, questionId)
			.orElseThrow(this::notFound);
		link.setDeletedAt(Instant.now());
	}

	@Transactional
	public void reorderQuestions(UUID componentId, JsonNode body) {
		requireComponent(componentId);
		JsonNode ids = body.get("questionIds");
		if (ids == null || !ids.isArray() || ids.isEmpty()) {
			throw validationFailed();
		}
		List<UUID> ordered = new ArrayList<>();
		for (JsonNode idNode : ids) {
			ordered.add(UUID.fromString(idNode.asText()));
		}
		List<ComponentQuestion> rows = links.findByComponentIdAndDeletedAtIsNullOrderByPositionAsc(componentId);
		if (rows.size() != ordered.size()) {
			throw validationFailed();
		}
		Set<UUID> linkIds = new HashSet<>();
		Map<UUID, ComponentQuestion> byQuestion = new HashMap<>();
		for (ComponentQuestion row : rows) {
			linkIds.add(row.getQuestionId());
			byQuestion.put(row.getQuestionId(), row);
		}
		if (!linkIds.equals(new HashSet<>(ordered))) {
			throw validationFailed();
		}
		for (int index = 0; index < ordered.size(); index++) {
			byQuestion.get(ordered.get(index)).setPosition(index);
		}
	}

	@Transactional
	public void softDeleteForPaper(UUID paperId) {
		for (PaperComponent component : components.findByPaperIdAndDeletedAtIsNull(paperId)) {
			component.setDeletedAt(Instant.now());
			for (ComponentQuestion link : links.findByComponentIdAndDeletedAtIsNullOrderByPositionAsc(component.getId())) {
				link.setDeletedAt(Instant.now());
			}
		}
	}

	private void attach(PaperComponent component, UUID questionId) {
		int position = links.findByComponentIdAndDeletedAtIsNullOrderByPositionAsc(component.getId()).size();
		ComponentQuestion link = new ComponentQuestion();
		link.setComponentId(component.getId());
		link.setQuestionId(questionId);
		link.setPosition(position);
		links.save(link);
	}

	private Question buildQuestion(String componentType, JsonNode body) {
		Question question = new Question();
		question.setQuestionType(componentType);
		question.setStem(requiredText(body, "stem"));
		question.setMarks(parseMarks(body.get("marks")));
		question.setSubject(requiredText(body, "subject"));
		if (body.has("explanation") && !body.get("explanation").isNull()) {
			question.setExplanation(body.get("explanation").asText());
		}
		if ("MCQ".equals(componentType)) {
			validateMcqOptions(body.get("options"));
		}
		else if (body.has("options") && body.get("options").isArray() && !body.get("options").isEmpty()) {
			throw validationFailed();
		}
		return question;
	}

	private void saveOptions(UUID questionId, JsonNode optionsNode) {
		validateMcqOptions(optionsNode);
		int position = 0;
		for (JsonNode optionNode : optionsNode) {
			QuestionOption option = new QuestionOption();
			option.setQuestionId(questionId);
			option.setText(requiredText(optionNode, "text"));
			option.setCorrect(optionNode.has("correct") && optionNode.get("correct").asBoolean());
			option.setPosition(position++);
			options.save(option);
		}
	}

	private void validateMcqOptions(JsonNode optionsNode) {
		if (optionsNode == null || !optionsNode.isArray() || optionsNode.size() < 2) {
			throw validationFailed();
		}
		int correct = 0;
		for (JsonNode optionNode : optionsNode) {
			if (optionNode.has("correct") && optionNode.get("correct").asBoolean()) {
				correct++;
			}
		}
		if (correct != 1) {
			throw validationFailed();
		}
	}

	private void softDeleteOptions(UUID questionId) {
		for (QuestionOption option : options.findByQuestionIdAndDeletedAtIsNullOrderByPositionAsc(questionId)) {
			option.setDeletedAt(Instant.now());
		}
	}

	private List<QuestionView> loadQuestions(UUID componentId) {
		return links.findByComponentIdAndDeletedAtIsNullOrderByPositionAsc(componentId)
			.stream()
			.map(link -> questionView(requireQuestion(link.getQuestionId())))
			.toList();
	}

	private QuestionView questionView(Question question) {
		List<OptionView> optionViews = List.of();
		if ("MCQ".equals(question.getQuestionType())) {
			optionViews = options.findByQuestionIdAndDeletedAtIsNullOrderByPositionAsc(question.getId())
				.stream()
				.map(option -> new OptionView(option.getId(), option.getText(), option.isCorrect(), option.getPosition()))
				.toList();
		}
		return new QuestionView(question.getId(), question.getQuestionType(), question.getStem(),
				question.getExplanation(), question.getMarks(), question.getSubject(), optionViews);
	}

	private void patchMcq(PaperComponent component, JsonNode body) {
		rejectWrittenOnlyFields(body);
		boolean configuring = body.has("descriptionEn") || body.has("descriptionBn") || body.has("startsAt")
				|| body.has("endsAt") || body.has("durationSeconds") || body.has("immediateResults");
		if (configuring && (!body.has("descriptionEn") || body.get("descriptionEn").isNull()
				|| body.get("descriptionEn").asText().isBlank() || !body.has("descriptionBn")
				|| body.get("descriptionBn").isNull() || body.get("descriptionBn").asText().isBlank())) {
			throw validationFailed();
		}
		if (body.has("descriptionEn")) {
			component.setDescriptionEn(requiredText(body, "descriptionEn"));
		}
		if (body.has("descriptionBn")) {
			component.setDescriptionBn(requiredText(body, "descriptionBn"));
		}
		if (body.has("startsAt") && !body.get("startsAt").isNull()) {
			component.setStartsAt(parseInstant(body, "startsAt"));
		}
		if (body.has("endsAt") && !body.get("endsAt").isNull()) {
			component.setEndsAt(parseInstant(body, "endsAt"));
		}
		if (body.has("durationSeconds") && !body.get("durationSeconds").isNull()) {
			int duration = body.get("durationSeconds").asInt();
			if (duration <= 0) {
				throw validationFailed();
			}
			component.setDurationSeconds(duration);
		}
		if (body.has("immediateResults") && !body.get("immediateResults").isNull()) {
			component.setImmediateResults(body.get("immediateResults").asBoolean());
		}
		if (component.getStartsAt() != null && component.getEndsAt() != null
				&& !component.getEndsAt().isAfter(component.getStartsAt())) {
			throw validationFailed();
		}
	}

	private void patchWritten(PaperComponent component, JsonNode body) {
		rejectMcqOnlyFields(body);
		if (body.has("descriptionEn")) {
			component.setDescriptionEn(requiredText(body, "descriptionEn"));
		}
		if (body.has("descriptionBn")) {
			component.setDescriptionBn(requiredText(body, "descriptionBn"));
		}
		if (body.has("deadlineAt")) {
			if (body.get("deadlineAt").isNull()) {
				component.setDeadlineAt(null);
			}
			else {
				requirePassPercent(component.getPaperId());
				component.setDeadlineAt(parseInstant(body, "deadlineAt"));
			}
		}
	}

	private void rejectWrittenOnlyFields(JsonNode body) {
		if (body.has("deadlineAt") && !body.get("deadlineAt").isNull()) {
			throw validationFailed();
		}
	}

	private void rejectMcqOnlyFields(JsonNode body) {
		if (hasNonNull(body, "startsAt") || hasNonNull(body, "endsAt") || hasNonNull(body, "durationSeconds")
				|| hasNonNull(body, "immediateResults")) {
			throw validationFailed();
		}
	}

	private void requirePassPercent(UUID paperId) {
		ExamNode paper = nodes.findByIdAndDeletedAtIsNull(paperId).orElseThrow(this::notFound);
		if (paper.getPassPercent() == null) {
			throw new ApiException(ErrorCode.PASS_MARK_REQUIRED, HttpStatus.CONFLICT,
					"Set the paper pass percent before scheduling or setting a deadline.");
		}
	}

	private static boolean hasNonNull(JsonNode body, String field) {
		return body.has(field) && !body.get(field).isNull();
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private static Instant parseInstant(JsonNode body, String field) {
		JsonNode node = body.get(field);
		if (node == null || node.isNull() || node.asText().isBlank()) {
			throw validationFailed();
		}
		return Instant.parse(node.asText().trim());
	}

	private static int parsePassPercent(JsonNode node) {
		if (!node.isIntegralNumber()) {
			throw validationFailed();
		}
		int value = node.asInt();
		if (value < 0 || value > 100) {
			throw validationFailed();
		}
		return value;
	}

	private String deriveLifecycle(PaperComponent component) {
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

	private BigDecimal combinedMaxMarks(UUID paperId) {
		List<PaperComponent> rows = components.findByPaperIdAndDeletedAtIsNull(paperId);
		PaperComponent mcq = null;
		PaperComponent written = null;
		for (PaperComponent row : rows) {
			if ("MCQ".equals(row.getComponentType())) {
				mcq = row;
			}
			else if ("WRITTEN".equals(row.getComponentType())) {
				written = row;
			}
		}
		if (mcq == null || written == null) {
			return null;
		}
		return sumMarks(mcq.getId()).add(sumMarks(written.getId()));
	}

	private BigDecimal sumMarks(UUID componentId) {
		BigDecimal total = BigDecimal.ZERO;
		for (ComponentQuestion link : links.findByComponentIdAndDeletedAtIsNullOrderByPositionAsc(componentId)) {
			total = total.add(requireQuestion(link.getQuestionId()).getMarks());
		}
		return total;
	}

	private PaperView paperView(ExamNode paper) {
		return new PaperView(paper.getId(), paper.getTitleEn(), paper.getTitleBn(), paper.getPassPercent(),
				combinedMaxMarks(paper.getId()));
	}

	private ComponentView view(PaperComponent component, List<QuestionView> questionViews) {
		return new ComponentView(component.getId(), component.getPaperId(), component.getComponentType(),
				component.getDescriptionEn(), component.getDescriptionBn(), component.getStartsAt(), component.getEndsAt(),
				component.getDurationSeconds(), component.isImmediateResults(), component.isScheduled(),
				component.getDeadlineAt(), deriveLifecycle(component), questionViews);
	}

	private ExamNode requirePaper(UUID paperId) {
		ExamNode node = nodes.findByIdAndDeletedAtIsNull(paperId).orElseThrow(this::notFound);
		if (!"PAPER".equals(node.getNodeType())) {
			throw nodeTypeRejected();
		}
		return node;
	}

	private ExamNode requirePaperForPassPatch(UUID nodeId) {
		ExamNode node = nodes.findByIdAndDeletedAtIsNull(nodeId).orElseThrow(this::notFound);
		if ("SECTION".equals(node.getNodeType())) {
			throw validationFailed();
		}
		if (!"PAPER".equals(node.getNodeType())) {
			throw nodeTypeRejected();
		}
		return node;
	}

	private PaperComponent requireComponent(UUID componentId) {
		return components.findByIdAndDeletedAtIsNull(componentId).orElseThrow(this::notFound);
	}

	private Question requireQuestion(UUID questionId) {
		return questions.findByIdAndDeletedAtIsNull(questionId).orElseThrow(this::notFound);
	}

	private static BigDecimal parseMarks(JsonNode node) {
		if (node == null || node.isNull()) {
			throw validationFailed();
		}
		BigDecimal marks = node.isNumber() ? node.decimalValue() : new BigDecimal(node.asText().trim());
		if (marks.compareTo(BigDecimal.ZERO) <= 0) {
			throw validationFailed();
		}
		return marks;
	}

	private static String requiredText(JsonNode body, String field) {
		JsonNode node = body.get(field);
		if (node == null || node.isNull() || node.asText().isBlank()) {
			throw validationFailed();
		}
		return node.asText().trim();
	}

	private static ApiException nodeTypeRejected() {
		return new ApiException(ErrorCode.NODE_TYPE_REJECTED, HttpStatus.BAD_REQUEST, "Node type or placement is invalid.");
	}

	private static ApiException typeMismatch() {
		return new ApiException(ErrorCode.QUESTION_TYPE_MISMATCH, HttpStatus.BAD_REQUEST,
				"Question type does not match the component.");
	}

	private static ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
	}

	private ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	public record ComponentView(UUID id, UUID paperId, String type, String descriptionEn, String descriptionBn,
			Instant startsAt, Instant endsAt, Integer durationSeconds, boolean immediateResults, boolean scheduled,
			Instant deadlineAt, String lifecycle, List<QuestionView> questions) {
	}

	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
	public record PaperView(UUID id, String titleEn, String titleBn, Integer passPercent, BigDecimal combinedMaxMarks) {
	}

	public record QuestionView(UUID id, String questionType, String stem, String explanation, BigDecimal marks,
			String subject, List<OptionView> options) {
	}

	public record OptionView(UUID id, String text, boolean correct, int position) {
	}

}
