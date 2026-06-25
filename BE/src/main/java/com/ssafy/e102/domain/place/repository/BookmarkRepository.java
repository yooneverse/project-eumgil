package com.ssafy.e102.domain.place.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ssafy.e102.domain.place.entity.Bookmark;

public interface BookmarkRepository extends JpaRepository<Bookmark, Integer> {

	@EntityGraph(attributePaths = "place")
	Slice<Bookmark> findAllByUser_UserId(UUID userId, Pageable pageable);

	@EntityGraph(attributePaths = "place")
	Slice<Bookmark> findAllByUser_UserIdAndBookmarkIdLessThan(UUID userId, Integer bookmarkId, Pageable pageable);

	@EntityGraph(attributePaths = "place")
	List<Bookmark> findAllByUser_UserId(UUID userId);

	boolean existsByUser_UserIdAndPlace_PlaceId(UUID userId, Long placeId);

	boolean existsByUser_UserIdAndBookmarkTargetId(UUID userId, String bookmarkTargetId);

	@EntityGraph(attributePaths = "place")
	Optional<Bookmark> findByUser_UserIdAndBookmarkTargetId(UUID userId, String bookmarkTargetId);

	@EntityGraph(attributePaths = "place")
	Optional<Bookmark> findByUser_UserIdAndPlace_PlaceId(UUID userId, Long placeId);

	@Query("""
		select bookmark.place.placeId
		from Bookmark bookmark
		where bookmark.user.userId = :userId
			and bookmark.place.placeId in :placeIds
		""")
	Set<Long> findBookmarkedPlaceIds(
		@Param("userId")
		UUID userId,
		@Param("placeIds")
		Collection<Long> placeIds);

	void deleteAllByUser_UserId(UUID userId);
}
