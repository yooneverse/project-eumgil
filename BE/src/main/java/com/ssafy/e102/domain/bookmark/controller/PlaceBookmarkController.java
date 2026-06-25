package com.ssafy.e102.domain.bookmark.controller;

import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.e102.domain.bookmark.dto.request.CreatePlaceBookmarkRequest;
import com.ssafy.e102.domain.bookmark.dto.response.PlaceBookmarkCreateResponse;
import com.ssafy.e102.domain.bookmark.dto.response.PlaceBookmarkListResponse;
import com.ssafy.e102.domain.bookmark.exception.PlaceBookmarkErrorCode;
import com.ssafy.e102.domain.bookmark.exception.PlaceBookmarkException;
import com.ssafy.e102.domain.bookmark.service.PlaceBookmarkService;
import com.ssafy.e102.global.response.ApiResponse;
import com.ssafy.e102.global.security.principal.AuthPrincipal;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;

@Tag(name = "장소 북마크", description = "장소 북마크 저장, 조회, 삭제 API")
@Validated
@RestController
@RequestMapping("/bookmarks")
@RequiredArgsConstructor
public class PlaceBookmarkController {

	private static final Pattern BOOKMARK_TARGET_ID_PATTERN = Pattern.compile("tgt_[0-9a-f]{16}");

	private final PlaceBookmarkService placeBookmarkService;

	@Operation(summary = "장소 북마크 목록 조회", description = "현재 로그인한 사용자의 장소 북마크를 최신순 커서 기반으로 조회합니다.")
	@GetMapping
	public ApiResponse<PlaceBookmarkListResponse> getBookmarks(
		@Parameter(hidden = true) @AuthenticationPrincipal
		AuthPrincipal principal,
		@Parameter(description = "마지막으로 조회한 장소 북마크 ID. 첫 조회 시 생략합니다.") @RequestParam(required = false) @Positive
		Long cursor,
		@Parameter(description = "조회 개수. 허용 범위는 1~100입니다.") @RequestParam(defaultValue = "10") @Min(1) @Max(100)
		int size) {
		return ApiResponse.success(placeBookmarkService.getBookmarks(principal.userId(), cursor, size));
	}

	@Operation(summary = "장소 북마크 저장", description = "현재 로그인한 사용자의 장소 북마크를 저장합니다.")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<PlaceBookmarkCreateResponse> createBookmark(
		@Parameter(hidden = true) @AuthenticationPrincipal
		AuthPrincipal principal,
		@Valid @RequestBody
		CreatePlaceBookmarkRequest request) {
		return ApiResponse.created(placeBookmarkService.createBookmark(principal.userId(), request));
	}

	@Operation(summary = "장소 북마크 대상 ID로 삭제", description = "북마크 대상 ID를 기준으로 현재 로그인한 사용자의 장소 북마크를 삭제합니다.")
	@DeleteMapping("/targets/{bookmarkTargetId}")
	public ResponseEntity<Void> deleteBookmarkByTarget(
		@Parameter(hidden = true) @AuthenticationPrincipal
		AuthPrincipal principal,
		@Parameter(description = "삭제할 북마크 대상 ID") @PathVariable
		String bookmarkTargetId) {
		placeBookmarkService.deleteBookmarkByTarget(principal.userId(), validateBookmarkTargetId(bookmarkTargetId));
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "장소 ID로 북마크 삭제", description = "장소 ID를 기준으로 현재 로그인한 사용자의 장소 북마크를 삭제합니다.")
	@DeleteMapping("/places/{placeId}")
	public ResponseEntity<Void> deleteBookmarkByPlaceId(
		@Parameter(hidden = true) @AuthenticationPrincipal
		AuthPrincipal principal,
		@Parameter(description = "삭제할 장소 ID") @PathVariable
		String placeId) {
		placeBookmarkService.deleteBookmarkByPlaceId(principal.userId(), parsePlaceId(placeId));
		return ResponseEntity.noContent().build();
	}

	private String validateBookmarkTargetId(String bookmarkTargetId) {
		if (bookmarkTargetId == null || !BOOKMARK_TARGET_ID_PATTERN.matcher(bookmarkTargetId).matches()) {
			throw new PlaceBookmarkException(PlaceBookmarkErrorCode.INVALID_PLACE_BOOKMARK_DELETE_REQUEST);
		}
		return bookmarkTargetId;
	}

	private Long parsePlaceId(String placeId) {
		try {
			long parsedPlaceId = Long.parseLong(placeId);
			if (parsedPlaceId <= 0) {
				throw new NumberFormatException("placeId must be positive");
			}
			return parsedPlaceId;
		} catch (NumberFormatException exception) {
			throw new PlaceBookmarkException(PlaceBookmarkErrorCode.INVALID_PLACE_BOOKMARK_DELETE_REQUEST);
		}
	}
}
