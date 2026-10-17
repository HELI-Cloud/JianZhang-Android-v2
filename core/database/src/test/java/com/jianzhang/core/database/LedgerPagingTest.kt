package com.jianzhang.core.database

import androidx.paging.PagingSource
import androidx.room.Room
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LedgerPagingTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `filtered paging source loads every page without a full-table query`() = runBlocking {
        repeat(130) { index ->
            db.transactionDao().insert(
                TransactionEntity(
                    type = if (index % 2 == 0) "EXPENSE" else "INCOME",
                    amountCents = 100L + index,
                    note = "row-$index",
                    date = "2026-10-%02d".format((index % 28) + 1),
                    createdAt = "2026-10-17T12:%02d:00Z".format(index % 60),
                ),
            )
        }

        val source = db.transactionDao().pagingFiltered(
            start = "2026-10-01",
            end = "2026-11-01",
            type = "",
            accountId = null,
            tagId = null,
            query = "",
        )
        val first = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 60, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page
        val second = source.load(
            PagingSource.LoadParams.Append(key = first.nextKey!!, loadSize = 60, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page
        val third = source.load(
            PagingSource.LoadParams.Append(key = second.nextKey!!, loadSize = 60, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page

        assertThat(first.data).hasSize(60)
        assertThat(second.data).hasSize(60)
        assertThat(third.data).hasSize(10)
        assertThat(third.nextKey).isNull()
        assertThat((first.data + second.data + third.data).map { it.txn.id }.toSet()).hasSize(130)
    }

    @Test
    fun `tag filter narrows page sums and count`() = runBlocking {
        val tagId = db.tagDao().insert(TagEntity(name = "出差"))
        val tagged = db.transactionDao().insert(
            TransactionEntity(type = "EXPENSE", amountCents = 1_500, note = "高铁", date = "2026-10-04"),
        )
        db.transactionDao().insert(
            TransactionEntity(type = "EXPENSE", amountCents = 900, note = "午餐", date = "2026-10-05"),
        )
        db.transactionTagDao().insert(TransactionTagEntity(transactionId = tagged, tagId = tagId))

        val source = db.transactionDao().pagingFiltered(
            start = "2026-10-01",
            end = "2026-11-01",
            type = "",
            accountId = null,
            tagId = tagId,
            query = "",
        )
        val page = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 60, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page
        val count = db.transactionDao().observeFilteredCount(
            start = "2026-10-01",
            end = "2026-11-01",
            type = "",
            accountId = null,
            tagId = tagId,
            query = "",
        ).first()
        val sums = db.transactionDao().observeFilteredSums(
            start = "2026-10-01",
            end = "2026-11-01",
            type = "",
            accountId = null,
            tagId = tagId,
            query = "",
        ).first()

        assertThat(page.data).hasSize(1)
        assertThat(page.data.single().txn.note).isEqualTo("高铁")
        assertThat(count).isEqualTo(1)
        assertThat(sums.expenseCents).isEqualTo(1_500)
        assertThat(sums.incomeCents).isEqualTo(0)
    }

    @Test
    fun `filtered summary count and search agree`() = runBlocking {
        db.transactionDao().insert(
            TransactionEntity(type = "EXPENSE", amountCents = 1_000, note = "coffee", date = "2026-10-01"),
        )
        db.transactionDao().insert(
            TransactionEntity(type = "INCOME", amountCents = 2_000, note = "salary", date = "2026-10-02"),
        )
        db.transactionDao().insert(
            TransactionEntity(type = "EXPENSE", amountCents = 3_000, note = "rent", date = "2026-10-03"),
        )

        val count = db.transactionDao().observeFilteredCount(
            start = "2026-10-01",
            end = "2026-11-01",
            type = "EXPENSE",
            accountId = null,
            tagId = null,
            query = "",
        ).first()
        val sums = db.transactionDao().observeFilteredSums(
            start = "2026-10-01",
            end = "2026-11-01",
            type = "",
            accountId = null,
            tagId = null,
            query = "",
        ).first()
        val searchCount = db.transactionDao().observeFilteredCount(
            start = "2026-10-01",
            end = "2026-11-01",
            type = "",
            accountId = null,
            tagId = null,
            query = "salary",
        ).first()

        assertThat(count).isEqualTo(2)
        assertThat(sums.expenseCents).isEqualTo(4_000)
        assertThat(sums.incomeCents).isEqualTo(2_000)
        assertThat(searchCount).isEqualTo(1)
    }
}
