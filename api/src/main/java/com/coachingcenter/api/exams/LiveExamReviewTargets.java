package com.coachingcenter.api.exams;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

/** The exam or package a review is about, read for its titles, category and status (reviews plan, sections 4 and 6). */
@Component
class LiveExamReviewTargets {

	private final LiveExamRepository exams;

	private final LiveExamModuleRepository modules;

	LiveExamReviewTargets(LiveExamRepository exams, LiveExamModuleRepository modules) {
		this.exams = exams;
		this.modules = modules;
	}

	/** The target when it exists and is not soft-deleted, otherwise null. */
	Target live(LiveExamReviewKind kind, UUID id) {
		return switch (kind) {
			case EXAM -> exams.findByIdAndDeletedAtIsNull(id).map(LiveExamReviewTargets::of).orElse(null);
			case PACKAGE -> modules.findByIdAndDeletedAtIsNull(id).map(LiveExamReviewTargets::of).orElse(null);
		};
	}

	/** Like {@link #live}, but a missing or soft-deleted target is a not-found error. */
	Target requireLive(LiveExamReviewKind kind, UUID id) {
		Target target = live(kind, id);
		if (target == null) {
			throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
		}
		return target;
	}

	/** The targets among {@code ids}, by id. Soft-deleted ones are left out unless {@code includeDeleted} is set. */
	Map<UUID, Target> load(LiveExamReviewKind kind, Collection<UUID> ids, boolean includeDeleted) {
		Map<UUID, Target> byId = new HashMap<>();
		switch (kind) {
			case EXAM -> exams.findAllById(ids).forEach(exam -> {
				if (includeDeleted || exam.getDeletedAt() == null) {
					byId.put(exam.getId(), of(exam));
				}
			});
			case PACKAGE -> modules.findAllById(ids).forEach(module -> {
				if (includeDeleted || module.getDeletedAt() == null) {
					byId.put(module.getId(), of(module));
				}
			});
		}
		return byId;
	}

	private static Target of(LiveExam exam) {
		return new Target(LiveExamReviewKind.EXAM, exam.getId(), exam.getCategoryId(), exam.getStatus(), exam.getTitleEn(),
				exam.getTitleBn());
	}

	private static Target of(LiveExamModule module) {
		return new Target(LiveExamReviewKind.PACKAGE, module.getId(), module.getCategoryId(), module.getStatus(),
				module.getTitleEn(), module.getTitleBn());
	}

	record Target(LiveExamReviewKind kind, UUID id, UUID categoryId, String status, String titleEn, String titleBn) {
	}

}
