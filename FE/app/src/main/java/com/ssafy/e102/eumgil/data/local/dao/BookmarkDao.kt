package com.ssafy.e102.eumgil.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ssafy.e102.eumgil.data.local.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmark WHERE accountScopeKey = :accountScopeKey ORDER BY updatedAt DESC")
    fun observeBookmarks(accountScopeKey: String): Flow<List<BookmarkEntity>>

    @Query(
        "SELECT * FROM bookmark WHERE accountScopeKey = :accountScopeKey AND placeId = :placeId LIMIT 1",
    )
    fun observeBookmark(
        accountScopeKey: String,
        placeId: String,
    ): Flow<BookmarkEntity?>

    @Query(
        "SELECT * FROM bookmark WHERE accountScopeKey = :accountScopeKey AND placeId = :placeId LIMIT 1",
    )
    suspend fun getBookmark(
        accountScopeKey: String,
        placeId: String,
    ): BookmarkEntity?

    @Query(
        "SELECT * FROM bookmark " +
            "WHERE accountScopeKey = :accountScopeKey AND bookmarkTargetId = :bookmarkTargetId LIMIT 1",
    )
    suspend fun getBookmarkByTargetId(
        accountScopeKey: String,
        bookmarkTargetId: String,
    ): BookmarkEntity?

    @Query("SELECT COUNT(*) FROM bookmark WHERE accountScopeKey = :accountScopeKey")
    suspend fun getBookmarkCount(accountScopeKey: String): Int

    @Upsert
    suspend fun upsertBookmark(bookmark: BookmarkEntity)

    @Upsert
    suspend fun upsertBookmarks(bookmarks: List<BookmarkEntity>)

    @Query("DELETE FROM bookmark WHERE accountScopeKey = :accountScopeKey AND placeId = :placeId")
    suspend fun deleteBookmark(
        accountScopeKey: String,
        placeId: String,
    )

    @Query(
        "DELETE FROM bookmark WHERE accountScopeKey = :accountScopeKey AND bookmarkTargetId = :bookmarkTargetId",
    )
    suspend fun deleteBookmarkByTargetId(
        accountScopeKey: String,
        bookmarkTargetId: String,
    )

    @Query("DELETE FROM bookmark WHERE accountScopeKey = :accountScopeKey")
    suspend fun clearBookmarks(accountScopeKey: String)
}
