package asagiribeta.serverMarket.api.internal

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.api.ServerMarketApi
import asagiribeta.serverMarket.api.ServerMarketEvents
import asagiribeta.serverMarket.api.economy.EconomyTransactionResult
import asagiribeta.serverMarket.menu.MarketGui
import net.minecraft.server.network.ServerPlayerEntity
import java.util.UUID
import java.util.concurrent.CompletableFuture

internal class ServerMarketApiImpl(private val mod: ServerMarket) : ServerMarketApi {
    override fun getBalance(uuid: UUID): CompletableFuture<Double> = mod.database.supplyAsync0 {
        mod.database.getBalance(uuid)
    }

    override fun getParcelCount(uuid: UUID): CompletableFuture<Int> = mod.database.supplyAsync0 {
        mod.database.parcelRepository.getParcelCountForPlayer(uuid)
    }

    override fun addBalance(uuid: UUID, amount: Double, reason: String?, actor: UUID?): CompletableFuture<EconomyTransactionResult> {
        return mod.database.supplyAsync0 {
            if (amount.isNaN() || amount.isInfinite()) return@supplyAsync0 EconomyTransactionResult(false, "Invalid amount")
            try {
                mod.database.addBalance(uuid, amount)
                val newBal = mod.database.getBalance(uuid)
                mod.server?.execute {
                    ServerMarketEvents.BALANCE_CHANGED.invoker().onBalanceChanged(uuid, amount, reason, actor)
                }
                EconomyTransactionResult(true, newBalance = newBal)
            } catch (t: Throwable) {
                EconomyTransactionResult(false, t.message)
            }
        }
    }

    override fun withdraw(uuid: UUID, amount: Double, reason: String?, actor: UUID?): CompletableFuture<EconomyTransactionResult> {
        return mod.database.supplyAsync { conn ->
            if (amount.isNaN() || amount.isInfinite() || amount <= 0.0) return@supplyAsync EconomyTransactionResult(false, "Invalid amount")
            try {
                val ok = mod.database.withdrawIfEnough(conn, uuid, amount)
                if (!ok) return@supplyAsync EconomyTransactionResult(false, "Insufficient funds", newBalance = mod.database.getBalance(uuid))

                val newBal = mod.database.getBalance(uuid)
                mod.server?.execute {
                    ServerMarketEvents.BALANCE_CHANGED.invoker().onBalanceChanged(uuid, -amount, reason, actor)
                }
                EconomyTransactionResult(true, newBalance = newBal)
            } catch (t: Throwable) {
                EconomyTransactionResult(false, t.message)
            }
        }
    }

    override fun transfer(from: UUID, to: UUID, amount: Double, reason: String?, actor: UUID?): CompletableFuture<EconomyTransactionResult> {
        return mod.database.supplyAsync0 {
            if (amount.isNaN() || amount.isInfinite() || amount <= 0.0) return@supplyAsync0 EconomyTransactionResult(false, "Invalid amount")
            try {
                mod.database.transfer(from, to, amount)
                mod.server?.execute {
                    ServerMarketEvents.BALANCE_CHANGED.invoker().onBalanceChanged(from, -amount, reason, actor)
                    ServerMarketEvents.BALANCE_CHANGED.invoker().onBalanceChanged(to, amount, reason, actor)
                }
                EconomyTransactionResult(true)
            } catch (t: Throwable) {
                EconomyTransactionResult(false, t.message)
            }
        }
    }

    override fun openMenu(player: ServerPlayerEntity) {
        MarketGui(player).open()
    }

    override fun getModVersion(): String = mod::class.java.`package`?.implementationVersion ?: "unknown"
}
