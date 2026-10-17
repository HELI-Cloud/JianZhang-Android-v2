package com.jianzhang.feature.analysis

import com.jianzhang.core.common.Dates
import com.jianzhang.core.data.LedgerRepository
import com.jianzhang.core.data.SettingsStore
import com.jianzhang.core.model.AiFocus
import com.jianzhang.core.model.AiRange
import com.jianzhang.core.model.AppSettings
import com.jianzhang.core.model.CsvRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

private const val MAX_RESPONSE_CHARS = 512_000
private const val MAX_RESULT_CHARS = 32_000
private const val USER_AGENT = "JianZhang/1.0.3 (Android)"

/**
 * AI transport for the ledger.
 *
 * This intentionally uses HttpURLConnection instead of OkHttp: it removes a
 * third-party dependency from the release path and avoids R8/reflection
 * failure modes during startup of the analysis screen. All database and
 * network work runs on Dispatchers.IO.
 */
@Singleton
class AiAnalyzer @Inject constructor(
    private val repository: LedgerRepository,
    private val settingsStore: SettingsStore,
) : AiAnalysisService {

    override suspend fun analyze(
        focus: AiFocus,
        range: AiRange,
        month: YearMonth,
        customQuestion: String,
    ): String = withContext(Dispatchers.IO) {
        val settings = settingsStore.settings.first()
        val apiKey = settings.aiApiKey.trim()
        require(apiKey.isNotEmpty()) { "请先在设置中填写 API Key" }

        val context = buildContext(range, month)
        val body = JSONObject()
            .put("model", settings.aiModel.trim().ifBlank { "deepseek-chat" })
            .put("messages", buildMessages(focus, customQuestion, context))
            .put("temperature", AiRequestPolicy.temperature(settings.aiTemperature))
            .put("stream", false)

        request(settings.copy(aiApiKey = apiKey), body)
    }

    private suspend fun buildContext(range: AiRange, month: YearMonth): JSONObject {
        val start = when (range) {
            AiRange.CURRENT_MONTH -> month.atDay(1)
            AiRange.LAST_3_MONTHS -> month.minusMonths(2).atDay(1)
            AiRange.THIS_YEAR -> month.withMonth(1).atDay(1)
        }
        val endExclusive = month.plusMonths(1).atDay(1)

        // Query only the selected window. The old implementation read every
        // transaction in the database even for a one-month report, which could
        // block the UI or exhaust memory on a large ledger.
        val rows = repository.csvRows(Dates.iso(start), Dates.iso(endExclusive))
            .filter { row -> Dates.parseOrNull(row.date) != null }
        val selectedRows = rows.filter { it.date.startsWith(month.toString()) }
        val expense = rows.filter { it.isExpense() }.sumOf { it.amountCents }
        val income = rows.filter { it.isIncome() }.sumOf { it.amountCents }

        val expenseByCategory = selectedRows
            .filter { it.isExpense() }
            .groupBy { it.category.ifBlank { "未分类" } }
            .mapValues { (_, value) -> value.sumOf { it.amountCents } }
            .entries.sortedByDescending { it.value }
            .take(12)
        val incomeByCategory = selectedRows
            .filter { it.isIncome() }
            .groupBy { it.category.ifBlank { "未分类" } }
            .mapValues { (_, value) -> value.sumOf { it.amountCents } }
            .entries.sortedByDescending { it.value }
            .take(8)
        val daily = selectedRows
            .filter { it.isExpense() }
            .groupBy { it.date }
            .mapValues { (_, value) -> value.sumOf { it.amountCents } }
            .toSortedMap()
        val monthly = rows.groupBy { it.date.take(7) }
            .mapValues { (_, value) ->
                value.filter { it.isExpense() }.sumOf { it.amountCents } to
                    value.filter { it.isIncome() }.sumOf { it.amountCents }
            }
            .toSortedMap()

        // A budget query must never make the whole report fail. If the table is
        // unavailable or the selected month has no rows, send an empty array.
        // Cancellation must still propagate so leaving the screen cancels work.
        val budgets = try {
            repository.budgets(month)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            emptyList()
        }

        return JSONObject()
            .put("period_label", periodLabel(range, month))
            .put("selected_month", month.toString())
            .put(
                "summary_yuan",
                JSONObject()
                    .put("income", yuan(income))
                    .put("expense", yuan(expense))
                    .put("balance", yuan(income - expense)),
            )
            .put(
                "category_expense_yuan",
                JSONObject().apply {
                    expenseByCategory.forEach { (name, cents) -> put(name, yuan(cents)) }
                },
            )
            .put(
                "category_income_yuan",
                JSONObject().apply {
                    incomeByCategory.forEach { (name, cents) -> put(name, yuan(cents)) }
                },
            )
            .put(
                "daily_expense_yuan",
                JSONObject().apply {
                    daily.forEach { (date, cents) -> put(date, yuan(cents)) }
                },
            )
            .put(
                "monthly_trend_yuan",
                JSONArray().apply {
                    monthly.forEach { (key, pair) ->
                        put(
                            JSONObject()
                                .put("month", key)
                                .put("expense", yuan(pair.first))
                                .put("income", yuan(pair.second)),
                        )
                    }
                },
            )
            .put(
                "budgets_yuan",
                JSONArray().apply {
                    budgets.forEach { budget ->
                        put(
                            JSONObject()
                                .put("name", if (budget.categoryId == null) "总预算" else "分类预算")
                                .put("amount", yuan(budget.amountCents)),
                        )
                    }
                },
            )
            .put(
                "recent_transactions",
                JSONArray().apply {
                    rows.sortedWith(
                        compareByDescending<CsvRow> { it.date }.thenByDescending { it.id },
                    )
                        .take(160)
                        .forEach { row ->
                            put(
                                JSONObject()
                                    .put("date", row.date)
                                    .put("type", if (row.isIncome()) "收入" else "支出")
                                    .put("category", row.category.take(40))
                                    .put("amount_yuan", yuan(row.amountCents))
                                    .put("note", row.note.take(40)),
                            )
                        }
                },
            )
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

    private fun request(settings: AppSettings, body: JSONObject): String {
        val endpoint = AiRequestPolicy.normalizeEndpoint(settings.aiEndpoint)
        val payload = body.toString().toByteArray(StandardCharsets.UTF_8)
        val timeoutMillis = AiRequestPolicy.timeoutSeconds(settings.aiTimeoutSeconds) * 1000
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutMillis
            readTimeout = timeoutMillis
            doInput = true
            doOutput = true
            useCaches = false
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Authorization", "Bearer ${settings.aiApiKey.trim()}")
            setFixedLengthStreamingMode(payload.size)
        }
        try {
            connection.outputStream.use { stream -> stream.write(payload) }
            val code = connection.responseCode
            val raw = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.use { readLimited(it) }
                .orEmpty()

            if (code !in 200..299) {
                val apiMessage = runCatching {
                    JSONObject(raw).optJSONObject("error")?.optString("message")
                }.getOrNull()
                val detail = apiMessage?.takeIf { it.isNotBlank() } ?: raw.take(240)
                throw IllegalArgumentException("AI 请求失败（HTTP $code）：$detail")
            }
            return parseAnswer(raw)
        } finally {
            connection.disconnect()
        }
    }

    private fun readLimited(stream: InputStream): String {
        val limit = MAX_RESPONSE_CHARS
        val output = StringBuilder()
        val buffer = CharArray(8_192)
        stream.reader(StandardCharsets.UTF_8).use { reader ->
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                if (output.length + count > limit) {
                    throw IllegalArgumentException("AI 返回内容过大，已停止解析")
                }
                output.append(buffer, 0, count)
            }
        }
        return output.toString()
    }

    private fun parseAnswer(raw: String): String {
        val root = runCatching { JSONObject(raw) }
            .getOrElse { throw IllegalArgumentException("AI 返回的不是有效 JSON") }
        val choices = root.optJSONArray("choices")
        val first = choices?.optJSONObject(0)
            ?: throw IllegalArgumentException("AI 返回缺少 choices")
        val content = extractContent(first.optJSONObject("message")?.opt("content"))
            .ifBlank { first.optString("text") }
        if (content.isBlank()) throw IllegalArgumentException("AI 返回内容为空")
        val trimmed = content.trim()
        return if (trimmed.length > MAX_RESULT_CHARS) {
            trimmed.take(MAX_RESULT_CHARS) + "\n\n[结果过长，已截断]"
        } else {
            trimmed
        }
    }

    private fun extractContent(value: Any?): String = when (value) {
        is String -> value
        is JSONArray -> buildString {
            for (index in 0 until value.length()) {
                when (val part = value.opt(index)) {
                    is JSONObject -> append(part.optString("text"))
                    is String -> append(part)
                }
            }
        }
        is JSONObject -> value.optString("text")
        else -> ""
    }

    private fun CsvRow.isIncome(): Boolean = type.equals("income", ignoreCase = true)

    private fun CsvRow.isExpense(): Boolean = type.equals("expense", ignoreCase = true)

    private fun periodLabel(range: AiRange, month: YearMonth): String = when (range) {
        AiRange.CURRENT_MONTH -> "$month 当月"
        AiRange.LAST_3_MONTHS -> "${month.minusMonths(2)} 至 $month"
        AiRange.THIS_YEAR -> "${month.year} 年 1 月至 $month"
    }

    private fun yuan(cents: Long): Double = cents / 100.0
}

/** Pure policy helpers, kept separate so they can be unit-tested without Android. */
internal object AiRequestPolicy {
    fun normalizeEndpoint(raw: String): String {
        val value = raw.trim()
        require(value.isNotEmpty()) { "AI 接口地址不能为空" }
        require(value.startsWith("http://") || value.startsWith("https://")) {
            "AI 接口地址必须以 http:// 或 https:// 开头"
        }
        val withoutTrailingSlash = value.trimEnd('/')
        val endpoint = if (withoutTrailingSlash.contains("/chat/completions")) {
            withoutTrailingSlash
        } else {
            "$withoutTrailingSlash/chat/completions"
        }
        val uri = runCatching { URI(endpoint) }
            .getOrElse { throw IllegalArgumentException("AI 接口地址格式不正确") }
        require(uri.host?.isNotBlank() == true) { "AI 接口地址缺少有效主机名" }
        return endpoint
    }

    fun temperature(value: Double): Double =
        if (value.isFinite()) value.coerceIn(0.0, 1.5) else 0.35

    fun timeoutSeconds(value: Int): Int = value.coerceIn(5, 120)

    fun friendlyError(error: Throwable): String = when {
        error is SocketTimeoutException -> "AI 请求超时，请检查网络或调大超时时间后重试"
        error is UnknownHostException -> "无法连接 AI 服务，请检查网络和接口地址"
        error is SSLException -> "AI 服务的 HTTPS 连接失败，请检查证书或网络环境"
        error is IOException && error.message.orEmpty().contains("timeout", ignoreCase = true) ->
            "AI 请求超时，请检查网络或调大超时时间后重试"
        !error.message.isNullOrBlank() -> error.message!!.take(240)
        else -> "分析失败，请稍后重试"
    }
}