package com.ssafy.e102.eumgil.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ssafy.e102.eumgil.data.local.entity.FavoriteRouteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteRouteDao {
    @Query("SELECT * FROM favoriteRoute WHERE accountScopeKey = :accountScopeKey ORDER BY updatedAt DESC")
    fun observeFavoriteRoutes(accountScopeKey: String): Flow<List<FavoriteRouteEntity>>

    @Query(
        "SELECT * FROM favoriteRoute " +
            "WHERE accountScopeKey = :accountScopeKey AND favoriteRouteId = :favoriteRouteId LIMIT 1",
    )
    fun observeFavoriteRoute(
        accountScopeKey: String,
        favoriteRouteId: Long,
    ): Flow<FavoriteRouteEntity?>

    @Query(
        "SELECT * FROM favoriteRoute " +
            "WHERE accountScopeKey = :accountScopeKey AND favoriteRouteId = :favoriteRouteId LIMIT 1",
    )
    suspend fun getFavoriteRoute(
        accountScopeKey: String,
        favoriteRouteId: Long,
    ): FavoriteRouteEntity?

    @Query("SELECT * FROM favoriteRoute WHERE accountScopeKey = :accountScopeKey ORDER BY updatedAt DESC")
    suspend fun getFavoriteRoutes(accountScopeKey: String): List<FavoriteRouteEntity>

    @Upsert
    suspend fun upsertFavoriteRoute(favoriteRoute: FavoriteRouteEntity)

    @Upsert
    suspend fun upsertFavoriteRoutes(favoriteRoutes: List<FavoriteRouteEntity>)

    @Query(
        "DELETE FROM favoriteRoute " +
            "WHERE accountScopeKey = :accountScopeKey AND favoriteRouteId = :favoriteRouteId",
    )
    suspend fun deleteFavoriteRoute(
        accountScopeKey: String,
        favoriteRouteId: Long,
    )

    @Query("DELETE FROM favoriteRoute WHERE accountScopeKey = :accountScopeKey")
    suspend fun clearFavoriteRoutes(accountScopeKey: String)
}
