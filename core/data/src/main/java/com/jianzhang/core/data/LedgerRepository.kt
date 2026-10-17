package com.jianzhang.core.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.room.withTransaction
import com.jianzhang.core.common.Dates
import com.jianzhang.core.database.AccountEntity
import com.jianzhang.core.database.AppDatabase
import com.jianzhang.core.database.AppMetaEntity
import com.jianzhang.core.database.BudgetEntity
import com.jianzhang.core.database.CategoryEntity
import com.jianzhang.core.database.RecurringRuleEntity
import com.jianzhang.core.database.TagEntity
import com.jianzhang.core.database.TransactionEntity
import com.jianzhang.core.database.TransactionTagEntity
import com.jianzhang.core.database.TransferEntity
import com.jianzhang.core.model.Account
import com.jianzhang.core.model.AccountBalance
import com.jianzhang.core.model.AccountKind
import com.jianzhang.core.model.BackupResult
import com.jianzhang.core.model.Budget
import com.jianzhang.core.model.Category
import com.jianzhang.core.model.CategoryTotal
import com.jianzhang.core.model.CsvRow
import com.jianzhang.core.model.DailyTotal
import com.jianzhang.core.model.MonthAggregate
import com.jianzhang.core.model.MonthSummary
import com.jianzhang.core.model.NetWorth
import com.jianzhang.core.model.RecurringRule
import com.jianzhang.core.model.Tag
import com.jianzhang.core.model.TagUsage
import com.jianzhang.core.model.Transfer
import com.jianzhang.core.model.Txn
import com.jianzhang.core.model.TxnType
import com.jianzhang.core.model.TxnWithCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

data class NewTransaction(
    val type: TxnType,
    val amountCents: Long,
    val categoryId: Long?,
    val accountId: Long?,
    val note: String = "",
    val merchant: String = "",
    val date: LocalDate = LocalDate.now(),
    val tagIds: List<Long> = emptyList(),
)

data class NewAccount(
    val name: String,
    val kind: AccountKind,
    val initialCents: Long,
    val color: String = "#E9B44C",
    val icon: String = "wallet",
)

data class NewTransfer(
    val fromAccountId: Long,
    val toAccountId: Long,
    val amountCents: Long,
    val feeCents: Long = 0,
    val note: String = "",
    val date: LocalDate = LocalDate.now(),
)

data class NewRecurring(
    val type: TxnType,
    val amountCents: Long,
    val categoryId: Long?,
    val accountId: Long?,
    val note: String,
    val frequency: com.jianzhang.core.model.RecurrenceFrequency,
    val intervalCount: Int,
    val weekday: Int?,
    val dayOfMonth: Int?,
    val monthOfYear: Int?,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val autoCreate: Boolean,
    val enabled: Boolean = true,
)

@Singleton
class LedgerRepository private constructor(
    private val provider: suspend () -> AppDatabase,
) {
    @Inject constructor(bootstrap: DatabaseBootstrap) : this({ bootstrap.await() })
    internal constructor(db: AppDatabase) : this({ db })

    private suspend fun database(): AppDatabase = provider()

    private fun <T> fromDb(block: suspend (AppDatabase) -> Flow<T>): Flow<T> = flow {
        emitAll(block(database()))
    }

    val categories: Flow<List<Category>> = fromDb { it.categoryDao().observeAll() }
        .map { list -> list.map { entity -> entity.toDomain() } }

    val accounts: Flow<List<Account>> = fromDb { it.accountDao().observeAll() }
        .map { list -> list.map { entity -> entity.toDomain() } }

    val activeAccounts: Flow<List<Account>> = fromDb { it.accountDao().observeActive() }
        .map { list -> list.map { entity -> entity.toDomain() } }

    val tags: Flow<List<Tag>> = fromDb { it.tagDao().observeAll() }
        .map { list -> list.map { entity -> entity.toDomain() } }

    val tagUsage: Flow<List<TagUsage>> = fromDb { it.tagDao().observeUsage() }
        .map { list -> list.map { TagUsage(it.tag.toDomain(), it.usageCount) } }

    val accountBalances: Flow<List<AccountBalance>> = fromDb { it.accountDao().observeBalances() }
        .map { rows -> rows.map { AccountBalance(it.account.toDomain(), it.balanceCents) } }

    val dismissedReminderKeys: Flow<Set<String>> = fromDb { it.reminderDao().observeAll() }
        .map { list -> list.map { it.key }.toSet() }

    val recurring: Flow<List<RecurringRule>> = fromDb { it.recurringDao().observeAll() }
        .map { list -> list.map { entity -> entity.toDomain() } }

    fun categories(type: TxnType): Flow<List<Category>> = categories.map { list -> list.filter { it.type == type } }

    fun observeTransactions(start: String, end: String): Flow<List<TxnWithCategory>> = fromDb {
        it.transactionDao().observeRows(start, end)
    }.map { list -> list.map { row -> row.toDomain() } }

    fun observeTransactions(month: YearMonth): Flow<List<TxnWithCategory>> {
        val (start, end) = Dates.monthRange(month)
        return observeTransactions(start, end)
    }

    fun observeFiltered(
        start: String,
        end: String,
        type: TxnType? = null,
        accountId: Long? = null,
        tagId: Long? = null,
        query: String = "",
        limit: Int = 5000,
        offset: Int = 0,
    ): Flow<List<TxnWithCategory>> = fromDb {
        it.transactionDao().observeFiltered(
            start = start,
            end = end,
            type = type?.name ?: "",
            accountId = accountId,
            tagId = tagId,
            query = query.trim(),
            limit = limit,
            offset = offset,
        )
    }.map { list -> list.map { row -> row.toDomain() } }

    fun filteredPager(
        start: String,
        end: String,
        type: TxnType? = null,
        accountId: Long? = null,
        tagId: Long? = null,
        query: String = "",
    ): Flow<PagingData<TxnWithCategory>> = flow {
        val db = database()
        emitAll(
            Pager(
                config = PagingConfig(
                    pageSize = 60,
                    initialLoadSize = 120,
                    prefetchDistance = 30,
                    enablePlaceholders = false,
                ),
            ) {
                db.transactionDao().pagingFiltered(
                    start = start,
                    end = end,
                    type = type?.name ?: "",
                    accountId = accountId,
                    tagId = tagId,
                    query = query.trim(),
                ).mapItems { row -> row.toDomain() }
            }.flow,
        )
    }

    fun observeFilteredSums(
        start: String,
        end: String,
        type: TxnType? = null,
        accountId: Long? = null,
        tagId: Long? = null,
        query: String = "",
    ): Flow<MonthSummary> = fromDb {
        it.transactionDao().observeFilteredSums(
            start = start,
            end = end,
            type = type?.name ?: "",
            accountId = accountId,
            tagId = tagId,
            query = query.trim(),
        )
    }.map { MonthSummary(YearMonth.from(Dates.parse(start)), it.incomeCents, it.expenseCents) }

    fun observeFilteredCount(
        start: String,
        end: String,
        type: TxnType? = null,
        accountId: Long? = null,
        tagId: Long? = null,
        query: String = "",
    ): Flow<Int> = fromDb {
        it.transactionDao().observeFilteredCount(
            start = start,
            end = end,
            type = type?.name ?: "",
            accountId = accountId,
            tagId = tagId,
            query = query.trim(),
        )
    }

    fun observeSums(month: YearMonth): Flow<MonthSummary> {
        val (start, end) = Dates.monthRange(month)
        return fromDb { it.transactionDao().observeSums(start, end) }
            .map { MonthSummary(month, it.incomeCents, it.expenseCents) }
    }

    fun observeRangeSums(start: String, end: String): Flow<MonthSummary> = fromDb {
        it.transactionDao().observeSums(start, end)
    }.map { MonthSummary(YearMonth.from(Dates.parse(start)), it.incomeCents, it.expenseCents) }

    fun observeMonthlyAggregates(start: String, end: String): Flow<List<MonthAggregate>> = fromDb {
        it.transactionDao().observeMonthlySums(start, end)
    }.map { rows ->
        rows.map {
            MonthAggregate(Dates.parseMonth(it.month), it.incomeCents, it.expenseCents)
        }
    }

    fun observeDailySums(month: YearMonth): Flow<List<DailyTotal>> = fromDb {
        val (start, end) = Dates.monthRange(month)
        it.transactionDao().observeDailySums(start, end)
    }.map { list -> list.map { DailyTotal(Dates.parse(it.day), it.amountCents) } }

    fun observeDailySums(start: String, end: String): Flow<List<DailyTotal>> = fromDb {
        it.transactionDao().observeDailySums(start, end)
    }.map { list -> list.map { DailyTotal(Dates.parse(it.day), it.amountCents) } }

    fun observeBudgets(month: YearMonth): Flow<List<Budget>> = fromDb {
        it.budgetDao().observeMonth(month.toString())
    }.map { list -> list.map { entity -> entity.toDomain() } }

    suspend fun budgets(month: YearMonth): List<Budget> =
        database().budgetDao().month(month.toString()).map { it.toDomain() }

    fun observeCategoryTotals(month: YearMonth, type: TxnType = TxnType.EXPENSE): Flow<List<CategoryTotal>> {
        val (start, end) = Dates.monthRange(month)
        return observeCategoryTotals(start, end, type)
    }

    fun observeCategoryTotals(
        start: String,
        end: String,
        type: TxnType = TxnType.EXPENSE,
    ): Flow<List<CategoryTotal>> = fromDb {
        it.transactionDao().observeCategorySums(start, end, type.name)
    }.map { list -> list.map { CategoryTotal(it.categoryId, it.name, it.amountCents) } }

    fun observeTagSummary(start: String, end: String, tagId: Long): Flow<MonthSummary> = fromDb {
        it.transactionDao().observeTagSummary(start, end, tagId)
    }.map { MonthSummary(YearMonth.from(Dates.parse(start)), it.incomeCents, it.expenseCents) }

    fun observeTagCategoryTotals(
        start: String,
        end: String,
        type: TxnType = TxnType.EXPENSE,
        tagId: Long,
    ): Flow<List<CategoryTotal>> = fromDb {
        it.transactionDao().observeTagCategorySums(start, end, type.name, tagId)
    }.map { list -> list.map { CategoryTotal(it.categoryId, it.name, it.amountCents) } }
    fun observeTagTotals(
        start: String,
        end: String,
        type: TxnType = TxnType.EXPENSE,
    ): Flow<List<CategoryTotal>> = fromDb {
        it.transactionDao().observeTagSums(start, end, type.name)
    }.map { list -> list.map { CategoryTotal(it.tagId, it.name, it.amountCents) } }

    fun observeNetWorth(): Flow<NetWorth> = accountBalances.map { list ->
        val assets = list.filter { it.balanceCents > 0 }.sumOf { it.balanceCents }
        val liabilities = list.filter { it.balanceCents < 0 }.sumOf { -it.balanceCents }
        NetWorth(assets, liabilities, assets - liabilities)
    }

    fun observeTransfers(month: YearMonth): Flow<List<Transfer>> {
        val (start, end) = Dates.monthRange(month)
        return observeTransfers(start, end)
    }

    fun observeTransfers(start: String, end: String): Flow<List<Transfer>> = fromDb {
        it.transferDao().observeRange(start, end)
    }.map { list -> list.map { entity -> entity.toDomain() } }

    suspend fun transaction(id: Long): Txn? = database().transactionDao().byId(id)?.toDomain()

    suspend fun tagIds(transactionId: Long): List<Long> = database().transactionTagDao().tagIds(transactionId)

    suspend fun saveTransaction(id: Long?, input: NewTransaction): Long {
        require(input.amountCents > 0) { "金额必须大于 0" }
        require(input.note.length <= 40) { "备注不能超过 40 字" }
        val db = database()
        return db.withTransaction {
            val old = id?.let { db.transactionDao().byId(it) }
            val accountId = input.accountId
                ?: db.accountDao().firstActiveId()
                ?: db.accountDao().firstId()
            val entity = TransactionEntity(
                id = id ?: 0,
                type = input.type.name,
                amountCents = input.amountCents,
                categoryId = input.categoryId,
                accountId = accountId,
                note = input.note.trim(),
                merchant = input.merchant.trim(),
                date = Dates.iso(input.date),
                createdAt = old?.createdAt?.takeIf { it.isNotBlank() } ?: Instant.now().toString(),
                recurringId = old?.recurringId,
            )
            val newId = if (id == null) db.transactionDao().insert(entity) else {
                db.transactionDao().update(entity)
                id
            }
            db.transactionTagDao().clearFor(newId)
            input.tagIds.distinct().forEach { db.transactionTagDao().insert(TransactionTagEntity(newId, it)) }
            newId
        }
    }

    suspend fun deleteTransactions(ids: Collection<Long>) {
        if (ids.isNotEmpty()) database().transactionDao().deleteByIds(ids.toList())
    }

    suspend fun saveCategory(id: Long?, type: TxnType, name: String, icon: String): Long {
        require(name.isNotBlank()) { "分类名称不能为空" }
        val db = database()
        val old = id?.let { db.categoryDao().byId(it) }
        val entity = CategoryEntity(
            id = id ?: 0,
            type = type.name,
            name = name.trim(),
            icon = icon,
            sort = old?.sort ?: ((db.categoryDao().all().maxOfOrNull { it.sort } ?: 0) + 1),
            builtin = old?.builtin ?: false,
        )
        return if (id == null) db.categoryDao().insert(entity) else {
            db.categoryDao().update(entity)
            id
        }
    }

    suspend fun deleteCategory(id: Long) {
        val db = database()
        db.withTransaction {
            db.transactionDao().clearCategory(id)
            db.categoryDao().byId(id)?.let { db.categoryDao().delete(it) }
        }
    }

    suspend fun saveBudget(month: YearMonth, categoryId: Long?, amountCents: Long) {
        val db = database()
        val existing = if (categoryId == null) db.budgetDao().total(month.toString())
        else db.budgetDao().category(month.toString(), categoryId)
        if (amountCents <= 0) {
            existing?.let { db.budgetDao().delete(it) }
        } else {
            db.budgetDao().upsert(
                BudgetEntity(
                    id = existing?.id ?: 0,
                    month = month.toString(),
                    categoryId = categoryId,
                    amountCents = amountCents,
                ),
            )
        }
    }

    suspend fun saveAccount(id: Long?, input: NewAccount): Long {
        require(input.name.isNotBlank()) { "账户名称不能为空" }
        val db = database()
        val old = id?.let { db.accountDao().byId(it) }
        val entity = AccountEntity(
            id = id ?: 0,
            name = input.name.trim(),
            kind = input.kind.name,
            icon = input.icon,
            color = input.color,
            initialCents = input.initialCents,
            sort = old?.sort ?: ((db.accountDao().maxSort() ?: -1) + 1),
            archived = old?.archived ?: false,
            createdAt = old?.createdAt?.takeIf { it.isNotBlank() } ?: Instant.now().toString(),
        )
        return if (id == null) db.accountDao().insert(entity) else {
            db.accountDao().update(entity)
            id
        }
    }

    suspend fun setAccountArchived(id: Long, archived: Boolean) {
        database().accountDao().setArchived(id, archived)
    }

    suspend fun deleteAccount(id: Long) {
        val db = database()
        val account = db.accountDao().byId(id) ?: return
        val fallback = db.accountDao().all().firstOrNull { it.id != id && !it.archived }
            ?: db.accountDao().all().firstOrNull { it.id != id }
        if (fallback == null) {
            throw IllegalStateException("至少保留一个账户")
        }
        db.withTransaction {
            db.accountDao().reassignTransactions(id, fallback.id)
            db.accountDao().reassignTransfersFrom(id, fallback.id)
            db.accountDao().reassignTransfersTo(id, fallback.id)
            db.accountDao().reassignRecurring(id, fallback.id)
            db.accountDao().delete(account)
        }
    }

    suspend fun saveTransfer(input: NewTransfer): Long = saveTransfer(null, input)

    suspend fun saveTransfer(id: Long?, input: NewTransfer): Long {
        require(input.fromAccountId != input.toAccountId) { "转出与转入账户不能相同" }
        require(input.amountCents > 0) { "转账金额必须大于 0" }
        require(input.feeCents >= 0) { "手续费不能为负数" }
        val db = database()
        val old = id?.let { db.transferDao().byId(it) }
        val entity = TransferEntity(
            id = id ?: 0,
            fromAccountId = input.fromAccountId,
            toAccountId = input.toAccountId,
            amountCents = input.amountCents,
            feeCents = input.feeCents,
            note = input.note.trim(),
            date = Dates.iso(input.date),
            createdAt = old?.createdAt?.takeIf { it.isNotBlank() } ?: Instant.now().toString(),
        )
        return if (id == null) db.transferDao().insert(entity) else {
            db.transferDao().update(entity)
            id
        }
    }

    suspend fun deleteTransfer(id: Long) {
        val db = database()
        db.transferDao().byId(id)?.let { db.transferDao().delete(it) }
    }

    suspend fun saveTag(id: Long?, name: String, color: String): Long {
        require(name.isNotBlank()) { "标签名称不能为空" }
        val db = database()
        val old = id?.let { db.tagDao().byId(it) }
        val entity = TagEntity(
            id = id ?: 0,
            name = name.trim(),
            color = color,
            sort = old?.sort ?: ((db.tagDao().maxSort() ?: -1) + 1),
        )
        return if (id == null) db.tagDao().insert(entity) else {
            db.tagDao().update(entity)
            id
        }
    }

    suspend fun deleteTag(id: Long) {
        val db = database()
        db.withTransaction {
            db.transactionTagDao().clearForTag(id)
            db.tagDao().byId(id)?.let { db.tagDao().delete(it) }
        }
    }

    suspend fun bulkSetCategory(ids: List<Long>, categoryId: Long?) {
        if (ids.isEmpty()) return
        database().transactionDao().bulkSetCategory(ids, categoryId)
    }

    suspend fun bulkSetAccount(ids: List<Long>, accountId: Long?) {
        if (ids.isEmpty()) return
        database().transactionDao().bulkSetAccount(ids, accountId)
    }

    suspend fun bulkAddTag(ids: List<Long>, tagId: Long) {
        if (ids.isEmpty()) return
        database().transactionTagDao().insertAll(ids.map { TransactionTagEntity(it, tagId) })
    }

    suspend fun saveRecurring(id: Long?, input: NewRecurring): Long {
        require(input.amountCents > 0) { "金额必须大于 0" }
        require(input.endDate == null || !input.endDate.isBefore(input.startDate)) {
            "结束日期不能早于起始日期"
        }
        val db = database()
        val interval = input.intervalCount.coerceAtLeast(1)
        val existing = id?.let { db.recurringDao().byId(it) }
        val scheduleUnchanged = existing != null &&
            Dates.normalizeFrequency(existing.frequency) == input.frequency.name &&
            existing.intervalCount == interval &&
            existing.startDate == Dates.iso(input.startDate)
        val next = when {
            existing == null -> input.startDate
            scheduleUnchanged -> Dates.parse(existing.nextDate)
            else -> Dates.firstOccurrenceOnOrAfter(
                anchor = input.startDate,
                target = Dates.today(),
                frequency = input.frequency.name,
                interval = interval,
            )
        }
        val expired = input.endDate != null && next.isAfter(input.endDate)
        val entity = RecurringRuleEntity(
            id = id ?: 0,
            type = input.type.name,
            amountCents = input.amountCents,
            categoryId = input.categoryId,
            accountId = input.accountId,
            note = input.note.trim(),
            frequency = input.frequency.name,
            intervalCount = interval,
            weekday = input.weekday,
            dayOfMonth = input.dayOfMonth,
            monthOfYear = input.monthOfYear,
            startDate = Dates.iso(input.startDate),
            endDate = input.endDate?.let(Dates::iso),
            nextDate = Dates.iso(next),
            autoCreate = input.autoCreate,
            enabled = input.enabled && !expired,
            createdAt = existing?.createdAt?.takeIf { it.isNotBlank() } ?: Instant.now().toString(),
        )
        return if (id == null) db.recurringDao().upsert(entity) else {
            db.recurringDao().update(entity)
            id
        }
    }

    suspend fun setRecurringEnabled(rule: RecurringRule, enabled: Boolean) {
        database().recurringDao().setEnabled(rule.id, enabled)
    }

    suspend fun deleteRecurring(id: Long) {
        val db = database()
        db.recurringDao().byId(id)?.let { db.recurringDao().delete(it) }
    }

    suspend fun runRecurring(today: LocalDate = LocalDate.now(), autoOnly: Boolean = true): Int {
        val db = database()
        return db.withTransaction {
            var created = 0
            var guard = 0
            while (guard++ < 500) {
                val due = db.recurringDao().due(Dates.iso(today))
                val rule = due.firstOrNull { !autoOnly || it.autoCreate } ?: break
                val end = rule.endDate?.let(Dates::parse)
                if (end != null && Dates.parse(rule.nextDate).isAfter(end)) {
                    db.recurringDao().update(rule.copy(enabled = false))
                    continue
                }
                val next = Dates.nextAfter(
                    anchor = Dates.parse(rule.startDate),
                    after = Dates.parse(rule.nextDate),
                    frequency = rule.frequency,
                    interval = rule.intervalCount,
                )
                val expired = end != null && next.isAfter(end)
                val inserted = db.transactionDao().insertIgnore(
                    TransactionEntity(
                        type = rule.type,
                        amountCents = rule.amountCents,
                        categoryId = rule.categoryId,
                        accountId = rule.accountId,
                        note = rule.note,
                        date = rule.nextDate,
                        createdAt = Instant.now().toString(),
                        recurringId = rule.id,
                    ),
                )
                if (inserted != -1L) created++
                db.recurringDao().update(
                    rule.copy(
                        nextDate = Dates.iso(next),
                        enabled = if (expired) false else rule.enabled,
                    ),
                )
            }
            created
        }
    }

    suspend fun confirmRecurring(ruleId: Long): Long {
        val db = database()
        val rule = db.recurringDao().byId(ruleId) ?: throw IllegalArgumentException("周期规则不存在")
        val end = rule.endDate?.let(Dates::parse)
        if (end != null && Dates.parse(rule.nextDate).isAfter(end)) {
            db.recurringDao().update(rule.copy(enabled = false))
            throw IllegalStateException("周期规则已结束，请检查结束日期")
        }
        val categoryId = rule.categoryId
            ?: db.categoryDao().findId(rule.type, "其他")
            ?: throw IllegalArgumentException("请先选择分类")
        val accountId = rule.accountId ?: db.accountDao().firstActiveId() ?: db.accountDao().firstId()
        val id = db.transactionDao().insertIgnore(
            TransactionEntity(
                type = rule.type,
                amountCents = rule.amountCents,
                categoryId = categoryId,
                accountId = accountId,
                note = rule.note,
                date = rule.nextDate,
                createdAt = Instant.now().toString(),
                recurringId = rule.id,
            ),
        )
        val next = Dates.nextAfter(
            anchor = Dates.parse(rule.startDate),
            after = Dates.parse(rule.nextDate),
            frequency = rule.frequency,
            interval = rule.intervalCount,
        )
        val expired = end != null && next.isAfter(end)
        db.recurringDao().update(
            rule.copy(
                nextDate = Dates.iso(next),
                enabled = if (expired) false else rule.enabled,
            ),
        )
        return id
    }

    suspend fun dismissedReminderKeys(): Set<String> = database().reminderDao().dismissedKeys().toSet()

    suspend fun dismissReminder(key: String) {
        database().reminderDao().upsert(
            com.jianzhang.core.database.ReminderDismissalEntity(key, Instant.now().toString()),
        )
    }

    suspend fun clearDismissedReminders() {
        database().reminderDao().clear()
    }

    suspend fun countTransactions(start: String, end: String): Int =
        database().transactionDao().countRange(start, end)

    suspend fun seedIfEmpty() {
        val db = database()
        if (db.categoryDao().count() == 0) {
            val expense = listOf(
                "餐饮" to "utensils", "交通" to "car", "购物" to "shopping-bag",
                "居住" to "home", "娱乐" to "gamepad-2", "医疗" to "heart-pulse",
                "教育" to "graduation-cap", "人情" to "gift", "其他" to "more-horizontal",
            )
            expense.forEachIndexed { index, (name, icon) ->
                db.categoryDao().insert(
                    CategoryEntity(type = "EXPENSE", name = name, icon = icon, sort = index + 1, builtin = true),
                )
            }
            val income = listOf(
                "工资" to "briefcase", "奖金" to "medal", "理财" to "trending-up",
                "兼职" to "laptop", "其他" to "more-horizontal",
            )
            income.forEachIndexed { index, (name, icon) ->
                db.categoryDao().insert(
                    CategoryEntity(type = "INCOME", name = name, icon = icon, sort = index + 1, builtin = true),
                )
            }
        }
        if (db.accountDao().count() == 0) {
            db.accountDao().insert(
                AccountEntity(name = "默认账户", kind = AccountKind.OTHER.name, icon = "wallet", color = "#E9B44C"),
            )
        }
        if (db.metaDao().get("seeded") != "1") db.metaDao().put(AppMetaEntity("seeded", "1"))
    }

    suspend fun resetAllData() {
        val db = database()
        db.withTransaction {
            db.transactionTagDao().clear()
            db.transactionDao().clear()
            db.transferDao().clear()
            db.recurringDao().clear()
            db.budgetDao().clear()
            db.reminderDao().clear()
            db.tagDao().clear()
            db.categoryDao().let { dao -> dao.all().forEach { dao.delete(it) } }
            db.accountDao().let { dao -> dao.all().forEach { dao.delete(it) } }
            db.metaDao().clear()
        }
        seedIfEmpty()
    }
    // ---------- 备份 / CSV ----------

    suspend fun csvRows(): List<CsvRow> = database().transactionDao().csvRows().map { row ->
        row.toDomain()
    }

    suspend fun csvRows(start: String, end: String): List<CsvRow> =
        database().transactionDao().csvRowsRange(start, end).map { row -> row.toDomain() }

    private fun com.jianzhang.core.database.CsvRowRow.toDomain(): CsvRow = CsvRow(
        id = id,
        date = date,
        type = type,
        category = category,
        account = account,
        amountCents = amountCents,
        note = note,
    )

    suspend fun buildCsv(): String {
        val sb = StringBuilder()
        sb.append('\uFEFF')
        sb.append("日期,类型,分类,账户,金额(元),备注\r\n")
        csvRows().forEach { row ->
            sb.append(csvCell(row.date)).append(',')
                .append(if (row.type == TxnType.INCOME.name) "收入" else "支出").append(',')
                .append(csvCell(row.category)).append(',')
                .append(csvCell(row.account)).append(',')
                .append(row.amountCents / 100).append('.')
                .append((row.amountCents % 100).toString().padStart(2, '0')).append(',')
                .append(csvCell(row.note)).append("\r\n")
        }
        return sb.toString()
    }

    private fun csvCell(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    suspend fun buildBackupJson(): String {
        val db = database()
        val root = JSONObject()
        root.put("format", "jianzhang-backup")
        root.put("version", 2)
        root.put("exportedAt", Instant.now().toString())

        root.put("categories", JSONArray().apply {
            db.categoryDao().all().forEach { item ->
                put(JSONObject().apply {
                    put("id", item.id)
                    put("type", item.type.lowercase())
                    put("name", item.name)
                    put("icon", item.icon)
                    put("sort", item.sort)
                    put("builtin", item.builtin)
                })
            }
        })
        root.put("accounts", JSONArray().apply {
            db.accountDao().all().forEach { item ->
                put(JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("kind", item.kind)
                    put("icon", item.icon)
                    put("color", item.color)
                    put("initial_cents", item.initialCents)
                    put("sort", item.sort)
                    put("archived", item.archived)
                    put("created_at", item.createdAt)
                })
            }
        })
        root.put("transactions", JSONArray().apply {
            db.transactionDao().all().forEach { item ->
                put(JSONObject().apply {
                    put("id", item.id)
                    put("type", item.type.lowercase())
                    put("amount_cents", item.amountCents)
                    put("category_id", item.categoryId ?: JSONObject.NULL)
                    put("account_id", item.accountId ?: JSONObject.NULL)
                    put("note", item.note)
                    put("merchant", item.merchant)
                    put("date", item.date)
                    put("created_at", item.createdAt)
                    put("recurring_id", item.recurringId ?: JSONObject.NULL)
                })
            }
        })
        root.put("budgets", JSONArray().apply {
            db.budgetDao().all().forEach { item ->
                put(JSONObject().apply {
                    put("id", item.id)
                    put("month", item.month)
                    put("category_id", item.categoryId ?: JSONObject.NULL)
                    put("amount_cents", item.amountCents)
                })
            }
        })
        root.put("transfers", JSONArray().apply {
            db.transferDao().all().forEach { item ->
                put(JSONObject().apply {
                    put("id", item.id)
                    put("from_account_id", item.fromAccountId)
                    put("to_account_id", item.toAccountId)
                    put("amount_cents", item.amountCents)
                    put("fee_cents", item.feeCents)
                    put("note", item.note)
                    put("date", item.date)
                    put("created_at", item.createdAt)
                })
            }
        })
        root.put("tags", JSONArray().apply {
            db.tagDao().all().forEach { item ->
                put(JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("color", item.color)
                    put("sort", item.sort)
                })
            }
        })
        root.put("transactionTags", JSONArray().apply {
            db.transactionTagDao().all().forEach { link ->
                put(JSONObject().apply {
                    put("transaction_id", link.transactionId)
                    put("tag_id", link.tagId)
                })
            }
        })
        root.put("recurringRules", JSONArray().apply {
            db.recurringDao().all().forEach { item ->
                put(JSONObject().apply {
                    put("id", item.id)
                    put("type", item.type.lowercase())
                    put("amount_cents", item.amountCents)
                    put("category_id", item.categoryId ?: JSONObject.NULL)
                    put("account_id", item.accountId ?: JSONObject.NULL)
                    put("note", item.note)
                    put("frequency", item.frequency)
                    put("interval_count", item.intervalCount)
                    put("weekday", item.weekday ?: JSONObject.NULL)
                    put("day_of_month", item.dayOfMonth ?: JSONObject.NULL)
                    put("month_of_year", item.monthOfYear ?: JSONObject.NULL)
                    put("start_date", item.startDate)
                    put("end_date", item.endDate ?: JSONObject.NULL)
                    put("next_date", item.nextDate)
                    put("auto_create", item.autoCreate)
                    put("enabled", item.enabled)
                    put("created_at", item.createdAt)
                })
            }
        })
        return root.toString(2)
    }

    suspend fun buildEncryptedBackup(password: String): String =
        BackupCrypto.encrypt(buildBackupJson(), password)

    suspend fun importBackup(
        raw: String,
        replace: Boolean,
        password: String? = null,
    ): BackupResult {
        val envelope = runCatching { JSONObject(raw) }.getOrElse {
            throw IllegalArgumentException("无法解析备份文件")
        }
        if (envelope.optString("format") != "jianzhang-backup") {
            throw IllegalArgumentException("文件格式不正确，请选择由简账导出的备份文件")
        }
        val version = envelope.optInt("version", 0)
        val root = when {
            version == 1 || version == 2 -> envelope
            version == BackupCrypto.VERSION -> {
                if (!BackupCrypto.isEncrypted(envelope)) {
                    throw IllegalArgumentException("加密备份文件结构无效")
                }
                if (password.isNullOrEmpty()) throw BackupPasswordRequiredException()
                val plaintext = BackupCrypto.decrypt(envelope.toString(), password)
                runCatching { JSONObject(plaintext) }.getOrElse {
                    throw IllegalArgumentException("备份文件解密后内容无效")
                }
            }
            else -> throw IllegalArgumentException("不支持的备份版本：$version")
        }
        if (root.optString("format") != "jianzhang-backup" || root.optInt("version", 0) !in 1..2) {
            throw IllegalArgumentException("备份文件的内部版本无效")
        }
        val db = database()
        val categories = root.optJSONArray("categories") ?: JSONArray()
        val accounts = root.optJSONArray("accounts") ?: JSONArray()
        val transactions = root.optJSONArray("transactions") ?: JSONArray()
        val budgets = root.optJSONArray("budgets") ?: JSONArray()
        val transfers = root.optJSONArray("transfers") ?: JSONArray()
        val tags = root.optJSONArray("tags") ?: JSONArray()
        val links = root.optJSONArray("transactionTags") ?: JSONArray()
        val recurring = root.optJSONArray("recurringRules") ?: JSONArray()
        val now = Instant.now().toString()

        if (replace) {
            val rawCategories = categories.length()
            if (rawCategories > 0) {
                val valid = (0 until rawCategories).count { i ->
                    val obj = categories.optJSONObject(i) ?: return@count false
                    obj.optLong("id", 0L) > 0L &&
                        obj.optString("name").isNotBlank() &&
                        normalizeType(obj.optString("type", "expense")) != null
                }
                if (valid == 0) throw IllegalArgumentException("备份中的分类数据全部无效，已取消导入")
            }
            val rawAccounts = accounts.length()
            if (rawAccounts > 0) {
                val valid = (0 until rawAccounts).count { i ->
                    val obj = accounts.optJSONObject(i) ?: return@count false
                    obj.optLong("id", 0L) > 0L && obj.optString("name").isNotBlank()
                }
                if (valid == 0) throw IllegalArgumentException("备份中的账户数据全部无效，已取消导入")
            }
            val rawTransactions = transactions.length()
            if (rawTransactions > 0) {
                val valid = (0 until rawTransactions).count { i ->
                    val obj = transactions.optJSONObject(i) ?: return@count false
                    obj.optLong("amount_cents", 0L) > 0L &&
                        runCatching { LocalDate.parse(obj.optString("date")) }.isSuccess
                }
                if (valid == 0) throw IllegalArgumentException("备份中的流水数据全部无效，已取消导入")
            }
        }

        var txCount = 0
        var catCount = 0
        var accountCount = 0
        var budgetCount = 0
        var transferCount = 0
        var tagCount = 0
        var ruleCount = 0
        var skipped = 0

        db.withTransaction {
            if (replace) {
                db.transactionTagDao().clear()
                db.transferDao().clear()
                db.recurringDao().clear()
                db.reminderDao().clear()
                db.transactionDao().clear()
                db.budgetDao().clear()
                db.tagDao().clear()
                db.accountDao().clear()
                db.categoryDao().all().forEach { db.categoryDao().delete(it) }

                val accountIds = mutableSetOf<Long>()
                for (i in 0 until accounts.length()) {
                    val obj = accounts.optJSONObject(i) ?: continue
                    val id = obj.optLong("id", 0L)
                    if (id <= 0L || obj.optString("name").isBlank()) continue
                    db.accountDao().insert(
                        AccountEntity(
                            id = id,
                            name = obj.optString("name").trim(),
                            kind = normalizeAccountKind(obj.optString("kind", "OTHER")),
                            icon = obj.optString("icon", "wallet"),
                            color = obj.optString("color", "#E9B44C"),
                            initialCents = obj.optLong("initial_cents", 0L),
                            sort = obj.optInt("sort", i),
                            archived = obj.optBoolean("archived", false),
                            createdAt = obj.optString("created_at", now),
                        ),
                    )
                    accountIds += id
                    accountCount++
                }
                if (accountIds.isEmpty()) {
                    accountIds += db.accountDao().insert(
                        AccountEntity(name = "默认账户", kind = AccountKind.OTHER.name, createdAt = now),
                    )
                    accountCount = 1
                }

                val catIds = mutableSetOf<Long>()
                for (i in 0 until categories.length()) {
                    val obj = categories.optJSONObject(i) ?: continue
                    val id = obj.optLong("id", 0L)
                    val name = obj.optString("name").trim()
                    val type = normalizeType(obj.optString("type", "expense")) ?: continue
                    if (id <= 0L || name.isBlank()) continue
                    db.categoryDao().insert(
                        CategoryEntity(
                            id = id,
                            type = type,
                            name = name,
                            icon = obj.optString("icon", "more-horizontal"),
                            sort = obj.optInt("sort", i),
                            builtin = obj.optBoolean("builtin", false),
                        ),
                    )
                    catIds += id
                    catCount++
                }

                val tagIds = mutableSetOf<Long>()
                for (i in 0 until tags.length()) {
                    val obj = tags.optJSONObject(i) ?: continue
                    val id = obj.optLong("id", 0L)
                    val name = obj.optString("name").trim()
                    if (id <= 0L || name.isBlank()) continue
                    db.tagDao().insert(
                        TagEntity(
                            id = id,
                            name = name,
                            color = obj.optString("color", "#E9B44C"),
                            sort = obj.optInt("sort", i),
                        ),
                    )
                    tagIds += id
                    tagCount++
                }

                for (i in 0 until transactions.length()) {
                    val obj = transactions.optJSONObject(i) ?: continue
                    // Replace mode clears the whole ledger first, so original ids can be kept.
                    // Preserving them is required for transactionTags references to stay valid.
                    val oldId = obj.optLong("id", 0L).takeIf { it > 0L }
                    val entity = transactionFromJson(obj, now, catIds, accountIds, oldId) ?: continue
                    db.transactionDao().insert(entity)
                    txCount++
                }

                for (i in 0 until links.length()) {
                    val obj = links.optJSONObject(i) ?: continue
                    val txId = jsonLong(obj, "transaction_id")
                    val tagId = jsonLong(obj, "tag_id")
                    if (txId != null && tagId != null && tagId in tagIds) {
                        runCatching { db.transactionTagDao().insert(TransactionTagEntity(txId, tagId)) }
                    }
                }

                for (i in 0 until transfers.length()) {
                    val obj = transfers.optJSONObject(i) ?: continue
                    val entity = transferFromJson(obj, now, accountIds, preserveId = true) ?: continue
                    db.transferDao().insert(entity)
                    transferCount++
                }

                for (i in 0 until recurring.length()) {
                    val obj = recurring.optJSONObject(i) ?: continue
                    val entity = recurringFromJson(obj, now, catIds, accountIds, preserveId = true) ?: continue
                    db.recurringDao().upsert(entity)
                    ruleCount++
                }

                for (i in 0 until budgets.length()) {
                    val obj = budgets.optJSONObject(i) ?: continue
                    val month = obj.optString("month")
                    val amount = obj.optLong("amount_cents", 0L)
                    if (Dates.parseMonthOrNull(month) == null || amount <= 0L) continue
                    val categoryId = jsonLong(obj, "category_id")?.takeIf { it in catIds }
                    db.budgetDao().upsert(
                        BudgetEntity(month = month, categoryId = categoryId, amountCents = amount),
                    )
                    budgetCount++
                }
            } else {
                val catMap = mutableMapOf<Long, Long>()
                for (i in 0 until categories.length()) {
                    val obj = categories.optJSONObject(i) ?: continue
                    val oldId = obj.optLong("id", 0L)
                    val name = obj.optString("name").trim()
                    val type = normalizeType(obj.optString("type", "expense")) ?: continue
                    if (name.isBlank()) continue
                    val existing = db.categoryDao().findId(type, name)
                    val newId = existing ?: db.categoryDao().insert(
                        CategoryEntity(
                            type = type,
                            name = name,
                            icon = obj.optString("icon", "more-horizontal"),
                            sort = obj.optInt("sort", i),
                            builtin = false,
                        ),
                    )
                    if (oldId > 0L) catMap[oldId] = newId
                    catCount++
                }

                val accountMap = mutableMapOf<Long, Long>()
                val existingAccounts = db.accountDao().all().associateBy { it.name.lowercase() }
                for (i in 0 until accounts.length()) {
                    val obj = accounts.optJSONObject(i) ?: continue
                    val oldId = obj.optLong("id", 0L)
                    val name = obj.optString("name").trim()
                    if (name.isBlank()) continue
                    val existing = existingAccounts[name.lowercase()]
                    val newId = existing?.id ?: db.accountDao().insert(
                        AccountEntity(
                            name = name,
                            kind = normalizeAccountKind(obj.optString("kind", "OTHER")),
                            icon = obj.optString("icon", "wallet"),
                            color = obj.optString("color", "#E9B44C"),
                            initialCents = obj.optLong("initial_cents", 0L),
                            sort = obj.optInt("sort", i),
                            archived = obj.optBoolean("archived", false),
                            createdAt = obj.optString("created_at", now),
                        ),
                    )
                    if (oldId > 0L) accountMap[oldId] = newId
                    accountCount++
                }
                val fallbackAccount = db.accountDao().firstActiveId() ?: db.accountDao().firstId()

                val tagMap = mutableMapOf<Long, Long>()
                for (i in 0 until tags.length()) {
                    val obj = tags.optJSONObject(i) ?: continue
                    val oldId = obj.optLong("id", 0L)
                    val name = obj.optString("name").trim()
                    if (name.isBlank()) continue
                    val existing = db.tagDao().findId(name)
                    val newId = existing ?: db.tagDao().insert(
                        TagEntity(
                            name = name,
                            color = obj.optString("color", "#E9B44C"),
                            sort = obj.optInt("sort", i),
                        ),
                    )
                    if (oldId > 0L) tagMap[oldId] = newId
                    tagCount++
                }

                val txMap = mutableMapOf<Long, Long>()
                for (i in 0 until transactions.length()) {
                    val obj = transactions.optJSONObject(i) ?: continue
                    val oldId = obj.optLong("id", 0L)
                    val entity = transactionFromJson(obj, now, catMap.keys, accountMap.keys, null) ?: continue
                    val mapped = entity.copy(
                        categoryId = entity.categoryId?.let { catMap[it] },
                        accountId = entity.accountId?.let { accountMap[it] } ?: fallbackAccount,
                    )
                    val existingId = db.transactionDao().findDuplicateId(
                        date = mapped.date,
                        type = mapped.type,
                        amount = mapped.amountCents,
                        note = mapped.note,
                        createdAt = mapped.createdAt,
                    )
                    val newId = existingId ?: db.transactionDao().insert(mapped)
                    if (oldId > 0L) txMap[oldId] = newId
                    if (existingId == null) txCount++ else skipped++
                }

                for (i in 0 until links.length()) {
                    val obj = links.optJSONObject(i) ?: continue
                    val oldTx = jsonLong(obj, "transaction_id") ?: continue
                    val oldTag = jsonLong(obj, "tag_id") ?: continue
                    val txId = txMap[oldTx] ?: continue
                    val tagId = tagMap[oldTag] ?: continue
                    db.transactionTagDao().insertAll(listOf(TransactionTagEntity(txId, tagId)))
                }

                for (i in 0 until transfers.length()) {
                    val obj = transfers.optJSONObject(i) ?: continue
                    val entity = transferFromJson(obj, now, accountMap.keys, preserveId = false) ?: continue
                    val mapped = entity.copy(
                        fromAccountId = accountMap[entity.fromAccountId] ?: continue,
                        toAccountId = accountMap[entity.toAccountId] ?: continue,
                    )
                    val existingId = db.transferDao().findDuplicateId(
                        date = mapped.date,
                        fromId = mapped.fromAccountId,
                        toId = mapped.toAccountId,
                        amount = mapped.amountCents,
                        fee = mapped.feeCents,
                        note = mapped.note,
                        createdAt = mapped.createdAt,
                    )
                    if (existingId == null) {
                        db.transferDao().insert(mapped)
                        transferCount++
                    } else {
                        skipped++
                    }
                }

                for (i in 0 until recurring.length()) {
                    val obj = recurring.optJSONObject(i) ?: continue
                    val entity = recurringFromJson(obj, now, catMap.keys, accountMap.keys, preserveId = false) ?: continue
                    val candidate = entity.copy(
                        categoryId = entity.categoryId?.let { catMap[it] },
                        accountId = entity.accountId?.let { accountMap[it] } ?: fallbackAccount,
                    )
                    val existingId = db.recurringDao().findDuplicateId(
                        type = candidate.type,
                        amount = candidate.amountCents,
                        note = candidate.note,
                        frequency = candidate.frequency,
                        interval = candidate.intervalCount,
                        start = candidate.startDate,
                        next = candidate.nextDate,
                        categoryId = candidate.categoryId,
                        accountId = candidate.accountId,
                    )
                    if (existingId == null) {
                        db.recurringDao().upsert(candidate)
                        ruleCount++
                    } else {
                        skipped++
                    }
                }

                for (i in 0 until budgets.length()) {
                    val obj = budgets.optJSONObject(i) ?: continue
                    val month = obj.optString("month")
                    val amount = obj.optLong("amount_cents", 0L)
                    if (Dates.parseMonthOrNull(month) == null || amount <= 0L) continue
                    val categoryId = jsonLong(obj, "category_id")?.let { catMap[it] }
                    db.budgetDao().upsert(
                        BudgetEntity(month = month, categoryId = categoryId, amountCents = amount),
                    )
                    budgetCount++
                }
            }
        }

        return BackupResult(
            ok = true,
            message = if (replace) "备份已替换当前账本" else "备份已合并到当前账本",
            categories = catCount,
            transactions = txCount,
            budgets = budgetCount,
            accounts = accountCount.coerceAtLeast(1),
            transfers = transferCount,
            tags = tagCount,
            recurringRules = ruleCount,
            skipped = skipped,
        )
    }

    private fun transactionFromJson(
        obj: JSONObject,
        now: String,
        validCategories: Set<Long>,
        validAccounts: Set<Long>,
        preserveId: Long?,
    ): TransactionEntity? {
        val type = normalizeType(obj.optString("type", "expense")) ?: return null
        val amount = obj.optLong("amount_cents", 0L)
        if (amount <= 0L) return null
        val date = obj.optString("date")
        if (runCatching { LocalDate.parse(date) }.isFailure) return null
        val categoryId = jsonLong(obj, "category_id")?.takeIf { it in validCategories }
        val accountId = jsonLong(obj, "account_id")?.takeIf { it in validAccounts }
        return TransactionEntity(
            id = preserveId ?: 0L,
            type = type,
            amountCents = amount,
            categoryId = categoryId,
            accountId = accountId,
            note = obj.optString("note"),
            merchant = obj.optString("merchant"),
            date = date,
            createdAt = obj.optString("created_at", now),
            recurringId = jsonLong(obj, "recurring_id"),
        )
    }

    private fun transferFromJson(
        obj: JSONObject,
        now: String,
        validAccounts: Set<Long>,
        preserveId: Boolean,
    ): TransferEntity? {
        val from = jsonLong(obj, "from_account_id") ?: return null
        val to = jsonLong(obj, "to_account_id") ?: return null
        if (from == to || from !in validAccounts || to !in validAccounts) return null
        val amount = obj.optLong("amount_cents", 0L)
        if (amount <= 0L) return null
        val date = obj.optString("date")
        if (runCatching { LocalDate.parse(date) }.isFailure) return null
        return TransferEntity(
            id = if (preserveId) obj.optLong("id", 0L) else 0L,
            fromAccountId = from,
            toAccountId = to,
            amountCents = amount,
            feeCents = obj.optLong("fee_cents", 0L),
            note = obj.optString("note"),
            date = date,
            createdAt = obj.optString("created_at", now),
        )
    }

    private fun recurringFromJson(
        obj: JSONObject,
        now: String,
        validCategories: Set<Long>,
        validAccounts: Set<Long>,
        preserveId: Boolean,
    ): RecurringRuleEntity? {
        val type = normalizeType(obj.optString("type", "expense")) ?: return null
        val amount = obj.optLong("amount_cents", 0L)
        if (amount <= 0L) return null
        val start = obj.optString("start_date")
        val next = obj.optString("next_date")
        if (runCatching { LocalDate.parse(start) }.isFailure) return null
        if (runCatching { LocalDate.parse(next) }.isFailure) return null
        val frequency = obj.optString("frequency", "MONTHLY").uppercase()
            .takeIf { it in setOf("DAILY", "WEEKLY", "MONTHLY", "YEARLY") } ?: "MONTHLY"
        return RecurringRuleEntity(
            id = if (preserveId) obj.optLong("id", 0L) else 0L,
            type = type,
            amountCents = amount,
            categoryId = jsonLong(obj, "category_id")?.takeIf { it in validCategories },
            accountId = jsonLong(obj, "account_id")?.takeIf { it in validAccounts },
            note = obj.optString("note"),
            frequency = frequency,
            intervalCount = obj.optInt("interval_count", 1).coerceAtLeast(1),
            weekday = jsonLong(obj, "weekday")?.toInt(),
            dayOfMonth = jsonLong(obj, "day_of_month")?.toInt(),
            monthOfYear = jsonLong(obj, "month_of_year")?.toInt(),
            startDate = start,
            endDate = obj.optString("end_date").takeIf { value ->
                value.isNotBlank() && runCatching { LocalDate.parse(value) }.isSuccess
            },
            nextDate = next,
            autoCreate = obj.optBoolean("auto_create", true),
            enabled = obj.optBoolean("enabled", true),
            createdAt = obj.optString("created_at", now),
        )
    }

    private fun normalizeType(value: String): String? = when (value.uppercase()) {
        "EXPENSE" -> TxnType.EXPENSE.name
        "INCOME" -> TxnType.INCOME.name
        else -> null
    }

    private fun normalizeAccountKind(value: String): String = when (value.uppercase()) {
        "CASH" -> AccountKind.CASH.name
        "BANK" -> AccountKind.BANK.name
        "CREDIT" -> AccountKind.CREDIT.name
        "ALIPAY" -> AccountKind.ALIPAY.name
        "WECHAT" -> AccountKind.WECHAT.name
        else -> AccountKind.OTHER.name
    }

    private fun jsonLong(obj: JSONObject, key: String): Long? {
        if (!obj.has(key) || obj.isNull(key)) return null
        return obj.optLong(key, 0L).takeIf { it > 0L }
    }
}


