@file:Suppress("unused")

package asagiribeta.serverMarket.model

import java.util.UUID


/**
 * 交易历史记录数据模型
 *
 * @property dtg 交易时间戳（毫秒）
 * @property fromId 转出方UUID
 * @property fromType 转出方类型（player/system）
 * @property fromName 转出方名称
 * @property toId 接收方UUID
 * @property toType 接收方类型（player/system）
 * @property toName 接收方名称
 * @property amount 交易金额
 * @property item 物品描述（可选）
 */
@Suppress("unused")
data class TransactionRecord(
    val dtg: Long,
    val fromId: UUID,
    val fromType: String,
    val fromName: String,
    val toId: UUID,
    val toType: String,
    val toName: String,
    val amount: Double,
    val item: String? = null
) {
    /**
     * 是否为系统交易
     */
    @Suppress("unused")
    val isSystemTransaction: Boolean
        get() = fromType == "system" || toType == "system"

    /**
     * 是否为玩家间交易
     */
    @Suppress("unused")
    val isPlayerToPlayer: Boolean
        get() = fromType == "player" && toType == "player"
}

