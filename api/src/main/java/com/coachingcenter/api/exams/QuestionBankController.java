package com.coachingcenter.api.exams;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.web.PageResponse;

import tools.jackson.databind.JsonNode;

@RestController
public class QuestionBankController {

	private final QuestionBankService banks;

	private final PermissionChecker permissions;

	public QuestionBankController(QuestionBankService banks, PermissionChecker permissions) {
		this.banks = banks;
		this.permissions = permissions;
	}

	@GetMapping("/api/v1/question-bank/questions")
	public PageResponse<QuestionBankService.BrowseQuestionView> browse(@RequestParam(required = false) String subject,
			@RequestParam(required = false) String questionType, @RequestParam(required = false) UUID paperId,
			@PageableDefault(size = 20) Pageable pageable) {
		requireManage();
		return banks.browse(subject, questionType, paperId, pageable);
	}

	@PostMapping("/api/v1/question-banks")
	public ResponseEntity<QuestionBankService.BankDetailView> create(@RequestBody JsonNode body) {
		requireManage();
		return ResponseEntity.status(HttpStatus.CREATED).body(banks.create(body));
	}

	@GetMapping("/api/v1/question-banks/{id}")
	public QuestionBankService.BankDetailView detail(@PathVariable UUID id) {
		requireManage();
		return banks.detail(id);
	}

	@DeleteMapping("/api/v1/question-banks/{id}/questions/{questionId}")
	public ResponseEntity<Void> removeQuestion(@PathVariable UUID id, @PathVariable UUID questionId) {
		requireManage();
		banks.removeQuestion(id, questionId);
		return ResponseEntity.noContent().build();
	}

	private void requireManage() {
		if (!permissions.has(Permissions.QUESTION_BANK_MANAGE)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
	}

}
