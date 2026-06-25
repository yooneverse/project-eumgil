package com.ssafy.e102.domain.report.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.ssafy.e102.domain.report.dto.request.CreateHazardReportImageUploadUrlRequest;
import com.ssafy.e102.domain.report.dto.response.CreateHazardReportImageUploadUrlResponse;
import com.ssafy.e102.domain.report.exception.HazardReportErrorCode;
import com.ssafy.e102.domain.report.exception.HazardReportException;
import com.ssafy.e102.domain.report.storage.ReportImagePresignedUrl;
import com.ssafy.e102.domain.report.storage.ReportImagePresigner;
import com.ssafy.e102.domain.report.storage.ReportImageStorageProperties;

@Service
public class HazardReportImageUploadService {

	private static final int MAX_IMAGE_COUNT = 5;
	private static final Map<String, String> EXTENSIONS_BY_CONTENT_TYPE = Map.of(
		"image/jpeg", ".jpg",
		"image/png", ".png",
		"image/webp", ".webp",
		"image/heic", ".heic",
		"image/heif", ".heif");

	private final ReportImageStorageProperties properties;
	private final ReportImagePresigner presigner;
	private final Clock clock;

	@Autowired
	public HazardReportImageUploadService(
		ReportImageStorageProperties properties,
		ReportImagePresigner presigner) {
		this(properties, presigner, Clock.systemUTC());
	}

	HazardReportImageUploadService(
		ReportImageStorageProperties properties,
		ReportImagePresigner presigner,
		Clock clock) {
		this.properties = properties;
		this.presigner = presigner;
		this.clock = clock;
	}

	public CreateHazardReportImageUploadUrlResponse createUploadUrl(
		UUID userId,
		CreateHazardReportImageUploadUrlRequest request) {
		UUID requiredUserId = requireUserId(userId);
		String contentType = normalizeContentType(request.contentType());
		long contentLength = requireContentLength(request.contentLength());
		validateContentType(contentType);
		validateContentLength(contentLength);

		String objectKey = createObjectKey(requiredUserId, contentType);
		ReportImagePresignedUrl presignedUpload = presigner.createPutObjectPresignedUrl(
			objectKey,
			contentType,
			contentLength,
			properties.presignTtl());

		return new CreateHazardReportImageUploadUrlResponse(
			presignedUpload.url(),
			objectKey,
			presignedUpload.expiresAt());
	}

	public List<CreateHazardReportImageUploadUrlResponse> createUploadUrls(
		UUID userId,
		List<CreateHazardReportImageUploadUrlRequest> requests) {
		if (requests == null || requests.isEmpty()) {
			throw invalidUploadRequest("이미지 업로드 요청 목록은 비어 있을 수 없습니다.");
		}
		if (requests.size() > MAX_IMAGE_COUNT) {
			throw invalidUploadRequest("제보 이미지는 최대 5장까지 업로드할 수 있습니다.");
		}
		return requests.stream()
			.map(request -> createUploadUrl(userId, request))
			.toList();
	}

	public String createReadUrl(String imageObjectKey) {
		String normalizedObjectKey = normalizeStoredImageReference(imageObjectKey);
		validateReadableImageObjectKey(normalizedObjectKey);
		return presigner.createGetObjectPresignedUrl(
			normalizedObjectKey,
			properties.presignTtl()).url();
	}

	public void validateImageObjectKeys(UUID userId, List<String> imageObjectKeys) {
		if (imageObjectKeys == null || imageObjectKeys.isEmpty()) {
			return;
		}
		if (imageObjectKeys.size() > MAX_IMAGE_COUNT) {
			throw invalidImageObjectKey("제보 이미지는 최대 5장까지 첨부할 수 있습니다.");
		}

		UUID requiredUserId = requireUserId(userId);
		for (String imageObjectKey : imageObjectKeys) {
			validateOwnedImageObjectKey(requiredUserId, imageObjectKey);
		}
	}

	public void validateThumbnailObjectKeys(UUID userId, List<String> thumbnailObjectKeys) {
		validateOwnedImageObjectKeys(userId, thumbnailObjectKeys);
	}

	private void validateOwnedImageObjectKeys(UUID userId, List<String> imageObjectKeys) {
		if (imageObjectKeys == null || imageObjectKeys.isEmpty()) {
			return;
		}
		if (imageObjectKeys.size() > MAX_IMAGE_COUNT) {
			throw invalidImageObjectKey("?쒕낫 ?대?吏??理쒕? 5?κ퉴吏 泥⑤??????덉뒿?덈떎.");
		}

		UUID requiredUserId = requireUserId(userId);
		for (String imageObjectKey : imageObjectKeys) {
			validateOwnedImageObjectKey(requiredUserId, imageObjectKey);
		}
	}

	private String createObjectKey(UUID userId, String contentType) {
		String date = LocalDate.now(clock.withZone(ZoneOffset.UTC)).format(DateTimeFormatter.BASIC_ISO_DATE);
		return "%s/%s/%s/%s%s".formatted(
			properties.keyPrefix(),
			userId,
			date,
			UUID.randomUUID(),
			extension(contentType));
	}

	private void validateOwnedImageObjectKey(UUID userId, String imageObjectKey) {
		String normalizedObjectKey = normalizeImageObjectKey(imageObjectKey);
		String expectedPrefix = properties.keyPrefix()
			+ "/"
			+ userId
			+ "/";

		if (!normalizedObjectKey.startsWith(expectedPrefix)) {
			throw invalidImageObjectKey("제보 이미지 object key는 업로드 API로 발급된 값이어야 합니다.");
		}
		if (normalizedObjectKey.contains("://")
			|| normalizedObjectKey.indexOf('?') >= 0
			|| normalizedObjectKey.indexOf('#') >= 0
			|| normalizedObjectKey.startsWith("/")
			|| normalizedObjectKey.contains("..")) {
			throw invalidImageObjectKey("제보 이미지 object key 형식이 올바르지 않습니다.");
		}
	}

	private String normalizeImageObjectKey(String imageObjectKey) {
		if (!StringUtils.hasText(imageObjectKey)) {
			throw invalidImageObjectKey("제보 이미지 object key는 필수입니다.");
		}
		return imageObjectKey.trim();
	}

	private String normalizeStoredImageReference(String storedImageReference) {
		String normalizedReference = normalizeImageObjectKey(storedImageReference);
		if (!normalizedReference.contains("://")) {
			return normalizedReference;
		}
		try {
			URI uri = new URI(normalizedReference);
			String path = uri.getPath();
			if (!StringUtils.hasText(path)) {
				throw invalidImageObjectKey("저장된 제보 이미지 경로가 올바르지 않습니다.");
			}
			String objectKey = path;
			while (objectKey.startsWith("/")) {
				objectKey = objectKey.substring(1);
			}
			String bucketPrefix = properties.bucket() + "/";
			if (objectKey.startsWith(bucketPrefix)) {
				objectKey = objectKey.substring(bucketPrefix.length());
			}
			return normalizeImageObjectKey(objectKey);
		} catch (URISyntaxException exception) {
			throw invalidImageObjectKey("저장된 제보 이미지 경로가 올바르지 않습니다.");
		}
	}

	private void validateReadableImageObjectKey(String imageObjectKey) {
		String expectedPrefix = properties.keyPrefix() + "/";
		if (!imageObjectKey.startsWith(expectedPrefix)) {
			throw invalidImageObjectKey("저장된 제보 이미지 object key 형식이 올바르지 않습니다.");
		}
		if (imageObjectKey.contains("://")
			|| imageObjectKey.indexOf('?') >= 0
			|| imageObjectKey.indexOf('#') >= 0
			|| imageObjectKey.startsWith("/")
			|| imageObjectKey.contains("..")) {
			throw invalidImageObjectKey("저장된 제보 이미지 object key 형식이 올바르지 않습니다.");
		}
	}

	private UUID requireUserId(UUID userId) {
		if (userId == null) {
			throw invalidUploadRequest("사용자는 필수입니다.");
		}
		return userId;
	}

	private long requireContentLength(Long contentLength) {
		if (contentLength == null || contentLength <= 0) {
			throw invalidUploadRequest("이미지 파일 크기는 0보다 커야 합니다.");
		}
		return contentLength;
	}

	private void validateContentType(String contentType) {
		if (!properties.allowedContentTypes().contains(contentType)) {
			throw invalidUploadRequest("지원하지 않는 이미지 형식입니다.");
		}
	}

	private void validateContentLength(long contentLength) {
		if (contentLength > properties.maxContentLength().toBytes()) {
			throw invalidUploadRequest("이미지 파일 크기는 10MB 이하여야 합니다.");
		}
	}

	private String normalizeContentType(String contentType) {
		if (!StringUtils.hasText(contentType)) {
			throw invalidUploadRequest("이미지 콘텐츠 타입은 필수입니다.");
		}
		return contentType.trim().toLowerCase(Locale.ROOT);
	}

	private String extension(String contentType) {
		String extension = EXTENSIONS_BY_CONTENT_TYPE.get(contentType);
		if (extension == null) {
			throw invalidUploadRequest("지원하지 않는 이미지 형식입니다.");
		}
		return extension;
	}

	private HazardReportException invalidUploadRequest(String message) {
		return new HazardReportException(
			HazardReportErrorCode.INVALID_HAZARD_REPORT_IMAGE_UPLOAD_REQUEST,
			message);
	}

	private HazardReportException invalidImageObjectKey(String message) {
		return new HazardReportException(
			HazardReportErrorCode.INVALID_HAZARD_REPORT_IMAGE_URL,
			message);
	}
}
