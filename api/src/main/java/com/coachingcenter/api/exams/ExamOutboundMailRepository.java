package com.coachingcenter.api.exams;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface ExamOutboundMailRepository extends JpaRepository<ExamOutboundMail, UUID> {

	boolean existsByKindAndMessageKey(String kind, String messageKey);

	@Query("select m.id from ExamOutboundMail m where m.status = 'PENDING' order by m.createdAt")
	List<UUID> findPendingIds();

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select m from ExamOutboundMail m where m.id = :id")
	Optional<ExamOutboundMail> lockById(@Param("id") UUID id);

}
