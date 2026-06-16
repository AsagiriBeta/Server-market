package asagiribeta.serverMarket.api

import asagiribeta.serverMarket.api.economy.EconomyTransactionResult
import asagiribeta.serverMarket.api.economy.BalanceRankEntry
import asagiribeta.serverMarket.model.TransactionRecord
import net.minecraft.server.network.ServerPlayerEntity
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Public API for inter-mod integration.
 */
interface ServerMarketApi {
    /** Returns player's balance (server-side). Always async (DB). */
    fun getBalance(uuid: UUID): CompletableFuture<Double>

    /** Returns whether the player has at least [amount]. Always async. */
    fun hasEnough(uuid: UUID, amount: Double): CompletableFuture<Boolean>

    /** Returns pending parcel count for player. Always async (DB). */
    fun getParcelCount(uuid: UUID): CompletableFuture<Int>

    /** Adds balance (can be negative). Always async (does DB work). */
    fun addBalance(uuid: UUID, amount: Double, reason: String? = null, actor: UUID? = null): CompletableFuture<EconomyTransactionResult>

    /** Attempts to withdraw. Returns success=false when insufficient funds. Always async. */
    fun withdraw(uuid: UUID, amount: Double, reason: String? = null, actor: UUID? = null): CompletableFuture<EconomyTransactionResult>

    /** Sets balance to an absolute value. Always async. */
    fun setBalance(uuid: UUID, amount: Double, reason: String? = null, actor: UUID? = null): CompletableFuture<EconomyTransactionResult>

    /** Transfers from -> to. Always async. */
    fun transfer(from: UUID, to: UUID, amount: Double, reason: String? = null, actor: UUID? = null): CompletableFuture<EconomyTransactionResult>

    /** Top balance leaderboard. Always async. */
    fun getTopBalances(limit: Int): CompletableFuture<List<BalanceRankEntry>>

    /** Transaction history for a player (newest first). Always async. */
    fun getHistory(uuid: UUID, page: Int = 1, pageSize: Int = 10): CompletableFuture<List<TransactionRecord>>

    /** Format a monetary value for display. */
    fun format(amount: Double): String

    /** Opens ServerMarket GUI for player. */
    fun openMenu(player: ServerPlayerEntity)

    /** Mod version string (best-effort, from jar manifest). */
    fun getModVersion(): String
}
