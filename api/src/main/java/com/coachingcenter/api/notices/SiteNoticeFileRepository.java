package com.coachingcenter.api.notices;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteNoticeFileRepository extends JpaRepository<SiteNoticeFile, UUID> {

	/** Every file of one notice, photos and the PDF together, in position order. */
	List<SiteNoticeFile> findByNoticeIdOrderByPositionAsc(UUID noticeId);

	/** The files of several notices at once, in position order, so a page of notices costs one query. */
	List<SiteNoticeFile> findByNoticeIdInOrderByPositionAsc(Collection<UUID> noticeIds);

	Optional<SiteNoticeFile> findByIdAndNoticeId(UUID id, UUID noticeId);

}
