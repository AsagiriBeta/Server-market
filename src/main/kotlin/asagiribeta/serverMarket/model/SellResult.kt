@file:Suppress("unused")

package asagiribeta.serverMarket.model

/**
 * 出售结果封装类
 */
sealed class SellResult {
    /**
     * 出售成功
     * @property totalEarned 总收入
     * @property amount 出售数量
     */
    data class Success(
        val totalEarned: Double,
        val amount: Int
    ) : SellResult()

    /**
     * 物品数量不足
     * @property required 需要数量
     * @property available 可用数量
     */
    @Suppress("unused")
    data class InsufficientItems(
        val required: Int,
        val available: Int
    ) : SellResult()

    /**
     * 价格无效
     */
    object InvalidPrice : SellResult()

    /**
     * 其他错误
     * @property message 错误消息
     */
    data class Error(val message: String) : SellResult()
}

