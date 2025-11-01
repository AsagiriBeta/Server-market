@file:Suppress("unused")

package asagiribeta.serverMarket.model

/**
 * 货币物品数据模型
 *
 * @property itemId 物品ID
 * @property nbt 物品NBT序列化字符串
 * @property value 货币面值
 */
data class CurrencyItem(
    val itemId: String,
    val nbt: String,
    val value: Double
) {
    /**
     * 是否为有效货币（面值大于0）
     */
    val isValid: Boolean
        get() = value > 0.0
}

