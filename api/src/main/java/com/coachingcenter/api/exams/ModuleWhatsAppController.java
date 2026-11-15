package com.coachingcenter.api.exams;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.common.error.FieldErrorDetail;
import com.coachingcenter.api.common.web.WhatsAppLinks;
import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.databind.JsonNode;

/**
 * A package's WhatsApp group or channel link (15 Nov). Staff read it; the admins who manage the package cover set it. It is saved at once,
 * outside the package's revision and publish steps, and students get it only through their own access (`/api/v1/me/live-exam-access`)
 * while the package is published and their access is live. The public package routes never carry it.
 */
@RestController
public class ModuleWhatsAppController {

	private final LiveExamModuleRepository modules;

	private final PermissionChecker permissions;

	public ModuleWhatsAppController(LiveExamModuleRepository modules, PermissionChecker permissions) {
		this.modules = modules;
		this.permissions = permissions;
	}

	@GetMapping("/api/v1/live-exam-modules/{id}/whatsapp")
	@Transactional(readOnly = true)
	public Link get(@PathVariable UUID id) {
		require(Permissions.EXAM_READ);
		return new Link(find(id).getWhatsappUrl());
	}

	/** Sets the link, or removes it with a blank or null {@code url}. */
	@PutMapping("/api/v1/live-exam-modules/{id}/whatsapp")
	@Transactional
	public Link set(@PathVariable UUID id, @RequestBody(required = false) JsonNode body) {
		require(Permissions.MODULE_COVER_MANAGE);
		LiveExamModule module = find(id);
		String url = WhatsAppLinks.clean(body != null && body.hasNonNull("url") ? body.get("url").asText() : null);
		String problem = WhatsAppLinks.problem(url);
		if (problem != null) {
			throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "Request validation failed.",
					List.of(new FieldErrorDetail("url", problem)));
		}
		module.setWhatsappUrl(url);
		modules.saveAndFlush(module);
		return new Link(url);
	}

	private LiveExamModule find(UUID id) {
		return modules.findByIdAndDeletedAtIsNull(id)
			.orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found."));
	}

	private void require(String permission) {
		if (!permissions.has(permission)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
	}

	/** The link, or null when the package has none. */
	public record Link(@JsonInclude(JsonInclude.Include.ALWAYS) String url) {
	}

}
