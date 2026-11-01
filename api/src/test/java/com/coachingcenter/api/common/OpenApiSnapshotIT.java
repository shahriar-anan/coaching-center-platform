package com.coachingcenter.api.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.coachingcenter.api.PostgresIntegrationTest;
import com.coachingcenter.api.auth.CapturingEmailConfig;

/**
 * Writes the generated OpenAPI document to {@code target/openapi.json}. The web's contract check
 * ({@code web/scripts/check-openapi-types.mjs}, run with {@code OPENAPI_FILE} pointing here) reads that file, so the web and
 * the API are compared on the same document the build produced. It shares its Spring context with {@link
 * com.coachingcenter.api.common.audit.AuditLogIT}, so it adds no second boot.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailConfig.class)
class OpenApiSnapshotIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void writesTheOpenApiDocumentForTheWebContractCheck() throws Exception {
		MvcResult result = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
		String document = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertThat(document).contains("\"openapi\"").contains("\"paths\"").contains("\"components\"");

		Path target = Path.of(System.getProperty("basedir", "."), "target", "openapi.json");
		Files.createDirectories(target.getParent());
		Files.writeString(target, document, StandardCharsets.UTF_8);
		assertThat(Files.readString(target, StandardCharsets.UTF_8)).isEqualTo(document);
	}

}
