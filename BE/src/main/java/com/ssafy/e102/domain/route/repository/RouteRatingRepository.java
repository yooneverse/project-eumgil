package com.ssafy.e102.domain.route.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ssafy.e102.domain.route.entity.RouteRating;

public interface RouteRatingRepository extends JpaRepository<RouteRating, Long> {

	Optional<RouteRating> findByRouteSession_SessionId(UUID sessionId);

	void deleteAllByUser_UserId(UUID userId);
}
