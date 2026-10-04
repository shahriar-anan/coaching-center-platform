package com.coachingcenter.api.courses;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentNodeRepository extends JpaRepository<ContentNode, UUID> {

	List<ContentNode> findByCourseIdAndParentIdAndDeletedAtIsNullOrderByPositionAsc(UUID courseId, UUID parentId);

	List<ContentNode> findByCourseIdAndParentIdIsNullAndDeletedAtIsNullOrderByPositionAsc(UUID courseId);

	List<ContentNode> findByCourseIdAndDeletedAtIsNullOrderByPositionAsc(UUID courseId);

	Optional<ContentNode> findByIdAndCourseIdAndDeletedAtIsNull(UUID id, UUID courseId);

}
