package com.jianzhang.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "categories",
    indices = [Index(value = ["type", "name"], unique = true)],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val name: String,
    val icon: String = "more-horizontal",
    val sort: Int = 0,
    val builtin: Boolean = false,
)

@Entity(
    tableName = "accounts",
    indices = [Index(value = ["name"], unique = true)],
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String = "OTHER",
    val icon: String = "wallet",
    val color: String = "#E9B44C",
    @ColumnInfo(name = "initial_cents") val initialCents: Long = 0,
    val sort: Int = 0,
    val archived: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: String = "",
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["date"]),
        Index(value = ["category_id"]),
        Index(value = ["account_id"]),
        Index(value = ["date", "account_id"]),
        Index(value = ["recurring_id", "date"], unique = true),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    @ColumnInfo(name = "category_id") val categoryId: Long? = null,
    @ColumnInfo(name = "account_id") val accountId: Long? = null,
    val note: String = "",
    val merchant: String = "",
    val date: String,
    @ColumnInfo(name = "created_at") val createdAt: String = "",
    @ColumnInfo(name = "recurring_id") val recurringId: Long? = null,
)

@Entity(
    tableName = "budgets",
    indices = [Index(value = ["month", "category_id"], unique = true)],
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val month: String,
    @ColumnInfo(name = "category_id") val categoryId: Long? = null,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
)

@Entity(
    tableName = "transfers",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["from_account_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["to_account_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["date"]),
        Index(value = ["from_account_id"]),
        Index(value = ["to_account_id"]),
    ],
)
data class TransferEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "from_account_id") val fromAccountId: Long,
    @ColumnInfo(name = "to_account_id") val toAccountId: Long,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    @ColumnInfo(name = "fee_cents") val feeCents: Long = 0,
    val note: String = "",
    val date: String,
    @ColumnInfo(name = "created_at") val createdAt: String = "",
)

@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)],
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: String = "#E9B44C",
    val sort: Int = 0,
)

@Entity(
    tableName = "transaction_tags",
    primaryKeys = ["transaction_id", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transaction_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["tag_id"])],
)
data class TransactionTagEntity(
    @ColumnInfo(name = "transaction_id") val transactionId: Long,
    @ColumnInfo(name = "tag_id") val tagId: Long,
)

@Entity(
    tableName = "recurring_rules",
    indices = [Index(value = ["next_date"])],
)
data class RecurringRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    @ColumnInfo(name = "category_id") val categoryId: Long? = null,
    @ColumnInfo(name = "account_id") val accountId: Long? = null,
    val note: String = "",
    val frequency: String,
    @ColumnInfo(name = "interval_count") val intervalCount: Int = 1,
    val weekday: Int? = null,
    @ColumnInfo(name = "day_of_month") val dayOfMonth: Int? = null,
    @ColumnInfo(name = "month_of_year") val monthOfYear: Int? = null,
    @ColumnInfo(name = "start_date") val startDate: String,
    @ColumnInfo(name = "end_date") val endDate: String? = null,
    @ColumnInfo(name = "next_date") val nextDate: String,
    @ColumnInfo(name = "auto_create") val autoCreate: Boolean = true,
    val enabled: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: String = "",
)

@Entity(tableName = "reminder_dismissals")
data class ReminderDismissalEntity(
    @PrimaryKey val key: String,
    @ColumnInfo(name = "dismissed_at") val dismissedAt: String,
)

@Entity(tableName = "app_meta")
data class AppMetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)