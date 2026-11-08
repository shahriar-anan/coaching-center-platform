package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
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
import org.springframework.test.web.servlet.MvcResult;

import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The staff exam list with a category filter and {@code branch} (NR-01, B3): the filter matches the category alone, or the
 * category and every sub-category under it, and {@code branch} means nothing without a category.
 *
 * The database is shared with other tests, so every check keeps only the four exams this test made (see {@link #listed}), and
 * never compares a whole-database total. The one exception is the unknown category, which matches no exam at all.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class StaffExamListBranchIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	private PackageFixtures fx;

	@BeforeEach
	void setUp() {
		fx = new PackageFixtures(mockMvc, json, dataSource, passwords);
	}

	@Test
	void aCategoryFilterWithoutBranchListsOnlyItsOwnExams() throws Exception {
		String system = fx.token(fx.seed("SYSTEM_ADMIN"));
		Tree tree = tree(system);

		assertThat(listed(system, "&categoryId=" + tree.parent(), tree)).containsExactlyInAnyOrder(tree.inParent());
	}

	@Test
	void branchAddsTheExamsOfEverySubcategoryAtAnyDepthButNotAnUnrelatedCategory() throws Exception {
		String system = fx.token(fx.seed("SYSTEM_ADMIN"));
		Tree tree = tree(system);

		assertThat(listed(system, "&categoryId=" + tree.parent() + "&branch=true", tree))
			.containsExactlyInAnyOrder(tree.inParent(), tree.inChild(), tree.inGrand());
	}

	@Test
	void branchFromAMiddleCategoryReachesItsOwnSubtreeOnly() throws Exception {
		String system = fx.token(fx.seed("SYSTEM_ADMIN"));
		Tree tree = tree(system);

		assertThat(listed(system, "&categoryId=" + tree.child() + "&branch=true", tree))
			.containsExactlyInAnyOrder(tree.inChild(), tree.inGrand());
	}

	@Test
	void branchWithAnUnknownCategoryReturnsNoExamAtAll() throws Exception {
		String system = fx.token(fx.seed("SYSTEM_ADMIN"));
		tree(system);

		JsonNode body = fx.body(system, "/api/v1/live-exams?size=100&categoryId=" + UUID.randomUUID() + "&branch=true");
		assertThat(body.get("totalElements").asLong()).isZero();
		assertThat(body.get("content").size()).isZero();
	}

	@Test
	void branchWithoutACategoryIsIgnoredAndTheWholeListStillHoldsEveryExam() throws Exception {
		String system = fx.token(fx.seed("SYSTEM_ADMIN"));
		Tree tree = tree(system);

		// The list is sorted by last update, newest first, so the four exams just made are on the first page of 100.
		assertThat(listed(system, "&branch=true", tree)).containsExactlyInAnyOrder(tree.inParent(), tree.inChild(),
				tree.inGrand(), tree.inOther());
		assertThat(listed(system, "", tree)).containsExactlyInAnyOrder(tree.inParent(), tree.inChild(), tree.inGrand(),
				tree.inOther());
	}

	/** The exam ids the list returns for {@code query}, keeping only the exams {@code tree} made. */
	private List<UUID> listed(String token, String query, Tree tree) throws Exception {
		JsonNode content = fx.content(token, "/api/v1/live-exams?size=100" + query);
		return PackageFixtures.ids(content).stream().filter(tree.exams()::contains).toList();
	}

	/** Parent P, its child C, C's child G, an unrelated root U, and one standalone exam in each. Titles carry a fresh tag. */
	private Tree tree(String system) throws Exception {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		UUID parent = category(system, "Branch parent " + tag, null);
		UUID child = category(system, "Branch child " + tag, parent);
		UUID grand = category(system, "Branch grand " + tag, child);
		UUID other = category(system, "Branch other " + tag, null);
		return new Tree(parent, child, grand, other,
				standalone(system, parent, "Parent exam " + tag),
				standalone(system, child, "Child exam " + tag),
				standalone(system, grand, "Grand exam " + tag),
				standalone(system, other, "Other exam " + tag));
	}

	/** A category; {@code parent} is null for a root. */
	private UUID category(String token, String titleEn, UUID parent) throws Exception {
		String parentField = parent == null ? "" : ",\"parentId\":\"" + parent + "\"";
		MvcResult result = mockMvc.perform(post("/api/v1/live-exam-categories").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titleEn":"%s","titleBn":"বিভাগ"%s}
					""".formatted(titleEn, parentField)))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	/** An MCQ exam in {@code category} with no package. */
	private UUID standalone(String token, UUID category, String titleEn) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/live-exams").header("Authorization", "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"categoryId":"%s","examType":"MCQ","paperLanguage":"BN","titleEn":"%s","titleBn":"খাতা",
					 "descriptionEn":"English description","descriptionBn":"বাংলা বিবরণ",
					 "rulesEn":"English rules","rulesBn":"বাংলা নিয়ম","durationMinutes":40,"passPercent":50}
					""".formatted(category, titleEn)))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private record Tree(UUID parent, UUID child, UUID grand, UUID other, UUID inParent, UUID inChild, UUID inGrand,
			UUID inOther) {

		Set<UUID> exams() {
			return Set.of(inParent, inChild, inGrand, inOther);
		}

	}

}
