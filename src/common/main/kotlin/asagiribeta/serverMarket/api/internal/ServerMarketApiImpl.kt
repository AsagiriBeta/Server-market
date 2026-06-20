package asagiribeta.serverMarket.api.internal

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.api.ServerMarketApi
import asagiribeta.serverMarket.menu.MarketGui
import asagiribeta.serverMarket.model.TransactionRecord
import net.minecraft.server.network.ServerPlayerEntity
import java.util.UUID
import java.util.concurrent.CompletableFuture

internal class ServerMarketApiImpl(private val mod: ServerMarket) : ServerMarketApi {

    override fun getParcelCount(uuid: UUID): CompletableFuture<Int> = mod.database.supplyAsync0 {
        mod.database.parcelRepository.getParcelCountForPlayer(uuid)
    }

    override fun getHistory(uuid: UUID, page: Int, pageSize: Int): CompletableFuture<List<TransactionRecord>> =
        mod.economyService.getHistory(uuid, page, pageSize)

    override fun openMenu(player: ServerPlayerEntity) {
        MarketGui(player).open()
    }

    override fun getModVersion(): String = mod::class.java.`package`?.implementationVersion ?: "unknown"
}
