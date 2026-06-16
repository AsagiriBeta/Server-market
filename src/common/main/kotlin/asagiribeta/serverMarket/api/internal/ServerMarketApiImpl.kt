package asagiribeta.serverMarket.api.internal

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.api.ServerMarketApi
import asagiribeta.serverMarket.api.economy.EconomyTransactionResult
import asagiribeta.serverMarket.menu.MarketGui
import asagiribeta.serverMarket.api.economy.BalanceRankEntry
import asagiribeta.serverMarket.model.TransactionRecord
import asagiribeta.serverMarket.service.EconomyService
import net.minecraft.server.network.ServerPlayerEntity
import java.util.UUID
import java.util.concurrent.CompletableFuture

internal class ServerMarketApiImpl(private val mod: ServerMarket) : ServerMarketApi {

    private val economy: EconomyService get() = mod.economyService

    override fun getBalance(uuid: UUID): CompletableFuture<Double> = economy.getBalance(uuid)

    override fun hasEnough(uuid: UUID, amount: Double): CompletableFuture<Boolean> = economy.hasEnough(uuid, amount)

    override fun getParcelCount(uuid: UUID): CompletableFuture<Int> = mod.database.supplyAsync0 {
        mod.database.parcelRepository.getParcelCountForPlayer(uuid)
    }

    override fun addBalance(uuid: UUID, amount: Double, reason: String?, actor: UUID?): CompletableFuture<EconomyTransactionResult> =
        economy.deposit(uuid, amount, reason, actor)

    override fun withdraw(uuid: UUID, amount: Double, reason: String?, actor: UUID?): CompletableFuture<EconomyTransactionResult> =
        economy.withdraw(uuid, amount, reason, actor)

    override fun setBalance(uuid: UUID, amount: Double, reason: String?, actor: UUID?): CompletableFuture<EconomyTransactionResult> =
        economy.setBalance(uuid, amount, reason, actor)

    override fun transfer(from: UUID, to: UUID, amount: Double, reason: String?, actor: UUID?): CompletableFuture<EconomyTransactionResult> =
        economy.transfer(from, "", to, "", amount, reason, actor).thenApply { outcome ->
            when (outcome) {
                is EconomyService.TransferOutcome.Success -> EconomyTransactionResult(true)
                is EconomyService.TransferOutcome.InsufficientFunds -> EconomyTransactionResult(false, "Insufficient funds")
                is EconomyService.TransferOutcome.Error -> EconomyTransactionResult(false, outcome.message)
            }
        }

    override fun getTopBalances(limit: Int): CompletableFuture<List<BalanceRankEntry>> =
        economy.getTopBalances(limit)

    override fun getHistory(uuid: UUID, page: Int, pageSize: Int): CompletableFuture<List<TransactionRecord>> =
        economy.getHistory(uuid, page, pageSize)

    override fun format(amount: Double): String = economy.format(amount)

    override fun openMenu(player: ServerPlayerEntity) {
        MarketGui(player).open()
    }

    override fun getModVersion(): String = mod::class.java.`package`?.implementationVersion ?: "unknown"
}
