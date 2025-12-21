package asagiribeta.serverMarket.menu.view

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.menu.MarketGui
import asagiribeta.serverMarket.model.PurchaseResult
import asagiribeta.serverMarket.repository.MarketMenuEntry
import asagiribeta.serverMarket.util.ItemKey
import asagiribeta.serverMarket.util.Language
import eu.pb4.sgui.api.ClickType
import eu.pb4.sgui.api.elements.GuiElementBuilder
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import java.util.*

/**
 * 卖家店铺视图
 */
class SellerShopView(private val gui: MarketGui) {

    private var shopEntries: List<MarketMenuEntry> = emptyList()
    private var selectedSellerId: String? = null

    fun show(sellerId: String, resetPage: Boolean = true) {
        gui.mode = ViewMode.SELLER_SHOP
        selectedSellerId = sellerId
        if (resetPage) gui.page = 0
        gui.clearContent()
        gui.clearNav()

        // 显示加载提示
        setNavButton(46, Items.BOOK, Language.get("menu.loading")) {}
        buildNav()

        // 异步加载商品列表
        loadShopItems(sellerId)
    }

    private fun loadShopItems(sellerId: String) {
        val db = ServerMarket.instance.database
        db.supplyAsync { db.marketRepository.getAllListingsForSeller(sellerId) }
            .whenComplete { list, _ ->
                gui.serverExecute {
                    if (gui.mode != ViewMode.SELLER_SHOP || selectedSellerId != sellerId) return@serverExecute

                    shopEntries = list ?: emptyList()
                    val totalPages = gui.pageCountOf(shopEntries.size)
                    gui.page = gui.clampPage(gui.page, totalPages)

                    gui.clearContent()

                    // 显示当前页的商品
                    gui.pageSlice(shopEntries, gui.page).forEachIndexed { idx, entry ->
                        gui.setSlot(idx, buildProductElement(entry))
                    }

                    buildNav()
                }
            }
    }

    private fun buildProductElement(entry: MarketMenuEntry): GuiElementBuilder {
        // 构建商品物品栈
        val stack = ItemKey.tryBuildFullStackFromSnbt(entry.nbt, 1) ?: run {
            val id = Identifier.tryParse(entry.itemId)
            val itemType = if (id != null && Registries.ITEM.containsId(id))
                           Registries.ITEM.get(id) else Items.STONE
            ItemStack(itemType)
        }

        val stockStr = if (entry.isSystem && entry.quantity < 0) "∞" else entry.quantity.toString()

        val element = GuiElementBuilder.from(stack)
            .setName(Text.literal(entry.itemId))
            .addLoreLine(Text.literal(Language.get("ui.seller", entry.sellerName)))
            .addLoreLine(Text.literal(Language.get("ui.price", String.format(Locale.ROOT, "%.2f", entry.price))))
            .addLoreLine(Text.literal(Language.get("ui.quantity", stockStr)))
            .addLoreLine(Text.literal(Language.get("menu.shop.click_tip")))
            .setCallback { _, clickType, _ ->
                val buyAll = clickType == ClickType.MOUSE_RIGHT || clickType == ClickType.MOUSE_RIGHT_SHIFT
                handlePurchase(entry, buyAll)
            }

        return element
    }

    private fun buildNav() {
        val totalPages = gui.pageCountOf(shopEntries.size)

        setNavButton(45, Items.ARROW, Language.get("menu.prev")) {
            if (gui.page > 0) {
                gui.page--
                show(selectedSellerId ?: return@setNavButton, false)
            }
        }

        val helpItem = GuiElementBuilder(Items.BOOK)
            .setName(Text.literal(Language.get("menu.shop.title")))
            .addLoreLine(Text.literal(Language.get("menu.shop.tip_left")))
            .addLoreLine(Text.literal(Language.get("menu.shop.tip_right")))
            .addLoreLine(Text.literal(Language.get("menu.shop.tip_back")))
            .addLoreLine(Text.literal(Language.get("menu.shop.tip_close")))
        gui.setSlot(46, helpItem)

        setNavButton(47, Items.NETHER_STAR, Language.get("menu.back_sellers")) {
            gui.showSellerList(false)
        }

        setNavButton(49, Items.BARRIER, Language.get("menu.close")) {
            gui.close()
        }

        setNavButton(53, Items.ARROW, Language.get("menu.next", "${gui.page + 1}/$totalPages")) {
            if (gui.page + 1 < totalPages) {
                gui.page++
                show(selectedSellerId ?: return@setNavButton, false)
            }
        }
    }

    private fun handlePurchase(entry: MarketMenuEntry, buyAll: Boolean) {
        val player = gui.player
        val desired = if (buyAll) 64 else 1

        // 使用 MarketService 处理购买逻辑
        ServerMarket.instance.marketService.purchaseItem(
            playerUuid = player.uuid,
            playerName = player.name.string,
            itemId = entry.itemId,
            quantity = desired,
            seller = entry.sellerId
        ).whenComplete { result, _ ->
            gui.serverExecute {
                when (result) {
                    is PurchaseResult.Success -> {
                        // 发放物品
                        result.items.forEach { (itemId, nbt, count) ->
                            val stack = ItemKey.tryBuildFullStackFromSnbt(nbt, count) ?: run {
                                val id = Identifier.tryParse(itemId)
                                val itemType = if (id != null && Registries.ITEM.containsId(id))
                                               Registries.ITEM.get(id) else Items.AIR
                                ItemStack(itemType, count)
                            }
                            player.giveItemStack(stack)
                        }

                        player.sendMessage(
                            Text.literal(Language.get(
                                "menu.buy_ok",
                                result.amount,
                                entry.itemId,
                                "%.2f".format(result.totalCost)
                            )),
                            false
                        )

                        refreshAfterPurchase()
                    }

                    is PurchaseResult.InsufficientFunds -> {
                        player.sendMessage(
                            Text.literal(Language.get("menu.no_money", "%.2f".format(result.required))),
                            false
                        )
                    }

                    is PurchaseResult.InsufficientStock -> {
                        player.sendMessage(
                            Text.literal(Language.get("menu.out_of_stock")),
                            false
                        )
                    }

                    is PurchaseResult.LimitExceeded -> {
                        player.sendMessage(
                            Text.literal(Language.get("menu.limit_exceeded")),
                            false
                        )
                    }

                    PurchaseResult.NotFound -> {
                        player.sendMessage(
                            Text.literal(Language.get("menu.item_not_found")),
                            false
                        )
                    }

                    PurchaseResult.CannotBuyOwnItem -> {
                        player.sendMessage(
                            Text.literal(Language.get("menu.cannot_buy_own_item")),
                            false
                        )
                    }

                    is PurchaseResult.Error -> {
                        player.sendMessage(
                            Text.literal(Language.get("menu.buy_error")),
                            false
                        )
                    }
                }
            }
        }
    }

    private fun refreshAfterPurchase() {
        if (gui.mode == ViewMode.SELLER_SHOP) {
            val sid = selectedSellerId ?: return
            val oldPage = gui.page
            show(sid, resetPage = false)
            gui.page = oldPage.coerceAtMost(gui.pageCountOf(shopEntries.size) - 1)
        }
    }

    private fun setNavButton(slot: Int, item: net.minecraft.item.Item, name: String, callback: () -> Unit) {
        val element = GuiElementBuilder(item)
            .setName(Text.literal(name))
            .setCallback { _, _, _ -> callback() }
        gui.setSlot(slot, element)
    }
}

