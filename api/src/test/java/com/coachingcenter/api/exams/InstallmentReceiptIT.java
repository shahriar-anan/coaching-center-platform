package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.coachingcenter.api.auth.CapturingEmailConfig;

import tools.jackson.databind.JsonNode;

/**
 * The receipt PDF of a direct-enrolment payment, read back as text (plan section 6.1). Each state prints its own stamp: PART PAID
 * while something is due, PAID once nothing is due, and VOID for a voided payment. A student may open only their own receipt.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ CapturingEmailConfig.class, InstallmentTestSupport.ClockConfig.class })
class InstallmentReceiptIT extends InstallmentTestSupport {

	@Test
	void aPartPaidReceiptSaysSoAndShowsTheNextInstallment() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Receipt Master"));
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID student = seed("STUDENT", "Part Paid Student");
		UUID module = module(system, "Part paid receipt");
		JsonNode grant = enrol(master, student, module, "300", "100", "TXN-PDF1", row("100", TODAY), row("100", "2026-11-20"),
				row("100", "2026-12-20"));
		String receiptNo = grant.get("receiptNo").asText();
		String payment = firstPayment(master, grant.get("paymentPlanId").asText());

		String text = receiptText(master, payment);

		assertThat(text).contains(receiptNo).contains("TXN-PDF1").contains("PART PAID");
		assertThat(text).contains("Next installment:").contains("1 of 3");
	}

	@Test
	void aFullyPaidReceiptSaysPaidAndHasNoNextInstallment() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Full Receipt Master"));
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID student = seed("STUDENT", "Full Paid Student");
		UUID module = module(system, "Full receipt");
		JsonNode grant = enrol(master, student, module, "300", "300", "TXN-PDF2", row("300", TODAY));
		String payment = firstPayment(master, grant.get("paymentPlanId").asText());

		String text = receiptText(master, payment);

		assertThat(text).contains(grant.get("receiptNo").asText()).contains("PAID");
		assertThat(text).doesNotContain("PART PAID").doesNotContain("Next installment:");
	}

	@Test
	void aVoidedReceiptIsStampedVoidAndTheEarlierOneStaysPartPaid() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Void Receipt Master"));
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID student = seed("STUDENT", "Voided Receipt Student");
		UUID module = module(system, "Void receipt");
		JsonNode grant = enrol(master, student, module, "300", "100", "TXN-PDF3", row("100", TODAY), row("100", "2026-11-20"),
				row("100", "2026-12-20"));
		String plan = grant.get("paymentPlanId").asText();
		pay(master, plan, "50", "TXN-PDF4");
		JsonNode before = detail(master, plan);
		String first = before.get("payments").get(0).get("id").asText();
		String latest = before.get("payments").get(1).get("id").asText();
		mockMvc.perform(post("/api/v1/installment-payments/" + latest + "/void").header("Authorization", "Bearer " + master)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"reason\":\"Entered by mistake\"}"))
			.andExpect(status().isOk());

		assertThat(receiptText(master, latest)).contains("VOID");
		assertThat(receiptText(master, first)).contains("PART PAID").doesNotContain("VOID");
	}

	@Test
	void aStudentOpensTheirOwnReceiptAndNoOneElsesOne() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Owner Receipt Master"));
		String system = token(seed("SYSTEM_ADMIN", "Staff"));
		UUID owner = seed("STUDENT", "Receipt Owner");
		UUID other = seed("STUDENT", "Receipt Stranger");
		UUID module = module(system, "Owner receipt");
		JsonNode grant = enrol(master, owner, module, "300", "100", "TXN-PDF5", row("100", TODAY), row("100", "2026-11-20"),
				row("100", "2026-12-20"));
		String payment = firstPayment(master, grant.get("paymentPlanId").asText());

		MvcResult own = mockMvc.perform(get("/api/v1/me/installment-payments/" + payment + "/receipt.pdf")
			.header("Authorization", "Bearer " + token(owner)))
			.andExpect(status().isOk())
			.andReturn();
		assertThat(own.getResponse().getContentType()).startsWith("application/pdf");
		assertThat(text(own.getResponse().getContentAsByteArray())).contains(grant.get("receiptNo").asText());

		mockMvc.perform(get("/api/v1/me/installment-payments/" + payment + "/receipt.pdf")
			.header("Authorization", "Bearer " + token(other)))
			.andExpect(status().isNotFound());
	}

	@Test
	void aReceiptThatDoesNotExistIsNotFound() throws Exception {
		String master = token(seed("MASTER_ADMIN", "Missing Receipt Master"));
		mockMvc.perform(get("/api/v1/installment-payments/" + UUID.randomUUID() + "/receipt.pdf")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isNotFound());
	}

	/** The id of the first payment on a plan, which is the one taken when the student enrolled. */
	private String firstPayment(String master, String plan) throws Exception {
		return detail(master, plan).get("payments").get(0).get("id").asText();
	}

	/** The receipt of one payment, as the Master Admin downloads it, read back as text. */
	private String receiptText(String master, String payment) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/installment-payments/" + payment + "/receipt.pdf")
			.header("Authorization", "Bearer " + master))
			.andExpect(status().isOk())
			.andReturn();
		assertThat(result.getResponse().getContentType()).startsWith("application/pdf");
		return text(result.getResponse().getContentAsByteArray());
	}

	/** The text of a PDF. The file must start with its header, so a non-PDF body fails here. */
	private static String text(byte[] pdf) throws Exception {
		assertThat(new String(pdf, 0, Math.min(4, pdf.length), java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF");
		try (PDDocument document = Loader.loadPDF(pdf)) {
			return new PDFTextStripper().getText(document);
		}
	}

}
