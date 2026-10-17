package com.jianzhang.core.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LegacyImporterTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("jianzhang.db")
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
        val file = context.getDatabasePath("jianzhang.db")
        listOf("", "-wal", "-shm").forEach { File(file.absolutePath + it).delete() }
        file.parentFile?.listFiles()
            ?.filter { it.name.startsWith("jianzhang.db.migrated-") }
            ?.forEach { it.delete() }
    }

    @Test
    fun `legacy v3 database is imported and reconciled without deleting the source`() = runBlocking {
        createLegacyDatabase()

        val result = LegacyImporter(db).importIfNeeded(context)

        assertThat(result.ok).isTrue()
        assertThat(result.imported).isTrue()
        assertThat(result.transactions).isEqualTo(2)
        assertThat(result.accounts).isEqualTo(2)
        assertThat(db.categoryDao().count()).isEqualTo(2)
        assertThat(result.budgets).isEqualTo(2)
        assertThat(result.transfers).isEqualTo(1)
        assertThat(result.tags).isEqualTo(1)
        assertThat(result.recurring).isEqualTo(1)

        val transactions = db.transactionDao().all()
        assertThat(transactions).hasSize(2)
        assertThat(transactions.first { it.note == "午饭" }.amountCents).isEqualTo(12_345)
        assertThat(transactions.first { it.note == "工资" }.date).isEqualTo("2026-01-10")
        assertThat(db.transactionTagDao().all()).containsExactly(TransactionTagEntity(1, 1))
        assertThat(db.budgetDao().all().sumOf { it.amountCents }).isEqualTo(130_000)
        assertThat(db.transferDao().all().single().feeCents).isEqualTo(100)

        val rules = db.recurringDao().all()
        assertThat(rules).hasSize(1)
        assertThat(rules.single().frequency).isEqualTo("MONTHLY")
        assertThat(rules.single().nextDate).isEqualTo("2026-01-31")

        assertThat(db.metaDao().get("legacy_v3_imported")).isEqualTo("1")
        assertThat(context.getDatabasePath("jianzhang.db").exists()).isFalse()
        val dir = context.getDatabasePath("jianzhang.db").parentFile!!
        assertThat(dir.listFiles()!!.any { it.name.startsWith("jianzhang.db.migrated-") }).isTrue()
    }

    private fun createLegacyDatabase() {
        val file = context.getDatabasePath("jianzhang.db")
        file.parentFile?.mkdirs()
        val entities = JSONObject(
            javaClass.classLoader!!.getResourceAsStream("legacy/3.json")!!
                .bufferedReader()
                .use { it.readText() },
        ).getJSONObject("database").getJSONArray("entities")

        SQLiteDatabase.openOrCreateDatabase(file, null).use { legacy ->
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                legacy.execSQL(
                    entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")),
                )
            }
            legacy.version = 3

            legacy.insert("categories", null, values("id" to 1, "type" to "EXPENSE", "name" to "餐饮", "icon" to "food", "sort" to 1, "builtin" to 1))
            legacy.insert("categories", null, values("id" to 2, "type" to "INCOME", "name" to "工资", "icon" to "pay", "sort" to 2, "builtin" to 1))

            legacy.insert("accounts", null, values("id" to 1, "name" to "现金", "kind" to "CASH", "icon" to "wallet", "color" to "#E9B44C", "initial_cents" to 100_000, "sort" to 1, "archived" to 0, "created_at" to "2026-01-01T00:00:00Z"))
            legacy.insert("accounts", null, values("id" to 2, "name" to "银行卡", "kind" to "BANK", "icon" to "bank", "color" to "#4FD1A5", "initial_cents" to 500_000, "sort" to 2, "archived" to 1, "created_at" to "2026-01-01T00:00:00Z"))

            legacy.insert("transactions", null, values("id" to 1, "type" to "EXPENSE", "amount_cents" to 12_345, "category_id" to 1, "account_id" to 1, "note" to "午饭", "date" to "2026-01-05", "created_at" to "2026-01-05T12:00:00Z", "recurring_id" to null))
            legacy.insert("transactions", null, values("id" to 2, "type" to "INCOME", "amount_cents" to 500_000, "category_id" to 2, "account_id" to 2, "note" to "工资", "date" to "2026-01-10", "created_at" to "2026-01-10T09:00:00Z", "recurring_id" to null))

            legacy.insert("budgets", null, values("id" to 1, "month" to "2026-01", "category_id" to null, "amount_cents" to 100_000))
            legacy.insert("budgets", null, values("id" to 2, "month" to "2026-01", "category_id" to 1, "amount_cents" to 30_000))

            legacy.insert("transfers", null, values("id" to 1, "from_account_id" to 1, "to_account_id" to 2, "amount_cents" to 5_000, "fee_cents" to 100, "note" to "存钱", "date" to "2026-01-06", "created_at" to "2026-01-06T08:00:00Z"))

            legacy.insert("tags", null, values("id" to 1, "name" to "出差", "color" to "#E9B44C", "sort" to 1))
            legacy.insert("transaction_tags", null, values("transaction_id" to 1, "tag_id" to 1))

            legacy.insert("recurring_rules", null, values(
                    "id" to 1, "type" to "EXPENSE", "amount_cents" to 2_000, "category_id" to 1,
                    "account_id" to 1, "note" to "订阅", "frequency" to "monthly", "interval_count" to 1,
                    "weekday" to null, "day_of_month" to 31, "month_of_year" to 1,
                    "start_date" to "2026-01-31", "end_date" to null, "next_date" to "2026-01-31",
                    "auto_create" to 1, "enabled" to 1, "created_at" to "2026-01-01T00:00:00Z",
                ),
            )
            legacy.insert("recurring_rules", null, values(
                    "id" to 2, "type" to "EXPENSE", "amount_cents" to 3_000, "category_id" to 1,
                    "account_id" to 1, "note" to "损坏结束日期", "frequency" to "MONTHLY", "interval_count" to 1,
                    "weekday" to null, "day_of_month" to 1, "month_of_year" to 1,
                    "start_date" to "2026-01-01", "end_date" to "2026-02-30", "next_date" to "2026-02-01",
                    "auto_create" to 1, "enabled" to 1, "created_at" to "2026-01-01T00:00:00Z",
                ),
            )

            legacy.insert("reminder_dismissals", null, values("key" to "daily:2026-01-01", "dismissed_at" to "2026-01-01T20:00:00Z"))
        }
    }

    private fun values(vararg pairs: Pair<String, Any?>): ContentValues =
        ContentValues().apply {
            pairs.forEach { (key, value) ->
                when (value) {
                    null -> putNull(key)
                    is Int -> put(key, value)
                    is Long -> put(key, value)
                    is Boolean -> put(key, value)
                    else -> put(key, value.toString())
                }
            }
        }
}