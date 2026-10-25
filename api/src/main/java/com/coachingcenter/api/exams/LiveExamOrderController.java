package com.coachingcenter.api.exams;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.config.AppProperties;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.web.PageResponse;

import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RestController
public class LiveExamOrderController {

	static final String RESULT_PATH = "/purchase/result";

	private final LiveExamOrderService orders;

	private final LiveExamOrderAdminService adminOrders;

	private final LiveExamOrderHistoryService history;

	private final PermissionChecker permissions;

	private final ObjectMapper json;

	private final AppProperties properties;

	public LiveExamOrderController(LiveExamOrderService orders, LiveExamOrderAdminService adminOrders,
			LiveExamOrderHistoryService history, PermissionChecker permissions, ObjectMapper json,
			AppProperties properties) {
		this.orders = orders;
		this.adminOrders = adminOrders;
		this.history = history;
		this.permissions = permissions;
		this.json = json;
		this.properties = properties;
	}

	@PostMapping("/api/v1/live-exam-orders")
	public ResponseEntity<LiveExamOrderService.OrderView> initiate(@RequestBody JsonNode body) {
		requireStudent();
		LiveExamOrderService.InitiateResult result = orders.initiate(actorId(), body);
		return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.order());
	}

	@GetMapping("/api/v1/live-exam-orders")
	public PageResponse<LiveExamOrderAdminService.OrderRow> list(@RequestParam(required = false) String status,
			@RequestParam(required = false) String tranId, @RequestParam(required = false) String q,
			@PageableDefault(size = 20) Pageable pageable) {
		if (!permissions.has(Permissions.LIVE_EXAM_ORDER_READ)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
		return PageResponse.from(adminOrders.list(status, tranId, q, pageable));
	}

	@GetMapping("/api/v1/me/live-exam-orders")
	public PageResponse<LiveExamOrderHistoryService.OwnOrderRow> mine(@PageableDefault(size = 20) Pageable pageable) {
		requireStudent();
		return PageResponse.from(history.list(actorId(), pageable));
	}

	@GetMapping("/api/v1/live-exam-orders/status")
	public LiveExamOrderService.PaymentStatus status(@RequestParam(required = false) String tranId) {
		if (tranId == null || tranId.isBlank()) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
		}
		return orders.paymentStatus(tranId.trim(), actorId());
	}

	@GetMapping("/api/v1/live-exam-orders/{id}")
	public LiveExamOrderService.OrderView get(@PathVariable UUID id) {
		requireStudent();
		return orders.get(id, actorId());
	}

	@PostMapping("/api/v1/live-exam-orders/{id}/cancel")
	public LiveExamOrderService.OrderView cancel(@PathVariable UUID id) {
		requireStudent();
		return orders.cancel(id, actorId());
	}

	@RequestMapping(method = { RequestMethod.GET, RequestMethod.POST }, path = "/api/v1/live-exam-orders/{tranId}/return")
	public ResponseEntity<Void> returned(@PathVariable String tranId, HttpServletRequest request) throws IOException {
		Notice notice = notice(request);
		orders.returned(tranId, notice.tranId(), notice.valId());
		return ResponseEntity.status(HttpStatus.FOUND)
			.cacheControl(CacheControl.noStore())
			.location(resultPage(tranId))
			.build();
	}

	@PostMapping("/api/v1/live-exam-orders/{tranId}/ipn")
	public LiveExamOrderService.OrderView ipn(@PathVariable String tranId, HttpServletRequest request) throws IOException {
		Notice notice = notice(request);
		return orders.ipn(tranId, notice.tranId(), notice.valId());
	}

	private Notice notice(HttpServletRequest request) throws IOException {
		String tranId = request.getParameter("tran_id");
		String valId = request.getParameter("val_id");
		String contentType = request.getContentType();
		if (contentType != null && contentType.contains("json") && request.getContentLength() != 0) {
			JsonNode body = json.readTree(request.getInputStream());
			if (body != null && body.isObject()) {
				tranId = first(tranId, body, "tran_id");
				valId = first(valId, body, "val_id");
			}
		}
		return new Notice(tranId, valId);
	}

	private URI resultPage(String tranId) {
		String origin = websiteOrigin();
		if (origin == null) {
			throw new ApiException(ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
					"The payment gateway is unavailable.");
		}
		return URI.create(origin + RESULT_PATH + "?tranId=" + URLEncoder.encode(tranId, StandardCharsets.UTF_8));
	}

	private String websiteOrigin() {
		if (properties.cors() == null || properties.cors().allowedOrigins() == null
				|| properties.cors().allowedOrigins().isEmpty()) {
			return null;
		}
		String origin = properties.cors().allowedOrigins().get(0);
		if (origin == null || origin.isBlank()) {
			return null;
		}
		String trimmed = origin.trim();
		while (trimmed.endsWith("/")) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed;
	}

	private static String first(String current, JsonNode body, String field) {
		if (current != null && !current.isBlank()) {
			return current;
		}
		if (!body.has(field) || body.get(field).isNull()) {
			return current;
		}
		String value = body.get(field).asText();
		return value.isBlank() ? current : value;
	}

	private void requireStudent() {
		if (!permissions.has(Permissions.LIVE_EXAM_BROWSE)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
	}

	private static UUID actorId() {
		var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null
				&& authentication.getPrincipal() instanceof com.coachingcenter.api.auth.permission.CurrentActor actor) {
			return actor.userId();
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

	private record Notice(String tranId, String valId) {
	}

}
