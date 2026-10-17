package com.jianzhang.feature.analysis

import com.jianzhang.core.data.LedgerRepository
import com.jianzhang.core.data.SettingsStore
import com.jianzhang.core.model.AiFocus
import com.jianzhang.core.model.AiRange
import com.jianzhang.core.model.AppSettings
import com.jianzhang.core.model.CsvRow
import com.jianzhang.core.model.TxnType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.YearMonth
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class AiAnalyzer @Inject constructor(
    private val repository: LedgerRepository,
    private val settingsStore: SettingsStore,
) {
    suspend fun analyze(
        focus: AiFocus,
        range: AiRange,
        month: YearMonth,
        customQuestion: String,
    ): String {
        val settings = settingsStore.settings.first()
        require(settings.aiApiKey.isNotBlank()) { "请先在设置中填写 API Key" }
        val context = buildContext(range, month)
        val body = JSONObject()
            .put("model", settings.aiModel.ifBlank { "deepseek-chat" })
            .put("messages", buildMessages(focus, customQuestion, context))
            .put("temperature", settings.aiTemperature.coerceIn(0.0, 1.5))
            .put("stream", false)
        return request(settings, body)
    }

    private suspend fun buildContext(range: AiRange, month: YearMonth): JSONObject {
        val start = when (range) {
            AiRange.CURRENT_MONTH -> month.atDay(1)
            AiRange.LAST_3_MONTHS -> month.minusMonths(2).atDay(1)
            AiRange.THIS_YEAR -> month.withMonth(1).atDay(1)
        }
        val endExclusive = month.plusMonths(1).atDay(1)
        val allRows = repository.csvRows()
        val rows = allRows.filter { row ->
            runCatching {
                val date = java.time.LocalDate.parse(row.date)
                !date.isBefore(start) && date.isBefore(endExclusive)
            }.getOrDefault(false)
        }
        val selectedRows = rows.filter { it.date.startsWith(month.toString()) }
        val expense = rows.filter { it.type.equals("expense", true) }.sumOf { it.amountCents }
        val income = rows.filter { it.type.equals("income", true) }.sumOf { it.amountCents }

        val expenseByCategory = selectedRows
            .filter { it.type.equals("expense", true) }
            .groupBy { it.category.ifBlank { "未分类" } }
            .mapValues { (_, value) -> value.sumOf { it.amountCents } }
            .entries.sortedByDescending { it.value }
            .take(12)
        val incomeByCategory = selectedRows
            .filter { it.type.equals("income", true) }
            .groupBy { it.category.ifBlank { "未分类" } }
            .mapValues { (_, value) -> value.sumOf { it.amountCents } }
            .entries.sortedByDescending { it.value }
            .take(8)
        val daily = selectedRows
            .filter { it.type.equals("expense", true) }
            .groupBy { it.date }
            .mapValues { (_, value) -> value.sumOf { it.amountCents } }
            .toSortedMap()
        val monthly = rows.groupBy { it.date.take(7) }
            .mapValues { (_, value) ->
                value.filter { it.type.equals("expense", true) }.sumOf { it.amountCents } to
                    value.filter { it.type.equals("income", true) }.sumOf { it.amountCents }
            }
            .toSortedMap()
        val budgets = repository.observeBudgets(month).first()

        return JSONObject()
            .put("period_label", periodLabel(range, month))
            .put("selected_month", month.toString())
            .put("summary_yuan", JSONObject()
                .put("income", yuan(income))
                .put("expense", yuan(expense))
                .put("balance", yuan(income - expense)))
            .put("category_expense_yuan", JSONObject().apply {
                expenseByCategory.forEach { (name, cents) -> put(name, yuan(cents)) }
            })
            .put("category_income_yuan", JSONObject().apply {
                incomeByCategory.forEach { (name, cents) -> put(name, yuan(cents)) }
            })
            .put("daily_expense_yuan", JSONObject().apply {
                daily.forEach { (date, cents) -> put(date, yuan(cents)) }
            })
            .put("monthly_trend_yuan", JSONArray().apply {
                monthly.forEach { (key, pair) ->
                    put(JSONObject().put("month", key).put("expense", yuan(pair.first)).put("income", yuan(pair.second)))
                }
            })
            .put("budgets_yuan", JSONArray().apply {
                budgets.forEach { budget ->
                    put(JSONObject()
                        .put("name", if (budget.categoryId == null) "总预算" else "分类预算")
                        .put("amount", yuan(budget.amountCents)))
                }
            })
            .put("recent_transactions", JSONArray().apply {
                rows.sortedWith(compareByDescending<CsvRow> { it.date }.thenByDescending { it.id })
                    .take(160)
                    .forEach { row ->
                        put(JSONObject()
                            .put("date", row.date)
                            .put("type", if (row.type.equals("income", true)) "收入" else "支出")
                            .put("category", row.category)
                            .put("amount_yuan", yuan(row.amountCents))
                            .put("note", row.note.take(40)))
                    }
            })
            .put("transaction_count", rows.size)
    }

    private fun buildMessages(focus: AiFocus, customQuestion: String, context: JSONObject): JSONArray {
        val system = """
            你是一名严谨的个人财务分析师。只依据用户提供的账本 JSON 分析，不编造收入、支出、资产或负债。
            用简体中文回答，结论先行，给出可执行建议。金额使用元，保留两位小数。
            输出结构：1）一句话结论；2）关键发现（不超过 5 条）；3）风险与机会；4）下月可执行动作（3 条以内）。
            如果数据不足，明确说明缺少什么，不要猜测。
        """.trimIndent()
        val focusText = when (focus) {
            AiFocus.SPENDING -> "分析支出结构与可优化空间，识别占比最高的类别和异常波动。"
            AiFocus.SAVING -> "分析节省空间，优先给出不影响基本生活的具体削减建议。"
            AiFocus.TREND -> "分析收支趋势、月度变化和持续性风险。"
            AiFocus.RISK -> "分析预算、超支、现金流和大额支出风险，按严重程度排序。"
        }
        val user = buildString {
            appendLine("分析范围：${context.optString("period_label")}")
            appendLine("分析重点：$focusText")
            if (customQuestion.isNotBlank()) appendLine("用户补充：${customQuestion.trim().take(300)}")
            appendLine()
            appendLine("账本数据（JSON）：")
            append(context.toString(2))
        }
        return JSONArray()
            .put(JSONObject().put("role", "system").put("content", system))
            .put(JSONObject().put("role", "user").put("content", user))
    }

    private suspend fun request(settings: AppSettings, body: JSONObject): String {
        val client = OkHttpClient.Builder()
            .connectTimeout(settings.aiTimeoutSeconds.toLong().coerceIn(5, 120), TimeUnit.SECONDS)
            .readTimeout(settings.aiTimeoutSeconds.toLong().coerceIn(5, 120), TimeUnit.SECONDS)
            .writeTimeout(settings.aiTimeoutSeconds.toLong().coerceIn(5, 120), TimeUnit.SECONDS)
            .callTimeout(settings.aiTimeoutSeconds.toLong().coerceIn(5, 120), TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url(normalizeEndpoint(settings.aiEndpoint))
            .header("Accept", "application/json")
            .header("Authorization", "Bearer ${settings.aiApiKey.trim()}")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        val response = client.newCall(request).await()
        response.use { res ->
            val raw = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                throw IllegalArgumentException("AI 请求失败（HTTP ${res.code}）：${raw.take(240)}")
            }
            val root = runCatching { JSONObject(raw) }
                .getOrElse { throw IllegalArgumentException("AI 返回的不是有效 JSON") }
            val choices = root.optJSONArray("choices")
            val first = choices?.optJSONObject(0)
                ?: throw IllegalArgumentException("AI 返回缺少 choices")
            val content = first.optJSONObject("message")?.optString("content").orEmpty()
                .ifBlank { first.optString("text") }
            if (content.isBlank()) throw IllegalArgumentException("AI 返回内容为空")
            return content.trim()
        }
    }

    private fun normalizeEndpoint(raw: String): String {
        var value = raw.trim().trimEnd('/')
        require(value.startsWith("http://") || value.startsWith("https://")) {
            "AI 接口地址必须以 http:// 或 https:// 开头"
        }
        if (value.contains("/chat/completions")) return value
        return "$value/chat/completions"
    }

    private fun periodLabel(range: AiRange, month: YearMonth): String = when (range) {
        AiRange.CURRENT_MONTH -> "$month 当月"
        AiRange.LAST_3_MONTHS -> "${month.minusMonths(2)} 至 $month"
        AiRange.THIS_YEAR -> "${month.year} 年 1 月至 $month"
    }

    private fun yuan(cents: Long): Double = cents / 100.0
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!continuation.isCancelled) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (continuation.isCancelled) {
                response.close()
            } else {
                continuation.resume(response)
            }
        }
    })
}