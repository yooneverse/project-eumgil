package com.ssafy.e102.global.logging;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(0)
public class RequestLoggingFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
	private static final String REQUEST_ID_HEADER = "X-Request-Id";
	private static final String REQUEST_ID_KEY = "requestId";

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		return path.startsWith("/actuator")
			|| path.startsWith("/health")
			|| path.startsWith("/swagger-ui")
			|| path.startsWith("/v3/api-docs");
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain) throws ServletException, IOException {
		String requestId = request.getHeader(REQUEST_ID_HEADER);
		if (requestId == null || requestId.isBlank()) {
			requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		}

		long startedAt = System.nanoTime();
		String remoteAddr = resolveRemoteAddr(request);

		MDC.put(REQUEST_ID_KEY, requestId);
		response.setHeader(REQUEST_ID_HEADER, requestId);

		try {
			filterChain.doFilter(request, response);
		} finally {
			long latencyMs = (System.nanoTime() - startedAt) / 1_000_000;
			log.info(
				"event=request_completed request_id={} method={} path={} status={} latency_ms={} remote_addr={}",
				requestId,
				request.getMethod(),
				request.getRequestURI(),
				response.getStatus(),
				latencyMs,
				remoteAddr);
			MDC.remove(REQUEST_ID_KEY);
		}
	}

	private String resolveRemoteAddr(HttpServletRequest request) {
		String forwardedFor = request.getHeader("X-Forwarded-For");
		if (forwardedFor == null || forwardedFor.isBlank()) {
			return request.getRemoteAddr();
		}
		return forwardedFor.split(",")[0].trim();
	}
}
