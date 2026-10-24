package com.coachingcenter.api.auth;

import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class GoogleOAuthStateStore {

	private final JdbcTemplate jdbc;

	private final SecretHasher hasher;

	public GoogleOAuthStateStore(JdbcTemplate jdbc, SecretHasher hasher) {
		this.jdbc = jdbc;
		this.hasher = hasher;
	}

	public void save(String state, Instant expiresAt) {
		jdbc.update("DELETE FROM google_oauth_states WHERE expires_at < ?", java.sql.Timestamp.from(Instant.now()));
		jdbc.update("INSERT INTO google_oauth_states (state_hash, expires_at) VALUES (?, ?)", hasher.sha256(state),
				java.sql.Timestamp.from(expiresAt));
	}

	public boolean consume(String state) {
		int removed = jdbc.update("DELETE FROM google_oauth_states WHERE state_hash = ? AND expires_at >= ?",
				hasher.sha256(state), java.sql.Timestamp.from(Instant.now()));
		return removed == 1;
	}

}
