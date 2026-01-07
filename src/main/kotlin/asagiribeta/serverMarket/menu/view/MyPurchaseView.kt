package asagiribeta.serverMarket.menu.view

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.menu.MarketGui
import asagiribeta.serverMarket.repository.PlayerPurchaseEntry
import asagiribeta.serverMarket.util.ItemKey
import asagiribeta.serverMarket.util.MoneyFormat
import asagiribeta.serverMarket.util.TextFormat
import eu.pb4.sgui.api.elements.GuiElementBuilder
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.text.Text
import net.minecraft.util.Identifier

/**
 * 我的收购视图 - 玩家管理自己的收购订单
 */
class MyPurchaseView(private val gui: MarketGui) {

    private var myPurchases: List<PlayerPurchaseEntry> = emptyList()

    fun show(resetPage: Boolean = true) {
        gui.mode = ViewMode.MY_PURCHASE
        if (resetPage) gui.page = 0
        gui.clearContent()
        gui.clearNav()

        setNavButton(46, Items.BOOK, Text.translatable("servermarket.menu.loading")) {}
        buildNav()

        loadMyPurchases()
    }

    private fun loadMyPurchases() {
        val db = ServerMarket.instance.database
        val playerUuid = gui.player.uuid

        db.supplyAsync { db.purchaseRepository.getPlayerPurchasesByBuyer(playerUuid) }
            .whenComplete { list, _ ->
                gui.serverExecute {
                    if (gui.mode != ViewMode.MY_PURCHASE) return@serverExecute

                    myPurchases = list ?: emptyList()
                    val totalPages = gui.pageCountOf(myPurchases.size)
                    gui.page = gui.clampPage(gui.page, totalPages)

                    gui.clearContent()

                    // 显示当前页的收购订单
                    gui.pageSlice(myPurchases, gui.page).forEachIndexed { idx, entry ->
                        gui.setSlot(idx, buildPurchaseElement(entry))
                    }

                    buildNav()
                }
            }
    }

    private fun buildPurchaseElement(entry: PlayerPurchaseEntry): GuiElementBuilder {
        // 构建物品栈
        val stack = ItemKey.tryBuildFullStackFromSnbt(entry.nbt, 1) ?: run {
            val id = Identifier.tryParse(entry.itemId)
            val itemType = if (id != null && Registries.ITEM.containsId(id))
                           Registries.ITEM.get(id) else Items.STONE
            ItemStack(itemType)
        }

        val progressPercent = if (entry.targetAmount > 0) {
            (entry.currentAmount * 100.0 / entry.targetAmount).toInt()
        } else 0

        val name = TextFormat.displayItemName(stack, entry.itemId)

        val element = GuiElementBuilder.from(stack)
            .setName(Text.literal(name))
            .addLoreLine(Text.translatable("servermarket.ui.price", MoneyFormat.format(entry.price, 2)))
            .addLoreLine(
                Text.translatable(
                    "servermarket.menu.mypurchase.progress",
                    entry.currentAmount,
                    entry.targetAmount,
                    progressPercent
                )
            )
            .addLoreLine(Text.translatable("servermarket.menu.mypurchase.remaining", entry.remaining))
            .addLoreLine(Text.literal(""))

        if (entry.currentAmount > 0) {
            element.addLoreLine(Text.translatable("servermarket.menu.mypurchase.auto_sent_tip").copy().formatted(net.minecraft.util.Formatting.GREEN))
        }
        if (entry.isCompleted) {
            element.addLoreLine(Text.translatable("servermarket.menu.mypurchase.completed").copy().formatted(net.minecraft.util.Formatting.GOLD))
        }
        element.addLoreLine(Text.translatable("servermarket.menu.mypurchase.click_cancel").copy().formatted(net.minecraft.util.Formatting.GRAY))
        element.setCallback { _, _, _ ->
            handleCancelPurchase(entry)
        }

        return element
    }

    private fun handleCancelPurchase(entry: PlayerPurchaseEntry) {
        val db = ServerMarket.instance.database
        val playerUuid = gui.player.uuid

        db.runAsync {
            db.purchaseRepository.removePlayerPurchase(playerUuid, entry.itemId, entry.nbt)

            gui.serverExecute {
                // In cancel callback / response message, use localized display name
                val stack = ItemKey.tryBuildFullStackFromSnbt(entry.nbt, 1) ?: run {
                    val id = Identifier.tryParse(entry.itemId)
                    val itemType = if (id != null && Registries.ITEM.containsId(id))
                                   Registries.ITEM.get(id) else Items.STONE
                    ItemStack(itemType)
                }

                gui.player.sendMessage(
                    Text.translatable("servermarket.menu.mypurchase.cancel_success", TextFormat.displayItemName(stack, entry.itemId)),
                    false
                )
                // 刷新界面
                show(false)
            }
        }
    }


    private fun buildNav() {
        val totalPages = gui.pageCountOf(myPurchases.size)

        // 上一页
        setNavButton(45, Items.ARROW, Text.translatable("servermarket.menu.prev")) {
            if (gui.page > 0) {
                gui.page--
                show(false)
            }
        }

        // 帮助信息
        val helpItem = GuiElementBuilder(Items.BOOK)
            .setName(Text.translatable("servermarket.menu.mypurchase.title"))
            .addLoreLine(Text.translatable("servermarket.menu.mypurchase.tip1"))
            .addLoreLine(Text.translatable("servermarket.menu.mypurchase.tip2"))
            .addLoreLine(Text.translatable("servermarket.menu.mypurchase.count", myPurchases.size))
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
