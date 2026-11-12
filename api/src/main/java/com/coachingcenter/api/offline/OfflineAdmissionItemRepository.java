package com.coachingcenter.api.offline;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OfflineAdmissionItemRepository extends JpaRepository<OfflineAdmissionItem, UUID> {

	List<OfflineAdmissionItem> findByAdmissionIdAndRemovedAtIsNull(UUID admissionId);

}
