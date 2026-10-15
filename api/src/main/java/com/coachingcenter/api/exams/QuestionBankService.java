package com.coachingcenter.api.exams;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.web.PageResponse;

import tools.jackson.databind.JsonNode;

@Service
public class QuestionBankService {

	private final QuestionRepository questions;

	private final ComponentQuestionRepository links;

	private final PaperComponentRepository components;

	private final QuestionBankRepository banks;

	private final QuestionBankItemRepository items;

	public QuestionBankService(QuestionRepository questions, ComponentQuestionRepository links,
			PaperComponentRepository components, QuestionBankRepository banks, QuestionBankItemRepository items) {
		this.questions = questions;
		this.links = links;
		this.components = components;
		this.banks = banks;
		this.items = items;
	}

	@Transactional(readOnly = true)
	public PageResponse<BrowseQuestionView> browse(String subject, String questionType, UUID paperId, Pageable pageable) {
		List<BrowseQuestionView> all = new ArrayList<>();
		for (Question question : questions.findAll()) {
			if (question.getDeletedAt() != null) {
				continue;
			}
			if (subject != null && !subject.isBlank() && !subject.equals(question.getSubject())) {
				continue;
			}
			if (questionType != null && !questionType.isBlank() && !questionType.equals(question.getQuestionType())) {
				continue;
			}
			List<UUID> paperIds = paperIdsForQuestion(question.getId());
			if (paperId != null && !paperIds.contains(paperId)) {
				continue;
			}
			all.add(new BrowseQuestionView(question.getId(), question.getQuestionType(), question.getSubject(), paperIds));
		}
		int start = (int) pageable.getOffset();
		int end = Math.min(start + pageable.getPageSize(), all.size());
		List<BrowseQuestionView> slice = start >= all.size() ? List.of() : all.subList(start, end);
		Page<BrowseQuestionView> page = new org.springframework.data.domain.PageImpl<>(slice, pageable, all.size());
		return PageResponse.from(page);
	}

	@Transactional
	public BankDetailView create(JsonNode body) {
		String name = requiredText(body, "name");
		JsonNode idsNode = body.get("questionIds");
		if (idsNode == null || !idsNode.isArray() || idsNode.isEmpty()) {
			throw validationFailed();
		}
		List<UUID> questionIds = new ArrayList<>();
		for (JsonNode node : idsNode) {
			questionIds.add(UUID.fromString(node.asText()));
		}
		for (UUID questionId : questionIds) {
			questions.findByIdAndDeletedAtIsNull(questionId).orElseThrow(this::notFound);
		}
		QuestionBank bank = new QuestionBank();
		bank.setName(name);
		bank = banks.save(bank);
		for (int i = 0; i < questionIds.size(); i++) {
			QuestionBankItem item = new QuestionBankItem();
			item.setBankId(bank.getId());
			item.setQuestionId(questionIds.get(i));
			item.setPosition(i);
			items.save(item);
		}
		return detail(bank.getId());
	}

	@Transactional(readOnly = true)
	public BankDetailView detail(UUID bankId) {
		QuestionBank bank = banks.findByIdAndDeletedAtIsNull(bankId).orElseThrow(this::notFound);
		List<BankQuestionView> questionViews = new ArrayList<>();
		for (QuestionBankItem item : items.findByBankIdAndDeletedAtIsNullOrderByPositionAsc(bankId)) {
			Question question = questions.findByIdAndDeletedAtIsNull(item.getQuestionId()).orElseThrow(this::notFound);
			questionViews.add(new BankQuestionView(question.getId(), question.getQuestionType(), question.getStem(),
					question.getSubject(), item.getPosition()));
		}
		return new BankDetailView(bank.getId(), bank.getName(), questionViews);
	}

	@Transactional
	public void removeQuestion(UUID bankId, UUID questionId) {
		banks.findByIdAndDeletedAtIsNull(bankId).orElseThrow(this::notFound);
		QuestionBankItem item = items.findByBankIdAndQuestionIdAndDeletedAtIsNull(bankId, questionId)
			.orElseThrow(this::notFound);
		item.setDeletedAt(java.time.Instant.now());
	}

	private List<UUID> paperIdsForQuestion(UUID questionId) {
		Set<UUID> paperIds = new HashSet<>();
		for (ComponentQuestion link : links.findByQuestionIdAndDeletedAtIsNull(questionId)) {
			components.findByIdAndDeletedAtIsNull(link.getComponentId())
				.ifPresent(component -> paperIds.add(component.getPaperId()));
		}
		return paperIds.stream().sorted().toList();
	}

	private static String requiredText(JsonNode body, String field) {
		JsonNode node = body.get(field);
		if (node == null || node.isNull() || node.asText().isBlank()) {
			throw validationFailed();
		}
		return node.asText();
	}

	private ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

	private static ApiException validationFailed() {
		return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
	}

	public record BrowseQuestionView(UUID id, String questionType, String subject, List<UUID> paperIds) {
	}

	public record BankDetailView(UUID id, String name, List<BankQuestionView> questions) {
	}

	public record BankQuestionView(UUID id, String questionType, String stem, String subject, int position) {
	}

}
