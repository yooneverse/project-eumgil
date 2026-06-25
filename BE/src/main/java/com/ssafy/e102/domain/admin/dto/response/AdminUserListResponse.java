package com.ssafy.e102.domain.admin.dto.response;

import java.util.List;

public record AdminUserListResponse(
	List<AdminUserResponse> users) {
}
