package com.ssafy.e102.eumgil.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ssafy.e102.eumgil.data.local.entity.ReportOutboxEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportOutboxDao {
    @Query("SELECT * FROM reportOutbox ORDER BY updatedAt DESC")
    fun observeReportOutboxItems(): Flow<List<ReportOutboxEntity>>

    @Query("SELECT * FROM reportOutbox WHERE outboxId = :outboxId LIMIT 1")
    suspend fun getReportOutbox(outboxId: String): ReportOutboxEntity?

    @Upsert
    suspend fun upsertReportOutbox(reportOutbox: ReportOutboxEntity)

    @Query("DELETE FROM reportOutbox WHERE outboxId = :outboxId")
    suspend fun deleteReportOutbox(outboxId: String)

    @Query("DELETE FROM reportOutbox")
    suspend fun clearReportOutboxes()

    /**
     * Task 4.2 — 앱이 비정상 종료되어 `Submitting` 상태로 멈춰있던 outbox row를
     * 다시 `Pending`으로 되돌린다. 다음 submit trigger 시 정상 흐름을 탈 수 있게 한다.
     * 영향 row 수를 반환해 호출자가 로깅·관찰할 수 있도록 한다.
     */
    @Query("UPDATE reportOutbox SET status = 'Pending', updatedAt = :now WHERE status = 'Submitting'")
    suspend fun resetSubmittingOutboxesToPending(now: Long): Int
}
