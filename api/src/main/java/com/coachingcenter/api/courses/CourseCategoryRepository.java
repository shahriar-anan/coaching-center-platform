package com.coachingcenter.api.courses;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseCategoryRepository extends JpaRepository<CourseCategory, UUID> {

	Optional<CourseCategory> findByIdAndDeletedAtIsNull(UUID id);

	List<CourseCategory> findByDeletedAtIsNull();

	List<CourseCategory> findByParentIdAndDeletedAtIsNull(UUID parentId);

	List<CourseCategory> findByParentIdIsNullAndDeletedAtIsNull();

}
