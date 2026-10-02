package com.coachingcenter.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
class FoundationIT extends PostgresIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private Environment environment;

	@Autowired
	private DataSource dataSource;

	@Test
	void schemaIsValidatedAgainstPostgreSQL() throws Exception {
		assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
		try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery("select version()")) {
			assertThat(result.next()).isTrue();
			assertThat(result.getString(1)).containsIgnoringCase("PostgreSQL");
		}
	}

	@Test
	void unauthenticatedErrorUsesTheSharedBodyAndRequestId() throws Exception {
		mockMvc.perform(get("/api/v1/me").header("X-Request-Id", "foundation-check-1"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string("X-Request-Id", "foundation-check-1"))
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
			.andExpect(jsonPath("$.message").exists())
			.andExpect(jsonPath("$.timestamp").exists())
			.andExpect(jsonPath("$.requestId").value("foundation-check-1"))
			.andExpect(jsonPath("$.fieldErrors").doesNotExist());
	}

	@Test
	void corsAllowsOnlyTheConfiguredWebOrigin() throws Exception {
		mockMvc.perform(options("/api/v1/me").header("Origin", "http://localhost:3000")
			.header("Access-Control-Request-Method", "GET"))
			.andExpect(status().isOk())
			.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));

		mockMvc.perform(options("/api/v1/me").header("Origin", "https://evil.example")
			.header("Access-Control-Request-Method", "GET"))
			.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}

}
