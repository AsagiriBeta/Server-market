package asagiribeta.serverMarket.model

import java.util.UUID

/**
 * 快递包裹条目
 */
data class ParcelEntry(
    val id: Long,
    val recipientUuid: UUID,
    val recipientName: String,
    val itemId: String,
    val nbt: String,
    val quantity: Int,
    val timestamp: Long,
    val reason: String  // 说明：例如"收购物品", "市场购买" 等
)

