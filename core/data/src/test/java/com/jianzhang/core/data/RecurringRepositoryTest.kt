package com.jianzhang.core.data

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.jianzhang.core.common.Dates
import com.jianzhang.core.database.AccountEntity
import com.jianzhang.core.database.AppDatabase
import com.jianzhang.core.database.CategoryEntity
import com.jianzhang.core.model.RecurrenceFrequency
import com.jianzhang.core.model.TxnType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RecurringRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: LedgerRepository
    private var categoryId: Long = 0L
    private var accountId: Long = 0L

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = LedgerRepository(db)
        categoryId = db.categoryDao().insert(
            CategoryEntity(type = TxnType.EXPENSE.name, name = "其他"),
        )
        accountId = db.accountDao().insert(AccountEntity(name = "现金"))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `auto run never creates an occurrence after the end date`() = runBlocking {
        val id = repository.saveRecurring(
            id = null,
            input = rule(
                start = LocalDate.of(2026, 1, 31),
                end = LocalDate.of(2026, 2, 28),
            ),
        )

        val created = repository.runRecurring(today = LocalDate.of(2026, 3, 5), autoOnly = true)

        assertThat(created).isEqualTo(2)
        assertThat(db.transactionDao().count()).isEqualTo(2)
        val rule = db.recurringDao().byId(id)!!
        assertThat(Dates.parse(rule.nextDate)).isEqualTo(LocalDate.of(2026, 3, 31))
        assertThat(rule.enabled).isFalse()
    }

    @Test
    fun `confirm refuses an occurrence beyond the end date`() = runBlocking {
        val id = repository.saveRecurring(
            id = null,
            input = rule(
                start = LocalDate.of(2026, 1, 31),
                end = LocalDate.of(2026, 1, 31),
            ),
        )
        val saved = db.recurringDao().byId(id)!!
        db.recurringDao().update(saved.copy(nextDate = "2026-03-31"))

        val result = runCatching { repository.confirmRecurring(id) }

        assertThat(result.isFailure).isTrue()
        assertThat(db.transactionDao().count()).isEqualTo(0)
        assertThat(db.recurringDao().byId(id)!!.enabled).isFalse()
    }

    @Test
    fun `editing amount keeps the existing next occurrence`() = runBlocking {
        val id = repository.saveRecurring(
            id = null,
            input = rule(
                start = LocalDate.of(2026, 1, 31),
                end = null,
            ),
        )
        repository.runRecurring(today = LocalDate.of(2026, 1, 31), autoOnly = true)
        val afterFirst = db.recurringDao().byId(id)!!
        assertThat(Dates.parse(afterFirst.nextDate)).isEqualTo(LocalDate.of(2026, 2, 28))

        repository.saveRecurring(
            id = id,
            input = rule(
                start = LocalDate.of(2026, 1, 31),
                end = null,
            ).copy(amountCents = 20_000),
        )

        val edited = db.recurringDao().byId(id)!!
        assertThat(Dates.parse(edited.nextDate)).isEqualTo(LocalDate.of(2026, 2, 28))
        assertThat(edited.amountCents).isEqualTo(20_000)
    }

    @Test
    fun `end date before start date is rejected`() = runBlocking {
        val result = runCatching {
            repository.saveRecurring(
                id = null,
                input = rule(
                    start = LocalDate.of(2026, 3, 1),
                    end = LocalDate.of(2026, 2, 1),
                ),
            )
        }

        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(db.recurringDao().all()).isEmpty()
    }

    @Test
    fun `auto run is atomic when a later rule is malformed`() = runBlocking {
        val validId = repository.saveRecurring(
            id = null,
            input = rule(
                start = LocalDate.of(2026, 1, 1),
                end = null,
            ),
        )
        db.recurringDao().upsert(
            com.jianzhang.core.database.RecurringRuleEntity(
                type = TxnType.EXPENSE.name,
                amountCents = 5_000,
                categoryId = categoryId,
                accountId = accountId,
                note = "损坏规则",
                frequency = "MONTHLY",
                intervalCount = 1,
                weekday = 1,
                dayOfMonth = 1,
                monthOfYear = 1,
                startDate = "2026-02-01",
                nextDate = "2026-02-30",
                autoCreate = true,
                enabled = true,
                createdAt = "2026-01-01T00:00:00Z",
            ),
        )

        val result = runCatching {
            repository.runRecurring(today = LocalDate.of(2026, 3, 5), autoOnly = true)
        }

        assertThat(result.isFailure).isTrue()
        assertThat(db.transactionDao().count()).isEqualTo(0)
        assertThat(Dates.parse(db.recurringDao().byId(validId)!!.nextDate))
            .isEqualTo(LocalDate.of(2026, 1, 1))
    }

    @Test
    fun `auto run does not duplicate the same occurrence`() = runBlocking {
        val id = repository.saveRecurring(
            id = null,
            input = rule(
                start = LocalDate.of(2026, 1, 1),
                end = null,
            ),
        )

        assertThat(repository.runRecurring(today = LocalDate.of(2026, 1, 1), autoOnly = true)).isEqualTo(1)
        db.recurringDao().update(db.recurringDao().byId(id)!!.copy(nextDate = "2026-01-01"))
        assertThat(repository.runRecurring(today = LocalDate.of(2026, 1, 1), autoOnly = true)).isEqualTo(0)
        assertThat(db.transactionDao().count()).isEqualTo(1)
    }
    private fun rule(start: LocalDate, end: LocalDate?): NewRecurring = NewRecurring(
        type = TxnType.EXPENSE,
        amountCents = 10_000,
        categoryId = categoryId,
        accountId = accountId,
        note = "房租",
        frequency = RecurrenceFrequency.MONTHLY,
        intervalCount = 1,
        weekday = start.dayOfWeek.value,
        dayOfMonth = start.dayOfMonth,
        monthOfYear = start.monthValue,
        startDate = start,
        endDate = end,
        autoCreate = true,
        enabled = true,
    )
}
