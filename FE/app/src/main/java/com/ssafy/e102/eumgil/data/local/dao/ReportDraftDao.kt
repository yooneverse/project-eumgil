package com.ssafy.e102.eumgil.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ssafy.e102.eumgil.data.local.entity.ReportDraftEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportDraftDao {
    @Query("SELECT * FROM reportDraft ORDER BY updatedAt DESC")
    fun observeReportDrafts(): Flow<List<ReportDraftEntity>>

    @Query("SELECT * FROM reportDraft WHERE draftId = :draftId LIMIT 1")
    fun observeReportDraft(draftId: String): Flow<ReportDraftEntity?>

    @Query("SELECT * FROM reportDraft WHERE draftId = :draftId LIMIT 1")
    suspend fun getReportDraft(draftId: String): ReportDraftEntity?

    @Query("SELECT * FROM reportDraft ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestReportDraft(): ReportDraftEntity?

    @Upsert
    suspend fun upsertReportDraft(reportDraft: ReportDraftEntity)

    @Upsert
    suspend fun upsertReportDrafts(reportDrafts: List<ReportDraftEntity>)

    @Query("DELETE FROM reportDraft WHERE draftId = :draftId")
    suspend fun deleteReportDraft(draftId: String)

    @Query("DELETE FROM reportDraft")
    suspend fun clearReportDrafts()
}
