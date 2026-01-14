package asagiribeta.serverMarket.menu.view

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.menu.MarketGui
import asagiribeta.serverMarket.model.ParcelEntry
import asagiribeta.serverMarket.util.ItemKey
import asagiribeta.serverMarket.util.TextFormat
import asagiribeta.serverMarket.util.whenCompleteOnServerThread
import eu.pb4.sgui.api.elements.GuiElementBuilder
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import java.text.SimpleDateFormat
import java.util.Date

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

        setNavButton(46, Items.BOOK, Text.translatable("servermarket.menu.loading")) {}
        buildNav()

        loadParcels()
    }

    private fun loadParcels() {
        ServerMarket.instance.parcelService.getParcelsForPlayerMerged(gui.player.uuid)
            .whenCompleteOnServerThread(gui.player.entityWorld.server) { list, _ ->
                if (gui.mode != ViewMode.PARCEL_STATION) return@whenCompleteOnServerThread

                parcelEntries = list ?: emptyList()
                val totalPages = gui.pageCountOf(parcelEntries.size)
                gui.page = gui.clampPage(gui.page, totalPages)

                gui.clearContent()

                if (parcelEntries.isEmpty()) {
                    val emptyItem = GuiElementBuilder(Items.BARRIER)
                        .setName(Text.translatable("servermarket.menu.parcel.empty"))
                    gui.setSlot(22, emptyItem)
                } else {
                    gui.pageSlice(parcelEntries, gui.page).forEachIndexed { idx, entry ->
                        gui.setSlot(idx, buildParcelElement(entry))
                    }
                }

                buildNav()
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

        val name = TextFormat.displayItemName(stack, entry.itemId)

        // reason 既要兼容旧数据（直接存中文/英文），也要支持新数据（存 translation key）
        val reasonText = if (entry.reason.startsWith("servermarket.")) {
            Text.translatable(entry.reason)
        } else {
            Text.literal(entry.reason)
        }

        val element = GuiElementBuilder.from(stack)
            .setName(Text.literal(name))
            .addLoreLine(Text.translatable("servermarket.menu.parcel.reason", reasonText))
            .addLoreLine(Text.translatable("servermarket.menu.parcel.time", timeStr))
            .addLoreLine(Text.translatable("servermarket.ui.quantity", entry.quantity.toString()))
            .addLoreLine(Text.translatable("servermarket.menu.parcel.click_tip").copy().formatted(net.minecraft.util.Formatting.GREEN))
            .setCallback { _, _, _ ->
                handleParcelReceive(entry)
            }

        return element
    }

    private fun buildNav() {
        val totalPages = gui.pageCountOf(parcelEntries.size)

        setNavButton(45, Items.ARROW, Text.translatable("servermarket.menu.prev")) {
            if (gui.page > 0) {
                gui.page--
                show(false)
            }
        }

        val helpItem = GuiElementBuilder(Items.BOOK)
            .setName(Text.translatable("servermarket.menu.parcel.title"))
            .addLoreLine(Text.translatable("servermarket.menu.parcel.tip1"))
            .addLoreLine(Text.translatable("servermarket.menu.parcel.tip2"))
            .addLoreLine(Text.translatable("servermarket.menu.parcel.tip3"))
            .addLoreLine(Text.translatable("servermarket.menu.parcel.tip4"))
        gui.setSlot(46, helpItem)

        setNavButton(47, Items.NETHER_STAR, Text.translatable("servermarket.menu.back_home")) {
            gui.showHome()
        }

        setNavButton(49, Items.BARRIER, Text.translatable("servermarket.menu.close")) {
            gui.close()
        }

        setNavButton(53, Items.ARROW, Text.translatable("servermarket.menu.next", "${gui.page + 1}/$totalPages")) {
            if (gui.page + 1 < totalPages) {
                gui.page++
                show(false)
            }
        }
    }

    private fun handleParcelReceive(entry: ParcelEntry) {
        val player = gui.player
        val normalizedNbt = ItemKey.normalizeSnbt(entry.nbt)

        ServerMarket.instance.parcelService.removeParcelsByItem(
            player.uuid,
            entry.itemId,
            normalizedNbt
        ).whenComplete { count, _ ->
            gui.serverExecute {
                if (count != null && count > 0) {
                    val stack = ItemKey.tryBuildFullStackFromSnbt(normalizedNbt, entry.quantity) ?: run {
                        val id = Identifier.tryParse(entry.itemId)
                        val itemType = if (id != null && Registries.ITEM.containsId(id))
                            Registries.ITEM.get(id) else Items.AIR
                        ItemStack(itemType, entry.quantity)
                    }
                    player.giveItemStack(stack)

                    val name = TextFormat.displayItemName(stack, entry.itemId)

                    player.sendMessage(
                        Text.translatable(
                            "servermarket.menu.parcel.received",
                            entry.quantity,
                            name
                        ),
                        false
                    )

                    refreshAfterReceive()
                } else {
                    player.sendMessage(Text.translatable("servermarket.menu.parcel.error"), false)
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

    private fun setNavButton(slot: Int, item: net.minecraft.item.Item, name: Text, callback: () -> Unit) {
        val element = GuiElementBuilder(item)
            .setName(name)
            .setCallback { _, _, _ -> callback() }
        gui.setSlot(slot, element)
    }
}
