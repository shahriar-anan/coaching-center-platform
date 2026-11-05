package com.coachingcenter.api.notices;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteNoticeRepository extends JpaRepository<SiteNotice, UUID> {

	Optional<SiteNotice> findByIdAndDeletedAtIsNull(UUID id);

	Page<SiteNotice> findByDeletedAtIsNullOrderByUpdatedAtDescIdDesc(Pageable pageable);

	Page<SiteNotice> findByStatusAndDeletedAtIsNullOrderByUpdatedAtDescIdDesc(String status, Pageable pageable);

	Page<SiteNotice> findByStatusAndDeletedAtIsNullOrderByPublishedAtDescIdDesc(String status, Pageable pageable);

}
