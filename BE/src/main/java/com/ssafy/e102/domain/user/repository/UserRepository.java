package com.ssafy.e102.domain.user.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ssafy.e102.domain.user.entity.User;
import com.ssafy.e102.domain.user.type.PrimaryUserType;
import com.ssafy.e102.domain.user.type.SocialProvider;
import com.ssafy.e102.domain.user.type.UserRole;

import jakarta.persistence.LockModeType;

public interface UserRepository extends JpaRepository<User, UUID> {

	default List<User> findAllOrderByCreatedAtDesc() {
		return findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
	}

	List<User> findAllByRoleOrderByCreatedAtDesc(UserRole role);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select userEntity from User userEntity where userEntity.userId = :userId")
	Optional<User> findByIdForUpdate(
		@Param("userId")
		UUID userId);

	long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(LocalDateTime from, LocalDateTime to);

	long countByRole(UserRole role);

	@Query("""
		select user.selectedPrimaryUserType as userType,
			count(user) as count
		from User user
		group by user.selectedPrimaryUserType
		""")
	List<UserTypeCount> countByPrimaryUserType();

	Optional<User> findBySocialProviderAndSocialProviderUserId(
		SocialProvider socialProvider,
		String socialProviderUserId);

	boolean existsBySocialProviderAndSocialProviderUserId(
		SocialProvider socialProvider,
		String socialProviderUserId);

	boolean existsByUserIdAndRole(UUID userId, UserRole role);

	interface UserTypeCount {
		PrimaryUserType getUserType();

		long getCount();
	}
}
