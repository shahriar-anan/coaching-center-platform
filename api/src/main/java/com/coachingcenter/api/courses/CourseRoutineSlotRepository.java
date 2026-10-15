package com.coachingcenter.api.courses;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRoutineSlotRepository extends JpaRepository<CourseRoutineSlot, UUID> {

	List<CourseRoutineSlot> findByCourseIdAndDeletedAtIsNullOrderByPositionAsc(UUID courseId);

	boolean existsByCourseIdAndDeletedAtIsNull(UUID courseId);

}
