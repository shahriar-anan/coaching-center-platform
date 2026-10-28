package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** The staff exam list across packages: each filter, the packages each exam is in, and an instructor's own exams (NR-01, B3). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, LiveExamIT.ClockConfig.class })
class StaffExamListIT extends PostgresIntegrationTest {

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
	void eachFilterNarrowsTheListAndAnExamInTwoPackagesCarriesBoth() throws Exception {
		String system = fx.token(fx.seed("SYSTEM_ADMIN"));
		UUID master = fx.seed("MASTER_ADMIN");
		String masterToken = fx.token(master);
		UUID shelf = fx.createCategory(system, "Filter shelf");
		UUID otherShelf = fx.createCategory(system, "Filter other shelf");
		UUID first = fx.createPackage(system, shelf, "Filter first");
		UUID second = fx.createPackage(system, otherShelf, "Filter second");

		// Questions can be added only before the window opens, so the setup runs at 03:30 UTC, then the clock returns to TEN_AM.
		clock.set(TEN_AM.minusSeconds(1800));
		UUID live = fx.createExam(system, first, "Live paper", "2026-10-20T10:00:00+06:00");
		UUID shared = fx.createExam(system, first, "Shared paper", null);
		UUID written = fx.createExam(system, first, "WRITTEN", "Written paper", null);
		UUID practice = fx.createExam(system, first, "Practice paper", null);
		fx.sql("update live_exams set mode = 'PRACTICE', live_at = null, ends_at = null where id = ?", practice);
		UUID moving = fx.createExam(system, second, "Moving paper", "2026-10-21T10:00:00+06:00");
		mockMvc.perform(post("/api/v1/live-exam-modules/" + second + "/exams/" + shared).header("Authorization", "Bearer " + system))
			.andExpect(status().isOk());
		mockMvc.perform(delete("/api/v1/live-exam-modules/" + second + "/exams/" + moving).header("Authorization", "Bearer " + system))
			.andExpect(status().isNoContent());
		fx.addQuestion(system, live);
		fx.publishExam(system, live);
		// The moving exam stays a draft: publishing a standalone exam is A's publish path, and no filter below needs it.
		clock.set(TEN_AM);
		mockMvc.perform(put("/api/v1/live-exams/" + live + "/popular").header("Authorization", "Bearer " + masterToken)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"popular\":true}"))
			.andExpect(status().isOk());
		fx.sql("insert into live_exam_revisions (id, exam_id, payload, created_by, created_at, updated_by, updated_at) "
				+ "values (gen_random_uuid(), ?, '{}'::jsonb, ?, now(), ?, now())", shared, master, master);

		String base = "/api/v1/live-exams?categoryId=" + shelf;
		assertThat(ids(system, base)).containsExactlyInAnyOrder(live, shared, written, practice);
		assertThat(ids(system, base + "&packageId=" + second)).containsExactly(shared);
		assertThat(ids(system, base + "&type=WRITTEN")).containsExactly(written);
		assertThat(ids(system, base + "&mode=PRACTICE")).containsExactly(practice);
		assertThat(ids(system, base + "&status=PUBLISHED")).containsExactly(live);
		assertThat(ids(system, base + "&status=DRAFT")).containsExactlyInAnyOrder(shared, written, practice);
		assertThat(ids(system, base + "&phase=LIVE")).containsExactly(live);
		assertThat(ids(system, base + "&phase=UNANNOUNCED")).containsExactlyInAnyOrder(shared, written);
		assertThat(ids(system, base + "&phase=PRACTICE")).containsExactly(practice);
		assertThat(ids(system, base + "&phase=ENDED")).isEmpty();
		assertThat(ids(system, base + "&q=practice")).containsExactly(practice);
		assertThat(ids(system, base + "&pending=true")).containsExactly(shared);
		assertThat(ids(system, base + "&popular=true")).containsExactly(live);
		assertThat(ids(system, "/api/v1/live-exams?categoryId=" + otherShelf + "&standalone=true")).containsExactly(moving);
		assertThat(ids(system, "/api/v1/live-exams?categoryId=" + otherShelf + "&phase=UPCOMING")).containsExactly(moving);

		JsonNode shownShared = rowOf(system, base + "&packageId=" + second, shared);
		assertThat(shownShared.get("packages")).hasSize(2);
		assertThat(shownShared.get("pendingChanges").asBoolean()).isTrue();
		assertThat(shownShared.get("questionCount").asInt()).isZero();
		JsonNode shownLive = rowOf(system, base, live);
		assertThat(shownLive.get("questionCount").asInt()).isEqualTo(1);
		assertThat(shownLive.get("popular").asBoolean()).isTrue();
		assertThat(shownLive.get("phase").asText()).isEqualTo("LIVE");
		assertThat(shownLive.get("pricing").hasNonNull("effectivePrice")).isFalse();

		mockMvc.perform(get(base + "&status=ARCHIVED").header("Authorization", "Bearer " + system)).andExpect(status().isOk());
		mockMvc.perform(get(base + "&phase=SOMETIMES").header("Authorization", "Bearer " + system))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void anInstructorSeesOnlyTheExamsAssignedToThemAndAStudentIsRefused() throws Exception {
		String system = fx.token(fx.seed("SYSTEM_ADMIN"));
		UUID instructor = fx.seed("INSTRUCTOR");
		UUID student = fx.seed("STUDENT");
		UUID shelf = fx.createCategory(system, "Instructor shelf");
		UUID pkg = fx.createPackage(system, shelf, "Instructor package");
		UUID mine = fx.createExam(system, pkg, "Mine", null);
		UUID theirs = fx.createExam(system, pkg, "Theirs", null);
		fx.sql("update live_exams set instructor_id = ? where id = ?", instructor, mine);

		String teacher = fx.token(instructor);
		String base = "/api/v1/live-exams?categoryId=" + shelf;
		assertThat(ids(teacher, base)).containsExactly(mine);
		assertThat(ids(system, base)).containsExactlyInAnyOrder(mine, theirs);
		assertThat(rowOf(teacher, base, mine).get("instructor").get("id").asText()).isEqualTo(instructor.toString());
		mockMvc.perform(get(base).header("Authorization", "Bearer " + fx.token(student)))
			.andExpect(status().isForbidden());
	}

	private List<UUID> ids(String token, String path) throws Exception {
		return PackageFixtures.ids(fx.content(token, path));
	}

	private JsonNode rowOf(String token, String path, UUID id) throws Exception {
		for (JsonNode row : fx.content(token, path)) {
			if (id.toString().equals(row.get("id").asText())) {
				return row;
			}
		}
		throw new AssertionError("No row " + id + " in " + path);
	}

}
