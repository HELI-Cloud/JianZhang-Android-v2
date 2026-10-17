package com.jianzhang.core.data

import com.jianzhang.core.common.Dates
import com.jianzhang.core.database.AccountEntity
import com.jianzhang.core.database.BudgetEntity
import com.jianzhang.core.database.CategoryEntity
import com.jianzhang.core.database.RecurringRuleEntity
import com.jianzhang.core.database.TagEntity
import com.jianzhang.core.database.TransactionEntity
import com.jianzhang.core.database.TransactionRow
import com.jianzhang.core.database.TransferEntity
import com.jianzhang.core.model.Account
import com.jianzhang.core.model.AccountKind
import com.jianzhang.core.model.Budget
import com.jianzhang.core.model.Category
import com.jianzhang.core.model.RecurrenceFrequency
import com.jianzhang.core.model.RecurringRule
import com.jianzhang.core.model.Tag
import com.jianzhang.core.model.Transfer
import com.jianzhang.core.model.Txn
import com.jianzhang.core.model.TxnType
import com.jianzhang.core.model.TxnWithCategory

fun CategoryEntity.toDomain() = Category(
    id = id,
    type = if (type.equals("income", true)) TxnType.INCOME else TxnType.EXPENSE,
    name = name,
    icon = icon,
    sort = sort,
    builtin = builtin,
)

fun AccountEntity.toDomain() = Account(
    id = id,
    name = name,
    kind = runCatching { AccountKind.valueOf(kind.uppercase()) }.getOrElse { AccountKind.OTHER },
    icon = icon,
    color = color,
    initialCents = initialCents,
    sort = sort,
    archived = archived,
    createdAt = createdAt,
)

fun TransactionEntity.toDomain() = Txn(
    id = id,
    type = if (type.equals("income", true)) TxnType.INCOME else TxnType.EXPENSE,
    amountCents = amountCents,
    categoryId = categoryId,
    accountId = accountId,
    note = note,
    merchant = merchant,
    date = Dates.parse(date),
    createdAt = createdAt,
    recurringId = recurringId,
)

fun TransactionRow.toDomain() = TxnWithCategory(
    txn = txn.toDomain(),
    categoryName = categoryName ?: "未分类",
    categoryIcon = categoryIcon ?: "more-horizontal",
    accountName = accountName,
)

fun BudgetEntity.toDomain() = Budget(
    id = id,
    month = Dates.parseMonth(month),
    categoryId = categoryId,
    amountCents = amountCents,
)

fun TransferEntity.toDomain() = Transfer(
    id = id,
    fromAccountId = fromAccountId,
    toAccountId = toAccountId,
    amountCents = amountCents,
    feeCents = feeCents,
    note = note,
    date = Dates.parse(date),
    createdAt = createdAt,
)

fun TagEntity.toDomain() = Tag(id = id, name = name, color = color, sort = sort)

fun RecurringRuleEntity.toDomain() = RecurringRule(
    id = id,
    type = if (type.equals("income", true)) TxnType.INCOME else TxnType.EXPENSE,
    amountCents = amountCents,
    categoryId = categoryId,
    accountId = accountId,
    note = note,
    frequency = runCatching { RecurrenceFrequency.valueOf(frequency.uppercase()) }.getOrElse { RecurrenceFrequency.MONTHLY },
    intervalCount = intervalCount,
    weekday = weekday,
    dayOfMonth = dayOfMonth,
    monthOfYear = monthOfYear,
    startDate = Dates.parse(startDate),
    endDate = endDate?.let(Dates::parse),
    nextDate = Dates.parse(nextDate),
    autoCreate = autoCreate,
    enabled = enabled,
    createdAt = createdAt,
)