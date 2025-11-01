@file:Suppress("unused")

package asagiribeta.serverMarket.model

import java.util.UUID


/**
 * 市场商品条目数据模型
 *
 * @property itemId 物品ID（如 minecraft:diamond）
 * @property nbt 物品NBT序列化字符串
 * @property price 单价
 * @property quantity 库存数量（-1 表示无限）
 * @property sellerUuid 卖家UUID（null 表示系统）
 * @property sellerName 卖家名称
 * @property limitPerDay 每日限购数量（-1 表示无限制）
 */
data class MarketEntry(
    val itemId: String,
    val nbt: String,
    val price: Double,
    val quantity: Int,
    val sellerUuid: UUID?,
    val sellerName: String,
    val limitPerDay: Int = -1
) {
    /**
     * 是否为系统商品
     */
    val isSystemSeller: Boolean
        get() = sellerUuid == null || sellerName == "SERVER"

    /**
     * 是否为无限库存
     */
    val isUnlimited: Boolean
        get() = quantity < 0

    /**
     * 是否有每日限购
     */
    val hasLimitPerDay: Boolean
        get() = limitPerDay >= 0

    /**
     * 是否为玩家商品
     */
    val isPlayerSeller: Boolean
        get() = !isSystemSeller
}

