package com.coachingcenter.api.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LectureProgressRepository extends JpaRepository<LectureProgress, UUID> {

	Optional<LectureProgress> findByStudentIdAndLectureNodeIdAndDeletedAtIsNull(UUID studentId, UUID lectureNodeId);

	List<LectureProgress> findByStudentIdAndCourseIdAndDeletedAtIsNullOrderByLastOpenedAtDesc(UUID studentId,
			UUID courseId);

}
