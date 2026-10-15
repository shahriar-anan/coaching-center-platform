package com.coachingcenter.api.exams;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamNodeRepository extends JpaRepository<ExamNode, UUID> {

	List<ExamNode> findByModuleIdAndParentIdAndDeletedAtIsNullOrderByPositionAsc(UUID moduleId, UUID parentId);

	List<ExamNode> findByModuleIdAndParentIdIsNullAndDeletedAtIsNullOrderByPositionAsc(UUID moduleId);

	List<ExamNode> findByModuleIdAndDeletedAtIsNullOrderByPositionAsc(UUID moduleId);

	Optional<ExamNode> findByIdAndModuleIdAndDeletedAtIsNull(UUID id, UUID moduleId);

	Optional<ExamNode> findByIdAndDeletedAtIsNull(UUID id);

}
