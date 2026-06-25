package com.ssafy.e102.global.external.ai;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.e102.domain.place.exception.PlaceErrorCode;
import com.ssafy.e102.domain.place.exception.PlaceException;
import com.ssafy.e102.global.exception.BusinessException;
import com.ssafy.e102.global.exception.CommonErrorCode;

@Component
public class RestTemplateAiVoiceAnalysisClient implements AiVoiceAnalysisClient {

	private final RestTemplate restTemplate;
	private final AiVoiceAnalysisProperties properties;
	private final ObjectMapper objectMapper;

	@Autowired
	public RestTemplateAiVoiceAnalysisClient(
		RestTemplateBuilder builder,
		AiVoiceAnalysisProperties properties,
		ObjectMapper objectMapper) {
		this(builder.connectTimeout(Duration.ofSeconds(3))
			.readTimeout(Duration.ofSeconds(5))
			.build(), properties, objectMapper);
	}

	RestTemplateAiVoiceAnalysisClient(
		RestTemplate restTemplate,
		AiVoiceAnalysisProperties properties,
		ObjectMapper objectMapper) {
		this.restTemplate = restTemplate;
		this.properties = properties;
		this.objectMapper = objectMapper;
	}

	@Override
	public AiVoiceAnalyzeResult analyze(AiVoiceAnalyzeCommand command) {
		try {
			JsonNode response = restTemplate.postForObject(properties.voiceAnalyzeUri(), command, JsonNode.class);
			return toAnalyzeResult(response);
		} catch (HttpStatusCodeException exception) {
			throw mapHttpStatusException(exception);
		} catch (RestClientException exception) {
			throw new PlaceException(PlaceErrorCode.VOICE_ANALYSIS_AI_FAILED,
				"음성 분석 AI 호출에 실패했습니다.", exception);
		}
	}

	private AiVoiceAnalyzeResult toAnalyzeResult(JsonNode response) {
		if (response == null || response.isNull()) {
			throw new PlaceException(PlaceErrorCode.VOICE_ANALYSIS_AI_FAILED,
				"음성 분석 AI 응답이 비어 있습니다.");
		}
		JsonNode payload = response.has("data") && response.get("data").isObject()
			? response.get("data")
			: response;
		try {
			return objectMapper.treeToValue(payload, AiVoiceAnalyzeResult.class);
		} catch (JsonProcessingException exception) {
			throw new PlaceException(PlaceErrorCode.VOICE_ANALYSIS_AI_FAILED,
				"음성 분석 AI 응답을 해석하지 못했습니다.", exception);
		}
	}

	private RuntimeException mapHttpStatusException(HttpStatusCodeException exception) {
		JsonNode errorResponse = readErrorResponse(exception);
		if (exception.getStatusCode().equals(HttpStatus.BAD_REQUEST)
			&& CommonErrorCode.INVALID_INPUT.getStatus().equals(errorStatus(errorResponse))) {
			return new BusinessException(CommonErrorCode.INVALID_INPUT,
				errorMessage(errorResponse, CommonErrorCode.INVALID_INPUT.getMessage()),
				exception);
		}
		return new PlaceException(PlaceErrorCode.VOICE_ANALYSIS_AI_FAILED,
			errorMessage(errorResponse, PlaceErrorCode.VOICE_ANALYSIS_AI_FAILED.getMessage()),
			exception);
	}

	private JsonNode readErrorResponse(HttpStatusCodeException exception) {
		String responseBody = exception.getResponseBodyAsString();
		if (responseBody == null || responseBody.isBlank()) {
			return null;
		}
		try {
			return objectMapper.readTree(responseBody);
		} catch (JsonProcessingException ignored) {
			return null;
		}
	}

	private String errorStatus(JsonNode errorResponse) {
		if (errorResponse == null) {
			return null;
		}
		return errorResponse.path("status").asText(null);
	}

	private String errorMessage(JsonNode errorResponse, String fallback) {
		if (errorResponse == null) {
			return fallback;
		}
		return errorResponse.path("message").asText(fallback);
	}
}
