package com.ssafy.e102.global.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.ssafy.e102.global.response.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception) {
		ErrorCode errorCode = exception.getErrorCode();

		return ResponseEntity
			.status(errorCode.getHttpStatus())
			.body(ErrorResponse.of(errorCode, exception.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
		MethodArgumentNotValidException exception) {
		return invalidInput(getValidationMessage(exception));
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ErrorResponse> handleConstraintViolationException(ConstraintViolationException exception) {
		return invalidInput(exception.getMessage());
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ErrorResponse> handleMissingServletRequestParameterException(
		MissingServletRequestParameterException exception) {
		return invalidInput(exception.getParameterName() + ": 필수 요청 파라미터입니다.");
	}

	@ExceptionHandler({
		HttpMessageNotReadableException.class,
		MethodArgumentTypeMismatchException.class
	})
	public ResponseEntity<ErrorResponse> handleInvalidRequestException(Exception exception) {
		return invalidInput(CommonErrorCode.INVALID_INPUT.getMessage());
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ErrorResponse> handleNoResourceFoundException(NoResourceFoundException exception) {
		return ResponseEntity
			.status(CommonErrorCode.NOT_FOUND.getHttpStatus())
			.body(ErrorResponse.from(CommonErrorCode.NOT_FOUND));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleException(HttpServletRequest request, Exception exception) {
		log.error(
			"event=unhandled_exception method={} path={} message={}",
			request.getMethod(),
			request.getRequestURI(),
			exception.getMessage(),
			exception);

		return ResponseEntity
			.status(CommonErrorCode.INTERNAL_ERROR.getHttpStatus())
			.body(ErrorResponse.from(CommonErrorCode.INTERNAL_ERROR));
	}

	private ResponseEntity<ErrorResponse> invalidInput(String message) {
		return ResponseEntity
			.status(CommonErrorCode.INVALID_INPUT.getHttpStatus())
			.body(ErrorResponse.of(CommonErrorCode.INVALID_INPUT, message));
	}

	private String getValidationMessage(MethodArgumentNotValidException exception) {
		return exception.getBindingResult()
			.getFieldErrors()
			.stream()
			.findFirst()
			.map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
			.orElse(CommonErrorCode.INVALID_INPUT.getMessage());
	}
}
