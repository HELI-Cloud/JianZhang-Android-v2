package com.jianzhang.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        CategoryEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        AccountEntity::class,
        TransferEntity::class,
        TagEntity::class,
        TransactionTagEntity::class,
        RecurringRuleEntity::class,
        ReminderDismissalEntity::class,
        AppMetaEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun accountDao(): AccountDao
    abstract fun transferDao(): TransferDao
    abstract fun tagDao(): TagDao
    abstract fun transactionTagDao(): TransactionTagDao
    abstract fun recurringDao(): RecurringDao
    abstract fun reminderDao(): ReminderDao
    abstract fun metaDao(): MetaDao

    companion object {
        const val NAME = "jianzhang-v4.db"

        fun build(context: Context, passphrase: ByteArray): AppDatabase {
            loadSqlCipher()
            val factory = SupportOpenHelperFactory(passphrase)
            return Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .openHelperFactory(factory)
                .setJournalMode(JournalMode.TRUNCATE)
                .build()
        }

        private fun loadSqlCipher() {
            try {
                System.loadLibrary("sqlcipher")
            } catch (t: Throwable) {
                throw IllegalStateException("数据库加密组件加载失败，无法安全打开本地账本", t)
            }
        }
    }
}
