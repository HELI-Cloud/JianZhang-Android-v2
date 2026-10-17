package com.jianzhang.core.data

import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import com.jianzhang.core.database.AppDatabase
import com.jianzhang.core.model.AccountKind
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
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupRoundTripTest {
    private lateinit var sourceDb: AppDatabase
    private lateinit var targetDb: AppDatabase
    private lateinit var source: LedgerRepository
    private lateinit var target: LedgerRepository

    @Before
    fun setUp() = runBlocking {
        sourceDb = newDb()
        targetDb = newDb()
        source = LedgerRepository(sourceDb)
        target = LedgerRepository(targetDb)
        source.seedIfEmpty()
    }

    @After
    fun tearDown() {
        sourceDb.close()
        targetDb.close()
    }

    private fun newDb(): AppDatabase = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication(),
        AppDatabase::class.java,
    ).allowMainThreadQueries().build()

    @Test
    fun `v2 backup round trip preserves ledger and tag links`() = runBlocking {
        val expenseCategory = sourceDb.categoryDao().findId(TxnType.EXPENSE.name, "其他")!!
        val firstAccount = sourceDb.accountDao().firstActiveId()!!
        val secondAccount = source.saveAccount(
            id = null,
            input = NewAccount("银行卡", AccountKind.BANK, 250_000),
        )
        val tagId = source.saveTag(null, "测试标签", "#E9B44C")

        source.saveTransaction(
            id = null,
            input = NewTransaction(
                type = TxnType.EXPENSE,
                amountCents = 12_345,
                categoryId = expenseCategory,
                accountId = firstAccount,
                note = "午饭",
                merchant = "小馆",
                date = LocalDate.of(2026, 1, 5),
                tagIds = listOf(tagId),
            ),
        )
        source.saveBudget(YearMonth.of(2026, 1), null, 100_000)
        source.saveTransfer(
            NewTransfer(
                fromAccountId = firstAccount,
                toAccountId = secondAccount,
                amountCents = 5_000,
                feeCents = 100,
                note = "存钱",
                date = LocalDate.of(2026, 1, 6),
            ),
        )
        source.saveRecurring(
            id = null,
            input = NewRecurring(
                type = TxnType.EXPENSE,
                amountCents = 2_000,
                categoryId = expenseCategory,
                accountId = firstAccount,
                note = "订阅",
                frequency = RecurrenceFrequency.MONTHLY,
                intervalCount = 1,
                weekday = 1,
                dayOfMonth = 31,
                monthOfYear = 1,
                startDate = LocalDate.of(2026, 1, 31),
                endDate = null,
                autoCreate = true,
            ),
        )

        val json = source.buildBackupJson()
        assertThat(json).contains("\"version\": 2")

        val replaceResult = target.importBackup(json, replace = true)

        assertThat(replaceResult.ok).isTrue()
        assertThat(replaceResult.transactions).isEqualTo(1)
        assertThat(replaceResult.accounts).isEqualTo(2)
        assertThat(replaceResult.transfers).isEqualTo(1)
        assertThat(replaceResult.recurringRules).isEqualTo(1)
        assertThat(targetDb.transactionDao().count()).isEqualTo(1)
        assertThat(targetDb.transferDao().all()).hasSize(1)
        assertThat(targetDb.recurringDao().all()).hasSize(1)
        val importedTag = targetDb.tagDao().all().single()
        assertThat(targetDb.transactionTagDao().tagIds(targetDb.transactionDao().all().single().id))
            .containsExactly(importedTag.id)
        assertThat(targetDb.transactionDao().all().single().merchant).isEqualTo("小馆")

        val mergeResult = target.importBackup(json, replace = false)

        assertThat(mergeResult.ok).isTrue()
        assertThat(mergeResult.skipped).isAtLeast(1)
        assertThat(targetDb.transactionDao().count()).isEqualTo(1)
        assertThat(targetDb.transferDao().all()).hasSize(1)
        assertThat(targetDb.recurringDao().all()).hasSize(1)
        assertThat(targetDb.accountDao().count()).isEqualTo(2)
    }

    @Test
    fun `v3 encrypted backup round trip requires the correct password`() = runBlocking {
        target.seedIfEmpty()
        val encrypted = source.buildEncryptedBackup("a-strong-passphrase")

        val missingPassword = runCatching {
            target.importBackup(encrypted, replace = true)
        }
        assertThat(missingPassword.exceptionOrNull())
            .isInstanceOf(BackupPasswordRequiredException::class.java)

        val wrongPassword = runCatching {
            target.importBackup(encrypted, replace = true, password = "not-the-password")
        }
        assertThat(wrongPassword.isFailure).isTrue()

        val result = target.importBackup(
            encrypted,
            replace = true,
            password = "a-strong-passphrase",
        )

        assertThat(result.ok).isTrue()
        assertThat(result.categories).isAtLeast(1)
        assertThat(result.accounts).isAtLeast(1)
        assertThat(targetDb.categoryDao().all()).isNotEmpty()
        assertThat(targetDb.accountDao().count()).isAtLeast(1)
    }

    @Test
    fun `malformed backup is rejected before touching the ledger`() = runBlocking {
        target.seedIfEmpty()
        val before = targetDb.transactionDao().count()

        val result = runCatching {
            target.importBackup("""{"format":"jianzhang-backup","version":2,"accounts":[{"id":1,"name":"账户"}],"transactions":[{"type":"expense","amount_cents":100,"date":"2026-02-30"}]}""", replace = true)
        }

        assertThat(result.isFailure).isTrue()
        assertThat(targetDb.transactionDao().count()).isEqualTo(before)
    }

    @Test
    fun `merge mode keeps valid rows and skips malformed ones`() = runBlocking {
        target.seedIfEmpty()
        val category = targetDb.categoryDao().findId(TxnType.EXPENSE.name, "其他")!!
        val account = targetDb.accountDao().firstActiveId()!!
        val json = """
            {
              "format": "jianzhang-backup",
              "version": 2,
              "categories": [{"id": $category, "type": "expense", "name": "其他", "icon": "more"}],
              "accounts": [{"id": $account, "name": "现金"}],
              "transactions": [
                {"id": 1, "type": "expense", "amount_cents": 100, "category_id": $category, "account_id": $account, "note": "有效", "date": "2026-01-01", "created_at": "2026-01-01T00:00:00Z"},
                {"id": 2, "type": "expense", "amount_cents": 200, "category_id": $category, "account_id": $account, "note": "无效", "date": "2026-02-30", "created_at": "2026-01-01T00:00:00Z"}
              ]
            }
        """.trimIndent()

        val result = target.importBackup(json, replace = false)

        assertThat(result.transactions).isEqualTo(1)
        assertThat(targetDb.transactionDao().count()).isEqualTo(1)
    }
}
