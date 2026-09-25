package com.coachingcenter.api.common.audit;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

public interface AuditLogRepository extends Repository<AuditLog, UUID> {

	Page<AuditLog> findAll(Pageable pageable);

}
