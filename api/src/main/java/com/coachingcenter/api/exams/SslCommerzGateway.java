package com.coachingcenter.api.exams;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class SslCommerzGateway implements LiveExamPaymentGateway {

	private static final Logger log = LoggerFactory.getLogger(SslCommerzGateway.class);

	private final SslCommerzProperties properties;

	private final ObjectMapper json;

	private final RestClient http;

	SslCommerzGateway(SslCommerzProperties properties, ObjectMapper json) {
		this.properties = properties;
		this.json = json;
		org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(10));
		factory.setReadTimeout(Duration.ofSeconds(20));
		this.http = RestClient.builder().requestFactory(factory).build();
	}

	@Override
	public Started start(Checkout checkout) {
		String base = trimSlash(properties.baseUrl());
		String callback = trimSlash(properties.callbackBaseUrl());
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("store_id", properties.storeId());
		form.add("store_passwd", properties.storePassword());
		form.add("total_amount", checkout.amount().setScale(2).toPlainString());
		form.add("currency", checkout.currency());
		form.add("tran_id", checkout.tranId());
		form.add("success_url", callback + "/api/v1/live-exam-orders/" + checkout.tranId() + "/return?status=success");
		form.add("fail_url", callback + "/api/v1/live-exam-orders/" + checkout.tranId() + "/return?status=fail");
		form.add("cancel_url", callback + "/api/v1/live-exam-orders/" + checkout.tranId() + "/return?status=cancel");
		form.add("ipn_url", callback + "/api/v1/live-exam-orders/" + checkout.tranId() + "/ipn");
		form.add("cus_name", limit(checkout.customerName(), 50));
		form.add("cus_email", checkout.email());
		form.add("cus_add1", "N/A");
		form.add("cus_city", "Dhaka");
		form.add("cus_country", "Bangladesh");
		form.add("cus_phone", checkout.phone());
		form.add("shipping_method", "NO");
		form.add("product_name", limit(checkout.productName(), 255));
		form.add("product_category", "exam");
		form.add("product_profile", "non-physical-goods");
		try {
			String body = http.post()
				.uri(base + "/gwprocess/v4/api.php")
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.body(String.class);
			JsonNode node = json.readTree(body == null ? "" : body);
			String page = node.path("GatewayPageURL").asText("");
			if (!"SUCCESS".equalsIgnoreCase(node.path("status").asText()) || page.isBlank()) {
				log.warn("sslcommerz session rejected tranId={} error={}", checkout.tranId(),
						ApiException.class.getSimpleName());
				throw unavailable();
			}
			log.info("sslcommerz session accepted tranId={}", checkout.tranId());
			return new Started(page);
		}
		catch (ApiException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			log.warn("sslcommerz session rejected tranId={} error={}", checkout.tranId(),
					exception.getClass().getSimpleName());
			throw unavailable();
		}
	}

	@Override
	public Verification verify(String tranId, String valId, BigDecimal amount, String currency) {
		if (valId == null || valId.isBlank()) {
			return Verification.invalid();
		}
		String base = trimSlash(properties.baseUrl());
		try {
			String body = http.get()
				.uri(base + "/validator/api/validationserverAPI.php?val_id={valId}&store_id={storeId}&store_passwd={storePassword}&format=json",
						valId, properties.storeId(), properties.storePassword())
				.retrieve()
				.body(String.class);
			JsonNode node = json.readTree(body == null ? "" : body);
			Verification verification = readPayment(node, tranId, amount, currency, valId);
			if (verification.outcome() == Verification.Outcome.VALID) {
				log.info("sslcommerz validation accepted tranId={}", tranId);
			}
			else {
				log.warn("sslcommerz validation rejected tranId={} error={}", tranId,
						rejection(node, tranId, amount, currency, valId));
			}
			return verification;
		}
		catch (RuntimeException exception) {
			log.warn("sslcommerz validation unreachable tranId={} error={}", tranId, exception.getClass().getSimpleName());
			return Verification.unreachable();
		}
	}

	@Override
	public Lookup lookup(String tranId, BigDecimal amount, String currency) {
		String base = trimSlash(properties.baseUrl());
		try {
			String body = http.get()
				.uri(base + "/validator/api/merchantTransIDvalidationAPI.php?tran_id={tranId}&store_id={storeId}&store_passwd={storePassword}&format=json",
						tranId, properties.storeId(), properties.storePassword())
				.retrieve()
				.body(String.class);
			Lookup lookup = readLookup(json.readTree(body == null ? "" : body), tranId, amount, currency);
			log.info("sslcommerz query {} tranId={}", lookup.kind().name().toLowerCase(Locale.ROOT), tranId);
			return lookup;
		}
		catch (RuntimeException exception) {
			log.warn("sslcommerz query unreachable tranId={} error={}", tranId, exception.getClass().getSimpleName());
			return Lookup.of(Lookup.Kind.UNREACHABLE);
		}
	}

	private Lookup readLookup(JsonNode node, String tranId, BigDecimal amount, String currency) {
		String connect = node.path("APIConnect").asText("");
		if (!connect.isBlank() && !"DONE".equalsIgnoreCase(connect)) {
			return Lookup.of(Lookup.Kind.UNREACHABLE);
		}
		List<JsonNode> rows = elements(node.path("element"));
		if (rows.isEmpty()) {
			return Lookup.of(Lookup.Kind.NONE);
		}
		boolean failed = false;
		boolean cancelled = false;
		for (JsonNode row : rows) {
			String status = row.path("status").asText("").toUpperCase(Locale.ROOT);
			if ("VALID".equals(status) || "VALIDATED".equals(status)) {
				Verification verification = readPayment(row, tranId, amount, currency, row.path("val_id").asText(""));
				if (verification.outcome() == Verification.Outcome.VALID) {
					return Lookup.paid(verification.valId());
				}
				failed = true;
			}
			else if ("CANCELLED".equals(status)) {
				cancelled = true;
			}
			else if ("FAILED".equals(status)) {
				failed = true;
			}
		}
		if (failed) {
			return Lookup.of(Lookup.Kind.FAILED);
		}
		if (cancelled) {
			return Lookup.of(Lookup.Kind.CANCELLED);
		}
		return Lookup.of(Lookup.Kind.NONE);
	}

	private String rejection(JsonNode node, String tranId, BigDecimal expectedAmount, String expectedCurrency,
			String fallbackValId) {
		String status = node.path("status").asText("").toUpperCase(Locale.ROOT);
		if (!"VALID".equals(status) && !"VALIDATED".equals(status)) {
			return status.matches("[A-Z0-9_]{1,32}") ? status : "status";
		}
		String echoed = node.path("tran_id").asText("").trim();
		if (!echoed.equals(tranId)) {
			return "tran";
		}
		if (readAmount(node) == null || expectedAmount == null || readAmount(node).compareTo(expectedAmount) != 0) {
			return "amount";
		}
		String currency = firstText(node, "currency", "currency_type");
		if (expectedCurrency == null || currency.isBlank() || !expectedCurrency.trim().equals(currency)) {
			return "currency";
		}
		String store = node.path("store_id").asText("").trim();
		if (!store.isBlank() && (properties.storeId() == null || !properties.storeId().trim().equals(store))) {
			return "store";
		}
		String valId = node.path("val_id").asText(fallbackValId == null ? "" : fallbackValId).trim();
		if (valId.isBlank()) {
			return "missing";
		}
		return "status";
	}

	private Verification readPayment(JsonNode node, String tranId, BigDecimal expectedAmount, String expectedCurrency,
			String fallbackValId) {
		String status = node.path("status").asText("").toUpperCase(Locale.ROOT);
		if (!"VALID".equals(status) && !"VALIDATED".equals(status)) {
			return Verification.invalid();
		}
		String echoed = node.path("tran_id").asText("").trim();
		String store = node.path("store_id").asText("").trim();
		String currency = firstText(node, "currency", "currency_type");
		BigDecimal amount = readAmount(node);
		String valId = node.path("val_id").asText(fallbackValId == null ? "" : fallbackValId).trim();
		if (amount == null || echoed.isBlank() || valId.isBlank() || !echoed.equals(tranId)) {
			return Verification.invalid();
		}
		if (expectedAmount == null || amount.compareTo(expectedAmount) != 0) {
			return Verification.invalid();
		}
		if (expectedCurrency == null || currency.isBlank() || !expectedCurrency.trim().equals(currency)) {
			return Verification.invalid();
		}
		if (properties.storeId() == null || properties.storeId().isBlank()) {
			return Verification.invalid();
		}
		if (!store.isBlank() && !properties.storeId().trim().equals(store)) {
			return Verification.invalid();
		}
		return Verification.valid(echoed, amount, currency, valId);
	}

	private static List<JsonNode> elements(JsonNode element) {
		List<JsonNode> rows = new ArrayList<>();
		if (element.isArray()) {
			element.forEach(rows::add);
		}
		else if (element.isObject()) {
			rows.add(element);
		}
		return rows;
	}

	private static BigDecimal readAmount(JsonNode node) {
		String raw = firstText(node, "currency_amount", "amount");
		if (raw.isBlank()) {
			return null;
		}
		try {
			return new BigDecimal(raw.trim());
		}
		catch (NumberFormatException exception) {
			return null;
		}
	}

	private static String firstText(JsonNode node, String primary, String fallback) {
		String value = node.path(primary).asText("");
		if (!value.isBlank()) {
			return value.trim();
		}
		return node.path(fallback).asText("").trim();
	}

	private static String trimSlash(String value) {
		String trimmed = value == null ? "" : value.trim();
		while (trimmed.endsWith("/")) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed;
	}

	private static String limit(String value, int max) {
		String trimmed = value == null ? "" : value.trim();
		return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
	}

	private static ApiException unavailable() {
		return new ApiException(ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
				"The payment gateway is unavailable.");
	}

}
