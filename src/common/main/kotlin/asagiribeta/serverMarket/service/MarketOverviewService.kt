package asagiribeta.serverMarket.service

import asagiribeta.serverMarket.repository.Database
import asagiribeta.serverMarket.repository.MarketItem
import asagiribeta.serverMarket.util.ItemKey
import asagiribeta.serverMarket.util.MoneyFormat
import net.minecraft.text.Text

/**
 * Market snapshot for a single item variant, inspired by Stonks' [ProductMarketOverview].
 *
 * Helps players price listings with context about competing sell offers and buy orders.
 */
class MarketOverviewService(private val database: Database) {

    data class Overview(
        val itemId: String,
        val nbt: String,
        val sellListingCount: Int,
        val sellQuantity: Int,
        val minSellPrice: Double?,
        val maxSellPrice: Double?,
        val avgSellPrice: Double?,
        val buyOrderCount: Int,
        val minBuyPrice: Double?,
        val maxBuyPrice: Double?,
        val avgBuyPrice: Double?
    )

    fun getOverview(itemId: String, nbt: String): Overview {
        val normalizedNbt = ItemKey.normalizeSnbt(nbt)
        val sellListings = database.marketRepository.searchForDisplay(itemId)
            .filter { ItemKey.normalizeSnbt(it.nbt) == normalizedNbt && it.quantity != 0 }

        val buyOrders = database.purchaseRepository.getPlayerPurchaseOrders(itemId, normalizedNbt)
        val systemBuyPrice = database.purchaseRepository.getSystemPurchasePrice(itemId, normalizedNbt)

        val sellPrices = sellListings.map { it.price }
        val buyPrices = buildList {
            addAll(buyOrders.map { it.price })
            if (systemBuyPrice != null && systemBuyPrice > 0) add(systemBuyPrice)
        }

        return Overview(
            itemId = itemId,
            nbt = normalizedNbt,
            sellListingCount = sellListings.size,
            sellQuantity = sellListings.sumOf { listingQuantity(it) },
            minSellPrice = sellPrices.minOrNull(),
            maxSellPrice = sellPrices.maxOrNull(),
            avgSellPrice = weightedAverage(sellListings),
            buyOrderCount = buyOrders.size + if (systemBuyPrice != null && systemBuyPrice > 0) 1 else 0,
            minBuyPrice = buyPrices.minOrNull(),
            maxBuyPrice = buyPrices.maxOrNull(),
            avgBuyPrice = if (buyPrices.isEmpty()) null else buyPrices.average()
        )
    }

    fun formatListingHint(overview: Overview, listedPrice: Double? = null): Text {
        val parts = mutableListOf<Text>()

        if (overview.sellListingCount > 0) {
            parts.add(
                Text.translatable(
                    "servermarket.market.overview.sell",
                    overview.sellListingCount,
                    overview.sellQuantity,
                    MoneyFormat.format(overview.minSellPrice ?: 0.0, 2),
                    MoneyFormat.format(overview.maxSellPrice ?: 0.0, 2)
                )
            )
        } else {
            parts.add(Text.translatable("servermarket.market.overview.no_sell"))
        }

        if (overview.buyOrderCount > 0) {
            parts.add(
                Text.translatable(
                    "servermarket.market.overview.buy",
                    overview.buyOrderCount,
                    MoneyFormat.format(overview.maxBuyPrice ?: 0.0, 2),
                    MoneyFormat.format(overview.minBuyPrice ?: 0.0, 2)
                )
            )
        }

        if (listedPrice != null && listedPrice > 0 && overview.minSellPrice != null) {
            val comparison = when {
                listedPrice < overview.minSellPrice ->
                    Text.translatable("servermarket.market.overview.price_below_min")
                listedPrice > (overview.maxSellPrice ?: listedPrice) ->
                    Text.translatable("servermarket.market.overview.price_above_max")
                else -> Text.translatable("servermarket.market.overview.price_in_range")
            }
            parts.add(comparison)
        }

        return joinLines(parts)
    }

    private fun joinLines(parts: List<Text>): Text {
        if (parts.isEmpty()) return Text.empty()
        val merged = parts.first().copy()
        for (i in 1 until parts.size) {
            merged.append("\n").append(parts[i])
        }
        return merged
    }

    private fun listingQuantity(item: MarketItem): Int =
        if (item.quantity < 0) 0 else item.quantity

    private fun weightedAverage(listings: List<MarketItem>): Double? {
        if (listings.isEmpty()) return null
        var totalValue = 0.0
        var totalQty = 0
        for (listing in listings) {
            val qty = listingQuantity(listing)
            if (qty <= 0) continue
            totalValue += listing.price * qty
            totalQty += qty
        }
        return if (totalQty > 0) totalValue / totalQty else listings.map { it.price }.average()
    }
}
