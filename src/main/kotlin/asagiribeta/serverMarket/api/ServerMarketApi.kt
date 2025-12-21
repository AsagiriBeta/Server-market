package asagiribeta.serverMarket.api

import asagiribeta.serverMarket.api.economy.EconomyTransactionResult
import net.minecraft.server.network.ServerPlayerEntity
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Public API for inter-mod integration.
 *
 * Note: this branch targets Minecraft 1.21.10 only.
 */
interface ServerMarketApi {
    /** Returns player's balance (server-side). Always async (DB). */
    fun getBalance(uuid: UUID): CompletableFuture<Double>

    /** Returns pending parcel count for player. Always async (DB). */
    fun getParcelCount(uuid: UUID): CompletableFuture<Int>

    /** Adds balance (can be negative). Always async (does DB work). */
    fun addBalance(uuid: UUID, amount: Double, reason: String? = null, actor: UUID? = null): CompletableFuture<EconomyTransactionResult>

    /** Attempts to withdraw. Returns success=false when insufficient funds. Always async. */
    fun withdraw(uuid: UUID, amount: Double, reason: String? = null, actor: UUID? = null): CompletableFuture<EconomyTransactionResult>

    /** Transfers from -> to. Always async. */
    fun transfer(from: UUID, to: UUID, amount: Double, reason: String? = null, actor: UUID? = null): CompletableFuture<EconomyTransactionResult>

    /** Opens ServerMarket GUI for player. */
    fun openMenu(player: ServerPlayerEntity)

    /** Mod version string (best-effort, from jar manifest). */
    fun getModVersion(): String
}
