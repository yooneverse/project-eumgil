package com.ssafy.e102.eumgil.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ssafy.e102.eumgil.data.local.dao.AppSettingDao
import com.ssafy.e102.eumgil.data.local.dao.BookmarkDao
import com.ssafy.e102.eumgil.data.local.dao.FavoriteRouteDao
import com.ssafy.e102.eumgil.data.local.dao.ReportDraftDao
import com.ssafy.e102.eumgil.data.local.dao.ReportOutboxDao
import com.ssafy.e102.eumgil.data.local.entity.AppSettingEntity
import com.ssafy.e102.eumgil.data.local.entity.BookmarkEntity
import com.ssafy.e102.eumgil.data.local.entity.FavoriteRouteEntity
import com.ssafy.e102.eumgil.data.local.entity.ReportDraftEntity
import com.ssafy.e102.eumgil.data.local.entity.ReportOutboxEntity

@Database(
    entities = [
        BookmarkEntity::class,
        FavoriteRouteEntity::class,
        ReportDraftEntity::class,
        ReportOutboxEntity::class,
        AppSettingEntity::class,
    ],
    version = EumgilDatabase.DATABASE_VERSION,
    exportSchema = false,
)
abstract class EumgilDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao

    abstract fun favoriteRouteDao(): FavoriteRouteDao

    abstract fun reportDraftDao(): ReportDraftDao

    abstract fun reportOutboxDao(): ReportOutboxDao

    abstract fun appSettingDao(): AppSettingDao

    companion object {
        const val DATABASE_NAME = "eumgil.db"
        const val DATABASE_VERSION = 11

        @Volatile
        private var instance: EumgilDatabase? = null

        fun getInstance(context: Context): EumgilDatabase =
            instance
                ?: synchronized(this) {
                    instance ?: buildDatabase(context.applicationContext).also { database ->
                        instance = database
                    }
                }

        private fun buildDatabase(context: Context): EumgilDatabase =
            Room.databaseBuilder(context, EumgilDatabase::class.java, DATABASE_NAME)
                .addMigrations(*EumgilDatabaseMigrations.all)
                .build()
    }
}
