package asagiribeta.serverMarket.api.economy

import asagiribeta.serverMarket.service.EconomyService
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Default [EconomyProvider] backed by ServerMarket's [EconomyService].
 */
internal class ServerMarketEconomyProvider(
    private val economy: EconomyService
) : EconomyProvider {

    override val name: String = "ServerMarket"
    override val currencyNameSingular: String = "coin"
    override val currencyNamePlural: String = "coins"
    override val currencySymbol: String = "$"

    override fun getBalance(uuid: UUID): CompletableFuture<Double> = economy.getBalance(uuid)

    override fun hasEnough(uuid: UUID, amount: Double): CompletableFuture<Boolean> = economy.hasEnough(uuid, amount)

    override fun deposit(uuid: UUID, amount: Double, reason: String?): CompletableFuture<EconomyTransactionResult> =
        economy.deposit(uuid, amount, reason)

    override fun withdraw(uuid: UUID, amount: Double, reason: String?): CompletableFuture<EconomyTransactionResult> =
        economy.withdraw(uuid, amount, reason)

    override fun transfer(from: UUID, to: UUID, amount: Double, reason: String?): CompletableFuture<EconomyTransactionResult> =
        economy.transfer(from, "", to, "", amount, reason).thenApply { outcome ->
            when (outcome) {
                is EconomyService.TransferOutcome.Success -> EconomyTransactionResult(true)
                is EconomyService.TransferOutcome.InsufficientFunds -> EconomyTransactionResult(false, "Insufficient funds")
                is EconomyService.TransferOutcome.Error -> EconomyTransactionResult(false, outcome.message)
            }
        }

    override fun setBalance(uuid: UUID, amount: Double, reason: String?): CompletableFuture<EconomyTransactionResult> =
        economy.setBalance(uuid, amount, reason)

    override fun format(amount: Double): String = economy.format(amount)
}
