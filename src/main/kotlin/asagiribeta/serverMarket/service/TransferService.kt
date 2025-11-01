package asagiribeta.serverMarket.service

import asagiribeta.serverMarket.repository.Database
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * 转账业务逻辑服务
 *
 * 职责：处理玩家间转账和余额管理
 *
 * 设计说明：
 * - 使用 database.supplyAsync { ... } 而不是 database.*Async() 方法
 * - 原因：需要在同一个异步上下文中执行多个操作（检查余额 + 执行转账 + 记录历史）
 * - 好处：避免嵌套的 CompletableFuture，保持代码简洁
 */
class TransferService(private val database: Database) {

    private val historyRepo = database.historyRepository

    /**
     * 转账结果
     */
    sealed class TransferResult {
        object Success : TransferResult()
        object InsufficientFunds : TransferResult()
        data class Error(val message: String) : TransferResult()
    }

    /**
     * 玩家间转账
     *
     * @param fromUuid 转出方UUID
     * @param fromName 转出方名称
     * @param toUuid 接收方UUID
     * @param toName 接收方名称
     * @param amount 金额
     * @return 转账结果
     */
    fun transfer(
        fromUuid: UUID,
        fromName: String,
        toUuid: UUID,
        toName: String,
        amount: Double
    ): CompletableFuture<TransferResult> {
        return database.supplyAsync {
            try {
                // 检查余额
                val balance = database.getBalance(fromUuid)
                if (balance < amount) {
                    return@supplyAsync TransferResult.InsufficientFunds
                }

                // 执行转账
                database.transfer(fromUuid, toUuid, amount)

                // 记录历史
                historyRepo.postHistory(
                    dtg = System.currentTimeMillis(),
                    fromId = fromUuid,
                    fromType = "player",
                    fromName = fromName,
                    toId = toUuid,
                    toType = "player",
                    toName = toName,
                    price = amount,
                    item = "transfer"
                )

                TransferResult.Success
            } catch (e: Exception) {
                TransferResult.Error(e.message ?: "转账失败")
            }
        }
    }

    /**
     * 查询余额
     *
     * @param playerUuid 玩家UUID
     * @return 余额
     */
    fun getBalance(playerUuid: UUID): CompletableFuture<Double> {
        return database.supplyAsync {
            database.getBalance(playerUuid)
        }
    }

    /**
     * 设置余额（管理员操作）
     *
     * @param playerUuid 玩家UUID
     * @param amount 金额
     */
    fun setBalance(playerUuid: UUID, amount: Double): CompletableFuture<Boolean> {
        return database.supplyAsync {
            try {
                database.setBalance(playerUuid, amount)
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * 增加余额（管理员操作）
     *
     * @param playerUuid 玩家UUID
     * @param amount 金额
     */
    fun addBalance(playerUuid: UUID, amount: Double): CompletableFuture<Boolean> {
        return database.supplyAsync {
            try {
                val current = database.getBalance(playerUuid)
                database.setBalance(playerUuid, current + amount)
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}

