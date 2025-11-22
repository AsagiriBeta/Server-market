package asagiribeta.serverMarket.model

/**
 * 购买结果封装类
 */
sealed class PurchaseResult {
    /**
     * 购买成功
     * @property totalCost 总花费
     * @property amount 购买数量
     * @property items 购买的物品列表（itemId, nbt, amount）
     */
    data class Success(
        val totalCost: Double,
        val amount: Int,
        val items: List<Triple<String, String, Int>>
    ) : PurchaseResult()

    /**
     * 超出每日限购
     * @property remaining 剩余可购买数量
     */
    data class LimitExceeded(val remaining: Int) : PurchaseResult()

    /**
     * 库存不足
     * @property available 可用库存
     */
    data class InsufficientStock(val available: Int) : PurchaseResult()

    /**
     * 余额不足
     * @property required 所需金额
     */
    data class InsufficientFunds(val required: Double) : PurchaseResult()

    /**
     * 商品不存在
     */
    object NotFound : PurchaseResult()

    /**
     * 不允许购买自己的商品
     */
    object CannotBuyOwnItem : PurchaseResult()

    /**
     * 其他错误
     * @property message 错误消息
     */
    data class Error(val message: String) : PurchaseResult()
}

