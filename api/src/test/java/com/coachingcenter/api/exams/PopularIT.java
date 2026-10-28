package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.ObjectMapper;

/** The Master Admin's popular flag on exams and packages: who sets it, the filters, and drafts that stay hidden (NR-01, B5). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, LiveExamIT.ClockConfig.class })
class PopularIT extends PostgresIntegrationTest {

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
	void onlyTheMasterAdminSetsPopular() throws Exception {
		String system = fx.token(fx.seed("SYSTEM_ADMIN"));
		String master = fx.token(fx.seed("MASTER_ADMIN"));
		UUID shelf = fx.createCategory(system, "Popular denied shelf");
		UUID pkg = fx.createPackage(system, shelf, "Denied package");
		UUID exam = fx.createExam(system, pkg, "Denied paper", null);

		mockMvc.perform(put("/api/v1/live-exam-modules/" + pkg + "/popular").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"popular\":true}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
		mockMvc.perform(put("/api/v1/live-exams/" + exam + "/popular").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"popular\":true}"))
			.andExpect(status().isForbidden());

		mockMvc.perform(put("/api/v1/live-exam-modules/" + pkg + "/popular").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"popular\":true}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.popular").value(true));
		mockMvc.perform(put("/api/v1/live-exams/" + exam + "/popular").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"popular\":true}"))
			.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/live-exam-modules/" + pkg).header("Authorization", "Bearer " + system))
			.andExpect(jsonPath("$.popular").value(true));
		mockMvc.perform(put("/api/v1/live-exam-modules/" + pkg + "/popular").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"popular\":\"yes\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void thePopularFilterKeepsOnlyPopularPackagesAndExams() throws Exception {
		String system = fx.token(fx.seed("SYSTEM_ADMIN"));
		String master = fx.token(fx.seed("MASTER_ADMIN"));
		UUID shelf = fx.createCategory(system, "Popular shelf");
		UUID hot = fx.createPackage(system, shelf, "Hot package");
		UUID cold = fx.createPackage(system, shelf, "Cold package");
		UUID hotExam = fx.createExam(system, hot, "Hot paper", null);
		UUID coldExam = fx.createExam(system, hot, "Cold paper", null);
		for (UUID exam : java.util.List.of(hotExam, coldExam)) {
			fx.addQuestion(system, exam);
			fx.publishExam(system, exam);
		}
		fx.finalSaveAndPublish(system, hot);
		fx.finalSaveAndPublish(system, cold);
		setPopular(master, "/api/v1/live-exam-modules/" + hot + "/popular", true);
		setPopular(master, "/api/v1/live-exams/" + hotExam + "/popular", true);

		assertThat(ids("/api/v1/public/live-exam-modules?categoryId=" + shelf)).containsExactlyInAnyOrder(hot, cold);
		assertThat(ids("/api/v1/public/live-exam-modules?categoryId=" + shelf + "&popular=true")).containsExactly(hot);
		assertThat(ids("/api/v1/public/live-exam-modules/" + hot + "/exams?popular=true")).containsExactly(hotExam);
		assertThat(ids("/api/v1/public/live-exam-modules/" + hot + "/exams")).containsExactlyInAnyOrder(hotExam, coldExam);
		assertThat(ids("/api/v1/public/live-exam-categories/" + shelf + "/exams?popular=true")).containsExactly(hotExam);
	}

	@Test
	void aPopularDraftStaysHiddenFromThePublic() throws Exception {
		String system = fx.token(fx.seed("SYSTEM_ADMIN"));
		String master = fx.token(fx.seed("MASTER_ADMIN"));
		UUID shelf = fx.createCategory(system, "Draft popular shelf");
		UUID draft = fx.createPackage(system, shelf, "Draft popular");
		setPopular(master, "/api/v1/live-exam-modules/" + draft + "/popular", true);

		assertThat(ids("/api/v1/public/live-exam-modules?categoryId=" + shelf + "&popular=true")).isEmpty();
		mockMvc.perform(get("/api/v1/public/live-exam-modules/" + draft)).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/live-exam-modules?categoryId=" + shelf).header("Authorization", "Bearer " + system))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].popular").value(true));
	}

	private void setPopular(String master, String path, boolean popular) throws Exception {
		mockMvc.perform(put(path).header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"popular\":%s}".formatted(popular)))
			.andExpect(status().isOk());
	}

	private java.util.List<UUID> ids(String path) throws Exception {
		return PackageFixtures.ids(fx.content(fx.token(fx.seed("STUDENT")), path));
	}

}
