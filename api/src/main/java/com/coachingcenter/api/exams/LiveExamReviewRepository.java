package com.coachingcenter.api.exams;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LiveExamReviewRepository extends JpaRepository<LiveExamReview, UUID> {

	Optional<LiveExamReview> findByExamIdAndStudentId(UUID examId, UUID studentId);

	Optional<LiveExamReview> findByModuleIdAndStudentId(UUID moduleId, UUID studentId);

	List<LiveExamReview> findByStudentIdOrderByCreatedAtDescIdDesc(UUID studentId);

	long countByStatus(String status);

	/** The student's attempts that were submitted on the exam. A deleted attempt does not count. */
	@Query("""
			select count(p) from LiveExamParticipant p
			where p.studentId = :studentId and p.examId = :examId and p.status = 'SUBMITTED' and p.deletedAt is null
			""")
	long countSatExam(@Param("studentId") UUID studentId, @Param("examId") UUID examId);

	/**
	 * The student's submitted attempts on exams that are active members of the package (a removed membership and a deleted
	 * exam do not count). Zero means the student has not sat anything in the package.
	 */
	@Query("""
			select count(p) from LiveExamParticipant p, LiveExamPackageExam pe, LiveExam e
			where p.studentId = :studentId and p.status = 'SUBMITTED' and p.deletedAt is null
			  and pe.examId = p.examId and pe.moduleId = :moduleId and pe.removedAt is null
			  and e.id = p.examId and e.deletedAt is null
			""")
	long countSatExamsInPackage(@Param("studentId") UUID studentId, @Param("moduleId") UUID moduleId);

}
