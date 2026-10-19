package com.coachingcenter.api.exams;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveExamCategoryRepository extends JpaRepository<LiveExamCategory, UUID> {

	Optional<LiveExamCategory> findByIdAndDeletedAtIsNull(UUID id);

	List<LiveExamCategory> findByDeletedAtIsNull();

	List<LiveExamCategory> findByParentIdAndDeletedAtIsNull(UUID parentId);

	List<LiveExamCategory> findByParentIdIsNullAndDeletedAtIsNull();

}
