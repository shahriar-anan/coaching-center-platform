package com.coachingcenter.api.offline;

import static com.coachingcenter.api.offline.OfflineSupport.actorId;
import static com.coachingcenter.api.offline.OfflineSupport.require;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.offline.OfflineBatchRateService.RateView;

import tools.jackson.databind.JsonNode;

/**
 * The default pay rate of each instructor within a batch (plan F7, section 6). Staff and the Master Admin read and set them
 * (OFFLINE_BATCH_ROUTINE), from the batch page's instructor rates panel.
 */
@RestController
public class OfflineBatchRateController {

	private final OfflineBatchRateService rates;

	private final PermissionChecker permissions;

	public OfflineBatchRateController(OfflineBatchRateService rates, PermissionChecker permissions) {
		this.rates = rates;
		this.permissions = permissions;
	}

	@GetMapping("/api/v1/offline-batches/{id}/rates")
	public List<RateView> list(@PathVariable UUID id) {
		require(permissions, Permissions.OFFLINE_BATCH_ROUTINE);
		return rates.list(id);
	}

	@PutMapping("/api/v1/offline-batches/{id}/rates/{instructorId}")
	public RateView set(@PathVariable UUID id, @PathVariable UUID instructorId, @RequestBody JsonNode body) {
		require(permissions, Permissions.OFFLINE_BATCH_ROUTINE);
		return rates.set(actorId(), id, instructorId, body);
	}

	@DeleteMapping("/api/v1/offline-batches/{id}/rates/{instructorId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void remove(@PathVariable UUID id, @PathVariable UUID instructorId) {
		require(permissions, Permissions.OFFLINE_BATCH_ROUTINE);
		rates.remove(id, instructorId);
	}

}
