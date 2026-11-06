package com.coachingcenter.api.notices;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface SiteNoticeRepository extends JpaRepository<SiteNotice, UUID> {

	Optional<SiteNotice> findByIdAndDeletedAtIsNull(UUID id);

	/** The notice row locked until the transaction ends, so photo and PDF changes to one notice run one at a time. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select n from SiteNotice n where n.id = :id and n.deletedAt is null")
	Optional<SiteNotice> lockActiveById(@Param("id") UUID id);

	Page<SiteNotice> findByDeletedAtIsNullOrderByUpdatedAtDescIdDesc(Pageable pageable);

	Page<SiteNotice> findByStatusAndDeletedAtIsNullOrderByUpdatedAtDescIdDesc(String status, Pageable pageable);

	Page<SiteNotice> findByStatusAndDeletedAtIsNullOrderByPublishedAtDescIdDesc(String status, Pageable pageable);

}
