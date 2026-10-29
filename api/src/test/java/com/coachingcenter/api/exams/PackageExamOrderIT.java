package com.coachingcenter.api.exams;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

/**
 * The staff list of a package's exams follows the package order that the reorder controls write (NR-01 UI Step 1 fix).
 * The exams here are dated so that the date order differs from the membership order: the staff reads must not sort by date.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, LiveExamIT.ClockConfig.class })
class PackageExamOrderIT extends PostgresIntegrationTest {

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
	void aSwappedOrderIsReadBackFromThePackageListAndFromTheStaffList() throws Exception {
		String system = fx.token(fx.seed("SYSTEM_ADMIN"));
		UUID category = fx.createCategory(system, "Order read shelf");
		UUID pkg = fx.createPackage(system, category, "Read order package");
		// Added in the order A, B, C. By date the order is B (10:30), C (11:30), A (13:00).
		UUID a = fx.createExam(system, pkg, "Alpha", "2026-10-20T13:00:00+06:00");
		UUID b = fx.createExam(system, pkg, "Bravo", "2026-10-20T10:30:00+06:00");
		UUID c = fx.createExam(system, pkg, "Charlie", "2026-10-20T11:30:00+06:00");
		String moduleList = "/api/v1/live-exam-modules/" + pkg + "/exams";
		String staffList = "/api/v1/live-exams?packageId=" + pkg;

		PackageFixtures.assertOrder(PackageFixtures.ids(fx.content(system, moduleList)), a, b, c);
		PackageFixtures.assertOrder(PackageFixtures.ids(fx.content(system, staffList)), a, b, c);

		// Swap B and C: the new order is A, C, B. It is neither the add order nor the date order.
		mockMvc.perform(patch("/api/v1/live-exam-modules/" + pkg + "/exams/order").header("Authorization", "Bearer " + system)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"examIds":["%s","%s","%s"]}
					""".formatted(a, c, b)))
			.andExpect(status().isOk());

		PackageFixtures.assertOrder(PackageFixtures.ids(fx.content(system, moduleList)), a, c, b);
		PackageFixtures.assertOrder(PackageFixtures.ids(fx.content(system, staffList)), a, c, b);
		// An explicit sort still wins over the package order.
		PackageFixtures.assertOrder(PackageFixtures.ids(fx.content(system, staffList + "&sort=liveAt,asc")), b, c, a);
	}

}
