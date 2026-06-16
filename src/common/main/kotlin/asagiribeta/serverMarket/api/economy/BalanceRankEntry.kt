package asagiribeta.serverMarket.api.economy

import java.util.UUID

/** Leaderboard entry exposed via public API. */
data class BalanceRankEntry(val uuid: UUID, val name: String, val balance: Double)
