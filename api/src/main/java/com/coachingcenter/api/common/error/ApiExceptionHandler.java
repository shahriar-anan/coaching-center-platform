package com.coachingcenter.api.common.error;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.coachingcenter.api.common.web.RequestIds;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
		List<FieldErrorDetail> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
			.map(error -> new FieldErrorDetail(error.getField(), error.getDefaultMessage()))
			.toList();
		return body(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Request validation failed.", fieldErrors,
				request);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiError> malformed(HttpServletRequest request) {
		return body(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST, "Request body is malformed.", null, request);
	}

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ApiError> apiException(ApiException exception, HttpServletRequest request) {
		return body(exception.status(), exception.code(), exception.getMessage(), null, request);
	}

	@ExceptionHandler(MasterAdminProtectedException.class)
	public ResponseEntity<ApiError> masterAdminProtected(MasterAdminProtectedException exception,
			HttpServletRequest request) {
		return body(HttpStatus.FORBIDDEN, ErrorCode.MASTER_ADMIN_PROTECTED, exception.getMessage(), null, request);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ApiError> notFound(HttpServletRequest request) {
		return body(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "Resource not found.", null, request);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiError> methodNotAllowed(HttpServletRequest request) {
		return body(HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED, "Method is not allowed.", null,
				request);
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<ApiError> unsupportedMedia(HttpServletRequest request) {
		return body(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Media type is not supported.",
				null, request);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> unexpected(Exception exception, HttpServletRequest request) {
		log.error("Unhandled request failure", exception);
		return body(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "An unexpected error occurred.", null,
				request);
	}

	private static ResponseEntity<ApiError> body(HttpStatus status, ErrorCode code, String message,
			List<FieldErrorDetail> fieldErrors, HttpServletRequest request) {
		ApiError error = ApiError.of(code, message, fieldErrors, RequestIds.current(request));
		return ResponseEntity.status(status).body(error);
	}

}
