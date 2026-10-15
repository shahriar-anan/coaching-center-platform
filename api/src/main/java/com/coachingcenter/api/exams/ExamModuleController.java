package com.coachingcenter.api.exams;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.web.PageResponse;

import tools.jackson.databind.JsonNode;

@RestController
public class ExamModuleController {

	private final ExamModuleService modules;

	private final ExamNodeService nodes;

	private final PermissionChecker permissions;

	public ExamModuleController(ExamModuleService modules, ExamNodeService nodes, PermissionChecker permissions) {
		this.modules = modules;
		this.nodes = nodes;
		this.permissions = permissions;
	}

	@PostMapping("/api/v1/exam-modules")
	public ResponseEntity<ExamModuleService.ModuleView> create(@RequestBody JsonNode body) {
		require(Permissions.EXAM_MANAGE);
		return ResponseEntity.status(HttpStatus.CREATED).body(modules.create(body, canSetPrice()));
	}

	@PatchMapping("/api/v1/exam-modules/{id}")
	public ExamModuleService.ModuleView update(@PathVariable UUID id, @RequestBody JsonNode body) {
		require(Permissions.EXAM_MANAGE);
		return modules.update(id, body, canSetPrice());
	}

	@GetMapping("/api/v1/exam-modules")
	public PageResponse<ExamModuleService.ModuleView> list(@PageableDefault(size = 20) Pageable pageable) {
		require(Permissions.EXAM_READ);
		return PageResponse.from(modules.list(pageable));
	}

	@GetMapping("/api/v1/exam-modules/{id}")
	public ExamModuleService.ModuleView get(@PathVariable UUID id) {
		require(Permissions.EXAM_READ);
		return modules.get(id);
	}

	@DeleteMapping("/api/v1/exam-modules/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		require(Permissions.EXAM_DELETE);
		modules.delete(id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/api/v1/exam-modules/{id}/archive")
	public ResponseEntity<Void> archive(@PathVariable UUID id) {
		require(Permissions.EXAM_DELETE);
		modules.archive(id);
		return ResponseEntity.noContent().build();
	}

	@PatchMapping("/api/v1/exam-modules/{id}/access")
	public ExamModuleService.ModuleView patchAccess(@PathVariable UUID id, @RequestBody JsonNode body) {
		require(Permissions.EXAM_MANAGE);
		return modules.patchAccess(id, body);
	}

	@GetMapping("/api/v1/exam-modules/{id}/nodes")
	public List<ExamNodeService.NodeView> listNodes(@PathVariable UUID id) {
		require(Permissions.EXAM_READ);
		return nodes.list(id);
	}

	@PostMapping("/api/v1/exam-modules/{id}/nodes")
	public ResponseEntity<ExamNodeService.NodeView> createNode(@PathVariable UUID id, @RequestBody JsonNode body) {
		require(Permissions.EXAM_MANAGE);
		return ResponseEntity.status(HttpStatus.CREATED).body(nodes.create(id, body));
	}

	@PatchMapping("/api/v1/exam-modules/{id}/nodes/{nodeId}")
	public ExamNodeService.NodeView updateNode(@PathVariable UUID id, @PathVariable UUID nodeId,
			@RequestBody JsonNode body) {
		require(Permissions.EXAM_MANAGE);
		return nodes.update(id, nodeId, body);
	}

	@PostMapping("/api/v1/exam-modules/{id}/nodes/reorder")
	public ResponseEntity<Void> reorderNodes(@PathVariable UUID id, @RequestBody JsonNode body) {
		require(Permissions.EXAM_MANAGE);
		nodes.reorder(id, body);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/api/v1/exam-modules/{id}/nodes/{nodeId}")
	public ResponseEntity<Void> deleteNode(@PathVariable UUID id, @PathVariable UUID nodeId) {
		require(Permissions.EXAM_DELETE);
		nodes.delete(id, nodeId);
		return ResponseEntity.noContent().build();
	}

	private void require(String permission) {
		if (!permissions.has(permission)) {
			throw denied();
		}
	}

	private boolean canSetPrice() {
		return permissions.has(Permissions.EXAM_PRICE);
	}

	private static ApiException denied() {
		return new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
	}

}
