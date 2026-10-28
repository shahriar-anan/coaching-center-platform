package com.coachingcenter.api.exams;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentFavouriteRepository extends JpaRepository<StudentFavourite, UUID> {

	Optional<StudentFavourite> findByStudentIdAndExamId(UUID studentId, UUID examId);

	Optional<StudentFavourite> findByStudentIdAndModuleId(UUID studentId, UUID moduleId);

	List<StudentFavourite> findByStudentId(UUID studentId);

}
