package com.coachingcenter.api.exams;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import tools.jackson.databind.JsonNode;
import org.springframework.security.core.context.SecurityContextHolder;

/** A student's retake request, and the Master Admin's or System Admin's decision on it. */
@RestController
public class RetakeRequestController {

	private final RetakeRequestService requests;

	private final PermissionChecker permissions;

	public RetakeRequestController(RetakeRequestService requests, PermissionChecker permissions) {
		this.requests = requests;
		this.permissions = permissions;
	}

	/** The student's own latest request on this exam, and whether they can ask now. */
	@GetMapping("/api/v1/live-exams/{id}/retake-request")
	public RetakeRequestService.MyRequestView mine(@PathVariable UUID id) {
		requireStudent();
		return requests.mine(id, actor().userId());
	}

	/** The student asks for another written retake. The reason is optional. */
	@PostMapping("/api/v1/live-exams/{id}/retake-request")
	public ResponseEntity<RetakeRequestService.RequestView> request(@PathVariable UUID id, @RequestBody(required = false) JsonNode body) {
		requireStudent();
		JsonNode reason = body == null ? null : body.get("reason");
		String note = reason == null || reason.isNull() ? null : reason.asText();
		return ResponseEntity.status(HttpStatus.CREATED).body(requests.request(id, actor().userId(), note));
	}

	/** The requests with a status (waiting ones by default), for the Master Admin and the System Admin. */
	@GetMapping("/api/v1/retake-requests")
	public List<RetakeRequestService.StaffRequestView> list(@RequestParam(value = "status", required = false) String status) {
		requireDecider();
		return requests.list(status);
	}

	@PostMapping("/api/v1/retake-requests/{id}/approve")
	public RetakeRequestService.RequestView approve(@PathVariable UUID id) {
		requireDecider();
		return requests.approve(id, actor().userId());
	}

	@PostMapping("/api/v1/retake-requests/{id}/decline")
	public RetakeRequestService.RequestView decline(@PathVariable UUID id, @RequestBody(required = false) JsonNode body) {
		requireDecider();
		JsonNode note = body == null ? null : body.get("note");
		return requests.decline(id, note == null || note.isNull() ? null : note.asText(), actor().userId());
	}

	private void requireStudent() {
		if (!permissions.has(Permissions.LIVE_EXAM_BROWSE)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
	}

	private void requireDecider() {
		if (!permissions.has(Permissions.LIVE_EXAM_RETAKE_GRANT)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
	}

	private static CurrentActor actor() {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor) {
			return actor;
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

}
