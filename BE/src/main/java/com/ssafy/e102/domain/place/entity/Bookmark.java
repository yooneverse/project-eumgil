package com.ssafy.e102.domain.place.entity;

import org.locationtech.jts.geom.Point;
import org.springframework.util.StringUtils;

import com.ssafy.e102.domain.user.entity.User;
import com.ssafy.e102.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "bookmarks", uniqueConstraints = {
	@UniqueConstraint(name = "uk_bookmarks_user_place", columnNames = {"user_id", "place_id"}),
	@UniqueConstraint(name = "uk_bookmarks_user_target", columnNames = {"user_id", "bookmark_target_id"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bookmark extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(nullable = false, updatable = false)
	private Integer bookmarkId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(length = 32)
	private String bookmarkTargetId;

	@ManyToOne(fetch = FetchType.LAZY, optional = true)
	@JoinColumn(name = "place_id")
	private Place place;

	@Column(length = 30)
	private String provider;

	@Column(length = 100)
	private String providerPlaceId;

	@Column(length = 255)
	private String name;

	@Column(length = 255)
	private String providerCategory;

	@Column(length = 255)
	private String address;

	@Column(columnDefinition = "geometry(Point, 4326)")
	private Point point;

	public static Bookmark createInternal(User user, Place place, String bookmarkTargetId) {
		if (user == null || place == null || !StringUtils.hasText(bookmarkTargetId)) {
			throw new IllegalArgumentException("내부 장소 북마크 생성값이 올바르지 않습니다.");
		}
		Bookmark bookmark = new Bookmark();
		bookmark.user = user;
		bookmark.place = place;
		bookmark.bookmarkTargetId = bookmarkTargetId;
		return bookmark;
	}

	public static Bookmark createExternal(
		User user,
		String bookmarkTargetId,
		String provider,
		String providerPlaceId,
		String name,
		String providerCategory,
		String address,
		Point point) {
		if (user == null || !StringUtils.hasText(bookmarkTargetId) || !StringUtils.hasText(name) || point == null) {
			throw new IllegalArgumentException("외부 장소 북마크 생성값이 올바르지 않습니다.");
		}
		Bookmark bookmark = new Bookmark();
		bookmark.user = user;
		bookmark.bookmarkTargetId = bookmarkTargetId;
		bookmark.provider = provider;
		bookmark.providerPlaceId = providerPlaceId;
		bookmark.name = name;
		bookmark.providerCategory = providerCategory;
		bookmark.address = address;
		bookmark.point = point;
		return bookmark;
	}

	public void assignBookmarkTargetId(String bookmarkTargetId) {
		if (!StringUtils.hasText(bookmarkTargetId)) {
			throw new IllegalArgumentException("bookmarkTargetId는 비어 있을 수 없습니다.");
		}
		this.bookmarkTargetId = bookmarkTargetId;
	}
}
