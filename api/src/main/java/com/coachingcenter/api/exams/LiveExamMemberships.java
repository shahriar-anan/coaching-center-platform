package com.coachingcenter.api.exams;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

/**
 * Reads and writes an exam's packages (NR-01, R2). Code that used to read {@code exam.getModuleId()} asks here instead.
 * Until B adds several packages per exam, an exam has one active membership, so its primary package is that one.
 */
@Component
class LiveExamMemberships {

	private final LiveExamPackageExamRepository rows;

	LiveExamMemberships(LiveExamPackageExamRepository rows) {
		this.rows = rows;
	}

	/**
	 * The package the exam joined first among those it is in now, or empty when it is in none. Position orders exams
	 * inside one package, so it cannot rank one exam's packages against each other.
	 */
	Optional<UUID> primaryModuleId(UUID examId) {
		return rows.findActiveByExamId(examId).stream()
			.sorted(Comparator.comparing(LiveExamPackageExam::getAddedAt).thenComparing(LiveExamPackageExam::getId))
			.map(LiveExamPackageExam::getModuleId)
			.findFirst();
	}

	/** The primary package, or a not-found error when the exam is in none. For a read that cannot go on without one. */
	UUID requirePrimaryModuleId(UUID examId) {
		return primaryModuleId(examId).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
				HttpStatus.NOT_FOUND, "Resource not found."));
	}

	/** The packages the exam is in now, oldest membership first (the primary package first). */
	List<UUID> activeModuleIds(UUID examId) {
		return rows.findActiveByExamId(examId).stream()
			.sorted(Comparator.comparing(LiveExamPackageExam::getAddedAt).thenComparing(LiveExamPackageExam::getId))
			.map(LiveExamPackageExam::getModuleId)
			.toList();
	}

	/** Puts the exam at the end of the package. Joins the caller's transaction. */
	void attach(UUID moduleId, UUID examId, Instant now) {
		LiveExamPackageExam row = new LiveExamPackageExam();
		row.setModuleId(moduleId);
		row.setExamId(examId);
		row.setPosition(rows.findActiveByModuleId(moduleId).size());
		row.setAddedAt(now);
		rows.save(row);
	}

}
