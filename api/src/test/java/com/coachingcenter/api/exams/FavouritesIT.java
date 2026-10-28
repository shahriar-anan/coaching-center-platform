package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** A student marks exams and packages without buying; marks stay private and skip what is hidden (NR-01, R7 and B6). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, LiveExamIT.ClockConfig.class })
class FavouritesIT extends PostgresIntegrationTest {

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

	@BeforeEach
	void setUp() {
		clock.set(TEN_AM);
		fx = new PackageFixtures(mockMvc, json, dataSource, passwords);
	}

	@Test
	void aStudentMarksAndUnmarksAnExamAndAPackageIdempotently() throws Exception {
		String system = fx.token(fx.seed("SYSTEM_ADMIN"));
		UUID student = fx.seed("STUDENT");
		String token = fx.token(student);
		UUID shelf = fx.createCategory(system, "Favourite shelf");
		UUID pkg = fx.createPackage(system, shelf, "Favourite package");
		UUID exam = fx.createExam(system, pkg, "Favourite paper", null);
		fx.addQuestion(system, exam);
		fx.publishExam(system, exam);
		fx.finalSaveAndPublish(system, pkg);

		String examPath = "/api/v1/me/favourites/exams/" + exam;
		mockMvc.perform(put(examPath).header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
		mockMvc.perform(put(examPath).header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
		mockMvc.perform(put("/api/v1/me/favourites/packages/" + pkg).header("Authorization", "Bearer " + token))
			.andExpect(status().isNoContent());

		JsonNode favourites = fx.body(token, "/api/v1/me/favourites");
		assertThat(ids(favourites.get("exams"))).containsExactly(exam);
		assertThat(ids(favourites.get("packages"))).containsExactly(pkg);
		assertThat(favourites.get("exams").get(0).get("favourite").asBoolean()).isTrue();
		assertThat(favourites.get("exams").get(0).get("access").get("state").asText()).isEqualTo("NONE");

		JsonNode publicRow = fx.body(token, "/api/v1/public/live-exams/" + exam);
		assertThat(publicRow.get("favourite").asBoolean()).isTrue();
		assertThat(fx.body(fx.token(fx.seed("STUDENT")), "/api/v1/public/live-exams/" + exam).get("favourite").asBoolean())
			.isFalse();

		mockMvc.perform(delete(examPath).header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
		mockMvc.perform(delete(examPath).header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
		assertThat(ids(fx.body(token, "/api/v1/me/favourites").get("exams"))).isEmpty();
		assertThat(ids(fx.body(token, "/api/v1/me/favourites").get("packages"))).containsExactly(pkg);
	}

	@Test
	void anotherStudentsMarksNeverShowAndAHiddenItemIsSkippedNotDeleted() throws Exception {
		String system = fx.token(fx.seed("SYSTEM_ADMIN"));
		String master = fx.token(fx.seed("MASTER_ADMIN"));
		String owner = fx.token(fx.seed("STUDENT"));
		String other = fx.token(fx.seed("STUDENT"));
		UUID shelf = fx.createCategory(system, "Hidden favourite shelf");
		UUID pkg = fx.createPackage(system, shelf, "Hidden favourite package");
		UUID exam = fx.createExam(system, pkg, "Hidden favourite paper", null);
		fx.addQuestion(system, exam);
		fx.publishExam(system, exam);
		fx.finalSaveAndPublish(system, pkg);
		mockMvc.perform(put("/api/v1/me/favourites/exams/" + exam).header("Authorization", "Bearer " + owner))
			.andExpect(status().isNoContent());

		assertThat(ids(fx.body(other, "/api/v1/me/favourites").get("exams"))).isEmpty();
		assertThat(ids(fx.body(owner, "/api/v1/me/favourites").get("exams"))).containsExactly(exam);

		mockMvc.perform(post("/api/v1/live-exam-categories/" + shelf + "/archive").header("Authorization", "Bearer " + master))
			.andExpect(status().isNoContent());
		assertThat(ids(fx.body(owner, "/api/v1/me/favourites").get("exams"))).isEmpty();
		Integer kept = new JdbcTemplate(dataSource).queryForObject(
				"select count(*) from student_favourites where exam_id = ?", Integer.class, exam);
		assertThat(kept).isEqualTo(1);
	}

	@Test
	void aDraftCannotBeMarkedAndStaffAreRefused() throws Exception {
		String system = fx.token(fx.seed("SYSTEM_ADMIN"));
		String student = fx.token(fx.seed("STUDENT"));
		UUID shelf = fx.createCategory(system, "Refused favourite shelf");
		UUID pkg = fx.createPackage(system, shelf, "Refused favourite package");
		UUID draft = fx.createExam(system, pkg, "Draft paper", null);

		mockMvc.perform(put("/api/v1/me/favourites/exams/" + draft).header("Authorization", "Bearer " + student))
			.andExpect(status().isNotFound());
		mockMvc.perform(put("/api/v1/me/favourites/packages/" + pkg).header("Authorization", "Bearer " + student))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/me/favourites").header("Authorization", "Bearer " + system)).andExpect(status().isForbidden());
		mockMvc.perform(put("/api/v1/me/favourites/exams/" + draft).header("Authorization", "Bearer " + system))
			.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/live-exam-modules/" + pkg + "/exams/" + draft).header("Authorization", "Bearer " + student))
			.andExpect(status().isForbidden());
	}

	private static List<UUID> ids(JsonNode nodes) {
		return PackageFixtures.ids(nodes);
	}

}
