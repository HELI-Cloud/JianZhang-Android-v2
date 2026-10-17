package com.jianzhang.core.database

import androidx.room.ColumnInfo
import androidx.room.Embedded

data class TransactionRow(
    @Embedded val txn: TransactionEntity,
    @ColumnInfo(name = "category_name") val categoryName: String?,
    @ColumnInfo(name = "category_icon") val categoryIcon: String?,
    @ColumnInfo(name = "account_name") val accountName: String?,
)

data class CategorySum(
    @ColumnInfo(name = "category_id") val categoryId: Long?,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
)

data class MonthSum(
    @ColumnInfo(name = "income_cents") val incomeCents: Long,
    @ColumnInfo(name = "expense_cents") val expenseCents: Long,
)

data class TagSum(
    @ColumnInfo(name = "tag_id") val tagId: Long,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
)
data class AccountBalanceRow(
    @Embedded val account: AccountEntity,
    @ColumnInfo(name = "balance_cents") val balanceCents: Long,
)

data class DailySum(
    @ColumnInfo(name = "day") val day: String,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
)
data class MonthAggRow(
    @ColumnInfo(name = "month") val month: String,
    @ColumnInfo(name = "income_cents") val incomeCents: Long,
    @ColumnInfo(name = "expense_cents") val expenseCents: Long,
)

data class TagUsageRow(
    @Embedded val tag: TagEntity,
    @ColumnInfo(name = "usage_count") val usageCount: Int,
)

data class CsvRowRow(
    val id: Long,
    val date: String,
    val type: String,
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "account") val account: String,
    @ColumnInfo(name = "amount_cents") val amountCents: Long,
    val note: String,
)
