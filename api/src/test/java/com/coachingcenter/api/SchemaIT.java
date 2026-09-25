package com.coachingcenter.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SchemaIT extends PostgresIntegrationTest {

	@Autowired
	private DataSource dataSource;

	@Test
	void identityTablesMatchThePhaseSchema() throws Exception {
		assertThat(columns("users")).containsExactlyInAnyOrder("id", "phone", "email", "email_verified_at",
				"password_hash", "role", "status", "last_login_at", "created_by", "created_at", "updated_at",
				"deleted_at");
		assertThat(columns("student_profiles")).containsExactlyInAnyOrder("user_id", "full_name", "student_code");
		assertThat(columns("admin_profiles")).containsExactlyInAnyOrder("user_id", "full_name");
		assertThat(columns("refresh_tokens")).containsExactlyInAnyOrder("id", "user_id", "token_hash", "family_id",
				"client_type", "expires_at", "revoked_at", "replaced_by", "created_at", "ip", "user_agent");
		assertThat(columns("one_time_codes")).containsExactlyInAnyOrder("id", "user_id", "purpose", "code_hash",
				"expires_at", "used_at", "failed_attempts", "created_by", "created_at");
		assertThat(columns("audit_log")).containsExactlyInAnyOrder("id", "actor_user_id", "action", "entity_type",
				"entity_id", "metadata", "ip", "created_at");
		assertThat(columnType("audit_log", "metadata")).isEqualTo("jsonb");
		assertThat(columnType("users", "created_at")).isEqualTo("timestamp with time zone");
	}

	@Test
	void activePhoneAndEmailAreUniqueAndReusableAfterSoftDelete() throws Exception {
		String phone = uniquePhone();
		String otherPhone = uniquePhone();
		String email = uniqueEmail();
		UUID first = insertUser(phone, email, null);
		assertThatThrownBy(() -> insertUser(phone, uniqueEmail(), null)).isInstanceOf(SQLException.class);
		assertThatThrownBy(() -> insertUser(otherPhone, email, null)).isInstanceOf(SQLException.class);

		markDeleted(first);
		UUID second = insertUser(phone, email, null);
		assertThat(second).isNotNull();
		deleteUser(second);
		deleteUser(first);
	}

	@Test
	void phoneMustUseTheBangladeshMobileForm() throws Exception {
		assertThatThrownBy(() -> insertUser("01700000001", "local@example.com", null)).isInstanceOf(SQLException.class);
		assertThatThrownBy(() -> insertUser("+880170000000", "short@example.com", null)).isInstanceOf(SQLException.class);
	}

	@Test
	void auditLogRejectsUpdateAndDelete() throws Exception {
		UUID id = UUID.randomUUID();
		try (Connection connection = dataSource.getConnection();
				PreparedStatement insert = connection.prepareStatement(
						"INSERT INTO audit_log (id, action, entity_type, created_at) VALUES (?, 'CREATE', 'USER', now())")) {
			insert.setObject(1, id);
			insert.executeUpdate();
		}

		assertThatThrownBy(() -> mutate("UPDATE audit_log SET action = 'EDIT' WHERE id = '" + id + "'"))
			.isInstanceOf(SQLException.class)
			.hasMessageContaining("append-only");
		assertThatThrownBy(() -> mutate("DELETE FROM audit_log WHERE id = '" + id + "'"))
			.isInstanceOf(SQLException.class)
			.hasMessageContaining("append-only");
	}

	private static String uniquePhone() {
		int suffix = java.util.concurrent.ThreadLocalRandom.current().nextInt(100000000, 999999999);
		return "+8801" + suffix;
	}

	private static String uniqueEmail() {
		return "schema-" + UUID.randomUUID() + "@example.com";
	}

	private Set<String> columns(String table) throws SQLException {
		Set<String> names = new HashSet<>();
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(
						"SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ?")) {
			statement.setString(1, table);
			try (ResultSet result = statement.executeQuery()) {
				while (result.next()) {
					names.add(result.getString(1));
				}
			}
		}
		return names;
	}

	private String columnType(String table, String column) throws SQLException {
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(
						"SELECT data_type FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ? AND column_name = ?")) {
			statement.setString(1, table);
			statement.setString(2, column);
			try (ResultSet result = statement.executeQuery()) {
				assertThat(result.next()).isTrue();
				return result.getString(1);
			}
		}
	}

	private UUID insertUser(String phone, String email, UUID deletedMarker) throws SQLException {
		UUID id = UUID.randomUUID();
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement("""
						INSERT INTO users (id, phone, email, role, status, created_at, updated_at, deleted_at)
						VALUES (?, ?, ?, 'STUDENT', 'ACTIVE', now(), now(), ?)
						""")) {
			statement.setObject(1, id);
			statement.setString(2, phone);
			statement.setString(3, email);
			statement.setObject(4, deletedMarker);
			statement.executeUpdate();
		}
		return id;
	}

	private void markDeleted(UUID id) throws SQLException {
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement("UPDATE users SET deleted_at = now() WHERE id = ?")) {
			statement.setObject(1, id);
			statement.executeUpdate();
		}
	}

	private void deleteUser(UUID id) throws SQLException {
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement("DELETE FROM users WHERE id = ?")) {
			statement.setObject(1, id);
			statement.executeUpdate();
		}
	}

	private void mutate(String sql) throws SQLException {
		try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
			statement.executeUpdate(sql);
		}
	}

}
