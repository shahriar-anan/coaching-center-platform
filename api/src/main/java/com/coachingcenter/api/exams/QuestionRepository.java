package com.coachingcenter.api.exams;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, UUID> {

	Optional<Question> findByIdAndDeletedAtIsNull(UUID id);

}
