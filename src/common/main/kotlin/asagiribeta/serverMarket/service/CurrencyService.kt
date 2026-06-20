package asagiribeta.serverMarket.service

import asagiribeta.serverMarket.repository.Database
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * 货币兑换业务逻辑服务
 */
class CurrencyService(
    private val database: Database,
    private val economy: EconomyService
) {

    private val currencyRepo = database.currencyRepository
    private val systemUuid = economy.systemUuid

    fun exchangeCurrencyToBalance(
        playerUuid: UUID,
        itemId: String,
        nbt: String,
        amount: Int
    ): CompletableFuture<Double?> {
        return database.supplyAsync {
            try {
                val value = currencyRepo.getCurrencyValue(itemId, nbt)
                if (value == null || value <= 0.0) {
                    return@supplyAsync null
                }

                val totalValue = value * amount
                economy.transferFundsSync(
                    fromUuid = systemUuid,
                    toUuid = playerUuid,
                    amount = totalValue,
                    reason = "currency_cash_in"
                )
                totalValue
            } catch (_: Exception) {
                null
            }
        }
    }

    fun exchangeBalanceToCurrency(
        playerUuid: UUID,
        itemId: String,
        nbt: String,
        amount: Int
    ): CompletableFuture<Double?> {
        return database.supplyAsync {
            try {
                val value = currencyRepo.getCurrencyValue(itemId, nbt)
                if (value == null || value <= 0.0) {
                    return@supplyAsync null
                }

                val totalCost = value * amount
                if (database.getBalance(playerUuid) < totalCost) {
                    return@supplyAsync null
                }

                economy.transferFundsSync(
                    fromUuid = playerUuid,
                    toUuid = systemUuid,
                    amount = totalCost,
                    reason = "currency_cash_out"
                )
                totalCost
            } catch (_: Exception) {
                null
            }
        }
    }

    fun setCurrencyValue(
        itemId: String,
        nbt: String,
        value: Double
    ): CompletableFuture<Boolean> {
        return currencyRepo.upsertCurrencyAsync(itemId, nbt, value)
            .thenApply { true }
            .exceptionally { e ->
                asagiribeta.serverMarket.ServerMarket.LOGGER.error("Failed to set currency value", e)
                false
            }
    }

    fun removeCurrency(
        itemId: String,
        nbt: String
    ): CompletableFuture<Boolean> {
        return database.supplyAsync {
            currencyRepo.deleteCurrency(itemId, nbt)
        }.exceptionally { e ->
            asagiribeta.serverMarket.ServerMarket.LOGGER.error("Failed to remove currency mapping. item={}", itemId, e)
            false
        }
    }
}
