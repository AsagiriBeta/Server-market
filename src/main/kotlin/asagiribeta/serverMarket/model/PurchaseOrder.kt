package asagiribeta.serverMarket.model

import java.util.UUID

/**
 * 收购订单数据模型
 *
 * @property itemId 物品ID（如 minecraft:diamond）
 * @property nbt 物品NBT序列化字符串
 * @property price 收购单价
 * @property buyerUuid 收购者UUID（null 表示系统收购）
 * @property buyerName 收购者名称
 * @property limitPerDay 每日限额（-1 表示无限制，仅系统收购有效）
 */
data class PurchaseOrder(
    val itemId: String,
    val nbt: String,
    val price: Double,
    val buyerUuid: UUID?,
    val buyerName: String,
    val limitPerDay: Int = -1
) {
    /**
     * 是否为系统收购
     */
    val isSystemBuyer: Boolean
        get() = buyerUuid == null || buyerName == "SERVER"

    /**
     * 是否为无限收购（系统收购）
     */
    val isUnlimited: Boolean
        get() = isSystemBuyer

    /**
     * 是否有每日限额
     */
    val hasLimitPerDay: Boolean
        get() = limitPerDay >= 0 && isSystemBuyer

    /**
     * 是否为玩家收购
     */
    val isPlayerBuyer: Boolean
        get() = !isSystemBuyer
}

/**
 * 出售结果（玩家向收购者出售物品）
 */
sealed class SellToBuyerResult {
    /**
     * 成功
     * @property amount 出售数量
     * @property totalEarned 获得金额
     */
    data class Success(val amount: Int, val totalEarned: Double) : SellToBuyerResult()

    /**
     * 未找到收购订单
     */
    data object NotFound : SellToBuyerResult()

    /**
     * 超出每日限额
     * @property remaining 剩余可出售数量
     */
    data class LimitExceeded(val remaining: Int) : SellToBuyerResult()

    /**
     * 收购者余额不足
     * @property required 需要的金额
     */
    data class InsufficientFunds(val required: Double) : SellToBuyerResult()

    /**
     * 卖家物品不足
     */
    data object InsufficientItems : SellToBuyerResult()

    /**
     * 错误
     * @property message 错误消息
     */
    data class Error(val message: String) : SellToBuyerResult()
}

