package com.coachingcenter.api.exams;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveExamRosterRepository extends JpaRepository<LiveExamRoster, LiveExamRoster.Key> {

	boolean existsByModuleIdAndStudentId(UUID moduleId, UUID studentId);

	void deleteByModuleIdAndStudentId(UUID moduleId, UUID studentId);

}
