package com.coachingcenter.api.exams;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coachingcenter.api.common.audit.AuditRecorder;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

/**
 * Soft delete of a live exam. Refused once a student has sat it or paid for it by itself. The exam also leaves every
 * package it was in, and a staged edit waiting on it is dropped.
 */
@Service
public class LiveExamRemoval {

	private final LiveExamRepository exams;

	private final LiveExamParticipantRepository participants;

	private final LiveExamOrderRepository orders;

	private final LiveExamGrantRepository grants;

	private final LiveExamPackageExamRepository packageExams;

	private final LiveExamRevisionRepository revisions;

	private final AuditRecorder audit;

	private final Clock clock;

	LiveExamRemoval(LiveExamRepository exams, LiveExamParticipantRepository participants, LiveExamOrderRepository orders,
			LiveExamGrantRepository grants, LiveExamPackageExamRepository packageExams,
			LiveExamRevisionRepository revisions, AuditRecorder audit, Clock clock) {
		this.exams = exams;
		this.participants = participants;
		this.orders = orders;
		this.grants = grants;
		this.packageExams = packageExams;
		this.revisions = revisions;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional
	public void delete(UUID examId) {
		LiveExam exam = exams.lockById(examId)
			.filter(found -> found.getDeletedAt() == null)
			.orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found."));
		if (participants.existsByExamId(examId)) {
			throw new ApiException(ErrorCode.EXAM_HAS_PARTICIPANTS, HttpStatus.CONFLICT,
					"A student has already sat this exam.");
		}
		if (orders.existsByExamIdAndStatus(examId, LiveExamOrder.INITIATED)) {
			throw new ApiException(ErrorCode.EXAM_HAS_PURCHASES, HttpStatus.CONFLICT,
					"A student has already started the payment process.");
		}
		if (orders.existsByExamIdAndStatus(examId, LiveExamOrder.SUCCEEDED)
				|| grants.existsByScopeAndExamId(LiveExamOrder.EXAM, examId)) {
			throw new ApiException(ErrorCode.EXAM_HAS_PURCHASES, HttpStatus.CONFLICT,
					"A student has already bought this exam.");
		}
		Instant now = clock.instant();
		exam.setDeletedAt(now);
		for (LiveExamPackageExam membership : packageExams.findActiveByExamId(examId)) {
			membership.setRemovedAt(now);
		}
		revisions.findByExamId(examId).ifPresent(revisions::delete);
		audit.record(AuditRecorder.LIVE_EXAM_DELETE, AuditRecorder.LIVE_EXAM_ENTITY, examId);
	}

}
