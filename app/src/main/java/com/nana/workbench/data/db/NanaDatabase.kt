package com.nana.workbench.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    version = 3,
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

        /**
         * 1 → 2：把旧的"优先级"换成四象限编码（1.1 改进第 3 条）。
         *
         * 只改**数据值**、不动表结构，所以老用户升级时一条数据都不会丢：
         *   旧 1（重要）→ 2（重要不紧急）
         *   旧 2（紧急）→ 3（重要且紧急）
         *   旧 0（普通）→ 0（都不）
         * 换算表和 Quadrant.kt 里的 legacyPriorityToQuadrantCode 保持一致（那里有测试）。
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "UPDATE todo_item SET priority = CASE priority " +
                        "WHEN 1 THEN 2 WHEN 2 THEN 3 ELSE 0 END",
                )
            }
        }

        /**
         * 2 → 3：学习任务加上"学科、同科顺序、有没有划掉"三个字段
         * （1.1 改进：今日复习改成"学科 + 任务"）。
         *
         * 只**加列**、不改也不删任何已有列，所以老数据（如果用户之前用过学习卡片）一条都不会丢。
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE study_card ADD COLUMN subject TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE study_card ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE study_card ADD COLUMN done INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile
        private var instance: NanaDatabase? = null

        fun get(context: Context): NanaDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NanaDatabase::class.java,
                    DB_NAME,
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
    }
}
