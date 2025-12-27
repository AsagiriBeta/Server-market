package asagiribeta.serverMarket.menu.view

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.menu.MarketGui
import asagiribeta.serverMarket.repository.PlayerPurchaseEntry
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
 * 我的收购视图 - 玩家管理自己的收购订单
 */
class MyPurchaseView(private val gui: MarketGui) {

    private var myPurchases: List<PlayerPurchaseEntry> = emptyList()

    fun show(resetPage: Boolean = true) {
        gui.mode = ViewMode.MY_PURCHASE
        if (resetPage) gui.page = 0
        gui.clearContent()
        gui.clearNav()

        // 显示加载提示
        setNavButton(46, Items.BOOK, Language.get("menu.loading")) {}
        buildNav()

        // 异步加载玩家的收购订单
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

        val element = GuiElementBuilder.from(stack)
            .setName(stack.getName())
            .addLoreLine(Text.literal(Language.get("ui.price", String.format(Locale.ROOT, "%.2f", entry.price))))
            .addLoreLine(Text.literal(Language.get("menu.mypurchase.progress",
                entry.currentAmount, entry.targetAmount, progressPercent)))
            .addLoreLine(Text.literal(Language.get("menu.mypurchase.remaining", entry.remaining)))
            .addLoreLine(Text.literal(""))

        if (entry.currentAmount > 0) {
            element.addLoreLine(Text.literal("§a${Language.get("menu.mypurchase.auto_sent_tip")}"))
        }
        if (entry.isCompleted) {
            element.addLoreLine(Text.literal("§6${Language.get("menu.mypurchase.completed")}"))
        }
        element.addLoreLine(Text.literal("§7${Language.get("menu.mypurchase.click_cancel")}"))
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
                gui.player.sendMessage(
                    Text.literal(Language.get("menu.mypurchase.cancel_success", entry.itemId)),
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
        setNavButton(45, Items.ARROW, Language.get("menu.prev")) {
            if (gui.page > 0) {
                gui.page--
                show(false)
            }
        }

        // 帮助信息
        val helpItem = GuiElementBuilder(Items.BOOK)
            .setName(Text.literal(Language.get("menu.mypurchase.title")))
            .addLoreLine(Text.literal(Language.get("menu.mypurchase.tip1")))
            .addLoreLine(Text.literal(Language.get("menu.mypurchase.tip2")))
            .addLoreLine(Text.literal(Language.get("menu.mypurchase.count", myPurchases.size)))
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

