package com.coachingcenter.api.exams;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaperComponentRepository extends JpaRepository<PaperComponent, UUID> {

	Optional<PaperComponent> findByIdAndDeletedAtIsNull(UUID id);

	Optional<PaperComponent> findByPaperIdAndComponentTypeAndDeletedAtIsNull(UUID paperId, String componentType);

	List<PaperComponent> findByPaperIdAndDeletedAtIsNull(UUID paperId);

}
