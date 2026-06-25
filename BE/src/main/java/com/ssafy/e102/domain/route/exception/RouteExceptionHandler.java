package com.ssafy.e102.domain.route.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ssafy.e102.domain.route.controller.RouteController;
import com.ssafy.e102.domain.route.controller.RouteRatingController;
import com.ssafy.e102.global.response.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {RouteController.class, RouteRatingController.class})
public class RouteExceptionHandler {

	@ExceptionHandler({
		MethodArgumentNotValidException.class,
		HttpMessageNotReadableException.class
	})
	public ResponseEntity<ErrorResponse> handleInvalidRouteRequest(Exception exception, HttpServletRequest request) {
		if (isRatingRequest(request)) {
			return ResponseEntity
				.status(RouteErrorCode.INVALID_ROUTE_RATING_REQUEST.getHttpStatus())
				.body(ErrorResponse.from(RouteErrorCode.INVALID_ROUTE_RATING_REQUEST));
		}
		if (isSelectRequest(request)) {
			return ResponseEntity
				.status(RouteErrorCode.INVALID_ROUTE_SELECT_REQUEST.getHttpStatus())
				.body(ErrorResponse.from(RouteErrorCode.INVALID_ROUTE_SELECT_REQUEST));
		}
		if (isTransitRefreshRequest(request)) {
			return ResponseEntity
				.status(RouteErrorCode.INVALID_TRANSIT_REFRESH_REQUEST.getHttpStatus())
				.body(ErrorResponse.from(RouteErrorCode.INVALID_TRANSIT_REFRESH_REQUEST));
		}
		if (isRerouteRequest(request) && exception instanceof MethodArgumentNotValidException validationException) {
			RouteErrorCode errorCode = rerouteValidationErrorCode(validationException);
			return ResponseEntity
				.status(errorCode.getHttpStatus())
				.body(ErrorResponse.from(errorCode));
		}
		if (isRerouteRequest(request) && exception instanceof HttpMessageNotReadableException) {
			return ResponseEntity
				.status(RouteErrorCode.INVALID_CURRENT_POINT.getHttpStatus())
				.body(ErrorResponse.from(RouteErrorCode.INVALID_CURRENT_POINT));
		}
		return ResponseEntity
			.status(RouteErrorCode.INVALID_ROUTE_REQUEST.getHttpStatus())
			.body(ErrorResponse.from(RouteErrorCode.INVALID_ROUTE_REQUEST));
	}

	private boolean isRerouteRequest(HttpServletRequest request) {
		return "/routes/reroute".equals(request.getRequestURI());
	}

	private boolean isSelectRequest(HttpServletRequest request) {
		return request.getRequestURI().matches("^/routes/[^/]+/select$");
	}

	private boolean isTransitRefreshRequest(HttpServletRequest request) {
		return request.getRequestURI().matches("^/routes/[^/]+/transit-refresh$");
	}

	private boolean isRatingRequest(HttpServletRequest request) {
		return "/route-ratings".equals(request.getRequestURI());
	}

	private RouteErrorCode rerouteValidationErrorCode(MethodArgumentNotValidException exception) {
		boolean hasCurrentPointCoordinateError = exception.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(FieldError::getField)
			.anyMatch(field -> field.equals("currentPoint.lat") || field.equals("currentPoint.lng"));
		if (hasCurrentPointCoordinateError) {
			return RouteErrorCode.INVALID_CURRENT_POINT;
		}
		return RouteErrorCode.INVALID_REROUTE_REQUEST;
	}
}
