package com.coachingcenter.api.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OneTimeCodeRepository extends JpaRepository<OneTimeCode, UUID> {

	Optional<OneTimeCode> findFirstByUserIdAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(UUID userId, String purpose);

	/** Every code of this purpose the account has not used yet. Issuing a new one uses all of them up. */
	List<OneTimeCode> findByUserIdAndPurposeAndUsedAtIsNull(UUID userId, String purpose);

	Optional<OneTimeCode> findFirstByUserIdAndPurposeAndCodeHashOrderByCreatedAtDesc(UUID userId, String purpose,
			String codeHash);

	Optional<OneTimeCode> findFirstByPurposeAndCodeHashOrderByCreatedAtDesc(String purpose, String codeHash);

}
