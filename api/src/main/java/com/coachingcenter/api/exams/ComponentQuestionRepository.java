package com.coachingcenter.api.exams;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ComponentQuestionRepository extends JpaRepository<ComponentQuestion, UUID> {

	List<ComponentQuestion> findByComponentIdAndDeletedAtIsNullOrderByPositionAsc(UUID componentId);

	Optional<ComponentQuestion> findByComponentIdAndQuestionIdAndDeletedAtIsNull(UUID componentId, UUID questionId);

	List<ComponentQuestion> findByQuestionIdAndDeletedAtIsNull(UUID questionId);

}
