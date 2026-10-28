package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.coachingcenter.api.PostgresIntegrationTest;

import tools.jackson.databind.ObjectMapper;

/**
 * NR-01 final check, case 14 (R14): only a Master Admin or a System Admin grants a written retake. The System Admin's grant
 * and the instructor's refusal are covered by {@code WrittenRetakeGrantIT}; this class covers the student's refusal.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RetakeGrantBySystemAdminIT extends PostgresIntegrationTest {

	private static final String NOON = "2026-10-20T12:00:00+06:00";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private PasswordEncoder passwords;

	@Autowired
	private ObjectMapper json;

	private AuthoringFixture fx;

	@BeforeEach
	void setUp() {
		fx = new AuthoringFixture(mockMvc, dataSource, passwords, json);
	}

	@Test
	void aStudentCannotGrantAWrittenRetakeAndNoGrantIsWritten() throws Exception {
		String staff = fx.staff();
		UUID module = fx.module(staff, "Student grant");
		UUID exam = fx.createExam(staff, module, "WRITTEN", "Written", NOON, 40, null);
		UUID studentId = fx.seed("STUDENT");
		String student = fx.token(studentId);

		mockMvc.perform(post("/api/v1/live-exams/" + exam + "/retake-grants").header("Authorization", "Bearer " + student)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"studentId\":\"" + studentId + "\",\"reason\":\"Asked by a student\"}"))
			.andExpect(status().isForbidden());

		assertThat(fx.jdbc().queryForObject("select count(*) from live_exam_retake_grants where exam_id = ?", Integer.class, exam))
			.isZero();
	}

}
