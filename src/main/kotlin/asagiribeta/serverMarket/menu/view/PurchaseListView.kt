package asagiribeta.serverMarket.menu.view

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.menu.MarketGui
import asagiribeta.serverMarket.model.PurchaseMenuEntry
import asagiribeta.serverMarket.model.SellToBuyerResult
import asagiribeta.serverMarket.util.ItemKey
import asagiribeta.serverMarket.util.ItemStackFactory
import asagiribeta.serverMarket.util.InventoryQuery
import asagiribeta.serverMarket.util.MoneyFormat
import asagiribeta.serverMarket.util.TextFormat
import asagiribeta.serverMarket.util.whenCompleteOnServerThread
import eu.pb4.sgui.api.ClickType
import eu.pb4.sgui.api.elements.GuiElementBuilder
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.text.Text
import kotlin.math.min

/**
 * 收购列表视图
 */
class PurchaseListView(private val gui: MarketGui) {

    private var purchaseEntries: List<PurchaseMenuEntry> = emptyList()

    fun show(resetPage: Boolean = true) {
        gui.mode = ViewMode.PURCHASE_LIST
        if (resetPage) gui.page = 0
        gui.clearContent()
        gui.clearNav()

        // 显示加载提示
        gui.setNavButton(46, Items.BOOK, Text.translatable("servermarket.menu.loading")) {}
        buildNav()

        // 异步加载收购列表
        loadPurchaseList()
    }

    private fun loadPurchaseList() {
        val db = ServerMarket.instance.database
        db.supplyAsync {
            val systemPurchases = db.purchaseRepository.getAllSystemPurchases()
            val playerPurchases = db.purchaseRepository.getAllPlayerPurchases()

            // 合并系统和玩家收购
            val allPurchases = mutableListOf<PurchaseMenuEntry>()

            // 添加系统收购
            systemPurchases.forEach { order ->
                allPurchases.add(
                    PurchaseMenuEntry(
                        itemId = order.itemId,
                        nbt = order.nbt,
                        price = order.price,
                        buyerName = "SERVER",
                        buyerUuid = null,
                        limitPerDay = order.limitPerDay
                    )
                )
            }

            // 添加玩家收购（仅未完成的）
            playerPurchases.filter { !it.isCompleted }.forEach { entry ->
                allPurchases.add(
                    PurchaseMenuEntry(
                        itemId = entry.itemId,
                        nbt = entry.nbt,
                        price = entry.price,
                        buyerName = entry.buyerName,
                        buyerUuid = entry.buyerUuid,
                        limitPerDay = -1,
                        targetAmount = entry.targetAmount,
                        currentAmount = entry.currentAmount
                    )
                )
            }

            allPurchases
        }.whenCompleteOnServerThread(gui.player.entityWorld.server) { list, _ ->
            if (gui.mode != ViewMode.PURCHASE_LIST) return@whenCompleteOnServerThread

            purchaseEntries = list ?: emptyList()
            gui.renderPagedContent(
                list = purchaseEntries,
                buildElement = { entry -> buildPurchaseElement(entry) },
                buildNav = { buildNav() }
            )
        }
    }

    private fun buildPurchaseElement(entry: PurchaseMenuEntry): GuiElementBuilder {
        // 构建物品栈
        val stack = ItemStackFactory.forDisplay(
            itemId = entry.itemId,
            snbt = entry.nbt,
            count = 1,
            fallbackItem = Items.STONE
        )

        val limitStr = if (entry.limitPerDay < 0) "∞" else entry.limitPerDay.toString()
        val remainingStr = if (entry.targetAmount > 0) {
            "${entry.targetAmount - entry.currentAmount}/${entry.targetAmount}"
        } else "∞"

        val name = TextFormat.displayItemName(stack, entry.itemId)

        val element = GuiElementBuilder.from(stack)
            .setName(Text.literal(name))
            .addLoreLine(Text.translatable("servermarket.menu.purchase.buyer", entry.buyerName))
            .addLoreLine(Text.translatable("servermarket.menu.purchase.price", MoneyFormat.format(entry.price, 2)))
            .addLoreLine(Text.translatable("servermarket.menu.purchase.limit", if (entry.buyerName == "SERVER") limitStr else remainingStr))
            .addLoreLine(Text.translatable("servermarket.menu.purchase.click_tip"))
            .setCallback { _, clickType, _ ->
                val sellAll = clickType == ClickType.MOUSE_RIGHT || clickType == ClickType.MOUSE_RIGHT_SHIFT
                handleSellToPurchase(entry, sellAll)
            }

        return element
    }

    private fun buildNav() {
        val totalPages = gui.pageCountOf(purchaseEntries.size)

        val helpItem = GuiElementBuilder(Items.BOOK)
            .setName(Text.translatable("servermarket.menu.purchase_list.title"))
            .addLoreLine(Text.translatable("servermarket.menu.purchase_list.tip1"))
            .addLoreLine(Text.translatable("servermarket.menu.purchase_list.tip2"))
            .addLoreLine(Text.translatable("servermarket.menu.purchase_list.tip3"))
            .addLoreLine(Text.translatable("servermarket.menu.purchase_list.tip4"))

        gui.setStandardNavForListView(
            totalPages = totalPages,
            helpItem = helpItem,
            refresh = { show(false) }
        )
    }

    private fun handleSellToPurchase(entry: PurchaseMenuEntry, sellAll: Boolean) {
        val player = gui.player
        val desired = if (sellAll) 64 else 1
        val itemId = entry.itemId
        val nbt = ItemKey.normalizeSnbt(entry.nbt)

        // 检查玩家背包中的物品数量（NBT 需要 normalize，否则会出现“看似同 NBT 但字符串不同”导致匹配错误）
        val allStacks = InventoryQuery.findMatchingStacks(player, itemId, nbt)

        val totalAvailable = InventoryQuery.countTotal(allStacks)
        if (totalAvailable < desired) {
            player.sendMessage(Text.translatable("servermarket.menu.purchase.not_enough_items"), false)
            return
        }

        // 使用 PurchaseService 处理出售逻辑
        val buyerFilter = if (entry.buyerName == "SERVER") "SERVER" else entry.buyerUuid?.toString()

        ServerMarket.instance.purchaseService.sellToBuyer(
            sellerUuid = player.uuid,
            sellerName = player.name.string,
            itemId = itemId,
            nbt = nbt,
            quantity = desired,
            buyerFilter = buyerFilter
        ).whenComplete { result: SellToBuyerResult?, _ ->
            gui.serverExecute {
                when (result) {
                    is SellToBuyerResult.Success -> {
                        // 扣除物品
                        var remaining = result.amount
                        for (stack in allStacks) {
                            if (remaining <= 0) break
                            val deduct = min(remaining, stack.count)
                            stack.decrement(deduct)
                            remaining -= deduct
                        }

                        // 用展示名而不是 minecraft:id
                        val name = TextFormat.displayItemName(
                            ItemKey.tryBuildFullStackFromSnbt(nbt, 1) ?: ItemStack.EMPTY,
                            itemId
                        )

                        player.sendMessage(
                            Text.translatable(
                                "servermarket.menu.purchase.sell_ok",
                                result.amount,
                                name,
                                MoneyFormat.format(result.totalEarned, 2)
                            ),
                            false
                        )

                        refreshAfterSell()
                    }

                    SellToBuyerResult.NotFound -> {
                        player.sendMessage(Text.translatable("servermarket.menu.purchase.error"), false)
                    }

                    SellToBuyerResult.InsufficientItems -> {
                        player.sendMessage(Text.translatable("servermarket.menu.purchase.not_enough_items"), false)
                    }

                    is SellToBuyerResult.InsufficientFunds -> {
                        player.sendMessage(Text.translatable("servermarket.menu.purchase.buyer_no_money"), false)
                    }

                    is SellToBuyerResult.LimitExceeded -> {
                        player.sendMessage(Text.translatable("servermarket.menu.purchase.limit_exceeded"), false)
                    }

                    is SellToBuyerResult.Error, null -> {
                        player.sendMessage(Text.translatable("servermarket.menu.purchase.error"), false)
                    }
                }
            }
        }
    }

    private fun refreshAfterSell() {
        if (gui.mode != ViewMode.PURCHASE_LIST) return
        // Reload list and view.
        show(false)
    }
}
