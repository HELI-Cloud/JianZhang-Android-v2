package com.jianzhang.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY type, sort, id")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE type = :type ORDER BY sort, id")
    fun observeByType(type: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY type, sort, id")
    suspend fun all(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): CategoryEntity?

    @Query("SELECT id FROM categories WHERE type = :type AND name = :name LIMIT 1")
    suspend fun findId(type: String, name: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CategoryEntity): Long

    @Update
    suspend fun update(entity: CategoryEntity)

    @Delete
    suspend fun delete(entity: CategoryEntity)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int
}

@Dao
interface TransactionDao {
    @Query(
        """
        SELECT t.*, c.name AS category_name, c.icon AS category_icon, a.name AS account_name
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.category_id
        LEFT JOIN accounts a ON a.id = t.account_id
        WHERE t.date >= :start AND t.date < :end
        ORDER BY t.date DESC, t.created_at DESC, t.id DESC
        """,
    )
    fun observeRows(start: String, end: String): Flow<List<TransactionRow>>

    @Query(
        """
        SELECT t.*, c.name AS category_name, c.icon AS category_icon, a.name AS account_name
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.category_id
        LEFT JOIN accounts a ON a.id = t.account_id
        WHERE t.date >= :start AND t.date < :end
          AND (:type = '' OR t.type = :type)
          AND (:accountId IS NULL OR t.account_id = :accountId)
          AND (:tagId IS NULL OR EXISTS (
            SELECT 1 FROM transaction_tags tf
            WHERE tf.transaction_id = t.id AND tf.tag_id = :tagId
          ))
          AND (
            :query = ''
            OR t.note LIKE '%' || :query || '%'
            OR t.merchant LIKE '%' || :query || '%'
            OR c.name LIKE '%' || :query || '%'
            OR a.name LIKE '%' || :query || '%'
            OR EXISTS (
              SELECT 1 FROM transaction_tags tt
              JOIN tags g ON g.id = tt.tag_id
              WHERE tt.transaction_id = t.id AND g.name LIKE '%' || :query || '%'
            )
          )
        ORDER BY t.date DESC, t.created_at DESC, t.id DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    fun observeFiltered(
        start: String,
        end: String,
        type: String,
        accountId: Long?,
        tagId: Long?,
        query: String,
        limit: Int,
        offset: Int,
    ): Flow<List<TransactionRow>>

    @Query(
        """
        SELECT t.*, c.name AS category_name, c.icon AS category_icon, a.name AS account_name
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.category_id
        LEFT JOIN accounts a ON a.id = t.account_id
        WHERE t.date >= :start AND t.date < :end
          AND (:type = '' OR t.type = :type)
          AND (:accountId IS NULL OR t.account_id = :accountId)
          AND (:tagId IS NULL OR EXISTS (
            SELECT 1 FROM transaction_tags tf
            WHERE tf.transaction_id = t.id AND tf.tag_id = :tagId
          ))
          AND (
            :query = ''
            OR t.note LIKE '%' || :query || '%'
            OR t.merchant LIKE '%' || :query || '%'
            OR c.name LIKE '%' || :query || '%'
            OR a.name LIKE '%' || :query || '%'
            OR EXISTS (
              SELECT 1 FROM transaction_tags tt
              JOIN tags g ON g.id = tt.tag_id
              WHERE tt.transaction_id = t.id AND g.name LIKE '%' || :query || '%'
            )
          )
        ORDER BY t.date DESC, t.created_at DESC, t.id DESC
        """,
    )
    fun pagingFiltered(
        start: String,
        end: String,
        type: String,
        accountId: Long?,
        tagId: Long?,
        query: String,
    ): PagingSource<Int, TransactionRow>

    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount_cents ELSE 0 END), 0) AS income_cents,
               COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount_cents ELSE 0 END), 0) AS expense_cents
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.category_id
        LEFT JOIN accounts a ON a.id = t.account_id
        WHERE t.date >= :start AND t.date < :end
          AND (:type = '' OR t.type = :type)
          AND (:accountId IS NULL OR t.account_id = :accountId)
          AND (:tagId IS NULL OR EXISTS (
            SELECT 1 FROM transaction_tags tf
            WHERE tf.transaction_id = t.id AND tf.tag_id = :tagId
          ))
          AND (
            :query = ''
            OR t.note LIKE '%' || :query || '%'
            OR t.merchant LIKE '%' || :query || '%'
            OR c.name LIKE '%' || :query || '%'
            OR a.name LIKE '%' || :query || '%'
            OR EXISTS (
              SELECT 1 FROM transaction_tags tt
              JOIN tags g ON g.id = tt.tag_id
              WHERE tt.transaction_id = t.id AND g.name LIKE '%' || :query || '%'
            )
          )
        """,
    )
    fun observeFilteredSums(
        start: String,
        end: String,
        type: String,
        accountId: Long?,
        tagId: Long?,
        query: String,
    ): Flow<MonthSum>

    @Query(
        """
        SELECT COUNT(*)
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.category_id
        LEFT JOIN accounts a ON a.id = t.account_id
        WHERE t.date >= :start AND t.date < :end
          AND (:type = '' OR t.type = :type)
          AND (:accountId IS NULL OR t.account_id = :accountId)
          AND (:tagId IS NULL OR EXISTS (
            SELECT 1 FROM transaction_tags tf
            WHERE tf.transaction_id = t.id AND tf.tag_id = :tagId
          ))
          AND (
            :query = ''
            OR t.note LIKE '%' || :query || '%'
            OR t.merchant LIKE '%' || :query || '%'
            OR c.name LIKE '%' || :query || '%'
            OR a.name LIKE '%' || :query || '%'
            OR EXISTS (
              SELECT 1 FROM transaction_tags tt
              JOIN tags g ON g.id = tt.tag_id
              WHERE tt.transaction_id = t.id AND g.name LIKE '%' || :query || '%'
            )
          )
        """,
    )
    fun observeFilteredCount(
        start: String,
        end: String,
        type: String,
        accountId: Long?,
        tagId: Long?,
        query: String,
    ): Flow<Int>

    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount_cents ELSE 0 END), 0) AS income_cents,
               COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount_cents ELSE 0 END), 0) AS expense_cents
        FROM transactions WHERE date >= :start AND date < :end
        """,
    )
    fun observeSums(start: String, end: String): Flow<MonthSum>

    @Query(
        """
        SELECT strftime('%Y-%m', date) AS month,
               COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount_cents ELSE 0 END), 0) AS income_cents,
               COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount_cents ELSE 0 END), 0) AS expense_cents
        FROM transactions WHERE date >= :start AND date < :end
        GROUP BY strftime('%Y-%m', date) ORDER BY month
        """,
    )
    fun observeMonthlySums(start: String, end: String): Flow<List<MonthAggRow>>

    @Query(
        """
        SELECT date AS day, SUM(amount_cents) AS amount_cents
        FROM transactions
        WHERE type = 'EXPENSE' AND date >= :start AND date < :end
        GROUP BY date ORDER BY date
        """,
    )
    fun observeDailySums(start: String, end: String): Flow<List<DailySum>>

    @Query(
        """
        SELECT COALESCE(t.category_id, 0) AS category_id,
               COALESCE(c.name, '未分类') AS name,
               SUM(t.amount_cents) AS amount_cents
        FROM transactions t LEFT JOIN categories c ON c.id = t.category_id
        WHERE t.type = :type AND t.date >= :start AND t.date < :end
        GROUP BY t.category_id ORDER BY amount_cents DESC
        """,
    )
    fun observeCategorySums(start: String, end: String, type: String = "EXPENSE"): Flow<List<CategorySum>>

    @Query(
        """
        SELECT tg.id AS tag_id, tg.name AS name, SUM(t.amount_cents) AS amount_cents
        FROM transactions t
        JOIN transaction_tags tt ON tt.transaction_id = t.id
        JOIN tags tg ON tg.id = tt.tag_id
        WHERE t.type = :type AND t.date >= :start AND t.date < :end
        GROUP BY tg.id ORDER BY amount_cents DESC
        """,
    )
    fun observeTagSums(start: String, end: String, type: String = "EXPENSE"): Flow<List<TagSum>>

    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount_cents ELSE 0 END), 0) AS income_cents,
               COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount_cents ELSE 0 END), 0) AS expense_cents
        FROM transactions t
        WHERE t.date >= :start AND t.date < :end
          AND EXISTS (SELECT 1 FROM transaction_tags tt WHERE tt.transaction_id = t.id AND tt.tag_id = :tagId)
        """,
    )
    fun observeTagSummary(start: String, end: String, tagId: Long): Flow<MonthSum>

    @Query(
        """
        SELECT COALESCE(t.category_id, 0) AS category_id,
               COALESCE(c.name, '未分类') AS name,
               SUM(t.amount_cents) AS amount_cents
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.category_id
        WHERE t.type = :type AND t.date >= :start AND t.date < :end
          AND EXISTS (SELECT 1 FROM transaction_tags tt WHERE tt.transaction_id = t.id AND tt.tag_id = :tagId)
        GROUP BY t.category_id ORDER BY amount_cents DESC
        """,
    )
    fun observeTagCategorySums(start: String, end: String, type: String, tagId: Long): Flow<List<CategorySum>>
    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): TransactionEntity?

    @Insert
    suspend fun insert(entity: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: TransactionEntity): Long

    @Update
    suspend fun update(entity: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM transactions")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE date >= :start AND date < :end")
    suspend fun countRange(start: String, end: String): Int

    @Query(
        """
        SELECT id FROM transactions
        WHERE date = :date AND type = :type AND amount_cents = :amount
          AND note = :note AND created_at = :createdAt
        LIMIT 1
        """,
    )
    suspend fun findDuplicateId(
        date: String,
        type: String,
        amount: Long,
        note: String,
        createdAt: String,
    ): Long?

    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount_cents ELSE 0 END), 0) AS income_cents,
               COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount_cents ELSE 0 END), 0) AS expense_cents
        FROM transactions WHERE date >= :start AND date < :end
        """,
    )
    suspend fun sums(start: String, end: String): MonthSum

    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount_cents ELSE 0 END), 0) AS income_cents,
               COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount_cents ELSE 0 END), 0) AS expense_cents
        FROM transactions t
        WHERE t.date >= :start AND t.date < :end
          AND (:tagId IS NULL OR EXISTS (
              SELECT 1 FROM transaction_tags tt WHERE tt.transaction_id = t.id AND tt.tag_id = :tagId
          ))
        """,
    )
    suspend fun rangeSummary(start: String, end: String, tagId: Long?): MonthSum

    @Query("SELECT COALESCE(SUM(amount_cents), 0) FROM transactions")
    suspend fun totalAmount(): Long

    @Query("SELECT COALESCE(SUM(amount_cents), 0) FROM transactions WHERE type = :type")
    suspend fun totalByType(type: String): Long

    @Query("SELECT DISTINCT type FROM transactions WHERE id IN (:ids)")
    suspend fun typesForIds(ids: List<Long>): List<String>

    @Query("UPDATE transactions SET category_id = :categoryId WHERE id IN (:ids)")
    suspend fun bulkSetCategory(ids: List<Long>, categoryId: Long?)

    @Query("UPDATE transactions SET account_id = :accountId WHERE id IN (:ids)")
    suspend fun bulkSetAccount(ids: List<Long>, accountId: Long?)

    @Query("UPDATE transactions SET category_id = NULL WHERE category_id = :categoryId")
    suspend fun clearCategory(categoryId: Long)

    @Query(
        """
        SELECT t.id AS id, t.date AS date, t.type AS type,
               COALESCE(c.name, '未分类') AS category,
               COALESCE(a.name, '') AS account,
               t.amount_cents AS amount_cents, t.note AS note
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.category_id
        LEFT JOIN accounts a ON a.id = t.account_id
        ORDER BY t.date, t.id
        """,
    )
    suspend fun csvRows(): List<CsvRowRow>

    @Query("SELECT * FROM transactions ORDER BY date, id")
    suspend fun all(): List<TransactionEntity>
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE month = :month ORDER BY category_id IS NULL DESC, category_id")
    fun observeMonth(month: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets ORDER BY month, category_id")
    suspend fun all(): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE month = :month AND category_id IS NULL LIMIT 1")
    suspend fun total(month: String): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE month = :month AND category_id = :categoryId LIMIT 1")
    suspend fun category(month: String, categoryId: Long): BudgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BudgetEntity): Long

    @Delete
    suspend fun delete(entity: BudgetEntity)

    @Query("DELETE FROM budgets")
    suspend fun clear()
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY archived, sort, id")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE archived = 0 ORDER BY sort, id")
    fun observeActive(): Flow<List<AccountEntity>>

    @Query(
        """
        SELECT a.*,
          a.initial_cents
          + COALESCE((SELECT SUM(CASE WHEN t.type = 'INCOME' THEN t.amount_cents ELSE -t.amount_cents END)
                      FROM transactions t WHERE t.account_id = a.id), 0)
          + COALESCE((SELECT SUM(amount_cents - fee_cents) FROM transfers WHERE to_account_id = a.id), 0)
          - COALESCE((SELECT SUM(amount_cents + fee_cents) FROM transfers WHERE from_account_id = a.id), 0)
          AS balance_cents
        FROM accounts a ORDER BY a.archived, a.sort, a.id
        """,
    )
    fun observeBalances(): Flow<List<AccountBalanceRow>>

    @Query("SELECT * FROM accounts ORDER BY archived, sort, id")
    suspend fun all(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): AccountEntity?

    @Query("SELECT id FROM accounts WHERE archived = 0 ORDER BY sort, id LIMIT 1")
    suspend fun firstActiveId(): Long?

    @Query("SELECT id FROM accounts ORDER BY sort, id LIMIT 1")
    suspend fun firstId(): Long?

    @Query("SELECT COALESCE(MAX(sort), -1) FROM accounts")
    suspend fun maxSort(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AccountEntity): Long

    @Query("INSERT OR IGNORE INTO accounts(name, kind, icon, color, initial_cents, sort, archived, created_at) VALUES (:name, :kind, :icon, :color, :initialCents, :sort, 0, :createdAt)")
    suspend fun insertIgnore(
        name: String,
        kind: String,
        icon: String,
        color: String,
        initialCents: Long,
        sort: Int,
        createdAt: String,
    ): Long

    @Update
    suspend fun update(entity: AccountEntity)

    @Query("UPDATE accounts SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("UPDATE transactions SET account_id = :toId WHERE account_id = :fromId")
    suspend fun reassignTransactions(fromId: Long, toId: Long)

    @Query("UPDATE transfers SET from_account_id = :toId WHERE from_account_id = :fromId")
    suspend fun reassignTransfersFrom(fromId: Long, toId: Long)

    @Query("UPDATE transfers SET to_account_id = :toId WHERE to_account_id = :fromId")
    suspend fun reassignTransfersTo(fromId: Long, toId: Long)

    @Query("UPDATE recurring_rules SET account_id = :toId WHERE account_id = :fromId")
    suspend fun reassignRecurring(fromId: Long, toId: Long)

    @Delete
    suspend fun delete(entity: AccountEntity)

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int

    @Query("DELETE FROM accounts")
    suspend fun clear()
}

@Dao
interface TransferDao {
    @Query("SELECT * FROM transfers WHERE date >= :start AND date < :end ORDER BY date DESC, id DESC")
    fun observeRange(start: String, end: String): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfers WHERE date >= :start AND date < :end ORDER BY date DESC, id DESC")
    suspend fun range(start: String, end: String): List<TransferEntity>

    @Query("SELECT * FROM transfers ORDER BY date, id")
    suspend fun all(): List<TransferEntity>

    @Query("SELECT * FROM transfers WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): TransferEntity?

    @Query(
        """
        SELECT id FROM transfers
        WHERE date = :date AND from_account_id = :fromId AND to_account_id = :toId
          AND amount_cents = :amount AND fee_cents = :fee AND note = :note
          AND created_at = :createdAt
        LIMIT 1
        """,
    )
    suspend fun findDuplicateId(
        date: String,
        fromId: Long,
        toId: Long,
        amount: Long,
        fee: Long,
        note: String,
        createdAt: String,
    ): Long?

    @Insert
    suspend fun insert(entity: TransferEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: TransferEntity): Long

    @Update
    suspend fun update(entity: TransferEntity)

    @Query("DELETE FROM transfers WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Delete
    suspend fun delete(entity: TransferEntity)

    @Query("DELETE FROM transfers")
    suspend fun clear()
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY sort, id")
    fun observeAll(): Flow<List<TagEntity>>

    @Query(
        """
        SELECT g.*, (SELECT COUNT(*) FROM transaction_tags tt WHERE tt.tag_id = g.id) AS usage_count
        FROM tags g ORDER BY g.sort, g.id
        """,
    )
    fun observeUsage(): Flow<List<TagUsageRow>>

    @Query("SELECT * FROM tags ORDER BY sort, id")
    suspend fun all(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): TagEntity?

    @Query("SELECT id FROM tags WHERE name = :name LIMIT 1")
    suspend fun findId(name: String): Long?

    @Query("SELECT COALESCE(MAX(sort), -1) FROM tags")
    suspend fun maxSort(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TagEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: TagEntity): Long

    @Update
    suspend fun update(entity: TagEntity)

    @Delete
    suspend fun delete(entity: TagEntity)

    @Query("DELETE FROM tags")
    suspend fun clear()
}

@Dao
interface TransactionTagDao {
    @Query("SELECT tag_id FROM transaction_tags WHERE transaction_id = :transactionId")
    suspend fun tagIds(transactionId: Long): List<Long>

    @Query("SELECT tag_id FROM transaction_tags WHERE transaction_id IN (:transactionIds)")
    suspend fun tagIdsFor(transactionIds: List<Long>): List<Long>

    @Query("SELECT transaction_id, tag_id FROM transaction_tags")
    suspend fun all(): List<TransactionTagEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: TransactionTagEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<TransactionTagEntity>)

    @Query("DELETE FROM transaction_tags WHERE transaction_id = :transactionId")
    suspend fun clearFor(transactionId: Long)

    @Query("DELETE FROM transaction_tags WHERE tag_id = :tagId")
    suspend fun clearForTag(tagId: Long)

    @Query("DELETE FROM transaction_tags")
    suspend fun clear()
}

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring_rules ORDER BY enabled DESC, next_date, id")
    fun observeAll(): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rules ORDER BY enabled DESC, next_date, id")
    suspend fun all(): List<RecurringRuleEntity>

    @Query("SELECT * FROM recurring_rules WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): RecurringRuleEntity?

    @Query(
        """
        SELECT id FROM recurring_rules
        WHERE type = :type AND amount_cents = :amount AND note = :note
          AND frequency = :frequency AND interval_count = :interval
          AND start_date = :start AND next_date = :next
          AND category_id IS :categoryId AND account_id IS :accountId
        LIMIT 1
        """,
    )
    suspend fun findDuplicateId(
        type: String,
        amount: Long,
        note: String,
        frequency: String,
        interval: Int,
        start: String,
        next: String,
        categoryId: Long?,
        accountId: Long?,
    ): Long?

    @Query("SELECT * FROM recurring_rules WHERE enabled = 1 AND next_date <= :date ORDER BY next_date")
    suspend fun due(date: String): List<RecurringRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RecurringRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: RecurringRuleEntity): Long

    @Update
    suspend fun update(entity: RecurringRuleEntity)

    @Query("UPDATE recurring_rules SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Delete
    suspend fun delete(entity: RecurringRuleEntity)

    @Query("DELETE FROM recurring_rules")
    suspend fun clear()
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminder_dismissals")
    fun observeAll(): Flow<List<ReminderDismissalEntity>>

    @Query("SELECT * FROM reminder_dismissals WHERE key = :key LIMIT 1")
    suspend fun byKey(key: String): ReminderDismissalEntity?

    @Query("SELECT key FROM reminder_dismissals")
    suspend fun dismissedKeys(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReminderDismissalEntity)

    @Query("DELETE FROM reminder_dismissals")
    suspend fun clear()
}

@Dao
interface MetaDao {
    @Query("SELECT value FROM app_meta WHERE key = :key LIMIT 1")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: AppMetaEntity)

    @Query("DELETE FROM app_meta WHERE key = :key")
    suspend fun remove(key: String)

    @Query("DELETE FROM app_meta")
    suspend fun clear()
}


