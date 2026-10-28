package com.coachingcenter.api.exams;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveExamChangeLogRepository extends JpaRepository<LiveExamChangeLog, UUID> {

	List<LiveExamChangeLog> findByExamIdOrderByPublishedAtDesc(UUID examId);

	List<LiveExamChangeLog> findByModuleIdOrderByPublishedAtDesc(UUID moduleId);

}
