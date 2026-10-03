package com.nana.workbench.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        TodoEntity::class,
        StudyCardEntity::class,
        StudyReviewLogEntity::class,
        MemoEntity::class,
        NumberTableEntity::class,
        NumberRecordEntity::class,
        SkyAccountEntity::class,
        SkyWishEntity::class,
        SkyHoldingEntity::class,
        SkyTravelingSpiritEntity::class,
        SkySeasonEntity::class,
        AppStateEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class NanaDatabase : RoomDatabase() {

    abstract fun todoDao(): TodoDao
    abstract fun studyDao(): StudyDao
    abstract fun memoDao(): MemoDao
    abstract fun numberDao(): NumberDao
    abstract fun skyDao(): SkyDao
    abstract fun appStateDao(): AppStateDao

    companion object {
        const val DB_NAME = "nana-workbench.db"

        @Volatile
        private var instance: NanaDatabase? = null

        fun get(context: Context): NanaDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NanaDatabase::class.java,
                    DB_NAME,
                ).build().also { instance = it }
            }
    }
}
