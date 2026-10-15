package com.coachingcenter.api.courses;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseInstructorRepository extends JpaRepository<CourseInstructor, UUID> {

	List<CourseInstructor> findByCourseIdAndDeletedAtIsNullOrderByPositionAsc(UUID courseId);

	Optional<CourseInstructor> findByIdAndCourseIdAndDeletedAtIsNull(UUID id, UUID courseId);

	Optional<CourseInstructor> findByPhotoFileIdAndDeletedAtIsNull(UUID photoFileId);

}
