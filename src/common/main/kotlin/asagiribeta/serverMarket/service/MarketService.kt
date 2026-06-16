package asagiribeta.serverMarket.service

import asagiribeta.serverMarket.model.*
import asagiribeta.serverMarket.repository.Database
import asagiribeta.serverMarket.repository.MarketItem
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * 市场交易业务逻辑服务
 *
 * 职责：处理商品购买、出售等业务逻辑
 * 优势：可被命令和GUI复用，易于单元测试
 */
class MarketService(private val database: Database) {

    private val marketRepo = database.marketRepository
    private val historyRepo = database.historyRepository

    /**
     * 购买商品（异步）
     *
     * @param playerUuid 玩家UUID
     * @param playerName 玩家名称
     * @param itemId 物品ID
     * @param quantity 购买数量
     * @param seller 指定卖家（null表示不限制）
     * @return 购买结果
     */
    fun purchaseItem(
        playerUuid: UUID,
        playerName: String,
        itemId: String,
        quantity: Int,
        seller: String? = null
    ): CompletableFuture<PurchaseResult> {
        return database.supplyAsync {
            try {
                doPurchaseItem(playerUuid, playerName, itemId, quantity, seller)
            } catch (e: Exception) {
                PurchaseResult.Error(e.message ?: "未知错误")
            }
        }
    }

    /**
     * 购买商品（同步版本，在数据库线程中调用）
     */
    private fun doPurchaseItem(
        playerUuid: UUID,
        playerName: String,
        itemId: String,
        quantity: Int,
        seller: String?
    ): PurchaseResult {
        val today = LocalDate.now().toString()

        // 1. 查询可用商品
        val items = if (seller == null) {
            marketRepo.searchForTransaction(itemId)
        } else {
            marketRepo.searchForTransaction(itemId, seller)
        }

        if (items.isEmpty()) {
            return PurchaseResult.NotFound
        }

        // If multiple NBT variants exist for the same item id, do NOT guess.
        // Players should use GUI to pick the exact variant.
        val variantCount = items.asSequence()
            .map { it.nbt }
            .map { asagiribeta.serverMarket.util.ItemKey.normalizeSnbt(it) }
            .distinct()
            .count()
        if (variantCount > 1) {
            return PurchaseResult.AmbiguousVariants(itemId = itemId, variantCount = variantCount)
        }

        // 2. 检查是否尝试购买自己的商品（防止旅行者背包等容器物品刷物品的漏洞）
        val hasOwnItems = items.any { entry ->
            entry.sellerName != "SERVER" &&
            try {
                UUID.fromString(entry.sellerName) == playerUuid
            } catch (_: Exception) {
                false
            }
        }

        if (hasOwnItems) {
            return PurchaseResult.CannotBuyOwnItem
        }

        // 3. 检查总可用数量（考虑限购）
        var totalAvailable = 0
        for (entry in items) {
            if (entry.sellerName == "SERVER") {
                val limit = marketRepo.getSystemLimitPerDay(entry.itemId, entry.nbt)
                if (limit < 0) {
                    // 无限购
                    totalAvailable = Int.MAX_VALUE
                    break
                } else {
                    // 检查今日已购买数量
                    val purchased = marketRepo.getSystemPurchasedOn(today, playerUuid, entry.itemId, entry.nbt)
                    val remaining = (limit - purchased).coerceAtLeast(0)
                    totalAvailable = totalAvailable.saturatingAdd(remaining)
                }
            } else {
                // 玩家商品
                totalAvailable = if (totalAvailable == Int.MAX_VALUE) {
                    Int.MAX_VALUE
                } else {
                    totalAvailable.saturatingAdd(entry.quantity)
                }
            }
        }

        if (totalAvailable < quantity) {
            return PurchaseResult.InsufficientStock(totalAvailable)
        }

        // 3. 计算购买计划
        var remaining = quantity
        var totalCost = 0.0
        val purchaseList = mutableListOf<Pair<MarketItem, Int>>()

        for (entry in items) {
            if (remaining <= 0) break

            val purchaseAmount = if (entry.sellerName == "SERVER") {
                val limit = marketRepo.getSystemLimitPerDay(entry.itemId, entry.nbt)
                if (limit < 0) {
                    remaining
                } else {
                    val purchased = marketRepo.getSystemPurchasedOn(today, playerUuid, entry.itemId, entry.nbt)
                    val allowed = (limit - purchased).coerceAtLeast(0)
                    minOf(remaining, allowed)
                }
            } else {
                minOf(remaining, entry.quantity)
            }

            if (purchaseAmount <= 0) continue

            totalCost += entry.price * purchaseAmount
            purchaseList.add(entry to purchaseAmount)
            remaining -= purchaseAmount
        }

        // 4. 检查余额
        val balance = database.getBalance(playerUuid)
        if (balance < totalCost) {
            return PurchaseResult.InsufficientFunds(totalCost)
        }

        // 5. 执行交易
        // 玩家支付到系统
        database.transfer(playerUuid, UUID(0, 0), totalCost)
        val dtg = System.currentTimeMillis()
        historyRepo.postHistory(
            dtg = dtg,
            fromId = playerUuid,
            fromType = "player",
            fromName = playerName,
            toId = UUID(0, 0),
            toType = "system",
            toName = "MARKET",
            price = totalCost,
            item = "$itemId x$quantity"
        )

        // 6. 处理每个卖家的商品
        for ((marketItem, amount) in purchaseList) {
            if (marketItem.sellerName != "SERVER") {
                // 玩家商品：扣库存 + 转账给卖家
                marketRepo.incrementPlayerItemQuantity(
                    UUID.fromString(marketItem.sellerName),
                    marketItem.itemId,
                    marketItem.nbt,
                    -amount
                )
                database.transfer(UUID(0, 0), UUID.fromString(marketItem.sellerName), marketItem.price * amount)
                historyRepo.postHistory(
                    dtg = dtg,
                    fromId = UUID(0, 0),
                    fromType = "system",
                    fromName = "MARKET",
                    toId = UUID.fromString(marketItem.sellerName),
                    toType = "player",
                    toName = marketItem.sellerName,
                    price = marketItem.price * amount,
                    item = "${marketItem.itemId} x$amount"
                )
            } else {
                // 系统商品：记录限购
                marketRepo.incrementSystemPurchasedOn(today, playerUuid, marketItem.itemId, marketItem.nbt, amount)
            }
        }

        // 7. 返回结果
        val itemsToGive = purchaseList.map {
            Triple(it.first.itemId, it.first.nbt, it.second)
        }
        return PurchaseResult.Success(totalCost, quantity, itemsToGive)
    }

    /**
     * Purchase a specific variant of an item (itemId + normalized NBT).
     *
     * This is intended for GUI clicks where the player selects a concrete product entry.
     */
    fun purchaseItemVariant(
        playerUuid: UUID,
        playerName: String,
        itemId: String,
        nbt: String,
        quantity: Int,
        seller: String? = null
    ): CompletableFuture<PurchaseResult> {
        return database.supplyAsync {
            try {
                doPurchaseItemVariant(playerUuid, playerName, itemId, nbt, quantity, seller)
            } catch (e: Exception) {
                PurchaseResult.Error(e.message ?: "未知错误")
            }
        }
    }

    private fun doPurchaseItemVariant(
        playerUuid: UUID,
        playerName: String,
        itemId: String,
        nbt: String,
        quantity: Int,
        seller: String?
    ): PurchaseResult {
        val today = LocalDate.now().toString()
        val normalizedNbt = asagiribeta.serverMarket.util.ItemKey.normalizeSnbt(nbt)

        // 1) fetch candidates (respect seller filter if present)
        val candidates = if (seller == null) {
            marketRepo.searchForTransaction(itemId)
        } else {
            marketRepo.searchForTransaction(itemId, seller)
        }

        // 2) keep only the exact variant
        val items = candidates.filter {
            asagiribeta.serverMarket.util.ItemKey.normalizeSnbt(it.nbt) == normalizedNbt
        }

        if (items.isEmpty()) return PurchaseResult.NotFound

        // 3) prevent buying own items
        val hasOwnItems = items.any { entry ->
            entry.sellerName != "SERVER" &&
                try { UUID.fromString(entry.sellerName) == playerUuid } catch (_: Exception) { false }
        }
        if (hasOwnItems) return PurchaseResult.CannotBuyOwnItem

        // 4) compute availability (system limits / player stock)
        var totalAvailable = 0
        for (entry in items) {
            if (entry.sellerName == "SERVER") {
                val limit = marketRepo.getSystemLimitPerDay(entry.itemId, entry.nbt)
                if (limit < 0) {
                    totalAvailable = Int.MAX_VALUE
                    break
                } else {
                    val purchased = marketRepo.getSystemPurchasedOn(today, playerUuid, entry.itemId, entry.nbt)
                    val remaining = (limit - purchased).coerceAtLeast(0)
                    totalAvailable = totalAvailable.saturatingAdd(remaining)
                }
            } else {
                totalAvailable = if (totalAvailable == Int.MAX_VALUE) Int.MAX_VALUE else totalAvailable.saturatingAdd(entry.quantity)
            }
        }
        if (totalAvailable < quantity) return PurchaseResult.InsufficientStock(totalAvailable)

        // 5) plan purchase (still ordered by price among same variant)
        var remaining = quantity
        var totalCost = 0.0
        val purchaseList = mutableListOf<Pair<MarketItem, Int>>()
        for (entry in items) {
            if (remaining <= 0) break

            val purchaseAmount = if (entry.sellerName == "SERVER") {
                val limit = marketRepo.getSystemLimitPerDay(entry.itemId, entry.nbt)
                if (limit < 0) remaining else {
                    val purchased = marketRepo.getSystemPurchasedOn(today, playerUuid, entry.itemId, entry.nbt)
                    val allowed = (limit - purchased).coerceAtLeast(0)
                    minOf(remaining, allowed)
                }
            } else {
                minOf(remaining, entry.quantity)
            }

            if (purchaseAmount <= 0) continue
            totalCost += entry.price * purchaseAmount
            purchaseList.add(entry to purchaseAmount)
            remaining -= purchaseAmount
        }

        // 6) verify balance
        val balance = database.getBalance(playerUuid)
        if (balance < totalCost) return PurchaseResult.InsufficientFunds(totalCost)

        // 7) execute transfer + inventory updates
        database.transfer(playerUuid, UUID(0, 0), totalCost)
        val dtg = System.currentTimeMillis()
        historyRepo.postHistory(
            dtg = dtg,
            fromId = playerUuid,
            fromType = "player",
            fromName = playerName,
            toId = UUID(0, 0),
            toType = "system",
            toName = "MARKET",
            price = totalCost,
            item = "$itemId x$quantity"
        )

        for ((marketItem, amount) in purchaseList) {
            if (marketItem.sellerName != "SERVER") {
                marketRepo.incrementPlayerItemQuantity(UUID.fromString(marketItem.sellerName), marketItem.itemId, marketItem.nbt, -amount)
                database.transfer(UUID(0, 0), UUID.fromString(marketItem.sellerName), marketItem.price * amount)
                historyRepo.postHistory(
                    dtg = dtg,
                    fromId = UUID(0, 0),
                    fromType = "system",
                    fromName = "MARKET",
                    toId = UUID.fromString(marketItem.sellerName),
                    toType = "player",
                    toName = marketItem.sellerName,
                    price = marketItem.price * amount,
                    item = "${marketItem.itemId} x$amount"
                )
            } else {
                marketRepo.incrementSystemPurchasedOn(today, playerUuid, marketItem.itemId, marketItem.nbt, amount)
            }
        }

        val itemsToGive = purchaseList.map { Triple(it.first.itemId, it.first.nbt, it.second) }
        return PurchaseResult.Success(totalCost, quantity, itemsToGive)
    }

    /**
     * 上架/补货商品到玩家市场
     *
     * @param playerUuid 玩家UUID
     * @param playerName 玩家名称
     * @param itemId 物品ID
     * @param nbt 物品NBT
     * @param quantity 数量
     * @param price 单价（如果已上架则忽略此参数，保持原价格）
     * @return 出售结果
     */
    fun listItemForSale(
        playerUuid: UUID,
        playerName: String,
        itemId: String,
        nbt: String,
        quantity: Int,
        price: Double? = null
    ): CompletableFuture<SellResult> {
        return database.supplyAsync {
            try {
                // Check if item already exists
                val exists = marketRepo.hasPlayerItem(playerUuid, itemId, nbt)

                if (!exists) {
                    // First time listing - price is required
                    if (price == null || price <= 0) {
                        return@supplyAsync SellResult.InvalidPrice
                    }
                    // Add new item with price
                    marketRepo.addPlayerItem(playerUuid, playerName, itemId, nbt, price)
                    if (quantity > 0) {
                        marketRepo.incrementPlayerItemQuantity(playerUuid, itemId, nbt, quantity)
                    }
                } else {
                    // Already listed - just add quantity
                    marketRepo.incrementPlayerItemQuantity(playerUuid, itemId, nbt, quantity)
                    // Update price if provided
                    if (price != null && price > 0) {
                        marketRepo.updatePlayerItemPrice(playerUuid, itemId, nbt, price)
                    }
                }

                SellResult.Success(0.0, quantity)
            } catch (e: Exception) {
                SellResult.Error(e.message ?: "未知错误")
            }
        }
    }

    /**
     * 从市场下架商品
     *
     * @param playerUuid 玩家UUID
     * @param itemId 物品ID
     * @param nbt 物品NBT
     * @param quantity 数量（目前忽略此参数，总是返回全部）
     * @return 下架的物品数量
     */
    fun removeItemFromSale(
        playerUuid: UUID,
        itemId: String,
        nbt: String,
        quantity: Int
    ): CompletableFuture<Int> {
        return database.supplyAsync {
            try {
                // 直接调用 removePlayerItem 方法来下架
                val removed = marketRepo.removePlayerItem(playerUuid, itemId, nbt)
                minOf(quantity, removed)
            } catch (_: Exception) {
                0
            }
        }
    }
}

/**
 * Int 安全加法扩展（防止溢出）
 */
private fun Int.saturatingAdd(other: Int): Int {
    return if (this == Int.MAX_VALUE || other == Int.MAX_VALUE) {
        Int.MAX_VALUE
    } else {
        val result = this.toLong() + other.toLong()
        if (result > Int.MAX_VALUE) Int.MAX_VALUE else result.toInt()
    }
}
