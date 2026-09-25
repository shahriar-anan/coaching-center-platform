package com.coachingcenter.api.auth;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenRevocation {

	private final RefreshTokenRepository refreshTokens;

	public RefreshTokenRevocation(RefreshTokenRepository refreshTokens) {
		this.refreshTokens = refreshTokens;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void revokeFamily(UUID familyId) {
		refreshTokens.revokeFamily(familyId, Instant.now());
	}

}
