package com.jianzhang.core.navigation

import kotlinx.serialization.Serializable

@Serializable data object OverviewRoute
@Serializable data object LedgerRoute
@Serializable data object StatsRoute
@Serializable data object MineRoute
@Serializable data class RecordRoute(val id: Long? = null, val amountCents: Long? = null, val type: String = "EXPENSE")
@Serializable data object BudgetRoute
@Serializable data object RecurringRoute
@Serializable data object ReminderRoute
@Serializable data object TagsRoute
@Serializable data object AnalysisRoute
@Serializable data object SettingsRoute
@Serializable data object AboutRoute
@Serializable data object AccountsRoute
@Serializable data object LockRoute

const val MAIN_OVERVIEW = "overview"
const val MAIN_LEDGER = "ledger"
const val MAIN_STATS = "stats"
const val MAIN_MINE = "mine"