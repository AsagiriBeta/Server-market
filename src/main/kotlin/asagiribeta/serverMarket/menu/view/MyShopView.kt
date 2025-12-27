package asagiribeta.serverMarket.menu.view

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.menu.MarketGui
import asagiribeta.serverMarket.repository.MarketItem
import asagiribeta.serverMarket.util.ItemKey
import asagiribeta.serverMarket.util.Language
import eu.pb4.sgui.api.elements.GuiElementBuilder
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import java.util.*

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

        // 显示加载提示
        setNavButton(46, Items.BOOK, Language.get("menu.loading")) {}
        buildNav()

        // 异步加载玩家的商品列表
        loadMyItems()
    }

    private fun loadMyItems() {
        val db = ServerMarket.instance.database
        val playerUuid = gui.player.uuid

        db.supplyAsync { db.marketRepository.getPlayerItems(playerUuid.toString()) }
            .whenComplete { list, _ ->
                gui.serverExecute {
                    if (gui.mode != ViewMode.MY_SHOP) return@serverExecute

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
    }

    private fun buildItemElement(item: MarketItem): GuiElementBuilder {
        // 构建商品物品栈
        val stack = ItemKey.tryBuildFullStackFromSnbt(item.nbt, 1) ?: run {
            val id = Identifier.tryParse(item.itemId)
            val itemType = if (id != null && Registries.ITEM.containsId(id))
                           Registries.ITEM.get(id) else Items.STONE
            ItemStack(itemType)
        }

        val element = GuiElementBuilder.from(stack)
            .setName(stack.getName())
            .addLoreLine(Text.literal(Language.get("ui.price", String.format(Locale.ROOT, "%.2f", item.price))))
            .addLoreLine(Text.literal(Language.get("ui.quantity", item.quantity)))
            .addLoreLine(Text.literal(""))
            .addLoreLine(Text.literal("§e${Language.get("menu.myshop.click_to_manage")}"))
            .setCallback { _, _, _ ->
                gui.showMyShopDetail(item)
            }

        return element
    }


    private fun buildNav() {
        val totalPages = gui.pageCountOf(myItems.size)

        // 上一页
        setNavButton(45, Items.ARROW, Language.get("menu.prev")) {
            if (gui.page > 0) {
                gui.page--
                show(false)
            }
        }

        // 帮助信息
        val helpItem = GuiElementBuilder(Items.BOOK)
            .setName(Text.literal(Language.get("menu.myshop.title")))
            .addLoreLine(Text.literal(Language.get("menu.myshop.tip1")))
            .addLoreLine(Text.literal(Language.get("menu.myshop.tip2")))
            .addLoreLine(Text.literal(Language.get("menu.myshop.count", myItems.size)))
        gui.setSlot(46, helpItem)

        // 返回首页
        setNavButton(47, Items.NETHER_STAR, Language.get("menu.back_home")) {
            gui.showHome()
        }

        // 关闭
        setNavButton(49, Items.BARRIER, Language.get("menu.close")) {
            gui.close()
        }

        // 下一页
        setNavButton(53, Items.ARROW, Language.get("menu.next", "${gui.page + 1}/$totalPages")) {
            if (gui.page < totalPages - 1) {
                gui.page++
                show(false)
            }
        }
    }

    private fun setNavButton(slot: Int, item: net.minecraft.item.Item, name: String, callback: () -> Unit) {
        val element = GuiElementBuilder(item)
            .setName(Text.literal(name))
            .setCallback { _, _, _ -> callback() }
        gui.setSlot(slot, element)
    }
}

