package asagiribeta.serverMarket.menu.view

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.menu.MarketGui
import asagiribeta.serverMarket.model.PurchaseResult
import asagiribeta.serverMarket.repository.MarketMenuEntry
import asagiribeta.serverMarket.util.ItemKey
import asagiribeta.serverMarket.util.ItemStackFactory
import asagiribeta.serverMarket.util.MoneyFormat
import asagiribeta.serverMarket.util.TextFormat
import eu.pb4.sgui.api.ClickType
import eu.pb4.sgui.api.elements.GuiElementBuilder
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.text.Text
import net.minecraft.util.Identifier

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

        setNavButton(46, Items.BOOK, Text.translatable("servermarket.menu.loading")) {}
        buildNav()

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
        val stack = ItemStackFactory.forDisplay(
            itemId = entry.itemId,
            snbt = entry.nbt,
            count = 1,
            fallbackItem = Items.STONE
        )

        val stockStr = if (entry.isSystem && entry.quantity < 0) "∞" else entry.quantity.toString()
        val name = TextFormat.displayItemName(stack, entry.itemId)

        val element = GuiElementBuilder.from(stack)
            .setName(Text.literal(name))
            .addLoreLine(Text.translatable("servermarket.ui.seller", entry.sellerName))
            .addLoreLine(Text.translatable("servermarket.ui.price", MoneyFormat.format(entry.price, 2)))
            .addLoreLine(Text.translatable("servermarket.ui.quantity", stockStr))
            .addLoreLine(Text.translatable("servermarket.menu.shop.click_tip"))
            .setCallback { _, clickType, _ ->
                val buyAll = clickType == ClickType.MOUSE_RIGHT || clickType == ClickType.MOUSE_RIGHT_SHIFT
                handlePurchase(entry, buyAll)
            }

        return element
    }

    private fun buildNav() {
        val totalPages = gui.pageCountOf(shopEntries.size)

        setNavButton(45, Items.ARROW, Text.translatable("servermarket.menu.prev")) {
            if (gui.page > 0) {
                gui.page--
                show(selectedSellerId ?: return@setNavButton, false)
            }
        }

        val helpItem = GuiElementBuilder(Items.BOOK)
            .setName(Text.translatable("servermarket.menu.shop.title"))
            .addLoreLine(Text.translatable("servermarket.menu.shop.tip_left"))
            .addLoreLine(Text.translatable("servermarket.menu.shop.tip_right"))
            .addLoreLine(Text.translatable("servermarket.menu.shop.tip_back"))
            .addLoreLine(Text.translatable("servermarket.menu.shop.tip_close"))
        gui.setSlot(46, helpItem)

        setNavButton(47, Items.NETHER_STAR, Text.translatable("servermarket.menu.back_sellers")) {
            gui.showSellerList(false)
        }

        setNavButton(49, Items.BARRIER, Text.translatable("servermarket.menu.close")) {
            gui.close()
        }

        setNavButton(53, Items.ARROW, Text.translatable("servermarket.menu.next", "${gui.page + 1}/$totalPages")) {
            if (gui.page + 1 < totalPages) {
                gui.page++
                show(selectedSellerId ?: return@setNavButton, false)
            }
        }
    }

    private fun handlePurchase(entry: MarketMenuEntry, buyAll: Boolean) {
        val player = gui.player
        val desired = if (buyAll) 64 else 1

        // compute display name for messages (avoid minecraft: prefix)
        val displayName = TextFormat.displayItemName(
            ItemKey.tryBuildFullStackFromSnbt(entry.nbt, 1) ?: ItemStack.EMPTY,
            entry.itemId
        )

        // 使用 MarketService 处理购买逻辑（GUI 端必须严格按 NBT 购买）
        ServerMarket.instance.marketService.purchaseItemVariant(
            playerUuid = player.uuid,
            playerName = player.name.string,
            itemId = entry.itemId,
            nbt = entry.nbt,
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
                            Text.translatable(
                                "servermarket.menu.buy_ok",
                                result.amount,
                                displayName,
                                MoneyFormat.format(result.totalCost, 2)
                            ),
                            false
                        )

                        refreshAfterPurchase()
                    }

                    is PurchaseResult.InsufficientFunds -> {
                        player.sendMessage(
                            Text.translatable("servermarket.menu.no_money", MoneyFormat.format(result.required, 2)),
                            false
                        )
                    }

                    is PurchaseResult.InsufficientStock -> {
                        player.sendMessage(Text.translatable("servermarket.menu.out_of_stock"), false)
                    }

                    is PurchaseResult.LimitExceeded -> {
                        player.sendMessage(Text.translatable("servermarket.menu.limit_exceeded"), false)
                    }

                    PurchaseResult.NotFound -> {
                        player.sendMessage(Text.translatable("servermarket.menu.item_not_found"), false)
                    }

                    PurchaseResult.CannotBuyOwnItem -> {
                        player.sendMessage(Text.translatable("servermarket.menu.cannot_buy_own_item"), false)
                    }

                    is PurchaseResult.Error -> {
                        player.sendMessage(Text.translatable("servermarket.menu.buy_error"), false)
                    }

                    is PurchaseResult.AmbiguousVariants -> {
                        // Should normally not happen when buying from a concrete GUI slot,
                        // but keep it safe in case of legacy/duplicate entries.
                        player.sendMessage(
                            Text.translatable(
                                "servermarket.command.mbuy.ambiguous_variants",
                                result.itemId,
                                result.variantCount
                            ),
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

    private fun setNavButton(slot: Int, item: net.minecraft.item.Item, name: Text, callback: () -> Unit) {
        val element = GuiElementBuilder(item)
            .setName(name)
            .setCallback { _, _, _ -> callback() }
        gui.setSlot(slot, element)
    }
}
