package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;
import com.coachingcenter.api.common.audit.AuditRecorder;

import tools.jackson.databind.ObjectMapper;

/**
 * One audit row for each catalog action of the staff (NR-01, contracts §5): a package's exam added and removed, a package
 * revision published and discarded, and popular set on an exam and on a package. Each row carries the action, the entity
 * and the actor.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, CatalogAuditIT.ClockConfig.class })
class CatalogAuditIT extends PostgresIntegrationTest {

	private static final Instant TEN_AM = Instant.parse("2026-10-20T04:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	@Autowired
	private MutableClock clock;

	private PackageFixtures fx;

	private JdbcTemplate jdbc;

	@BeforeEach
	void setUp() {
		clock.set(TEN_AM);
		fx = new PackageFixtures(mockMvc, json, dataSource, passwords);
		jdbc = new JdbcTemplate(dataSource);
	}

	@Test
	void eachCatalogActionWritesOneAuditRowWithItsActorAndEntity() throws Exception {
		UUID systemId = fx.seed("SYSTEM_ADMIN");
		UUID masterId = fx.seed("MASTER_ADMIN");
		String system = fx.token(systemId);
		String master = fx.token(masterId);
		UUID shelf = fx.createCategory(system, "Audit shelf " + UUID.randomUUID());
		UUID pkg = fx.createPackage(system, shelf, "Audit package");
		UUID member = fx.createExam(system, pkg, "Member paper", null);
		UUID source = fx.createPackage(system, shelf, "Audit source");
		UUID moving = fx.createExam(system, source, "Moving paper", null);
		fx.finalSaveAndPublish(master, pkg);

		// A published package stages a change: adding an exam, and removing the one it holds.
		mockMvc.perform(post("/api/v1/live-exam-modules/" + pkg + "/exams/" + moving).header("Authorization", "Bearer " + system))
			.andExpect(status().isOk());
		assertThat(audits("LIVE_EXAM_PACKAGE_EXAM_ADD", AuditRecorder.LIVE_EXAM_MODULE_ENTITY, pkg, systemId)).isOne();
		mockMvc.perform(delete("/api/v1/live-exam-modules/" + pkg + "/exams/" + member).header("Authorization", "Bearer " + system))
			.andExpect(status().isNoContent());
		assertThat(audits("LIVE_EXAM_PACKAGE_EXAM_REMOVE", AuditRecorder.LIVE_EXAM_MODULE_ENTITY, pkg, systemId)).isOne();

		// Publishing the staged change writes the revision publish row.
		mockMvc.perform(post("/api/v1/live-exam-modules/" + pkg + "/publish").header("Authorization", "Bearer " + master))
			.andExpect(status().isOk());
		assertThat(audits("LIVE_EXAM_MODULE_REVISION_PUBLISH", AuditRecorder.LIVE_EXAM_MODULE_ENTITY, pkg, masterId)).isOne();

		// Discarding a staged title edit writes the discard row.
		mockMvc.perform(patch("/api/v1/live-exam-modules/" + pkg).header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"titleEn\":\"Edited then dropped\"}"))
			.andExpect(status().isOk());
		mockMvc.perform(delete("/api/v1/live-exam-modules/" + pkg + "/pending-changes").header("Authorization", "Bearer " + system))
			.andExpect(status().isNoContent());
		assertThat(audits("LIVE_EXAM_MODULE_REVISION_DISCARD", AuditRecorder.LIVE_EXAM_MODULE_ENTITY, pkg, systemId)).isOne();

		// Popular is set on the package and on an exam.
		mockMvc.perform(put("/api/v1/live-exam-modules/" + pkg + "/popular").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"popular\":true}"))
			.andExpect(status().isOk());
		assertThat(audits("LIVE_EXAM_POPULAR_SET", AuditRecorder.LIVE_EXAM_MODULE_ENTITY, pkg, masterId)).isOne();
		mockMvc.perform(put("/api/v1/live-exams/" + moving + "/popular").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"popular\":true}"))
			.andExpect(status().isOk());
		assertThat(audits("LIVE_EXAM_POPULAR_SET", AuditRecorder.LIVE_EXAM_ENTITY, moving, masterId)).isOne();
	}

	private int audits(String action, String entityType, UUID entityId, UUID actorId) {
		return jdbc.queryForObject("select count(*) from audit_log where action = ? and entity_type = ? and entity_id = ?"
				+ " and actor_user_id = ?", Integer.class, action, entityType, entityId, actorId);
	}

	@TestConfiguration
	static class ClockConfig {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(TEN_AM);
		}

	}

}
