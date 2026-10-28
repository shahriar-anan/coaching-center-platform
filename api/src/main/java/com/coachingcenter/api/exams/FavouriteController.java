package com.coachingcenter.api.exams;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;

@RestController
public class FavouriteController {

	private final FavouriteService favourites;

	private final StudentOwnAccessService access;

	private final PermissionChecker permissions;

	public FavouriteController(FavouriteService favourites, StudentOwnAccessService access,
			PermissionChecker permissions) {
		this.favourites = favourites;
		this.access = access;
		this.permissions = permissions;
	}

	@GetMapping("/api/v1/me/favourites")
	public FavouriteService.Favourites list() {
		UUID student = require();
		return favourites.list(student, access.viewer(student));
	}

	@PutMapping("/api/v1/me/favourites/exams/{id}")
	public ResponseEntity<Void> putExam(@PathVariable UUID id) {
		favourites.putExam(require(), id);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/api/v1/me/favourites/exams/{id}")
	public ResponseEntity<Void> removeExam(@PathVariable UUID id) {
		favourites.removeExam(require(), id);
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/api/v1/me/favourites/packages/{id}")
	public ResponseEntity<Void> putModule(@PathVariable UUID id) {
		favourites.putModule(require(), id);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/api/v1/me/favourites/packages/{id}")
	public ResponseEntity<Void> removeModule(@PathVariable UUID id) {
		favourites.removeModule(require(), id);
		return ResponseEntity.noContent().build();
	}

	/** Students only (LIVE_EXAM_FAVOURITE). Returns the signed-in student's id. */
	private UUID require() {
		if (!permissions.has(Permissions.LIVE_EXAM_FAVOURITE)) {
			throw new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
		}
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor) {
			return actor.userId();
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

}
