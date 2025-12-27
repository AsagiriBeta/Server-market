package asagiribeta.serverMarket.menu.view

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.menu.MarketGui
import asagiribeta.serverMarket.model.ParcelEntry
import asagiribeta.serverMarket.util.ItemKey
import asagiribeta.serverMarket.util.Language
import eu.pb4.sgui.api.elements.GuiElementBuilder
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import java.text.SimpleDateFormat
import java.util.*

/**
 * 快递驿站视图
 */
class ParcelStationView(private val gui: MarketGui) {

    private var parcelEntries: List<ParcelEntry> = emptyList()

    fun show(resetPage: Boolean = true) {
        gui.mode = ViewMode.PARCEL_STATION
        if (resetPage) gui.page = 0
        gui.clearContent()
        gui.clearNav()

        // 显示加载提示
        setNavButton(46, Items.BOOK, Language.get("menu.loading")) {}
        buildNav()

        // 异步加载包裹列表（合并相同物品）
        loadParcels()
    }

    private fun loadParcels() {
        ServerMarket.instance.parcelService.getParcelsForPlayerMergedAsync(gui.player.uuid)
            .whenComplete { list, _ ->
                gui.serverExecute {
                    if (gui.mode != ViewMode.PARCEL_STATION) return@serverExecute

                    parcelEntries = list ?: emptyList()
                    val totalPages = gui.pageCountOf(parcelEntries.size)
                    gui.page = gui.clampPage(gui.page, totalPages)

                    gui.clearContent()

                    if (parcelEntries.isEmpty()) {
                        // 显示空包裹提示
                        val emptyItem = GuiElementBuilder(Items.BARRIER)
                            .setName(Text.literal(Language.get("menu.parcel.empty")))
                        gui.setSlot(22, emptyItem)
                    } else {
                        // 显示当前页的包裹
                        gui.pageSlice(parcelEntries, gui.page).forEachIndexed { idx, entry ->
                            gui.setSlot(idx, buildParcelElement(entry))
                        }
                    }

                    buildNav()
                }
            }
    }

    private fun buildParcelElement(entry: ParcelEntry): GuiElementBuilder {
        // 构建物品栈
        val stack = ItemKey.tryBuildFullStackFromSnbt(entry.nbt, entry.quantity) ?: run {
            val id = Identifier.tryParse(entry.itemId)
            val itemType = if (id != null && Registries.ITEM.containsId(id))
                           Registries.ITEM.get(id) else Items.CHEST
            ItemStack(itemType, entry.quantity)
        }

        // 格式化时间
        val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date(entry.timestamp))

        val element = GuiElementBuilder.from(stack)
            .setName(stack.getName())
            .addLoreLine(Text.literal(Language.get("menu.parcel.reason", entry.reason)))
            .addLoreLine(Text.literal(Language.get("menu.parcel.time", timeStr)))
            .addLoreLine(Text.literal(Language.get("ui.quantity", entry.quantity)))
            .addLoreLine(Text.literal("§a${Language.get("menu.parcel.click_tip")}"))
            .setCallback { _, _, _ ->
                handleParcelReceive(entry)
            }

        return element
    }

    private fun buildNav() {
        val totalPages = gui.pageCountOf(parcelEntries.size)

        setNavButton(45, Items.ARROW, Language.get("menu.prev")) {
            if (gui.page > 0) {
                gui.page--
                show(false)
            }
        }

        val helpItem = GuiElementBuilder(Items.BOOK)
            .setName(Text.literal(Language.get("menu.parcel.title")))
            .addLoreLine(Text.literal(Language.get("menu.parcel.tip1")))
            .addLoreLine(Text.literal(Language.get("menu.parcel.tip2")))
            .addLoreLine(Text.literal(Language.get("menu.parcel.tip3")))
            .addLoreLine(Text.literal(Language.get("menu.parcel.tip4")))
        gui.setSlot(46, helpItem)

        setNavButton(47, Items.NETHER_STAR, Language.get("menu.back_home")) {
            gui.showHome()
        }

        setNavButton(49, Items.BARRIER, Language.get("menu.close")) {
            gui.close()
        }

        setNavButton(53, Items.ARROW, Language.get("menu.next", "${gui.page + 1}/$totalPages")) {
            if (gui.page + 1 < totalPages) {
                gui.page++
                show(false)
            }
        }
    }

    private fun handleParcelReceive(entry: ParcelEntry) {
        val player = gui.player

        // 删除该玩家的所有相同物品的包裹
        ServerMarket.instance.parcelService.removeParcelsByItemAsync(
            player.uuid,
            entry.itemId,
            entry.nbt
        ).whenComplete { count, _ ->
            gui.serverExecute {
                if (count != null && count > 0) {
                    // 发放物品
                    val stack = ItemKey.tryBuildFullStackFromSnbt(entry.nbt, entry.quantity) ?: run {
                        val id = Identifier.tryParse(entry.itemId)
                        val itemType = if (id != null && Registries.ITEM.containsId(id))
                                       Registries.ITEM.get(id) else Items.AIR
                        ItemStack(itemType, entry.quantity)
                    }
                    player.giveItemStack(stack)

                    player.sendMessage(
                        Text.literal(Language.get(
                            "menu.parcel.received",
                            entry.quantity,
                            entry.itemId
                        )),
                        false
                    )

                    // 刷新包裹列表
                    refreshAfterReceive()
                } else {
                    player.sendMessage(
                        Text.literal(Language.get("menu.parcel.error")),
                        false
                    )
                }
            }
        }
    }

    private fun refreshAfterReceive() {
        if (gui.mode == ViewMode.PARCEL_STATION) {
            val oldPage = gui.page
            show(resetPage = false)
            gui.page = oldPage.coerceAtMost(gui.pageCountOf(parcelEntries.size) - 1)
        }
    }

    private fun setNavButton(slot: Int, item: net.minecraft.item.Item, name: String, callback: () -> Unit) {
        val element = GuiElementBuilder(item)
            .setName(Text.literal(name))
            .setCallback { _, _, _ -> callback() }
        gui.setSlot(slot, element)
    }
}

