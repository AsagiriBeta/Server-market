package asagiribeta.serverMarket.menu.view

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.menu.MarketGui
import asagiribeta.serverMarket.util.Language
import asagiribeta.serverMarket.util.whenCompleteOnServerThread
import eu.pb4.sgui.api.elements.GuiElementBuilder
import net.minecraft.item.Items
import net.minecraft.text.Text

/**
 * 首页视图
 */
class HomeView(private val gui: MarketGui) {

    fun show() {
        gui.mode = ViewMode.HOME
        gui.page = 0
        gui.clearContent()
        gui.clearNav()

        // 余额显示（槽位 4）
        val balanceItem = GuiElementBuilder(Items.GOLD_INGOT)
            .setName(Text.literal(Language.get("menu.balance", "...")))
        gui.setSlot(4, balanceItem)

        // 帮助信息（槽位 22）
        val helpItem = GuiElementBuilder(Items.BOOK)
            .setName(Text.literal(Language.get("menu.home.help_title")))
            .addLoreLine(Text.literal(Language.get("menu.home.help1")))
            .addLoreLine(Text.literal(Language.get("menu.home.help2")))
            .addLoreLine(Text.literal(Language.get("menu.home.help3")))
        gui.setSlot(22, helpItem)

        // 导航按钮
        setNavButton(47, Items.WRITABLE_BOOK, Language.get("menu.enter_my_purchase")) {
            gui.showMyPurchase(true)
        }
        setNavButton(48, Items.ENDER_CHEST, Language.get("menu.enter_my_shop")) {
            gui.showMyShop(true)
        }
        setNavButton(49, Items.BARRIER, Language.get("menu.close")) {
            gui.close()
        }
        setNavButton(50, Items.CHEST_MINECART, Language.get("menu.enter_parcel")) {
            gui.showParcelStation(true)
        }
        setNavButton(51, Items.EMERALD, Language.get("menu.enter_purchase")) {
            gui.showPurchaseList(true)
        }
        setNavButton(53, Items.CHEST, Language.get("menu.enter_market_sellers")) {
            gui.showSellerList(true)
        }

        // 异步加载余额和包裹数量
        loadAsyncData()
    }

    private fun loadAsyncData() {
        val db = ServerMarket.instance.database
        val player = gui.player

        // 加载余额
        db.supplyAsync0 { db.getBalance(player.uuid) }
            .whenCompleteOnServerThread(gui.player.entityWorld.server) { balance, _ ->
                if (gui.mode != ViewMode.HOME) return@whenCompleteOnServerThread

                val updatedBalance = GuiElementBuilder(Items.GOLD_INGOT)
                    .setName(Text.literal(Language.get("menu.balance", "%.2f".format(balance))))
                gui.setSlot(4, updatedBalance)
            }

        // 加载包裹数量
        ServerMarket.instance.parcelService.getParcelCountForPlayer(player.uuid)
            .whenCompleteOnServerThread(gui.player.entityWorld.server) { count, _ ->
                if (gui.mode != ViewMode.HOME || count == null || count <= 0) return@whenCompleteOnServerThread

                val updated = GuiElementBuilder(Items.CHEST_MINECART)
                    .setName(Text.literal(Language.get("menu.enter_parcel")))
                    .addLoreLine(Text.literal("§e${Language.get("menu.parcel.count", count)}"))
                    .setCallback { _, _, _ -> gui.showParcelStation(true) }
                gui.setSlot(50, updated)
            }
    }

    private fun setNavButton(slot: Int, item: net.minecraft.item.Item, name: String, callback: () -> Unit) {
        val element = GuiElementBuilder(item)
            .setName(Text.literal(name))
            .setCallback { _, _, _ -> callback() }
        gui.setSlot(slot, element)
    }
}

