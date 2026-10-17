package com.jianzhang.core.model

import java.time.LocalDate
import java.time.YearMonth

enum class TxnType { EXPENSE, INCOME }

enum class AccountKind(val label: String) {
    CASH("现金"),
    BANK("银行卡"),
    WECHAT("微信"),
    ALIPAY("支付宝"),
    CREDIT("信用卡"),
    INVESTMENT("投资"),
    OTHER("其他"),
}

enum class RecurrenceFrequency { DAILY, WEEKLY, MONTHLY, YEARLY }

enum class AiFocus(val label: String) {
    SPENDING("支出结构"),
    SAVING("节省建议"),
    TREND("趋势判断"),
    RISK("风险提醒"),
}

enum class AiRange(val label: String) {
    CURRENT_MONTH("本月"),
    LAST_3_MONTHS("近三月"),
    THIS_YEAR("本年"),
}

data class Category(
    val id: Long = 0,
    val type: TxnType,
    val name: String,
    val icon: String = "more-horizontal",
    val sort: Int = 0,
    val builtin: Boolean = false,
)

data class Txn(
    val id: Long = 0,
    val type: TxnType,
    val amountCents: Long,
    val categoryId: Long?,
    val accountId: Long?,
    val note: String = "",
    val merchant: String = "",
    val date: LocalDate,
    val createdAt: String = "",
    val recurringId: Long? = null,
)

data class Budget(
    val id: Long = 0,
    val month: YearMonth,
    val categoryId: Long?,
    val amountCents: Long,
)

data class Account(
    val id: Long = 0,
    val name: String,
    val kind: AccountKind = AccountKind.OTHER,
    val icon: String = "wallet",
    val color: String = "#E9B44C",
    val initialCents: Long = 0,
    val sort: Int = 0,
    val archived: Boolean = false,
    val createdAt: String = "",
)

data class Transfer(
    val id: Long = 0,
    val fromAccountId: Long,
    val toAccountId: Long,
    val amountCents: Long,
    val feeCents: Long = 0,
    val note: String = "",
    val date: LocalDate,
    val createdAt: String = "",
)

data class Tag(
    val id: Long = 0,
    val name: String,
    val color: String = "#E9B44C",
    val sort: Int = 0,
)

data class RecurringRule(
    val id: Long = 0,
    val type: TxnType,
    val amountCents: Long,
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val note: String = "",
    val frequency: RecurrenceFrequency,
    val intervalCount: Int = 1,
    val weekday: Int? = null,
    val dayOfMonth: Int? = null,
    val monthOfYear: Int? = null,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val nextDate: LocalDate,
    val autoCreate: Boolean = true,
    val enabled: Boolean = true,
    val createdAt: String = "",
)

data class ReminderDismissal(
    val key: String,
    val dismissedAt: String,
)

data class TxnWithCategory(
    val txn: Txn,
    val categoryName: String,
    val categoryIcon: String,
    val accountName: String?,
)

data class AccountBalance(
    val account: Account,
    val balanceCents: Long,
)

data class CategoryTotal(
    val categoryId: Long?,
    val name: String,
    val amountCents: Long,
)

data class MonthSummary(
    val month: YearMonth,
    val incomeCents: Long,
    val expenseCents: Long,
) {
    val netCents: Long get() = incomeCents - expenseCents
}

data class NetWorth(
    val assetsCents: Long,
    val liabilitiesCents: Long,
    val totalCents: Long,
)

data class AppSettings(
    val lockEnabled: Boolean = false,
    val lockHash: String = "",
    val lockSalt: String = "",
    val themeMode: String = "dark",
    val fontScale: Float = 1f,
    val fontFamily: String = "default",
    val defaultAccountId: Long? = null,
    val defaultType: TxnType = TxnType.EXPENSE,
    val rememberLastCategory: Boolean = true,
    val lastCategoryId: Long? = null,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0,
    val budgetAlert: Boolean = true,
    val billAlert: Boolean = true,
    val dailyReminder: Boolean = false,
    val weeklyReminder: Boolean = true,
    val weeklyReminderWeekday: Int = 0,
    val systemNotify: Boolean = true,
    val aiEndpoint: String = "https://api.deepseek.com/chat/completions",
    val aiApiKey: String = "",
    val aiModel: String = "deepseek-chat",
    val aiTemperature: Double = 0.35,
    val aiTimeoutSeconds: Int = 90,
    val showAmountSign: Boolean = true,
    val biometricEnabled: Boolean = false,
)

data class AiRequest(
    val focus: AiFocus,
    val range: AiRange,
    val prompt: String,
)
data class TagUsage(
    val tag: Tag,
    val usageCount: Int,
)

data class DailyTotal(
    val date: LocalDate,
    val amountCents: Long,
)

data class MonthAggregate(
    val month: YearMonth,
    val incomeCents: Long,
    val expenseCents: Long,
)

data class BackupResult(
    val ok: Boolean,
    val message: String,
    val categories: Int = 0,
    val transactions: Int = 0,
    val budgets: Int = 0,
    val accounts: Int = 0,
    val transfers: Int = 0,
    val tags: Int = 0,
    val recurringRules: Int = 0,
    val skipped: Int = 0,
)

enum class ReminderKind { BUDGET, BILL, DAILY, WEEKLY }
enum class ReminderLevel { INFO, WARN, DANGER }

data class ReminderItem(
    val key: String,
    val kind: ReminderKind,
    val title: String,
    val body: String,
    val level: ReminderLevel,
    val route: String,
    val ruleId: Long? = null,
    val confirmable: Boolean = false,
)

data class CsvRow(
    val id: Long,
    val date: String,
    val type: String,
    val category: String,
    val account: String,
    val amountCents: Long,
    val note: String,
)

