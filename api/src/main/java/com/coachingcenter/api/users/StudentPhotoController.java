package com.coachingcenter.api.users;

import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.coachingcenter.api.auth.AuthService;
import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.files.FileResponses;

/** The signed-in student's own profile picture. Staff get 403 from every route here. */
@RestController
public class StudentPhotoController {

	private final StudentPhotoService photos;

	private final AuthService auth;

	public StudentPhotoController(StudentPhotoService photos, AuthService auth) {
		this.photos = photos;
		this.auth = auth;
	}

	@PutMapping("/api/v1/me/photo")
	public AuthService.MeView upload(@RequestPart("file") MultipartFile file) {
		UUID userId = currentUser().userId();
		photos.upload(userId, file);
		return auth.me(userId);
	}

	@DeleteMapping("/api/v1/me/photo")
	public AuthService.MeView remove() {
		UUID userId = currentUser().userId();
		photos.remove(userId);
		return auth.me(userId);
	}

	/** The picture bytes. Private to the student: the browser may keep it, but no shared cache may. */
	@GetMapping("/api/v1/me/photo")
	public ResponseEntity<InputStreamResource> photo() {
		StudentPhotoService.PhotoFile file = photos.open(currentUser().userId());
		return FileResponses.inline(file.body(), file.contentType(), file.byteSize(), StudentPhotoService.FILE_NAME,
				"private, no-cache");
	}

	private CurrentActor currentUser() {
		var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor) {
			return actor;
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

}
