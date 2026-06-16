package asagiribeta.serverMarket.model

import java.util.*

/**
 * 收购菜单条目数据模型
 */
data class PurchaseMenuEntry(
    val itemId: String,
    val nbt: String,
    val price: Double,
    val buyerName: String,
    val buyerUuid: UUID?,
    val limitPerDay: Int = -1,
    val targetAmount: Int = 0,
    val currentAmount: Int = 0
)

