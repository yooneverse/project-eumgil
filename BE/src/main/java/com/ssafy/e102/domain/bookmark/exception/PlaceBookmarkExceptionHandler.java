package com.ssafy.e102.domain.bookmark.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.ssafy.e102.domain.bookmark.controller.PlaceBookmarkController;
import com.ssafy.e102.global.response.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = PlaceBookmarkController.class)
public class PlaceBookmarkExceptionHandler {

	@ExceptionHandler({
		MethodArgumentNotValidException.class,
		ConstraintViolationException.class,
		MissingServletRequestParameterException.class,
		HttpMessageNotReadableException.class,
		MethodArgumentTypeMismatchException.class
	})
	public ResponseEntity<ErrorResponse> handleInvalidBookmarkRequest(
		Exception exception,
		HttpServletRequest request) {
		PlaceBookmarkErrorCode errorCode = resolveErrorCode(request);
		return ResponseEntity
			.status(errorCode.getHttpStatus())
			.body(ErrorResponse.of(errorCode, resolveMessage(exception, errorCode)));
	}

	private PlaceBookmarkErrorCode resolveErrorCode(HttpServletRequest request) {
		if ("DELETE".equalsIgnoreCase(request.getMethod())) {
			return PlaceBookmarkErrorCode.INVALID_PLACE_BOOKMARK_DELETE_REQUEST;
		}
		return PlaceBookmarkErrorCode.INVALID_PLACE_BOOKMARK_REQUEST;
	}

	private String resolveMessage(Exception exception, PlaceBookmarkErrorCode errorCode) {
		if (exception instanceof MethodArgumentNotValidException validationException) {
			return validationException.getBindingResult()
				.getFieldErrors()
				.stream()
				.findFirst()
				.map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
				.orElse(errorCode.getMessage());
		}
		if (exception instanceof MissingServletRequestParameterException missingParameterException) {
			return missingParameterException.getParameterName() + ": 필수 요청 파라미터입니다.";
		}
		if (exception instanceof ConstraintViolationException constraintViolationException) {
			return constraintViolationException.getMessage();
		}
		return errorCode.getMessage();
	}
}
