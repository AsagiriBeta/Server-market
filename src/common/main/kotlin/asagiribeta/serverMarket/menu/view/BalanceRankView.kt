package asagiribeta.serverMarket.menu.view

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.menu.MarketGui
import asagiribeta.serverMarket.menu.builder.GuiElementBuilders
import asagiribeta.serverMarket.menu.builder.GuiElementBuilders.setPlayerSkin
import asagiribeta.serverMarket.model.BalanceRankEntry
import asagiribeta.serverMarket.util.MoneyFormat
import asagiribeta.serverMarket.util.marketServer
import asagiribeta.serverMarket.util.whenCompleteOnServerThread
import eu.pb4.sgui.api.elements.GuiElementBuilder
import net.minecraft.item.Items
import net.minecraft.text.Text

/**
 * 余额排行榜视图（管理员）
 */
class BalanceRankView(private val gui: MarketGui) {
    private var entries: List<BalanceRankEntry> = emptyList()

    fun show(resetPage: Boolean = true) {
        gui.mode = ViewMode.BALANCE_RANK
        if (resetPage) gui.page = 0
        gui.clearContent()
        gui.clearNav()

        loadRankings()
    }

    private fun loadRankings() {
        ServerMarket.instance.economyService.getTopBalances(100)
            .whenCompleteOnServerThread(gui.player.marketServer()) { list, _ ->
                if (gui.mode != ViewMode.BALANCE_RANK) return@whenCompleteOnServerThread

                entries = list ?: emptyList()
                gui.renderPagedContent(
                    list = entries,
                    buildElement = { entry -> buildRankElement(entry) },
                    buildNav = { buildNav() }
                )
            }
    }

    private fun buildRankElement(entry: BalanceRankEntry): GuiElementBuilder {
        val rank = entries.indexOf(entry) + 1
        val displayName = entry.name.ifBlank { entry.uuid.toString() }

        val element = GuiElementBuilder(Items.PLAYER_HEAD)
            .setName(Text.literal("#$rank $displayName"))
            .addLoreLine(Text.translatable("servermarket.menu.balance_rank.balance", MoneyFormat.format(entry.balance, 2)))
            .addLoreLine(Text.translatable("servermarket.menu.balance_rank.uuid", entry.uuid.toString()))

        val profile = GuiElementBuilders.obtainProfileByUuid(gui.player, entry.uuid, displayName)
        element.setPlayerSkin(profile)

        return element
    }

    private fun buildNav() {
        val totalPages = gui.pageCountOf(entries.size)

        val helpItem = GuiElementBuilder(Items.BOOK)
            .setName(Text.translatable("servermarket.menu.balance_rank.title"))
            .addLoreLine(Text.translatable("servermarket.menu.balance_rank.tip1"))
            .addLoreLine(Text.translatable("servermarket.menu.balance_rank.tip2"))

        gui.setStandardNavForListView(
            totalPages = totalPages,
            helpItem = helpItem,
            refresh = { show(false) }
        )
    }
}
