package asagiribeta.serverMarket.menu.view

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.menu.MarketGui
import asagiribeta.serverMarket.menu.builder.GuiElementBuilders
import asagiribeta.serverMarket.menu.builder.GuiElementBuilders.setPlayerSkin
import asagiribeta.serverMarket.repository.SellerMenuEntry
import asagiribeta.serverMarket.util.whenCompleteOnServerThread
import eu.pb4.sgui.api.elements.GuiElementBuilder
import net.minecraft.item.Items
import net.minecraft.text.Text

/**
 * 卖家列表视图
 */
class SellerListView(private val gui: MarketGui) {

    private var sellerEntries: List<SellerMenuEntry> = emptyList()

    fun show(resetPage: Boolean = true) {
        gui.mode = ViewMode.SELLER_LIST
        if (resetPage) gui.page = 0
        gui.clearContent()
        gui.clearNav()

        loadSellerList()
    }

    private fun loadSellerList() {
        val db = ServerMarket.instance.database
        db.supplyAsync { db.marketRepository.getAllSellersForMenu() }
            .whenCompleteOnServerThread(gui.player.entityWorld.server) { list, _ ->
                if (gui.mode != ViewMode.SELLER_LIST) return@whenCompleteOnServerThread

                sellerEntries = list ?: emptyList()
                gui.renderPagedContent(
                    list = sellerEntries,
                    buildElement = { entry -> buildSellerElement(entry) },
                    buildNav = { buildNav() }
                )
            }
    }

    private fun buildSellerElement(entry: SellerMenuEntry): GuiElementBuilder {
        val item = GuiElementBuilders.createSellerIcon(entry)
        val element = GuiElementBuilder(item)
            .setName(Text.literal(entry.sellerName))
            .addLoreLine(Text.translatable("servermarket.menu.seller.items", entry.itemCount))
            .addLoreLine(Text.translatable("servermarket.menu.seller.open_shop"))
            .setCallback { _, _, _ ->
                gui.showSellerShop(entry.sellerId, true)
            }

        // 为玩家卖家设置皮肤
        if (item == Items.PLAYER_HEAD) {
            val profile = GuiElementBuilders.obtainPlayerGameProfile(gui.player, entry)
            element.setPlayerSkin(profile)
        }

        return element
    }

    private fun buildNav() {
        val totalPages = gui.pageCountOf(sellerEntries.size)

        val helpItem = GuiElementBuilder(Items.BOOK)
            .setName(Text.translatable("servermarket.menu.seller_list.title"))
            .addLoreLine(Text.translatable("servermarket.menu.seller_list.tip1"))
            .addLoreLine(Text.translatable("servermarket.menu.seller_list.tip2"))
            .addLoreLine(Text.translatable("servermarket.menu.seller_list.tip3"))
            .addLoreLine(Text.translatable("servermarket.menu.seller_list.tip4"))

        gui.setStandardNavForListView(
            totalPages = totalPages,
            helpItem = helpItem,
            refresh = { show(false) }
        )
    }
}
