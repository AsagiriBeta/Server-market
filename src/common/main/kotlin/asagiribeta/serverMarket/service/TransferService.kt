package asagiribeta.serverMarket.service

import asagiribeta.serverMarket.repository.Database
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Transfer business logic — delegates to [EconomyService] for unified history and events.
 */
class TransferService(private val database: Database) {

    private val economy get() = asagiribeta.serverMarket.ServerMarket.instance.economyService

    /** @deprecated Use [EconomyService.TransferOutcome] directly */
    sealed class TransferResult {
        object Success : TransferResult()
        object InsufficientFunds : TransferResult()
        data class Error(val message: String) : TransferResult()
    }

    fun transfer(
        fromUuid: UUID,
        fromName: String,
        toUuid: UUID,
        toName: String,
        amount: Double
    ): CompletableFuture<TransferResult> =
        economy.transfer(fromUuid, fromName, toUuid, toName, amount).thenApply { outcome ->
            when (outcome) {
                is EconomyService.TransferOutcome.Success -> TransferResult.Success
                is EconomyService.TransferOutcome.InsufficientFunds -> TransferResult.InsufficientFunds
                is EconomyService.TransferOutcome.Error -> TransferResult.Error(outcome.message)
            }
        }

    fun getBalance(playerUuid: UUID): CompletableFuture<Double> = economy.getBalance(playerUuid)

    fun setBalance(playerUuid: UUID, amount: Double): CompletableFuture<Boolean> =
        economy.setBalance(playerUuid, amount).thenApply { it.success }

    fun addBalance(playerUuid: UUID, amount: Double): CompletableFuture<Boolean> =
        economy.deposit(playerUuid, amount).thenApply { it.success }

    fun withdraw(playerUuid: UUID, amount: Double): CompletableFuture<Boolean> =
        economy.withdraw(playerUuid, amount).thenApply { it.success }
}
