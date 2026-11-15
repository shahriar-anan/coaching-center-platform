package com.coachingcenter.api.offline;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OfflineResultRowRepository extends JpaRepository<OfflineResultRow, UUID> {

	/** The rows of one sheet. */
	List<OfflineResultRow> findBySheetId(UUID sheetId);

}
