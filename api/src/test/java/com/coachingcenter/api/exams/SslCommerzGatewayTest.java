package com.coachingcenter.api.exams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.coachingcenter.api.common.error.ApiException;
import com.sun.net.httpserver.HttpServer;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class SslCommerzGatewayTest {

	private static final String STORE = "store-under-test";

	private static final String PASSWORD = "password-under-test";

	private static final String TRAN = "abc123tranid0000000000000001";

	private static final BigDecimal AMOUNT = new BigDecimal("500.00");

	private HttpServer server;

	private SslCommerzGateway gateway;

	private ListAppender<ILoggingEvent> logs;

	private Logger gatewayLog;

	private final AtomicReference<String> responseBody = new AtomicReference<>("{}");

	private final AtomicBoolean passwordSent = new AtomicBoolean();

	private final AtomicBoolean amountSent = new AtomicBoolean();

	@BeforeEach
	void setUp() throws Exception {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", this::handle);
		server.start();
		String base = "http://127.0.0.1:" + server.getAddress().getPort();
		gateway = new SslCommerzGateway(new SslCommerzProperties(STORE, PASSWORD, base, "https://callback.test"), mapper());
		gatewayLog = (Logger) LoggerFactory.getLogger(SslCommerzGateway.class);
		logs = new ListAppender<>();
		logs.start();
		gatewayLog.addAppender(logs);
	}

	@AfterEach
	void tearDown() {
		gatewayLog.detachAppender(logs);
		if (server != null) {
			server.stop(0);
		}
	}

	@Test
	void sessionUsesTheServerAmountAndDoesNotLogSecrets() {
		responseBody.set("""
				{"status":"SUCCESS","GatewayPageURL":"https://payments.example/pay","card_no":"411111XXXXXX1111"}
				""");
		LiveExamPaymentGateway.Started started = gateway.start(checkout());
		assertThat(started.redirectUrl()).isEqualTo("https://payments.example/pay");
		assertThat(amountSent).isTrue();
		assertThat(passwordSent).isTrue();
		assertThat(logText()).contains("sslcommerz session accepted");
		assertLogsHideSecrets();
	}

	@Test
	void rejectedSessionLogsTheClassAndNotTheBody() {
		responseBody.set("""
				{"status":"FAILED","failedreason":"%s https://payments.example/secret","GatewayPageURL":""}
				""".formatted(PASSWORD));
		assertThatThrownBy(() -> gateway.start(checkout())).isInstanceOf(ApiException.class);
		assertThat(logText()).contains("sslcommerz session rejected tranId=" + TRAN + " error=ApiException");
		assertThat(logText()).doesNotContain("failedreason");
		assertThat(logText()).doesNotContain("https://");
		assertLogsHideSecrets();
	}

	@Test
	void validationAcceptsAMatchingPayment() {
		responseBody.set(payment("VALID", "500.00", "BDT", STORE));
		LiveExamPaymentGateway.Verification verification = gateway.verify(TRAN, "val-1", AMOUNT, "BDT");
		assertThat(verification.outcome()).isEqualTo(LiveExamPaymentGateway.Verification.Outcome.VALID);
		assertThat(verification.tranId()).isEqualTo(TRAN);
		assertThat(verification.amount()).isEqualByComparingTo(AMOUNT);
		assertThat(verification.currency()).isEqualTo("BDT");
		assertThat(logText()).contains("sslcommerz validation accepted");
		assertLogsHideSecrets();
	}

	@Test
	void validationReadsThePaymentChannel() {
		responseBody.set(payment("VALID", "500.00", "BDT", STORE));
		assertThat(gateway.verify(TRAN, "val-1", AMOUNT, "BDT").paymentChannel()).isEqualTo("VISA");

		responseBody.set(payment("VALID", "500.00", "BDT", STORE).replace("\"card_type\":\"VISA\"",
				"\"card_type\":\"\",\"card_brand\":\"MOBILEBANKING\""));
		assertThat(gateway.verify(TRAN, "val-1", AMOUNT, "BDT").paymentChannel()).isEqualTo("MOBILEBANKING");

		responseBody.set(payment("VALID", "500.00", "BDT", STORE).replace("\"card_type\":\"VISA\"",
				"\"card_type\":\"" + "B".repeat(80) + "\""));
		assertThat(gateway.verify(TRAN, "val-1", AMOUNT, "BDT").paymentChannel()).isEqualTo("B".repeat(64));

		responseBody.set(payment("VALID", "500.00", "BDT", STORE).replace(",\"card_type\":\"VISA\"", ""));
		assertThat(gateway.verify(TRAN, "val-1", AMOUNT, "BDT").paymentChannel()).isNull();
	}

	@Test
	void validationRejectsAFailedStatus() {
		responseBody.set(payment("FAILED", "500.00", "BDT", STORE));
		assertThat(gateway.verify(TRAN, "val-1", AMOUNT, "BDT").outcome())
			.isEqualTo(LiveExamPaymentGateway.Verification.Outcome.INVALID);
		assertLogsHideSecrets();
	}

	@Test
	void validationRejectsAnAmountMismatch() {
		responseBody.set(payment("VALID", "1.00", "BDT", STORE));
		assertThat(gateway.verify(TRAN, "val-1", AMOUNT, "BDT").outcome())
			.isEqualTo(LiveExamPaymentGateway.Verification.Outcome.INVALID);
		assertLogsHideSecrets();
	}

	@Test
	void validationRejectsACurrencyMismatch() {
		responseBody.set(payment("VALIDATED", "500.00", "USD", STORE));
		assertThat(gateway.verify(TRAN, "val-1", AMOUNT, "BDT").outcome())
			.isEqualTo(LiveExamPaymentGateway.Verification.Outcome.INVALID);
		assertLogsHideSecrets();
	}

	@Test
	void validationAcceptsWhenTheResponseOmitsTheStoreId() {
		responseBody.set("""
				{"status":"VALIDATED","tran_id":"%s","val_id":"val-1","amount":"500.00","currency_amount":"500.00","currency":"BDT","currency_type":"BDT"}
				""".formatted(TRAN));
		assertThat(gateway.verify(TRAN, "val-1", AMOUNT, "BDT").outcome())
			.isEqualTo(LiveExamPaymentGateway.Verification.Outcome.VALID);
		assertLogsHideSecrets();
	}

	@Test
	void validationRejectsADifferentStore() {
		responseBody.set(payment("VALID", "500.00", "BDT", "other-store"));
		assertThat(gateway.verify(TRAN, "val-1", AMOUNT, "BDT").outcome())
			.isEqualTo(LiveExamPaymentGateway.Verification.Outcome.INVALID);
		assertLogsHideSecrets();
	}

	@Test
	void aRepeatedValidationStaysValidAndDoesNotItselfGrantTwice() {
		responseBody.set(payment("VALID", "500.00", "BDT", STORE));
		assertThat(gateway.verify(TRAN, "val-1", AMOUNT, "BDT").outcome())
			.isEqualTo(LiveExamPaymentGateway.Verification.Outcome.VALID);
		assertThat(gateway.verify(TRAN, "val-1", AMOUNT, "BDT").outcome())
			.isEqualTo(LiveExamPaymentGateway.Verification.Outcome.VALID);
		assertLogsHideSecrets();
	}

	@Test
	void queryFindsAPaidTransactionAndRejectsMismatches() {
		responseBody.set(query("VALID", "500.00", "BDT", STORE));
		LiveExamPaymentGateway.Lookup paid = gateway.lookup(TRAN, AMOUNT, "BDT");
		assertThat(paid.kind()).isEqualTo(LiveExamPaymentGateway.Lookup.Kind.PAID);
		assertThat(paid.valId()).isEqualTo("val-1");

		responseBody.set(query("FAILED", "500.00", "BDT", STORE));
		assertThat(gateway.lookup(TRAN, AMOUNT, "BDT").kind()).isEqualTo(LiveExamPaymentGateway.Lookup.Kind.FAILED);

		responseBody.set(query("VALID", "9.00", "BDT", STORE));
		assertThat(gateway.lookup(TRAN, AMOUNT, "BDT").kind()).isEqualTo(LiveExamPaymentGateway.Lookup.Kind.FAILED);

		responseBody.set(query("VALID", "500.00", "USD", STORE));
		assertThat(gateway.lookup(TRAN, AMOUNT, "BDT").kind()).isEqualTo(LiveExamPaymentGateway.Lookup.Kind.FAILED);

		responseBody.set(query("VALID", "500.00", "BDT", "other-store"));
		assertThat(gateway.lookup(TRAN, AMOUNT, "BDT").kind()).isEqualTo(LiveExamPaymentGateway.Lookup.Kind.FAILED);
		assertLogsHideSecrets();
	}

	@Test
	void anErrorResponseDoesNotLogThePasswordOrTheBody() {
		responseBody.set(payment("VALID", "500.00", "BDT", STORE));
		server.stop(0);
		server = null;
		assertThat(gateway.verify(TRAN, "val-1", AMOUNT, "BDT").outcome())
			.isEqualTo(LiveExamPaymentGateway.Verification.Outcome.UNREACHABLE);
		assertThat(logText()).contains("sslcommerz validation unreachable");
		assertLogsHideSecrets();
	}

	private void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
		byte[] posted = exchange.getRequestBody().readAllBytes();
		String body = new String(posted, StandardCharsets.UTF_8);
		String query = exchange.getRequestURI().getRawQuery();
		String combined = body + "&" + (query == null ? "" : query);
		if (combined.contains("store_passwd=" + PASSWORD)) {
			passwordSent.set(true);
		}
		if (body.contains("total_amount=500.00")) {
			amountSent.set(true);
		}
		byte[] bytes = responseBody.get().getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json");
		exchange.sendResponseHeaders(200, bytes.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(bytes);
		}
	}

	private void assertLogsHideSecrets() {
		String text = logText();
		assertThat(text).doesNotContain(PASSWORD);
		assertThat(text).doesNotContain(STORE);
		assertThat(text).doesNotContain("411111");
		assertThat(text).doesNotContain("card_no");
		assertThat(logs.list).allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
	}

	private String logText() {
		StringBuilder text = new StringBuilder();
		for (ILoggingEvent event : logs.list) {
			text.append(event.getFormattedMessage()).append('\n');
		}
		return text.toString();
	}

	private static LiveExamPaymentGateway.Checkout checkout() {
		return new LiveExamPaymentGateway.Checkout(TRAN, AMOUNT, "BDT", "Exam", "Student", "s@example.com", "01700000000");
	}

	private static String payment(String status, String amount, String currency, String store) {
		return """
				{"status":"%s","tran_id":"%s","val_id":"val-1","amount":"%s","currency_amount":"%s","currency":"%s","store_id":"%s","card_type":"VISA","card_no":"411111XXXXXX1111"}
				""".formatted(status, TRAN, amount, amount, currency, store);
	}

	private static String query(String status, String amount, String currency, String store) {
		return """
				{"APIConnect":"DONE","no_of_trans_found":1,"element":[%s]}
				""".formatted(payment(status, amount, currency, store).trim());
	}

	private static ObjectMapper mapper() {
		return JsonMapper.builder().build();
	}

}
