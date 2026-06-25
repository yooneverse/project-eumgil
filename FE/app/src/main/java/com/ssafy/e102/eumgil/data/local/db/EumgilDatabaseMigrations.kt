package com.ssafy.e102.eumgil.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object EumgilDatabaseMigrations {
    val MIGRATION_1_2: Migration =
        object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE reportDraft ADD COLUMN locationSource TEXT")
                database.execSQL("ALTER TABLE reportDraft ADD COLUMN photoMimeType TEXT")
                database.execSQL("ALTER TABLE reportDraft ADD COLUMN photoSizeBytes INTEGER")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS reportOutbox (
                        outboxId TEXT NOT NULL PRIMARY KEY,
                        reportCategory TEXT NOT NULL,
                        description TEXT NOT NULL,
                        address TEXT,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        photoUri TEXT,
                        photoMimeType TEXT,
                        photoSizeBytes INTEGER,
                        status TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_reportOutbox_status ON reportOutbox(status)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_reportOutbox_updatedAt ON reportOutbox(updatedAt)")
            }
        }

    val MIGRATION_2_3: Migration =
        object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE favoriteRoute ADD COLUMN transportMode TEXT")
            }
        }

    val MIGRATION_3_4: Migration =
        object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE reportOutbox ADD COLUMN serverReportId INTEGER")
                database.execSQL("ALTER TABLE reportOutbox ADD COLUMN lastFailureReason TEXT")
            }
        }

    val MIGRATION_4_5: Migration =
        object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE bookmark ADD COLUMN serverBookmarkId INTEGER")
                database.execSQL("ALTER TABLE bookmark ADD COLUMN bookmarkTargetId TEXT")
                database.execSQL("ALTER TABLE bookmark ADD COLUMN targetType TEXT")
                database.execSQL("ALTER TABLE bookmark ADD COLUMN serverPlaceId INTEGER")
                database.execSQL("ALTER TABLE bookmark ADD COLUMN provider TEXT")
                database.execSQL("ALTER TABLE bookmark ADD COLUMN providerPlaceId TEXT")
                database.execSQL("ALTER TABLE bookmark ADD COLUMN providerCategory TEXT")
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_bookmark_bookmarkTargetId ON bookmark(bookmarkTargetId)",
                )
            }
        }

    val MIGRATION_5_6: Migration =
        object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE IF EXISTS bookmark")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS bookmark (
                        bookmarkId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        accountScopeKey TEXT NOT NULL,
                        placeId TEXT NOT NULL,
                        serverBookmarkId INTEGER,
                        bookmarkTargetId TEXT,
                        targetType TEXT,
                        serverPlaceId INTEGER,
                        provider TEXT,
                        providerPlaceId TEXT,
                        providerCategory TEXT,
                        placeName TEXT NOT NULL,
                        address TEXT,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        category TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_bookmark_accountScopeKey_placeId
                    ON bookmark(accountScopeKey, placeId)
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_bookmark_accountScopeKey_bookmarkTargetId
                    ON bookmark(accountScopeKey, bookmarkTargetId)
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_bookmark_accountScopeKey_updatedAt
                    ON bookmark(accountScopeKey, updatedAt)
                    """.trimIndent(),
                )

                database.execSQL("DROP TABLE IF EXISTS favoriteRoute")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS favoriteRoute (
                        accountScopeKey TEXT NOT NULL,
                        favoriteRouteId INTEGER NOT NULL,
                        routeName TEXT NOT NULL,
                        originName TEXT NOT NULL,
                        originPlaceId TEXT,
                        originLatitude REAL NOT NULL,
                        originLongitude REAL NOT NULL,
                        destinationName TEXT NOT NULL,
                        destinationPlaceId TEXT,
                        destinationLatitude REAL NOT NULL,
                        destinationLongitude REAL NOT NULL,
                        transportMode TEXT,
                        routeOption TEXT,
                        summaryDistanceMeters INTEGER,
                        summaryDurationSeconds INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(accountScopeKey, favoriteRouteId)
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_favoriteRoute_accountScopeKey_updatedAt
                    ON favoriteRoute(accountScopeKey, updatedAt)
                    """.trimIndent(),
                )
            }
        }

    val MIGRATION_6_7: Migration =
        object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE reportDraft ADD COLUMN photosJson TEXT")
            }
        }

    val MIGRATION_7_8: Migration =
        object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE reportDraft ADD COLUMN addressDetail TEXT")
                database.execSQL("ALTER TABLE reportOutbox ADD COLUMN addressDetail TEXT")
            }
        }

    val MIGRATION_8_9: Migration =
        object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE favoriteRoute ADD COLUMN routeSnapshotJson TEXT")
            }
        }

    /**
     * v10 — Task 5.5: 제보 outbox에 사진 업로드 흐름을 영속화.
     *
     * `photosJson` — 사용자가 첨부한 사진 local URI/메타데이터 목록(JSON). 제출 시점에 이 목록으로
     * presigned URL을 요청하고 S3에 업로드한다.
     * `imageObjectKeysJson` — 업로드 성공한 사진들의 S3 object key 목록(JSON). 부분 성공해도
     * 성공한 key는 보존되어 재시도 시 중복 업로드를 피할 수 있다.
     */
    val MIGRATION_9_10: Migration =
        object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE reportOutbox ADD COLUMN photosJson TEXT")
                database.execSQL("ALTER TABLE reportOutbox ADD COLUMN imageObjectKeysJson TEXT")
            }
        }

    val MIGRATION_10_11: Migration =
        object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE reportOutbox ADD COLUMN thumbnailObjectKeysJson TEXT")
            }
        }

    val all: Array<Migration> =
        arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11,
        )
}
