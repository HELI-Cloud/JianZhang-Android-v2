package com.jianzhang.core.database

import android.content.Context
import androidx.room.withTransaction
import java.io.File
import java.time.LocalDate

class LegacyImporter(
    private val db: AppDatabase,
) {
    data class Result(
        val ok: Boolean,
        val imported: Boolean,
        val accounts: Int = 0,
        val transactions: Int = 0,
        val budgets: Int = 0,
        val transfers: Int = 0,
        val tags: Int = 0,
        val recurring: Int = 0,
        val message: String = "",
    )

    suspend fun importIfNeeded(context: Context): Result {
        val old = context.getDatabasePath("jianzhang.db")
        if (!old.isFile || old.length() == 0L) return Result(true, false, message = "没有旧版数据库")
        if (db.metaDao().get("legacy_v3_imported") == "1") {
            return Result(true, false, message = "旧数据已迁移")
        }
        val tempDir = File(context.cacheDir, "legacy-import-${System.nanoTime()}").apply { mkdirs() }
        val copy = File(tempDir, "legacy.db")
        try {
            old.copyTo(copy, overwrite = true)
            listOf("-wal", "-shm").forEach { suffix ->
                val side = File(old.absolutePath + suffix)
                if (side.isFile) side.copyTo(File(copy.absolutePath + suffix), overwrite = true)
            }
        } catch (t: Throwable) {
            tempDir.deleteRecursively()
            return Result(false, false, message = "旧数据库复制失败：${t.message ?: "未知错误"}")
        }

        val snapshot = try {
            LegacyReader(copy).read()
        } catch (t: Throwable) {
            tempDir.deleteRecursively()
            return Result(false, false, message = "旧数据库读取失败：${t.message ?: "未知错误"}")
        }
        val normalized = normalize(snapshot)
        try {
            // Clear, import, verify, and write the migration marker in one transaction.
            // A failed verification rolls everything back instead of leaving a half-imported ledger.
            db.withTransaction {
                db.transactionTagDao().clear()
                db.transactionDao().clear()
                db.transferDao().clear()
                db.budgetDao().clear()
                db.recurringDao().clear()
                db.reminderDao().clear()
                db.categoryDao().all().forEach { db.categoryDao().delete(it) }
                db.accountDao().all().forEach { db.accountDao().delete(it) }
                db.tagDao().all().forEach { db.tagDao().delete(it) }

                normalized.categories.forEach { db.categoryDao().insert(it) }
                normalized.accounts.forEach { db.accountDao().insert(it) }
                normalized.tags.forEach { db.tagDao().insert(it) }
                normalized.budgets.forEach { db.budgetDao().upsert(it) }
                normalized.transactions.forEach { db.transactionDao().insertIgnore(it) }
                normalized.transfers.forEach { db.transferDao().insertIgnore(it) }
                normalized.recurring.forEach { db.recurringDao().upsert(it) }
                normalized.transactionTags.forEach { db.transactionTagDao().insert(it) }
                normalized.dismissals.forEach { db.reminderDao().upsert(it) }

                val verification = verify(normalized)
                if (!verification.first) {
                    throw IllegalStateException(verification.second)
                }
                db.metaDao().put(AppMetaEntity("legacy_v3_imported", "1"))
                db.metaDao().put(AppMetaEntity("legacy_v3_source_rows", normalized.transactions.size.toString()))
                db.metaDao().put(AppMetaEntity("legacy_v3_imported_at", java.time.Instant.now().toString()))
            }
        } catch (t: Throwable) {
            tempDir.deleteRecursively()
            return Result(false, false, message = "旧数据导入失败：${t.message ?: "未知错误"}")
        }

        val retained = retainPlaintext(old)
        tempDir.deleteRecursively()
        val retentionMessage = if (retained != null) {
            "旧库已保留为 ${retained.name}"
        } else {
            "旧库未能重命名，已保留在原位置"
        }
        return Result(
            ok = true,
            imported = true,
            accounts = normalized.accounts.size,
            transactions = normalized.transactions.size,
            budgets = normalized.budgets.size,
            transfers = normalized.transfers.size,
            tags = normalized.tags.size,
            recurring = normalized.recurring.size,
            message = "旧数据迁移完成：${normalized.transactions.size} 笔流水，${normalized.accounts.size} 个账户；$retentionMessage",
        )
    }

    /**
     * The legacy plaintext database is never deleted after a migration.
     * Rename it (and its sidecars) to an immutable recovery copy so a future
     * import can be audited or retried without risking the original data.
     */
    private fun retainPlaintext(old: File): File? {
        if (!old.isFile) return null
        val stamp = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .format(java.time.LocalDateTime.now())
        var target = File(old.parentFile, "${old.name}.migrated-$stamp")
        var counter = 1
        while (target.exists()) {
            target = File(old.parentFile, "${old.name}.migrated-$stamp-${counter++}")
        }
        if (!old.renameTo(target)) return null
        listOf("-wal", "-shm").forEach { suffix ->
            val sidecar = File(old.absolutePath + suffix)
            if (sidecar.isFile) {
                val sidecarTarget = File(target.absolutePath + suffix)
                runCatching { sidecar.renameTo(sidecarTarget) }
            }
        }
        return target
    }

    private fun normalize(snapshot: LegacyReader.Snapshot): LegacyReader.Snapshot {
        val categories = snapshot.categories
            .filter { it.name.isNotBlank() }
            .distinctBy { it.type.uppercase() + "|" + it.name.trim() }
            .map { it.copy(type = it.type.uppercase()) }
        val categoryIds = categories.map { it.id }.toSet()
        val accounts = snapshot.accounts
            .filter { it.name.isNotBlank() }
            .distinctBy { it.name.trim() }
        val accountIds = accounts.map { it.id }.toSet()
        val tags = snapshot.tags
            .filter { it.name.isNotBlank() }
            .distinctBy { it.name.trim() }
        val tagIds = tags.map { it.id }.toSet()
        val budgets = snapshot.budgets
            .filter { runCatching { java.time.YearMonth.parse(it.month) }.isSuccess && it.amountCents > 0 }
            .distinctBy { it.month + "|" + (it.categoryId ?: -1) }
        val recurring = snapshot.recurring
            .filter {
                validDate(it.startDate) &&
                    validDate(it.nextDate) &&
                    (it.endDate == null || validDate(it.endDate)) &&
                    it.amountCents > 0
            }
            .distinctBy { it.id }
            .map {
                it.copy(
                    type = it.type.uppercase(),
                    categoryId = it.categoryId?.takeIf { id -> id in categoryIds },
                    accountId = it.accountId?.takeIf { id -> id in accountIds },
                    frequency = it.frequency.trim().uppercase()
                        .takeIf { f -> f in setOf("DAILY", "WEEKLY", "MONTHLY", "YEARLY") }
                        ?: "MONTHLY",
                )
            }
        val transactions = snapshot.transactions
            .filter { validDate(it.date) && it.amountCents > 0 }
            .distinctBy { tx ->
                if (tx.recurringId == null) "id:${tx.id}" else "rec:${tx.recurringId}:${tx.date}"
            }
            .map {
                it.copy(
                    type = it.type.uppercase(),
                    categoryId = it.categoryId?.takeIf { id -> id in categoryIds },
                    accountId = it.accountId?.takeIf { id -> id in accountIds },
                )
            }
        val txIds = transactions.map { it.id }.toSet()
        val transfers = snapshot.transfers
            .filter {
                validDate(it.date) && it.amountCents > 0 &&
                    it.fromAccountId in accountIds && it.toAccountId in accountIds &&
                    it.fromAccountId != it.toAccountId
            }
        return snapshot.copy(
            categories = categories,
            accounts = accounts,
            transactions = transactions,
            budgets = budgets,
            transfers = transfers,
            tags = tags,
            transactionTags = snapshot.transactionTags
                .filter { it.transactionId in txIds && it.tagId in tagIds }
                .distinctBy { it.transactionId to it.tagId },
            recurring = recurring,
            dismissals = snapshot.dismissals.filter { it.key.isNotBlank() }.distinctBy { it.key },
        )
    }

    private suspend fun verify(snapshot: LegacyReader.Snapshot): Pair<Boolean, String> {
        val counts = listOf(
            "分类" to (db.categoryDao().count() to snapshot.categories.size),
            "账户" to (db.accountDao().count() to snapshot.accounts.size),
            "流水" to (db.transactionDao().count() to snapshot.transactions.size),
            "预算" to (db.budgetDao().all().size to snapshot.budgets.size),
            "转账" to (db.transferDao().all().size to snapshot.transfers.size),
            "标签" to (db.tagDao().all().size to snapshot.tags.size),
            "标签关联" to (db.transactionTagDao().all().size to snapshot.transactionTags.size),
            "周期规则" to (db.recurringDao().all().size to snapshot.recurring.size),
            "提醒忽略" to (db.reminderDao().dismissedKeys().size to snapshot.dismissals.size),
        )
        counts.firstOrNull { it.second.first != it.second.second }?.let {
            return false to "迁移对账失败：${it.first}数量不一致（${it.second.first}/${it.second.second}）"
        }

        val expectedIncome = snapshot.transactions.filter { it.type == "INCOME" }.sumOf { it.amountCents }
        val expectedExpense = snapshot.transactions.filter { it.type == "EXPENSE" }.sumOf { it.amountCents }
        val actualIncome = db.transactionDao().totalByType("INCOME")
        val actualExpense = db.transactionDao().totalByType("EXPENSE")
        if (expectedIncome != actualIncome || expectedExpense != actualExpense) {
            return false to "迁移对账失败：流水金额不一致"
        }

        val actualTransactions = db.transactionDao().all()
        val expectedTransactions = snapshot.transactions.associateBy { it.id }
        for (actual in actualTransactions) {
            val expected = expectedTransactions[actual.id] ?: continue
            if (actual.type != expected.type ||
                actual.amountCents != expected.amountCents ||
                actual.date != expected.date ||
                actual.categoryId != expected.categoryId ||
                actual.accountId != expected.accountId ||
                actual.note != expected.note ||
                actual.recurringId != expected.recurringId
            ) {
                return false to "迁移对账失败：流水 #${actual.id} 字段不一致"
            }
        }

        val accountIds = snapshot.accounts.map { it.id }.toSet()
        val categoryIds = snapshot.categories.map { it.id }.toSet()
        if (actualTransactions.any { it.accountId != null && it.accountId !in accountIds }) {
            return false to "迁移对账失败：流水引用了不存在的账户"
        }
        if (actualTransactions.any { it.categoryId != null && it.categoryId !in categoryIds }) {
            return false to "迁移对账失败：流水引用了不存在的分类"
        }

        val actualTransfers = db.transferDao().all()
        val expectedTransfers = snapshot.transfers.associateBy { it.id }
        for (actual in actualTransfers) {
            val expected = expectedTransfers[actual.id] ?: continue
            if (actual.fromAccountId != expected.fromAccountId ||
                actual.toAccountId != expected.toAccountId ||
                actual.amountCents != expected.amountCents ||
                actual.feeCents != expected.feeCents ||
                actual.date != expected.date ||
                actual.fromAccountId !in accountIds ||
                actual.toAccountId !in accountIds
            ) {
                return false to "迁移对账失败：转账 #${actual.id} 字段或账户引用不一致"
            }
        }
        if (snapshot.transfers.sumOf { it.amountCents } != actualTransfers.sumOf { it.amountCents }) {
            return false to "迁移对账失败：转账金额合计不一致"
        }

        val actualTags = db.tagDao().all().associateBy { it.id }
        val actualTagsJunctions = db.transactionTagDao().all()
            .map { it.transactionId to it.tagId }
            .toSet()
        val expectedTagsJunctions = snapshot.transactionTags
            .map { it.transactionId to it.tagId }
            .toSet()
        if (actualTagsJunctions != expectedTagsJunctions) {
            return false to "迁移对账失败：标签关联不一致"
        }
        if (actualTagsJunctions.any { (txId, tagId) ->
                txId !in expectedTransactions || tagId !in actualTags
            }
        ) {
            return false to "迁移对账失败：标签关联引用了不存在的流水或标签"
        }

        val expectedRecurring = snapshot.recurring.associateBy { it.id }
        for (actual in db.recurringDao().all()) {
            val expected = expectedRecurring[actual.id] ?: continue
            if (actual.type != expected.type ||
                actual.amountCents != expected.amountCents ||
                actual.categoryId != expected.categoryId ||
                actual.accountId != expected.accountId ||
                actual.frequency != expected.frequency ||
                actual.startDate != expected.startDate ||
                actual.nextDate != expected.nextDate ||
                actual.endDate != expected.endDate
            ) {
                return false to "迁移对账失败：周期规则 #${actual.id} 字段不一致"
            }
            if (actual.categoryId != null && actual.categoryId !in categoryIds) {
                return false to "迁移对账失败：周期规则引用了不存在的分类"
            }
            if (actual.accountId != null && actual.accountId !in accountIds) {
                return false to "迁移对账失败：周期规则引用了不存在的账户"
            }
        }

        if (snapshot.budgets.sumOf { it.amountCents } != db.budgetDao().all().sumOf { it.amountCents }) {
            return false to "迁移对账失败：预算金额合计不一致"
        }
        return true to "迁移对账通过"
    }

    private fun validDate(value: String): Boolean = runCatching { LocalDate.parse(value) }.isSuccess
}
