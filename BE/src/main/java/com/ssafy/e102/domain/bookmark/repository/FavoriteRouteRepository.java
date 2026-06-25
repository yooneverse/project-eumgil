package com.ssafy.e102.domain.bookmark.repository;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ssafy.e102.domain.bookmark.entity.FavoriteRoute;

public interface FavoriteRouteRepository extends JpaRepository<FavoriteRoute, Long> {

	Slice<FavoriteRoute> findAllByUser_UserId(UUID userId, Pageable pageable);

	Slice<FavoriteRoute> findAllByUser_UserIdAndFavRouteIdLessThan(UUID userId, Long favRouteId, Pageable pageable);

	void deleteAllByUser_UserId(UUID userId);
}
