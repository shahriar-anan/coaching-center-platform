package com.coachingcenter.api.exams;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LiveExamPackageExamRepository extends JpaRepository<LiveExamPackageExam, UUID> {

	@Query("select m from LiveExamPackageExam m where m.examId = :examId and m.removedAt is null")
	List<LiveExamPackageExam> findActiveByExamId(@Param("examId") UUID examId);

	@Query("""
			select m from LiveExamPackageExam m
			where m.moduleId = :moduleId and m.removedAt is null
			order by m.position asc, m.id asc
			""")
	List<LiveExamPackageExam> findActiveByModuleId(@Param("moduleId") UUID moduleId);

	/** Every membership of the exam, removed ones included. */
	List<LiveExamPackageExam> findByExamId(UUID examId);

	/** Every membership of the package, removed ones included: a grant still covers an exam it held before it was removed. */
	List<LiveExamPackageExam> findByModuleId(UUID moduleId);

	boolean existsByModuleIdAndExamIdAndRemovedAtIsNull(UUID moduleId, UUID examId);

	default boolean existsActive(UUID moduleId, UUID examId) {
		return existsByModuleIdAndExamIdAndRemovedAtIsNull(moduleId, examId);
	}

}
