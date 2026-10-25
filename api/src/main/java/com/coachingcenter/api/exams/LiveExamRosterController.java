package com.coachingcenter.api.exams;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.web.PageResponse;

import tools.jackson.databind.JsonNode;

@RestController
public class LiveExamRosterController {

	private final LiveExamRosterService roster;

	private final PermissionChecker permissions;

	public LiveExamRosterController(LiveExamRosterService roster, PermissionChecker permissions) {
		this.roster = roster;
		this.permissions = permissions;
	}

	@GetMapping("/api/v1/live-exam-modules/{moduleId}/roster")
	public PageResponse<LiveExamRosterService.RosterRow> list(@PathVariable UUID moduleId,
			@RequestParam(required = false) String q, @PageableDefault(size = 20) Pageable pageable) {
		requireRoster();
		return PageResponse.from(roster.rows(moduleId, q, pageable));
	}

	@GetMapping("/api/v1/live-exam-modules/{moduleId}/roster/search")
	public List<LiveExamRosterService.StudentMatch> search(@PathVariable UUID moduleId,
			@RequestParam(defaultValue = "") String q) {
		requireRoster();
		return roster.search(moduleId, q);
	}

	@PostMapping("/api/v1/live-exam-modules/{moduleId}/roster")
	public ResponseEntity<LiveExamRosterService.StudentMatch> mark(@PathVariable UUID moduleId,
			@RequestBody JsonNode body) {
		requireRoster();
		JsonNode studentId = body.get("studentId");
		if (studentId == null || studentId.isNull() || studentId.asText().isBlank()) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.");
		}
		return ResponseEntity.status(HttpStatus.CREATED).body(roster.mark(moduleId, UUID.fromString(studentId.asText())));
	}

	@PostMapping("/api/v1/live-exam-modules/{moduleId}/roster/paste")
	public LiveExamRosterService.PasteResult paste(@PathVariable UUID moduleId, @RequestBody JsonNode body) {
		requireRoster();
		return roster.paste(moduleId, body);
	}

	@DeleteMapping("/api/v1/live-exam-modules/{moduleId}/roster/{studentId}")
	public ResponseEntity<Void> remove(@PathVariable UUID moduleId, @PathVariable UUID studentId) {
		requireRoster();
		roster.remove(moduleId, studentId);
		return ResponseEntity.noContent().build();
	}

	private void requireRoster() {
		if (!permissions.has(Permissions.LIVE_EXAM_ROSTER)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
	}

}
