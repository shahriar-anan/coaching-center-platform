package com.coachingcenter.api.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class BootstrapIT extends PostgresIntegrationTest {

	@BeforeAll
	static void removeExistingMasterAdmins() throws Exception {
		String url = System.getenv("TEST_DB_URL");
		if (url == null || url.isBlank()) {
			return;
		}
		try (var connection = java.sql.DriverManager.getConnection(url, System.getenv("TEST_DB_USER"),
				System.getenv("TEST_DB_PASSWORD"))) {
			try (var statement = connection.createStatement()) {
				// Soft delete: other suites leave Master Admins that courses, profiles, and orders still reference.
				statement.executeUpdate("""
						UPDATE users SET deleted_at = now(), updated_at = now()
						WHERE role = 'MASTER_ADMIN' AND deleted_at IS NULL
						""");
			}
		}
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private MasterAdminBootstrap bootstrap;

	@Test
	void createsOneMasterAdminAndDoesNotOverwriteAnExistingPassword() throws Exception {
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		// The context may be cached from an earlier class, so its startup run happened before the cleanup.
		bootstrap.run(null);
		Integer count = jdbc.queryForObject(
				"select count(*) from users where role = 'MASTER_ADMIN' and deleted_at is null", Integer.class);
		assertThat(count).isEqualTo(1);
		String hash = jdbc.queryForObject(
				"select password_hash from users where role = 'MASTER_ADMIN' and deleted_at is null", String.class);
		assertThat(hash).isNotEqualTo("bootstrap-password-1");
		String name = jdbc.queryForObject("""
				select full_name from admin_profiles
				where user_id = (select id from users where role = 'MASTER_ADMIN' and deleted_at is null)
				""", String.class);
		assertThat(name).isEqualTo("Master Admin");
		mockMvc.perform(post("/api/v1/auth/login").header("X-Client-Type", "mobile")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"identifier":"+8801700000001","password":"bootstrap-password-1"}
					"""))
			.andExpect(status().isOk());

		jdbc.update("update users set password_hash = ? where role = 'MASTER_ADMIN' and deleted_at is null",
				"replaced-hash");
		try {
			bootstrap.run(null);
			String after = jdbc.queryForObject(
					"select password_hash from users where role = 'MASTER_ADMIN' and deleted_at is null", String.class);
			Integer stillOne = jdbc.queryForObject(
					"select count(*) from users where role = 'MASTER_ADMIN' and deleted_at is null", Integer.class);
			assertThat(after).isEqualTo("replaced-hash");
			assertThat(stillOne).isEqualTo(1);
		}
		finally {
			// Later classes sign in as this Master Admin with the bootstrap password.
			jdbc.update("update users set password_hash = ? where role = 'MASTER_ADMIN' and deleted_at is null", hash);
		}
	}

}
