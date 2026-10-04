package com.coachingcenter.api.files;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.coachingcenter.api.auth.permission.CurrentActor;
import com.coachingcenter.api.auth.permission.PermissionChecker;
import com.coachingcenter.api.auth.permission.Permissions;
import com.coachingcenter.api.catalog.ContentAccessService;
import com.coachingcenter.api.common.error.ApiException;
import com.coachingcenter.api.common.error.ErrorCode;
import com.coachingcenter.api.courses.CourseRepository;
import com.coachingcenter.api.courses.Material;
import com.coachingcenter.api.library.BookRepository;

@Service
public class FileAccessService {

	private final PermissionChecker permissions;

	private final CourseRepository courses;

	private final BookRepository books;

	private final ContentAccessService contentAccess;

	public FileAccessService(PermissionChecker permissions, CourseRepository courses, BookRepository books,
			ContentAccessService contentAccess) {
		this.permissions = permissions;
		this.courses = courses;
		this.books = books;
		this.contentAccess = contentAccess;
	}

	public void ensureCanDownload(Material material) {
		if (material.getCourseId() != null && permissions.has(Permissions.COURSE_READ)) {
			return;
		}
		if (material.getBookId() != null && permissions.has(Permissions.BOOK_READ)) {
			return;
		}
		if (permissions.has(Permissions.CATALOG_BROWSE) && isStudentCover(material)) {
			return;
		}
		if (permissions.has(Permissions.CONTENT_READ, true)) {
			contentAccess.ensureCanDownload(material, actor().userId());
			return;
		}
		if (permissions.has(Permissions.CATALOG_BROWSE)) {
			throw new ApiException(ErrorCode.MATERIAL_FORBIDDEN, HttpStatus.FORBIDDEN, "Material access is denied.");
		}
		throw denied();
	}

	private static CurrentActor actor() {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof CurrentActor actor) {
			return actor;
		}
		throw new ApiException(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, "Authentication is required.");
	}

	private boolean isStudentCover(Material material) {
		if (courses.findByCoverFileIdAndDeletedAtIsNull(material.getId())
			.filter(course -> "PUBLISHED".equals(course.getStatus()) && !course.isClosed())
			.isPresent()) {
			return true;
		}
		return books.findByCoverFileIdAndDeletedAtIsNull(material.getId()).filter(book -> book.isAvailable()).isPresent();
	}

	private static ApiException denied() {
		return new ApiException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, "Access is denied.");
	}

}
