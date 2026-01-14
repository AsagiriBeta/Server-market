package asagiribeta.serverMarket.service

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.repository.Database
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * 货币兑换业务逻辑服务
 *
 * 职责：处理实物货币与余额的兑换
 */
class CurrencyService(private val database: Database) {

    private val currencyRepo = database.currencyRepository

    /**
     * 兑换实物货币为余额
     *
     * @param playerUuid 玩家UUID
     * @param itemId 物品ID
     * @param nbt 物品NBT
     * @param amount 数量
     * @return 兑换获得的金额，失败返回 null
     */
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
                // 系统 -> 玩家（存入余额）
                database.transfer(UUID(0, 0), playerUuid, totalValue)
                totalValue
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * 兑换余额为实物货币
     *
     * @param playerUuid 玩家UUID
     * @param itemId 物品ID
     * @param nbt 物品NBT
     * @param amount 数量
     * @return 扣除的金额，失败返回 null
     */
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
                val balance = database.getBalance(playerUuid)
                if (balance < totalCost) {
                    return@supplyAsync null
                }

                // 玩家 -> 系统（扣余额）
                database.transfer(playerUuid, UUID(0, 0), totalCost)
                totalCost
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * 设置货币面值
     *
     * @param itemId 物品ID
     * @param nbt 物品NBT
     * @param value 面值
     */
    fun setCurrencyValue(
        itemId: String,
        nbt: String,
        value: Double
    ): CompletableFuture<Boolean> {
        return currencyRepo.upsertCurrencyAsync(itemId, nbt, value)
            .thenApply { true }
            .exceptionally { e ->
                ServerMarket.LOGGER.error("Failed to set currency value. item={} value={}", itemId, value, e)
                false
            }
    }

    /**
     * 移除货币设置
     *
     * @param itemId 物品ID
     * @param nbt 物品NBT
     */
    fun removeCurrency(
        itemId: String,
        nbt: String
    ): CompletableFuture<Boolean> {
        return database.supplyAsync {
            currencyRepo.deleteCurrency(itemId, nbt)
        }.exceptionally { e ->
            ServerMarket.LOGGER.error("Failed to remove currency mapping. item={}", itemId, e)
            false
        }
    }
}
