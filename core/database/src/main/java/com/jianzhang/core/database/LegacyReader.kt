package com.jianzhang.core.database

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.io.File

/** Reads the legacy plaintext Room v3 database without opening it through Room. */
class LegacyReader(private val file: File) {
    data class Snapshot(
        val categories: List<CategoryEntity>,
        val accounts: List<AccountEntity>,
        val transactions: List<TransactionEntity>,
        val budgets: List<BudgetEntity>,
        val transfers: List<TransferEntity>,
        val tags: List<TagEntity>,
        val transactionTags: List<TransactionTagEntity>,
        val recurring: List<RecurringRuleEntity>,
        val dismissals: List<ReminderDismissalEntity>,
    )

    fun read(): Snapshot {
        require(file.isFile) { "旧数据库不存在" }
        val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        db.use { database ->
            return Snapshot(
                categories = query(database, "categories") { c ->
                    CategoryEntity(
                        id = c.long("id"),
                        type = (c.string("type") ?: "EXPENSE").uppercase(),
                        name = c.string("name") ?: "未分类",
                        icon = c.string("icon") ?: "more-horizontal",
                        sort = c.int("sort"),
                        builtin = c.bool("builtin"),
                    )
                },
                accounts = query(database, "accounts") { c ->
                    AccountEntity(
                        id = c.long("id"),
                        name = c.string("name") ?: "账户",
                        kind = c.string("kind") ?: "OTHER",
                        icon = c.string("icon") ?: "wallet",
                        color = c.string("color") ?: "#E9B44C",
                        initialCents = c.long("initial_cents"),
                        sort = c.int("sort"),
                        archived = c.bool("archived"),
                        createdAt = c.string("created_at") ?: "",
                    )
                },
                transactions = query(database, "transactions") { c ->
                    TransactionEntity(
                        id = c.long("id"),
                        type = (c.string("type") ?: "EXPENSE").uppercase(),
                        amountCents = c.long("amount_cents"),
                        categoryId = c.nullableLong("category_id"),
                        accountId = c.nullableLong("account_id"),
                        note = c.string("note") ?: "",
                        merchant = c.stringOrNull("merchant") ?: "",
                        date = c.string("date") ?: "",
                        createdAt = c.string("created_at") ?: "",
                        recurringId = c.nullableLong("recurring_id"),
                    )
                },
                budgets = query(database, "budgets") { c ->
                    BudgetEntity(
                        id = c.long("id"),
                        month = c.string("month") ?: "",
                        categoryId = c.nullableLong("category_id"),
                        amountCents = c.long("amount_cents"),
                    )
                },
                transfers = query(database, "transfers") { c ->
                    TransferEntity(
                        id = c.long("id"),
                        fromAccountId = c.long("from_account_id"),
                        toAccountId = c.long("to_account_id"),
                        amountCents = c.long("amount_cents"),
                        feeCents = c.long("fee_cents"),
                        note = c.string("note") ?: "",
                        date = c.string("date") ?: "",
                        createdAt = c.string("created_at") ?: "",
                    )
                },
                tags = query(database, "tags") { c ->
                    TagEntity(
                        id = c.long("id"),
                        name = c.string("name") ?: "标签",
                        color = c.string("color") ?: "#E9B44C",
                        sort = c.int("sort"),
                    )
                },
                transactionTags = query(database, "transaction_tags") { c ->
                    TransactionTagEntity(
                        transactionId = c.long("transaction_id"),
                        tagId = c.long("tag_id"),
                    )
                },
                recurring = query(database, "recurring_rules") { c ->
                    RecurringRuleEntity(
                        id = c.long("id"),
                        type = (c.string("type") ?: "EXPENSE").uppercase(),
                        amountCents = c.long("amount_cents"),
                        categoryId = c.nullableLong("category_id"),
                        accountId = c.nullableLong("account_id"),
                        note = c.string("note") ?: "",
                        frequency = c.string("frequency") ?: "MONTHLY",
                        intervalCount = c.int("interval_count").coerceAtLeast(1),
                        weekday = c.nullableInt("weekday"),
                        dayOfMonth = c.nullableInt("day_of_month"),
                        monthOfYear = c.nullableInt("month_of_year"),
                        startDate = c.string("start_date") ?: "",
                        endDate = c.stringOrNull("end_date"),
                        nextDate = c.string("next_date") ?: "",
                        autoCreate = c.bool("auto_create"),
                        enabled = c.bool("enabled"),
                        createdAt = c.string("created_at") ?: "",
                    )
                },
                dismissals = query(database, "reminder_dismissals") { c ->
                    ReminderDismissalEntity(
                        key = c.string("key") ?: "",
                        dismissedAt = c.string("dismissed_at") ?: "",
                    )
                },
            )
        }
    }

    private fun <T> query(db: SQLiteDatabase, table: String, map: (Row) -> T): List<T> {
        if (!tableExists(db, table)) return emptyList()
        val result = mutableListOf<T>()
        db.rawQuery("SELECT * FROM `$table`", null).use { c ->
            val row = Row(c)
            while (c.moveToNext()) result += map(row)
        }
        return result
    }

    private fun tableExists(db: SQLiteDatabase, table: String): Boolean =
        db.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1", arrayOf(table)).use { it.moveToFirst() }

    private class Row(private val c: android.database.Cursor) {
        fun string(name: String): String? = c.getColumnIndex(name).takeIf { it >= 0 && !c.isNull(it) }?.let(c::getString)
        fun stringOrNull(name: String): String? = string(name)
        fun long(name: String): Long = c.getColumnIndex(name).takeIf { it >= 0 && !c.isNull(it) }?.let(c::getLong) ?: 0L
        fun nullableLong(name: String): Long? = c.getColumnIndex(name).takeIf { it >= 0 && !c.isNull(it) }?.let(c::getLong)
        fun int(name: String): Int = long(name).toInt()
        fun nullableInt(name: String): Int? = nullableLong(name)?.toInt()
        fun bool(name: String): Boolean = long(name) != 0L
    }
}