package com.coachingcenter.api.offline;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;

import com.coachingcenter.api.offline.OfflineAdmissionService.AdmissionView;
import com.coachingcenter.api.offline.OfflineApplicationService.Started;

import tools.jackson.databind.JsonNode;

/**
 * The public application for a batch, for someone with no account (public-admission plan, section 5; P10). Each route acts on the
 * application the token opens; the token is the one {@code start} or {@code reopen} returned. Public under the security config's rule for
 * these paths, and behind the offline switch.
 */
@RestController
public class OfflineApplicationPublicController {

	private final OfflineApplicationService applications;

	public OfflineApplicationPublicController(OfflineApplicationService applications) {
		this.applications = applications;
	}

	@PostMapping("/api/v1/public/offline-applications")
	@ResponseStatus(HttpStatus.CREATED)
	public Started start(@RequestBody JsonNode body) {
		Started started = applications.start(body);
		if (started.alreadyApplied()) {
			// F7: the open application's form is never sent back here; a private link to it went to its email.
			throw new com.coachingcenter.api.common.error.ApiException(com.coachingcenter.api.common.error.ErrorCode.APPLICATION_EXISTS,
					org.springframework.http.HttpStatus.CONFLICT,
					"You already applied for this batch with this email. If the form is not sent yet, we emailed you a link to finish it.");
		}
		return started;
	}

	@PutMapping("/api/v1/public/offline-applications/{token}/form")
	public AdmissionView saveForm(@PathVariable String token, @RequestBody JsonNode body) {
		return applications.saveForm(token, body);
	}

	@PutMapping(value = "/api/v1/public/offline-applications/{token}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public AdmissionView savePhoto(@PathVariable String token, @RequestParam("file") MultipartFile file) {
		return applications.savePhoto(token, file);
	}

	@PostMapping("/api/v1/public/offline-applications/{token}/submit")
	public AdmissionView submit(@PathVariable String token) {
		return applications.submit(token);
	}

	/** The form again, for the link in a "changes requested" mail (Q4). Valid 7 days. */
	@GetMapping("/api/v1/public/offline-applications/reopen/{linkToken}")
	public Started reopen(@PathVariable String linkToken) {
		return applications.reopen(linkToken);
	}

}
