package asagiribeta.serverMarket.service

import asagiribeta.serverMarket.model.PurchaseMenuEntry
import asagiribeta.serverMarket.model.PurchaseOrder
import asagiribeta.serverMarket.model.SellToBuyerResult
import asagiribeta.serverMarket.repository.Database
import asagiribeta.serverMarket.repository.PlayerPurchaseEntry
import asagiribeta.serverMarket.util.ItemKey
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
class PurchaseService(
    private val database: Database,
    private val economy: EconomyService
) {

    private val purchaseRepo = database.purchaseRepository
    private val systemUuid = economy.systemUuid

    fun createPlayerPurchase(
        buyerUuid: UUID,
        buyerName: String,
        itemId: String,
        nbt: String,
        price: Double,
        targetAmount: Int
    ): CompletableFuture<Unit> {
        return database.supplyAsync {
            purchaseRepo.addPlayerPurchase(buyerUuid, buyerName, itemId, nbt, price, targetAmount)
        }
    }

    fun getPlayerPurchases(buyerUuid: UUID): CompletableFuture<List<PlayerPurchaseEntry>> {
        return database.supplyAsync {
            purchaseRepo.getPlayerPurchasesByBuyer(buyerUuid)
        }
    }

    fun cancelPlayerPurchase(
        buyerUuid: UUID,
        itemId: String,
        nbt: String
    ): CompletableFuture<Boolean> {
        return database.supplyAsync {
            val normalizedNbt = ItemKey.normalizeSnbt(nbt)
            val exists = purchaseRepo.getPlayerPurchasesByBuyer(buyerUuid)
                .any { it.itemId == itemId && ItemKey.normalizeSnbt(it.nbt) == normalizedNbt }
            if (!exists) return@supplyAsync false
            purchaseRepo.removePlayerPurchase(buyerUuid, itemId, normalizedNbt)
            true
        }
    }

    fun getPurchaseMenuEntries(): CompletableFuture<List<PurchaseMenuEntry>> {
        return database.supplyAsync {
            val allPurchases = mutableListOf<PurchaseMenuEntry>()
            purchaseRepo.getAllSystemPurchases().forEach { order ->
                allPurchases.add(
                    PurchaseMenuEntry(
                        itemId = order.itemId,
                        nbt = order.nbt,
                        price = order.price,
                        buyerName = "SERVER",
                        buyerUuid = null,
                        limitPerDay = order.limitPerDay
                    )
                )
            }
            purchaseRepo.getAllPlayerPurchases()
                .filter { !it.isCompleted }
                .forEach { entry ->
                    allPurchases.add(
                        PurchaseMenuEntry(
                            itemId = entry.itemId,
                            nbt = entry.nbt,
                            price = entry.price,
                            buyerName = entry.buyerName,
                            buyerUuid = entry.buyerUuid,
                            limitPerDay = -1,
                            targetAmount = entry.targetAmount,
                            currentAmount = entry.currentAmount
                        )
                    )
                }
            allPurchases
        }
    }

    /** @return true if a system purchase already existed (update), false if newly added */
    fun upsertSystemPurchase(
        itemId: String,
        nbt: String,
        price: Double,
        limitPerDay: Int
    ): CompletableFuture<Boolean> {
        return database.supplyAsync {
            val normalized = ItemKey.normalizeSnbt(nbt)
            val existed = purchaseRepo.hasSystemPurchase(itemId, normalized)
            purchaseRepo.addSystemPurchase(itemId, normalized, price, limitPerDay)
            existed
        }
    }

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
    fun sellToBuyer(
        sellerUuid: UUID,
        sellerName: String,
        itemId: String,
        nbt: String,
        quantity: Int,
        buyerFilter: String? = null
    ): CompletableFuture<SellToBuyerResult> {
        return database.supplyAsync {
            try {
                doSellToBuyer(sellerUuid, sellerName, itemId, nbt, quantity, buyerFilter)
            } catch (e: Exception) {
                SellToBuyerResult.Error(e.message ?: "未知错误")
            }
        }
    }

    /**
     * 玩家向收购者出售物品（同步版本，在数据库线程中调用）
     */
    private fun doSellToBuyer(
        sellerUuid: UUID,
        sellerName: String,
        itemId: String,
        nbt: String,
        quantity: Int,
        buyerFilter: String?
    ): SellToBuyerResult {
        val today = LocalDate.now().toString()
        val normalizedNbt = ItemKey.normalizeSnbt(nbt)

        // 1. 查询收购订单（优先系统，然后玩家）
        val systemOrder = if (buyerFilter == null || buyerFilter == "SERVER") {
            purchaseRepo.getSystemPurchaseOrder(itemId, normalizedNbt)
        } else null

        val playerOrders = if (buyerFilter != "SERVER") {
            if (buyerFilter != null) {
                // 指定玩家
                val uuid = try { UUID.fromString(buyerFilter) } catch (_: Exception) { null }
                if (uuid != null) {
                    val order = purchaseRepo.getPlayerPurchaseOrder(uuid, itemId, normalizedNbt)
                    if (order != null) listOf(order) else emptyList()
                } else emptyList()
            } else {
                // 查询所有玩家收购订单
                purchaseRepo.getPlayerPurchaseOrders(itemId, normalizedNbt).map {
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
            val sold = purchaseRepo.getSystemSoldOn(today, sellerUuid, itemId, normalizedNbt)
            val remaining = (limit - sold).coerceAtLeast(0)

            if (remaining <= 0) {
                return SellToBuyerResult.LimitExceeded(0)
            }

            actualQuantity = quantity.coerceAtMost(remaining)
        }

        // 4. 检查玩家收购订单的剩余需求量
        if (!order.isSystemBuyer) {
            val buyerUuid = order.buyerUuid ?: return SellToBuyerResult.Error("无效的收购者UUID")
            val current = purchaseRepo.getPlayerPurchaseCurrentAmount(buyerUuid, itemId, normalizedNbt)
            val target = purchaseRepo.getPlayerPurchaseTargetAmount(buyerUuid, itemId, normalizedNbt)
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
            if (database.getBalance(buyerUuid) < totalEarned) {
                return SellToBuyerResult.InsufficientFunds(totalEarned)
            }
        }

        // 7. 执行交易
        try {
            if (!order.isSystemBuyer) {
                val buyerUuid = order.buyerUuid ?: return SellToBuyerResult.Error("无效的收购者UUID")
                purchaseRepo.incrementPlayerPurchaseAmount(buyerUuid, itemId, normalizedNbt, actualQuantity)

                database.parcelRepository.addParcel(
                    recipientUuid = buyerUuid,
                    recipientName = order.buyerName,
                    itemId = itemId,
                    nbt = normalizedNbt,
                    quantity = actualQuantity,
                    reason = "servermarket.parcel.reason.purchase"
                )

                economy.transferFundsSync(
                    fromUuid = buyerUuid,
                    toUuid = sellerUuid,
                    amount = totalEarned,
                    reason = "supply_sale",
                    history = EconomyService.HistoryContext(
                        fromId = buyerUuid, fromType = "player", fromName = order.buyerName,
                        toId = sellerUuid, toType = "player", toName = sellerName,
                        price = totalEarned, item = itemId
                    )
                )
            } else {
                economy.transferFundsSync(
                    fromUuid = systemUuid,
                    toUuid = sellerUuid,
                    amount = totalEarned,
                    reason = "system_supply_sale",
                    history = EconomyService.HistoryContext(
                        fromId = systemUuid, fromType = "system", fromName = "MARKET",
                        toId = sellerUuid, toType = "player", toName = sellerName,
                        price = totalEarned, item = itemId
                    )
                )
            }

            if (order.isSystemBuyer && order.hasLimitPerDay) {
                purchaseRepo.incrementSystemSoldOn(today, sellerUuid, itemId, normalizedNbt, actualQuantity)
            }

            return SellToBuyerResult.Success(actualQuantity, totalEarned)

        } catch (e: Exception) {
            return SellToBuyerResult.Error(e.message ?: "交易失败")
        }
    }
}
