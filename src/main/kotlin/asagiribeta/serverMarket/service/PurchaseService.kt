package asagiribeta.serverMarket.service

import asagiribeta.serverMarket.model.*
import asagiribeta.serverMarket.repository.Database
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * 收购业务逻辑服务
 *
 * 职责：处理玩家向收购者出售物品的业务逻辑
 *
 * 架构说明：
 * - 本服务在事务中直接访问 parcelRepository，这是合理的设计
 * - 因为添加包裹是交易事务的一部分，需要在同一个数据库连接中完成
 * - ParcelService 提供的异步方法供其他场景（如 GUI）使用
 */
class PurchaseService(private val database: Database) {

    private val purchaseRepo = database.purchaseRepository
    private val parcelRepo = database.parcelRepository

    /**
     * 玩家向收购者出售物品（异步）
     *
     * @param sellerUuid 卖家UUID
     * @param sellerName 卖家名称
     * @param itemId 物品ID
     * @param nbt 物品NBT
     * @param quantity 出售数量
     * @param buyerFilter 收购者过滤（null=优先系统，"SERVER"=仅系统，UUID=指定玩家）
     * @return 出售结果
     */
    fun sellToBuyerAsync(
        sellerUuid: UUID,
        sellerName: String,
        itemId: String,
        nbt: String,
        quantity: Int,
        buyerFilter: String? = null
    ): CompletableFuture<SellToBuyerResult> {
        return database.supplyAsync {
            try {
                sellToBuyer(sellerUuid, sellerName, itemId, nbt, quantity, buyerFilter)
            } catch (e: Exception) {
                SellToBuyerResult.Error(e.message ?: "未知错误")
            }
        }
    }

    /**
     * 玩家向收购者出售物品（同步版本，在数据库线程中调用）
     */
    private fun sellToBuyer(
        sellerUuid: UUID,
        sellerName: String,
        itemId: String,
        nbt: String,
        quantity: Int,
        buyerFilter: String?
    ): SellToBuyerResult {
        val today = LocalDate.now().toString()

        // 1. 查询收购订单（优先系统，然后玩家）
        val systemOrder = if (buyerFilter == null || buyerFilter == "SERVER") {
            purchaseRepo.getSystemPurchaseOrder(itemId, nbt)
        } else null

        val playerOrders = if (buyerFilter != "SERVER") {
            if (buyerFilter != null) {
                // 指定玩家
                val uuid = try { UUID.fromString(buyerFilter) } catch (_: Exception) { null }
                if (uuid != null) {
                    val order = purchaseRepo.getPlayerPurchaseOrder(uuid, itemId, nbt)
                    if (order != null) listOf(order) else emptyList()
                } else emptyList()
            } else {
                // 查询所有玩家收购订单
                purchaseRepo.getPlayerPurchaseOrders(itemId, nbt).map {
                    PurchaseOrder(
                        itemId = it.itemId,
                        nbt = it.nbt,
                        price = it.price,
                        buyerUuid = it.buyerUuid,
                        buyerName = it.buyerName,
                        limitPerDay = -1
                    )
                }
            }
        } else emptyList()

        if (systemOrder == null && playerOrders.isEmpty()) {
            return SellToBuyerResult.NotFound
        }

        // 2. 选择最优收购订单（系统优先，或价格最高的玩家）
        val order = systemOrder ?: playerOrders.maxByOrNull { it.price } ?: return SellToBuyerResult.NotFound

        // 3. 检查限额（仅系统收购）
        var actualQuantity = quantity
        if (order.isSystemBuyer && order.hasLimitPerDay) {
            val limit = order.limitPerDay
            val sold = purchaseRepo.getSystemSoldOn(today, sellerUuid, itemId, nbt)
            val remaining = (limit - sold).coerceAtLeast(0)

            if (remaining <= 0) {
                return SellToBuyerResult.LimitExceeded(0)
            }

            actualQuantity = quantity.coerceAtMost(remaining)
        }

        // 4. 检查玩家收购订单的剩余需求量
        if (!order.isSystemBuyer) {
            val buyerUuid = order.buyerUuid ?: return SellToBuyerResult.Error("无效的收购者UUID")
            val current = purchaseRepo.getPlayerPurchaseCurrentAmount(buyerUuid, itemId, nbt)
            val target = purchaseRepo.getPlayerPurchaseTargetAmount(buyerUuid, itemId, nbt)
            val remaining = (target - current).coerceAtLeast(0)

            if (remaining <= 0) {
                return SellToBuyerResult.NotFound
            }

            actualQuantity = actualQuantity.coerceAtMost(remaining)
        }

        // 5. 计算总价
        val totalEarned = actualQuantity * order.price

        // 6. 检查收购者余额（玩家收购）
        if (!order.isSystemBuyer) {
            val buyerUuid = order.buyerUuid ?: return SellToBuyerResult.Error("无效的收购者UUID")
            val buyerBalance = database.getBalance(buyerUuid)

            if (buyerBalance < totalEarned) {
                return SellToBuyerResult.InsufficientFunds(totalEarned)
            }
        }

        // 7. 执行交易
        try {
            // 扣除收购者余额（玩家收购）
            if (!order.isSystemBuyer) {
                val buyerUuid = order.buyerUuid ?: return SellToBuyerResult.Error("无效的收购者UUID")
                database.addBalance(buyerUuid, -totalEarned)

                // 增加玩家收购订单的当前数量
                purchaseRepo.incrementPlayerPurchaseAmount(buyerUuid, itemId, nbt, actualQuantity)

                // 将物品发送到快递驿站
                database.parcelRepository.addParcel(
                    recipientUuid = buyerUuid,
                    recipientName = order.buyerName,
                    itemId = itemId,
                    nbt = nbt,
                    quantity = actualQuantity,
                    reason = "收购物品"
                )
            }

            // 增加卖家余额
            database.addBalance(sellerUuid, totalEarned)

            // 记录限额（系统收购）
            if (order.isSystemBuyer && order.hasLimitPerDay) {
                purchaseRepo.incrementSystemSoldOn(today, sellerUuid, itemId, nbt, actualQuantity)
            }

            // 记录交易历史
            database.historyRepository.postHistory(
                dtg = System.currentTimeMillis(),
                fromId = order.buyerUuid ?: UUID(0, 0),
                fromType = if (order.isSystemBuyer) "SYSTEM" else "PLAYER",
                fromName = order.buyerName,
                toId = sellerUuid,
                toType = "PLAYER",
                toName = sellerName,
                price = totalEarned,
                item = itemId
            )

            return SellToBuyerResult.Success(actualQuantity, totalEarned)

        } catch (e: Exception) {
            return SellToBuyerResult.Error(e.message ?: "交易失败")
        }
    }
}

