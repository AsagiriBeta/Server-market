package asagiribeta.serverMarket.api

import asagiribeta.serverMarket.model.TransactionRecord
import net.minecraft.server.network.ServerPlayerEntity
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Public API for market-specific inter-mod integration.
 *
 * For balance operations (read/write/transfer/format), use [Common Economy API]
 * (`server-market` provider) instead — this interface intentionally does not
 * duplicate that surface.
 */
interface ServerMarketApi {
    /** Returns pending parcel count for player. Always async (DB). */
    fun getParcelCount(uuid: UUID): CompletableFuture<Int>

    /** Transaction history for a player (newest first). Always async. */
    fun getHistory(uuid: UUID, page: Int = 1, pageSize: Int = 10): CompletableFuture<List<TransactionRecord>>

    /** Opens ServerMarket GUI for player. */
    fun openMenu(player: ServerPlayerEntity)

    /** Mod version string (best-effort, from jar manifest). */
    fun getModVersion(): String
}
