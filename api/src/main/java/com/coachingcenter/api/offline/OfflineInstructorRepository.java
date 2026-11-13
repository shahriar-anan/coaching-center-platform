package com.coachingcenter.api.offline;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OfflineInstructorRepository extends JpaRepository<OfflineInstructor, UUID> {

	/** The instructors not archived, by name. */
	List<OfflineInstructor> findByArchivedAtIsNullOrderByNameAsc();

	/** Every instructor, archived ones too, by name. */
	List<OfflineInstructor> findAllByOrderByNameAsc();

	/** Whether a live (not archived) instructor already has this name, ignoring case. */
	boolean existsByNameIgnoreCaseAndArchivedAtIsNull(String name);

	/** The same check for a rename: another live instructor, not this one, with the name. */
	boolean existsByNameIgnoreCaseAndArchivedAtIsNullAndIdNot(String name, UUID id);

}
