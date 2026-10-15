package com.coachingcenter.api.exams;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionBankItemRepository extends JpaRepository<QuestionBankItem, UUID> {

	List<QuestionBankItem> findByBankIdAndDeletedAtIsNullOrderByPositionAsc(UUID bankId);

	Optional<QuestionBankItem> findByBankIdAndQuestionIdAndDeletedAtIsNull(UUID bankId, UUID questionId);

}
