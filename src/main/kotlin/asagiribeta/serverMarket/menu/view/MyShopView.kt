package asagiribeta.serverMarket.menu.view

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.menu.MarketGui
import asagiribeta.serverMarket.repository.MarketItem
import asagiribeta.serverMarket.util.ItemKey
import asagiribeta.serverMarket.util.MoneyFormat
import asagiribeta.serverMarket.util.TextFormat
import asagiribeta.serverMarket.util.whenCompleteOnServerThread
import eu.pb4.sgui.api.elements.GuiElementBuilder
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.text.Text
import net.minecraft.util.Identifier

/**
 * 我的店铺视图 - 玩家管理自己的售卖商品
 */
class MyShopView(private val gui: MarketGui) {

    private var myItems: List<MarketItem> = emptyList()

    fun show(resetPage: Boolean = true) {
        gui.mode = ViewMode.MY_SHOP
        if (resetPage) gui.page = 0
        gui.clearContent()
        gui.clearNav()

        setNavButton(46, Items.BOOK, Text.translatable("servermarket.menu.loading")) {}
        buildNav()

        loadMyItems()
    }

    private fun loadMyItems() {
        val db = ServerMarket.instance.database
        val playerUuid = gui.player.uuid

        db.supplyAsync { db.marketRepository.getPlayerItems(playerUuid.toString()) }
            .whenCompleteOnServerThread(gui.player.entityWorld.server) { list, _ ->
                if (gui.mode != ViewMode.MY_SHOP) return@whenCompleteOnServerThread

                    myItems = list ?: emptyList()
                    val totalPages = gui.pageCountOf(myItems.size)
                    gui.page = gui.clampPage(gui.page, totalPages)

                    gui.clearContent()

                    // 显示当前页的商品
                    gui.pageSlice(myItems, gui.page).forEachIndexed { idx, item ->
                        gui.setSlot(idx, buildItemElement(item))
                    }

                    buildNav()
            }
    }

    private fun buildItemElement(item: MarketItem): GuiElementBuilder {
        // 构建商品物品栈
        val stack = ItemKey.tryBuildFullStackFromSnbt(item.nbt, 1) ?: run {
            val id = Identifier.tryParse(item.itemId)
            val itemType = if (id != null && Registries.ITEM.containsId(id))
                           Registries.ITEM.get(id) else Items.STONE
            ItemStack(itemType)
        }

        val name = TextFormat.displayItemName(stack, item.itemId)

        val element = GuiElementBuilder.from(stack)
            .setName(Text.literal(name))
            .addLoreLine(Text.translatable("servermarket.ui.price", MoneyFormat.format(item.price, 2)))
            .addLoreLine(Text.translatable("servermarket.ui.quantity", item.quantity.toString()))
            .addLoreLine(Text.literal(""))
            .addLoreLine(Text.translatable("servermarket.menu.myshop.click_to_manage").copy().formatted(net.minecraft.util.Formatting.YELLOW))
            .setCallback { _, _, _ ->
                gui.showMyShopDetail(item)
            }

        return element
    }


    private fun buildNav() {
        val totalPages = gui.pageCountOf(myItems.size)

        // 上一页
        setNavButton(45, Items.ARROW, Text.translatable("servermarket.menu.prev")) {
            if (gui.page > 0) {
                gui.page--
                show(false)
            }
        }

        // 帮助信息
        val helpItem = GuiElementBuilder(Items.BOOK)
            .setName(Text.translatable("servermarket.menu.myshop.title"))
            .addLoreLine(Text.translatable("servermarket.menu.myshop.tip1"))
            .addLoreLine(Text.translatable("servermarket.menu.myshop.tip2"))
            .addLoreLine(Text.translatable("servermarket.menu.myshop.count", myItems.size))
        gui.setSlot(46, helpItem)

        // 返回首页
        setNavButton(47, Items.NETHER_STAR, Text.translatable("servermarket.menu.back_home")) {
            gui.showHome()
        }

        // 关闭
        setNavButton(49, Items.BARRIER, Text.translatable("servermarket.menu.close")) {
            gui.close()
        }

        // 下一页
        setNavButton(53, Items.ARROW, Text.translatable("servermarket.menu.next", "${gui.page + 1}/$totalPages")) {
            if (gui.page < totalPages - 1) {
                gui.page++
                show(false)
            }
        }
    }

    private fun setNavButton(slot: Int, item: net.minecraft.item.Item, name: Text, callback: () -> Unit) {
        val element = GuiElementBuilder(item)
            .setName(name)
            .setCallback { _, _, _ -> callback() }
        gui.setSlot(slot, element)
    }
}
