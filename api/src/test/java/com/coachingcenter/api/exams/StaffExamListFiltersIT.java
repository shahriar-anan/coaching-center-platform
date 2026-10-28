package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.coachingcenter.api.MutableClock;
import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The staff exam list (NR-01, B3): the Bangla title search, paging, and sorting by liveAt, title and updatedAt. The filters
 * one at a time are in {@link StaffExamListIT}, which this class does not repeat.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, StaffExamListFiltersIT.ClockConfig.class })
class StaffExamListFiltersIT extends PostgresIntegrationTest {

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
	void theTitleSearchFindsABanglaTitleAndAnEnglishOne() throws Exception {
		Fixture set = createSet();
		String base = "/api/v1/live-exams?packageId=" + set.pkg();
		// Raw text: MockMvc encodes the path itself, so a pre-encoded value would be encoded twice.
		assertThat(PackageFixtures.ids(fx.content(set.system(), base + "&q=গণিত")))
			.containsExactlyInAnyOrder(set.delta(), set.echo());
		assertThat(PackageFixtures.ids(fx.content(set.system(), base + "&q=echo"))).containsExactly(set.echo());
		assertThat(PackageFixtures.ids(fx.content(set.system(), base + "&q=PAPER"))).as("case-insensitive").hasSize(5);
	}

	@Test
	void pagingReturnsTheTotalsAndEachPageOnceInOrder() throws Exception {
		Fixture set = createSet();
		String base = "/api/v1/live-exams?packageId=" + set.pkg() + "&sort=title,asc&size=2";

		JsonNode first = fx.body(set.system(), base + "&page=0");
		assertThat(first.get("page").asInt()).isZero();
		assertThat(first.get("size").asInt()).isEqualTo(2);
		assertThat(first.get("totalElements").asLong()).isEqualTo(5);
		assertThat(first.get("totalPages").asInt()).isEqualTo(3);
		assertThat(PackageFixtures.ids(first.get("content"))).containsExactly(set.alpha(), set.bravo());

		assertThat(PackageFixtures.ids(fx.body(set.system(), base + "&page=1").get("content")))
			.containsExactly(set.charlie(), set.delta());
		assertThat(PackageFixtures.ids(fx.body(set.system(), base + "&page=2").get("content"))).containsExactly(set.echo());
		assertThat(PackageFixtures.ids(fx.body(set.system(), base + "&page=3").get("content"))).isEmpty();
	}

	@Test
	void sortsByLiveAtWithUndatedLast() throws Exception {
		Fixture set = createSet();
		String base = "/api/v1/live-exams?packageId=" + set.pkg();

		List<UUID> asc = PackageFixtures.ids(fx.content(set.system(), base + "&sort=liveAt,asc"));
		PackageFixtures.assertOrder(asc, set.alpha(), set.charlie(), set.delta(), set.echo(), set.bravo());

		// Descending keeps the dated exams in reverse date order; where the undated one lands is not part of this check.
		List<UUID> desc = new ArrayList<>(PackageFixtures.ids(fx.content(set.system(), base + "&sort=liveAt,desc")));
		desc.remove(set.bravo());
		PackageFixtures.assertOrder(desc, set.echo(), set.delta(), set.charlie(), set.alpha());
	}

	@Test
	void sortsByTitleInBothDirections() throws Exception {
		Fixture set = createSet();
		String base = "/api/v1/live-exams?packageId=" + set.pkg();

		PackageFixtures.assertOrder(PackageFixtures.ids(fx.content(set.system(), base + "&sort=title,asc")),
				set.alpha(), set.bravo(), set.charlie(), set.delta(), set.echo());
		PackageFixtures.assertOrder(PackageFixtures.ids(fx.content(set.system(), base + "&sort=title,desc")),
				set.echo(), set.delta(), set.charlie(), set.bravo(), set.alpha());
	}

	@Test
	void sortsByUpdatedAtAndNewestFirstIsTheDefault() throws Exception {
		Fixture set = createSet();
		String base = "/api/v1/live-exams?packageId=" + set.pkg();
		fx.sql("update live_exams set updated_at = ? where id = ?", java.sql.Timestamp.from(Instant.parse("2026-10-01T00:00:00Z")), set.alpha());
		fx.sql("update live_exams set updated_at = ? where id = ?", java.sql.Timestamp.from(Instant.parse("2026-10-02T00:00:00Z")), set.bravo());
		fx.sql("update live_exams set updated_at = ? where id = ?", java.sql.Timestamp.from(Instant.parse("2026-10-03T00:00:00Z")), set.charlie());
		fx.sql("update live_exams set updated_at = ? where id = ?", java.sql.Timestamp.from(Instant.parse("2026-10-04T00:00:00Z")), set.delta());
		fx.sql("update live_exams set updated_at = ? where id = ?", java.sql.Timestamp.from(Instant.parse("2026-10-05T00:00:00Z")), set.echo());

		List<UUID> newestFirst = List.of(set.echo(), set.delta(), set.charlie(), set.bravo(), set.alpha());
		PackageFixtures.assertOrder(PackageFixtures.ids(fx.content(set.system(), base + "&sort=updatedAt,desc")),
				newestFirst.toArray(UUID[]::new));
		PackageFixtures.assertOrder(PackageFixtures.ids(fx.content(set.system(), base)), newestFirst.toArray(UUID[]::new));
		PackageFixtures.assertOrder(PackageFixtures.ids(fx.content(set.system(), base + "&sort=updatedAt,asc")),
				set.alpha(), set.bravo(), set.charlie(), set.delta(), set.echo());
	}

	@Test
	void anUnknownSortKeyIsRefused() throws Exception {
		Fixture set = createSet();
		mockMvc.perform(get("/api/v1/live-exams?packageId=" + set.pkg() + "&sort=mode,asc")
			.header("Authorization", "Bearer " + set.system()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	/** One package with five drafts: Alpha, Bravo (undated), Charlie, Delta and Echo, two of them with Bangla titles. */
	private Fixture createSet() throws Exception {
		String system = fx.token(fx.seed("SYSTEM_ADMIN"));
		UUID shelf = fx.createCategory(system, "List shelf " + UUID.randomUUID());
		UUID pkg = fx.createPackage(system, shelf, "List package " + UUID.randomUUID());
		UUID alpha = fx.createExam(system, pkg, "Alpha paper", "2026-10-21T10:00:00+06:00");
		UUID bravo = fx.createExam(system, pkg, "Bravo paper", null);
		UUID charlie = fx.createExam(system, pkg, "Charlie paper", "2026-10-22T10:00:00+06:00");
		UUID delta = fx.createExam(system, pkg, "Delta paper", "2026-10-23T10:00:00+06:00");
		UUID echo = fx.createExam(system, pkg, "Echo paper", "2026-10-24T10:00:00+06:00");
		fx.sql("update live_exams set title_bn = ? where id = ?", "গণিত পরীক্ষা", delta);
		fx.sql("update live_exams set title_bn = ? where id = ?", "গণিত দ্বিতীয় পত্র", echo);
		return new Fixture(system, pkg, alpha, bravo, charlie, delta, echo);
	}

	private record Fixture(String system, UUID pkg, UUID alpha, UUID bravo, UUID charlie, UUID delta, UUID echo) {
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
