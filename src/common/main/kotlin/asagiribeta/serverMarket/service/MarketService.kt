package asagiribeta.serverMarket.service

import asagiribeta.serverMarket.api.ServerMarketEvents
import asagiribeta.serverMarket.model.*
import asagiribeta.serverMarket.repository.Database
import asagiribeta.serverMarket.repository.MarketItem
import asagiribeta.serverMarket.repository.MarketMenuEntry
import asagiribeta.serverMarket.repository.SellerMenuEntry
import asagiribeta.serverMarket.util.Config
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * 市场交易业务逻辑服务
 */
class MarketService(
    private val database: Database,
    private val economy: EconomyService
) {

    private val marketRepo = database.marketRepository
    private val systemUuid = economy.systemUuid

    fun getPlayerListings(playerUuid: UUID): CompletableFuture<List<MarketItem>> {
        return database.supplyAsync {
            marketRepo.getPlayerItems(playerUuid.toString())
        }
    }

    fun getAllSellersForMenu(): CompletableFuture<List<SellerMenuEntry>> {
        return database.supplyAsync {
            marketRepo.getAllSellersForMenu()
        }
    }

    fun getSellerListings(sellerId: String): CompletableFuture<List<MarketMenuEntry>> {
        return database.supplyAsync {
            marketRepo.getAllListingsForSeller(sellerId)
        }
    }

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

    private fun doPurchaseItem(
        playerUuid: UUID,
        playerName: String,
        itemId: String,
        quantity: Int,
        seller: String?
    ): PurchaseResult {
        val items = if (seller == null) {
            marketRepo.searchForTransaction(itemId)
        } else {
            marketRepo.searchForTransaction(itemId, seller)
        }

        if (items.isEmpty()) return PurchaseResult.NotFound

        val variantCount = items.asSequence()
            .map { it.nbt }
            .map { asagiribeta.serverMarket.util.ItemKey.normalizeSnbt(it) }
            .distinct()
            .count()
        if (variantCount > 1) {
            return PurchaseResult.AmbiguousVariants(itemId = itemId, variantCount = variantCount)
        }

        return doPurchaseItemVariant(playerUuid, playerName, itemId, items.first().nbt, quantity, seller)
    }

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

        val candidates = if (seller == null) {
            marketRepo.searchForTransaction(itemId)
        } else {
            marketRepo.searchForTransaction(itemId, seller)
        }

        val items = candidates.filter {
            asagiribeta.serverMarket.util.ItemKey.normalizeSnbt(it.nbt) == normalizedNbt
        }
        if (items.isEmpty()) return PurchaseResult.NotFound

        val hasOwnItems = items.any { entry ->
            entry.sellerName != "SERVER" &&
                try { UUID.fromString(entry.sellerName) == playerUuid } catch (_: Exception) { false }
        }
        if (hasOwnItems) return PurchaseResult.CannotBuyOwnItem

        var totalAvailable = 0
        for (entry in items) {
            if (entry.sellerName == "SERVER") {
                val limit = marketRepo.getSystemLimitPerDay(entry.itemId, entry.nbt)
                if (limit < 0) {
                    totalAvailable = Int.MAX_VALUE
                    break
                } else {
                    val purchased = marketRepo.getSystemPurchasedOn(today, playerUuid, entry.itemId, entry.nbt)
                    totalAvailable = totalAvailable.saturatingAdd((limit - purchased).coerceAtLeast(0))
                }
            } else {
                totalAvailable = if (totalAvailable == Int.MAX_VALUE) Int.MAX_VALUE
                else totalAvailable.saturatingAdd(entry.quantity)
            }
        }
        if (totalAvailable < quantity) return PurchaseResult.InsufficientStock(totalAvailable)

        var remaining = quantity
        var totalCost = 0.0
        val purchaseList = mutableListOf<Pair<MarketItem, Int>>()
        for (entry in items) {
            if (remaining <= 0) break
            val purchaseAmount = if (entry.sellerName == "SERVER") {
                val limit = marketRepo.getSystemLimitPerDay(entry.itemId, entry.nbt)
                if (limit < 0) remaining else {
                    val purchased = marketRepo.getSystemPurchasedOn(today, playerUuid, entry.itemId, entry.nbt)
                    minOf(remaining, (limit - purchased).coerceAtLeast(0))
                }
            } else {
                minOf(remaining, entry.quantity)
            }
            if (purchaseAmount <= 0) continue
            totalCost += entry.price * purchaseAmount
            purchaseList.add(entry to purchaseAmount)
            remaining -= purchaseAmount
        }

        if (!ServerMarketEvents.PRE_PURCHASE.invoker().allowPurchase(playerUuid, itemId, normalizedNbt, quantity, totalCost)) {
            notifyPurchase(playerUuid, itemId, quantity, totalCost, false)
            return PurchaseResult.CancelledByPlugin
        }

        if (database.getBalance(playerUuid) < totalCost) {
            return PurchaseResult.InsufficientFunds(totalCost)
        }

        return executePurchase(playerUuid, playerName, itemId, quantity, totalCost, purchaseList, today)
    }

    private fun executePurchase(
        playerUuid: UUID,
        playerName: String,
        itemId: String,
        quantity: Int,
        totalCost: Double,
        purchaseList: List<Pair<MarketItem, Int>>,
        today: String
    ): PurchaseResult {
        economy.transferFundsSync(
            fromUuid = playerUuid,
            toUuid = systemUuid,
            amount = totalCost,
            reason = "market_purchase",
            history = EconomyService.HistoryContext(
                fromId = playerUuid, fromType = "player", fromName = playerName,
                toId = systemUuid, toType = "system", toName = "MARKET",
                price = totalCost, item = "$itemId x$quantity"
            )
        )

        for ((marketItem, amount) in purchaseList) {
            if (marketItem.sellerName != "SERVER") {
                val sellerUuid = UUID.fromString(marketItem.sellerName)
                val gross = marketItem.price * amount
                marketRepo.incrementPlayerItemQuantity(sellerUuid, marketItem.itemId, marketItem.nbt, -amount)
                paySeller(sellerUuid, marketItem.sellerName, gross, "${marketItem.itemId} x$amount")
            } else {
                marketRepo.incrementSystemPurchasedOn(today, playerUuid, marketItem.itemId, marketItem.nbt, amount)
            }
        }

        notifyPurchase(playerUuid, itemId, quantity, totalCost, true)
        val itemsToGive = purchaseList.map { Triple(it.first.itemId, it.first.nbt, it.second) }
        return PurchaseResult.Success(totalCost, quantity, itemsToGive)
    }

    private fun paySeller(sellerUuid: UUID, sellerName: String, gross: Double, itemDesc: String) {
        val (net, tax) = economy.computeSellerPayout(gross)
        if (net > 0) {
            economy.transferFundsSync(
                fromUuid = systemUuid,
                toUuid = sellerUuid,
                amount = net,
                reason = "market_sale",
                history = EconomyService.HistoryContext(
                    fromId = systemUuid, fromType = "system", fromName = "MARKET",
                    toId = sellerUuid, toType = "player", toName = sellerName,
                    price = net, item = itemDesc
                )
            )
        }
        if (tax > 0 && Config.enableTax) {
            economy.recordHistorySync(
                EconomyService.HistoryContext(
                    fromId = sellerUuid, fromType = "player", fromName = sellerName,
                    toId = systemUuid, toType = "system", toName = "MARKET",
                    price = tax, item = "market_tax"
                )
            )
        }
    }

    private fun notifyPurchase(buyer: UUID, itemId: String, quantity: Int, totalCost: Double, success: Boolean) {
        asagiribeta.serverMarket.ServerMarket.instance.server?.execute {
            ServerMarketEvents.POST_PURCHASE.invoker().onPurchase(buyer, itemId, quantity, totalCost, success)
        }
    }

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
                val exists = marketRepo.hasPlayerItem(playerUuid, itemId, nbt)
                if (!exists) {
                    if (price == null || price <= 0) return@supplyAsync SellResult.InvalidPrice
                    marketRepo.addPlayerItem(playerUuid, playerName, itemId, nbt, price)
                    if (quantity > 0) {
                        marketRepo.incrementPlayerItemQuantity(playerUuid, itemId, nbt, quantity)
                    }
                } else {
                    marketRepo.incrementPlayerItemQuantity(playerUuid, itemId, nbt, quantity)
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

    fun removeItemFromSale(
        playerUuid: UUID,
        itemId: String,
        nbt: String,
        quantity: Int
    ): CompletableFuture<Int> {
        return database.supplyAsync {
            try {
                val removed = marketRepo.removePlayerItem(playerUuid, itemId, nbt)
                minOf(quantity, removed)
            } catch (_: Exception) {
                0
            }
        }
    }

    /**
     * Unlist items and deliver them to the player's parcel station (same path as GUI unlist).
     */
    fun unlistToParcel(
        playerUuid: UUID,
        playerName: String,
        itemId: String,
        nbt: String,
        quantity: Int = Int.MAX_VALUE,
        parcelReason: String = "servermarket.parcel.reason.unlist"
    ): CompletableFuture<Int> {
        return database.supplyAsync {
            try {
                val normalizedNbt = asagiribeta.serverMarket.util.ItemKey.normalizeSnbt(nbt)
                val removed = marketRepo.removePlayerItem(playerUuid, itemId, normalizedNbt).coerceAtMost(quantity)
                if (removed > 0) {
                    database.parcelRepository.addParcel(
                        recipientUuid = playerUuid,
                        recipientName = playerName,
                        itemId = itemId,
                        nbt = normalizedNbt,
                        quantity = removed,
                        reason = parcelReason
                    )
                }
                removed
            } catch (_: Exception) {
                0
            }
        }
    }

    /**
     * Partially unlist stock and send items to the parcel station.
     */
    fun unlistPartialToParcel(
        playerUuid: UUID,
        playerName: String,
        itemId: String,
        nbt: String,
        quantity: Int
    ): CompletableFuture<Int> {
        return database.supplyAsync {
            try {
                val normalizedNbt = asagiribeta.serverMarket.util.ItemKey.normalizeSnbt(nbt)
                val current = marketRepo.getPlayerItems(playerUuid.toString())
                    .firstOrNull { it.itemId == itemId && asagiribeta.serverMarket.util.ItemKey.normalizeSnbt(it.nbt) == normalizedNbt }
                    ?.quantity ?: 0
                val actual = minOf(quantity.coerceAtLeast(0), current)
                if (actual <= 0) return@supplyAsync 0

                marketRepo.incrementPlayerItemQuantity(playerUuid, itemId, normalizedNbt, -actual)
                database.parcelRepository.addParcel(
                    recipientUuid = playerUuid,
                    recipientName = playerName,
                    itemId = itemId,
                    nbt = normalizedNbt,
                    quantity = actual,
                    reason = "servermarket.parcel.reason.unlist"
                )
                actual
            } catch (_: Exception) {
                0
            }
        }
    }

    sealed class SetPriceResult {
        object Added : SetPriceResult()
        object Updated : SetPriceResult()
        object InvalidPrice : SetPriceResult()
        data class Error(val message: String) : SetPriceResult()
    }

    fun setListingPrice(
        playerUuid: UUID,
        playerName: String,
        itemId: String,
        nbt: String,
        price: Double
    ): CompletableFuture<SetPriceResult> {
        return database.supplyAsync {
            try {
                if (price <= 0.0 || price.isNaN() || price.isInfinite()) {
                    return@supplyAsync SetPriceResult.InvalidPrice
                }
                val exists = marketRepo.hasPlayerItem(playerUuid, itemId, nbt)
                if (!exists) {
                    marketRepo.addPlayerItem(playerUuid, playerName, itemId, nbt, price)
                    SetPriceResult.Added
                } else {
                    marketRepo.updatePlayerItemPrice(playerUuid, itemId, nbt, price)
                    SetPriceResult.Updated
                }
            } catch (e: Exception) {
                SetPriceResult.Error(e.message ?: "未知错误")
            }
        }
    }
}

private fun Int.saturatingAdd(other: Int): Int {
    return if (this == Int.MAX_VALUE || other == Int.MAX_VALUE) Int.MAX_VALUE
    else {
        val result = this.toLong() + other.toLong()
        if (result > Int.MAX_VALUE) Int.MAX_VALUE else result.toInt()
    }
}
