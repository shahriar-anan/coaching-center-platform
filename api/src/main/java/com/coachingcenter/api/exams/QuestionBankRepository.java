package com.coachingcenter.api.exams;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionBankRepository extends JpaRepository<QuestionBank, UUID> {

	Optional<QuestionBank> findByIdAndDeletedAtIsNull(UUID id);

}
