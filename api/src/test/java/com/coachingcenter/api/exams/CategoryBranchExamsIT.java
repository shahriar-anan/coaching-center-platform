package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.AuthService;
import com.coachingcenter.api.auth.ClientType;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The category exam list with {@code branch} (NR-01, S0.1), the popular upcoming list (S0.2) and the expiry on an exam's access
 * (S0.3), read from the review seed. The seed is loaded fresh for each test.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CategoryBranchExamsIT extends PostgresIntegrationTest {

	/** 27 October 2026, 16:00 in Dhaka: the same run time as DemoContentSeedIT. */
	private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");

	private static final String CLEANUP = ".cursor/progress/ui-delivery/demo-cleanup.sql";

	private static final String PASSWORD = "demo-pass-1234";

	private static final String PHONE_STUDENT_A = "01711000011";

	private static final String PHONE_STUDENT_D = "01711000014";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper json;

	@Autowired
	private DemoContentSeed seed;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private AuthService auth;

	private JdbcTemplate jdbc;

	@BeforeEach
	void seedFresh() throws Exception {
		jdbc = new JdbcTemplate(dataSource);
		runCleanup();
		seed.seed(new String[] { "dev" }, Map.of("SEED_DEMO_PASSWORD", PASSWORD)::get, NOW);
	}

	@AfterEach
	void cleanUp() throws Exception {
		runCleanup();
	}

	@Test
	void theCategoryListHoldsItsOwnExamsByDefault() throws Exception {
		// The root has no package of its own: only the two standalone exams sit directly in it.
		assertThat(ids(categoryExams(DemoContentSeed.CATEGORY_ROOT, "")))
			.containsExactlyInAnyOrder(exam(61), exam(62));
		// A category with only subcategory exams lists nothing by default.
		assertThat(ids(categoryExams(DemoContentSeed.CATEGORY_BAR, ""))).isEmpty();
	}

	@Test
	void branchAddsTheExamsOfEverySubcategoryAtAnyDepth() throws Exception {
		Set<String> branch = ids(categoryExams(DemoContentSeed.CATEGORY_ROOT, "&branch=true"));
		assertThat(branch).isEqualTo(subtree(DemoContentSeed.CATEGORY_ROOT));
		// Exams from the MCQ, written and full-mock subcategories, the standalone ones, and the two review packages.
		assertThat(branch).contains(exam(1), exam(6), exam(10), exam(60), exam(61), exam(62), exam(63), exam(64));
		// The Bar Council branch is a separate root, so it stays out.
		assertThat(branch).doesNotContain(exam(30), exam(31));
		assertThat(ids(categoryExams(DemoContentSeed.CATEGORY_BAR, "&branch=true")))
			.containsExactlyInAnyOrder(exam(30), exam(31));
	}

	@Test
	void branchOnALeafCategoryAddsNothing() throws Exception {
		assertThat(ids(categoryExams(DemoContentSeed.CATEGORY_MCQ, "&branch=true")))
			.isEqualTo(ids(categoryExams(DemoContentSeed.CATEGORY_MCQ, "")));
	}

	@Test
	void theUpcomingListKeepsOnlyPopularExamsWhenAsked() throws Exception {
		JsonNode everything = upcoming("");
		JsonNode popular = upcoming("&popular=true");
		assertThat(ids(popular)).contains(exam(63)).doesNotContain(exam(60), exam(64));
		for (JsonNode row : popular) {
			assertThat(row.get("popular").asBoolean()).isTrue();
		}
		assertThat(popular.size()).isLessThan(everything.size());
		assertThat(ids(everything)).contains(exam(60), exam(63));
	}

	@Test
	void theExpiringGrantShowsWhenItEndsAndLifetimeAndFreeAccessShowNone() throws Exception {
		// The grant on the 30-day written paper ends 30 days after its window.
		Instant windowEnd = jdbc.queryForObject("SELECT ends_at FROM live_exams WHERE id = ?", Timestamp.class,
				DemoContentSeed.demo('e', 62)).toInstant();
		JsonNode expiring = examAs(PHONE_STUDENT_D, exam(62)).get("access");
		assertThat(expiring.get("state").asText()).isEqualTo("EXAM");
		assertThat(expiring.get("blockedReason").asText()).isEqualTo("ALREADY_HAS_ACCESS");
		assertThat(Instant.parse(expiring.get("expiresAt").asText())).isEqualTo(windowEnd.plus(Duration.ofDays(30)));

		// A free exam never ends.
		JsonNode free = examAs(PHONE_STUDENT_D, exam(61)).get("access");
		assertThat(free.get("state").asText()).isEqualTo("FREE");
		assertThat(free.get("expiresAt").isNull()).isTrue();

		// A paper in the full mock package has no access days, so the package access is lifetime.
		JsonNode lifetime = examAs(PHONE_STUDENT_A, exam(10)).get("access");
		assertThat(lifetime.get("state").asText()).isEqualTo("MODULE");
		assertThat(lifetime.get("expiresAt").isNull()).isTrue();
	}

	private JsonNode categoryExams(UUID category, String query) throws Exception {
		String body = mockMvc.perform(get("/api/v1/public/live-exam-categories/" + category + "/exams?size=100" + query))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return json.readTree(body).get("content");
	}

	private JsonNode upcoming(String query) throws Exception {
		String body = mockMvc.perform(get("/api/v1/public/live-exams?upcoming=true&size=100" + query))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return json.readTree(body).get("content");
	}

	private JsonNode examAs(String phone, String exam) throws Exception {
		String token = auth.login(phone, PASSWORD, ClientType.WEB, "127.0.0.1", "test").accessToken();
		String body = mockMvc.perform(get("/api/v1/public/live-exams/" + exam).header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return json.readTree(body);
	}

	private static Set<String> ids(JsonNode rows) {
		Set<String> ids = new HashSet<>();
		for (JsonNode row : rows) {
			ids.add(row.get("id").asText());
		}
		return ids;
	}

	/** Every published exam whose home category is {@code root} or a category below it, read with SQL rather than the API. */
	private Set<String> subtree(UUID root) {
		List<String> found = jdbc.queryForList("""
				WITH RECURSIVE tree AS (
				  SELECT id FROM live_exam_categories WHERE id = ?
				  UNION ALL
				  SELECT c.id FROM live_exam_categories c JOIN tree t ON c.parent_id = t.id
				)
				SELECT e.id::text FROM live_exams e
				WHERE e.category_id IN (SELECT id FROM tree) AND e.status = 'PUBLISHED' AND e.deleted_at IS NULL
				""", String.class, root);
		return new HashSet<>(found);
	}

	private static String exam(int number) {
		return DemoContentSeed.demo('e', number).toString();
	}

	private void runCleanup() throws Exception {
		String script = Files.readString(Path.of(CLEANUP));
		String sql = Arrays.stream(script.split("\n"))
			.filter(line -> !line.trim().startsWith("--"))
			.reduce("", (all, line) -> all + line + "\n");
		for (String statement : sql.split(";")) {
			if (!statement.isBlank()) {
				jdbc.execute(statement);
			}
		}
	}

}
