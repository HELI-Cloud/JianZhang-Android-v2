package com.jianzhang.feature.analysis

import com.jianzhang.core.model.AiFocus
import com.jianzhang.core.model.AiRange
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.YearMonth
import javax.inject.Singleton

/**
 * Small boundary between the UI and the AI transport.
 *
 * Keeping this as an interface makes the ViewModel testable with a fake and
 * prevents R8 or a future transport swap from pulling UI code into the
 * networking layer.
 */
interface AiAnalysisService {
    suspend fun analyze(
        focus: AiFocus,
        range: AiRange,
        month: YearMonth,
        customQuestion: String,
    ): String
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AnalysisModule {
    @Binds
    @Singleton
    abstract fun bindAiAnalysisService(impl: AiAnalyzer): AiAnalysisService
}