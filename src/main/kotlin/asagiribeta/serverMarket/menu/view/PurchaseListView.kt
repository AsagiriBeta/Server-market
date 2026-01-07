package asagiribeta.serverMarket.menu.view

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.menu.MarketGui
import asagiribeta.serverMarket.model.PurchaseMenuEntry
import asagiribeta.serverMarket.model.SellToBuyerResult
import asagiribeta.serverMarket.util.ItemKey
import asagiribeta.serverMarket.util.MoneyFormat
import asagiribeta.serverMarket.util.TextFormat
import asagiribeta.serverMarket.util.whenCompleteOnServerThread
import eu.pb4.sgui.api.ClickType
import eu.pb4.sgui.api.elements.GuiElementBuilder
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.text.Text
import net.minecraft.util.Identifier
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
        setNavButton(46, Items.BOOK, Text.translatable("servermarket.menu.loading")) {}
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
                val totalPages = gui.pageCountOf(purchaseEntries.size)
                gui.page = gui.clampPage(gui.page, totalPages)

                gui.clearContent()

                // 显示当前页的收购订单
                gui.pageSlice(purchaseEntries, gui.page).forEachIndexed { idx, entry ->
                    gui.setSlot(idx, buildPurchaseElement(entry))
                }

                buildNav()
        }
    }

    private fun buildPurchaseElement(entry: PurchaseMenuEntry): GuiElementBuilder {
        // 构建物品栈
        val stack = ItemKey.tryBuildFullStackFromSnbt(entry.nbt, 1) ?: run {
            val id = Identifier.tryParse(entry.itemId)
            val itemType = if (id != null && Registries.ITEM.containsId(id))
                           Registries.ITEM.get(id) else Items.STONE
            ItemStack(itemType)
        }

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

        setNavButton(45, Items.ARROW, Text.translatable("servermarket.menu.prev")) {
            if (gui.page > 0) {
                gui.page--
                show(false)
            }
        }

        val helpItem = GuiElementBuilder(Items.BOOK)
            .setName(Text.translatable("servermarket.menu.purchase_list.title"))
            .addLoreLine(Text.translatable("servermarket.menu.purchase_list.tip1"))
            .addLoreLine(Text.translatable("servermarket.menu.purchase_list.tip2"))
            .addLoreLine(Text.translatable("servermarket.menu.purchase_list.tip3"))
            .addLoreLine(Text.translatable("servermarket.menu.purchase_list.tip4"))
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

    private fun handleSellToPurchase(entry: PurchaseMenuEntry, sellAll: Boolean) {
        val player = gui.player
        val desired = if (sellAll) 64 else 1
        val itemId = entry.itemId
        val nbt = entry.nbt

        // 检查玩家背包中的物品数量
        val allStacks = (0 until player.inventory.size()).map { player.inventory.getStack(it) }.filter {
            !it.isEmpty && Registries.ITEM.getId(it.item).toString() == itemId && ItemKey.snbtOf(it) == nbt
        }

        val totalAvailable = allStacks.sumOf { it.count }
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

                        player.sendMessage(
                            Text.translatable(
                                "servermarket.menu.purchase.sell_ok",
                                result.amount,
                                itemId,
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

    private fun setNavButton(slot: Int, item: net.minecraft.item.Item, name: Text, callback: () -> Unit) {
        val element = GuiElementBuilder(item)
            .setName(name)
            .setCallback { _, _, _ -> callback() }
        gui.setSlot(slot, element)
    }
}
