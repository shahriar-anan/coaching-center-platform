package com.coachingcenter.api.exams;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveExamOptionRepository extends JpaRepository<LiveExamOption, UUID> {

	List<LiveExamOption> findByQuestionIdAndDeletedAtIsNullOrderByPositionAscIdAsc(UUID questionId);

	List<LiveExamOption> findByQuestionIdInAndDeletedAtIsNullOrderByPositionAscIdAsc(List<UUID> questionIds);

}
