package com.coachingcenter.api.courses;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialRepository extends JpaRepository<Material, UUID> {

	Optional<Material> findByIdAndDeletedAtIsNull(UUID id);

	Optional<Material> findByIdAndCourseIdAndDeletedAtIsNull(UUID id, UUID courseId);

	List<Material> findByLectureNodeIdAndDeletedAtIsNull(UUID lectureNodeId);

	List<Material> findByCourseIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID courseId);

}
