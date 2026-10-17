package com.jianzhang.feature.analysis

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jianzhang.core.data.SettingsStore
import com.jianzhang.core.model.AiFocus
import com.jianzhang.core.model.AiRange
import com.jianzhang.core.model.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import javax.inject.Inject

data class AnalysisUiState(
    val month: YearMonth = YearMonth.now(),
    val focus: AiFocus = AiFocus.SPENDING,
    val range: AiRange = AiRange.CURRENT_MONTH,
    val prompt: String = "",
    val result: String = "",
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    private val analyzer: AiAnalysisService,
    settingsStore: SettingsStore,
) : ViewModel() {
    val settings = settingsStore.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings(),
    )

    private val _state = MutableStateFlow(AnalysisUiState())
    val state = _state

    private var job: Job? = null

    fun previousMonth() {
        _state.value = _state.value.copy(month = _state.value.month.minusMonths(1))
    }

    fun nextMonth() {
        val next = _state.value.month.plusMonths(1)
        if (next <= YearMonth.now()) _state.value = _state.value.copy(month = next)
    }

    fun setFocus(value: AiFocus) { _state.value = _state.value.copy(focus = value) }
    fun setRange(value: AiRange) { _state.value = _state.value.copy(range = value) }
    fun setPrompt(value: String) { _state.value = _state.value.copy(prompt = value.take(300)) }
    fun clearError() { _state.value = _state.value.copy(error = null) }
    fun clearResult() { _state.value = _state.value.copy(result = "") }

    fun analyze() {
        val snapshot = _state.value
        if (snapshot.loading) return
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val result = analyzer.analyze(
                    focus = snapshot.focus,
                    range = snapshot.range,
                    month = snapshot.month,
                    customQuestion = snapshot.prompt,
                )
                _state.value = _state.value.copy(result = result, loading = false)
            } catch (cancel: CancellationException) {
                _state.value = _state.value.copy(loading = false)
                throw cancel
            } catch (error: Throwable) {
                // Throwable, not Exception: release-only LinkageError/OOM must
                // become a visible message instead of a process crash.
                Log.e(TAG, "AI analysis failed", error)
                _state.value = _state.value.copy(
                    loading = false,
                    error = AiRequestPolicy.friendlyError(error),
                )
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = _state.value.copy(loading = false)
    }

    private companion object {
        const val TAG = "AnalysisViewModel"
    }
}