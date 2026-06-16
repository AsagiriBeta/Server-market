package asagiribeta.serverMarket.api.economy

import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * VaultUnlocked-style economy provider interface for Fabric mod integration.
 *
 * Other mods can obtain the active provider via [EconomyProviderRegistry.get].
 * ServerMarket registers itself as the default provider on startup.
 */
interface EconomyProvider {
    val name: String
    val currencyNameSingular: String
    val currencyNamePlural: String
    val currencySymbol: String

    fun isEnabled(): Boolean = true

    fun getBalance(uuid: UUID): CompletableFuture<Double>

    fun hasEnough(uuid: UUID, amount: Double): CompletableFuture<Boolean>

    fun deposit(uuid: UUID, amount: Double, reason: String? = null): CompletableFuture<EconomyTransactionResult>

    fun withdraw(uuid: UUID, amount: Double, reason: String? = null): CompletableFuture<EconomyTransactionResult>

    fun transfer(from: UUID, to: UUID, amount: Double, reason: String? = null): CompletableFuture<EconomyTransactionResult>

    fun setBalance(uuid: UUID, amount: Double, reason: String? = null): CompletableFuture<EconomyTransactionResult>

    fun format(amount: Double): String
}
