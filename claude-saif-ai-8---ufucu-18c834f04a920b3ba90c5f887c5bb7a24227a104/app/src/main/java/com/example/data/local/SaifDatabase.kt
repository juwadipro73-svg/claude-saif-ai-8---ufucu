/*
 * Copyright (c) SAIF AI. All rights reserved.
 * Original SAIF AI code. The app as distributed also contains third-party
 * GNU GPL v3 material - see LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ChatSessionEntity::class, ChatMessageEntity::class, UserAccountEntity::class],
    version = 5,
    exportSchema = false
)
abstract class SaifDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun userAccountDao(): UserAccountDao

    companion object {
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Keep existing chat data on disk, but do not expose it to an unverified
                // account. New sessions are explicitly owned by a Firebase UID.
                db.execSQL("ALTER TABLE chat_sessions ADD COLUMN ownerUid TEXT NOT NULL DEFAULT ''")
            }
        }

        @Volatile
        private var INSTANCE: SaifDatabase? = null

        fun getDatabase(context: Context): SaifDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SaifDatabase::class.java,
                    "saif_ai_database_v2"
                )
                    .addMigrations(MIGRATION_4_5)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
