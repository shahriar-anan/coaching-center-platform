package com.coachingcenter.api.exams;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

/**
 * The Master Admin's popular flag on an exam or a package (NR-01, R6 and B5). It is applied at once, not staged: it is not
 * part of an exam's content, and a popular draft stays hidden from the public.
 */
@Service
public class PopularService {

	private final LiveExamRepository exams;

	private final LiveExamModuleRepository modules;

	private final AuditRecorder audit;

	public PopularService(LiveExamRepository exams, LiveExamModuleRepository modules, AuditRecorder audit) {
		this.exams = exams;
		this.modules = modules;
		this.audit = audit;
	}

	@Transactional
	public void setExam(UUID examId, boolean popular) {
		LiveExam exam = exams.findByIdAndDeletedAtIsNull(examId).orElseThrow(this::notFound);
		exam.setPopular(popular);
		audit.record(AuditRecorder.LIVE_EXAM_POPULAR_SET, AuditRecorder.LIVE_EXAM_ENTITY, examId);
	}

	@Transactional
	public void setModule(UUID moduleId, boolean popular) {
		LiveExamModule module = modules.findByIdAndDeletedAtIsNull(moduleId).orElseThrow(this::notFound);
		module.setPopular(popular);
		audit.record(AuditRecorder.LIVE_EXAM_POPULAR_SET, AuditRecorder.LIVE_EXAM_MODULE_ENTITY, moduleId);
	}

	private ApiException notFound() {
		return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
	}

}
