package com.coachingcenter.api.exams;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveExamInstructorRepository extends JpaRepository<LiveExamInstructor, UUID> {
}
