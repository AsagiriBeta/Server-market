package asagiribeta.serverMarket.menu.view

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.menu.MarketGui
import asagiribeta.serverMarket.repository.MarketItem
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
 * 我的店铺商品详情视图
 */
class MyShopDetailView(private val gui: MarketGui) {

    private var currentItem: MarketItem? = null

    fun show(item: MarketItem) {
        gui.mode = ViewMode.MY_SHOP
        currentItem = item

        gui.clearContent()
        gui.clearNav()

        renderDetailView()
    }

    private fun renderDetailView() {
        val item = currentItem ?: return

        // 构建物品展示
        val stack = ItemKey.tryBuildFullStackFromSnbt(item.nbt, item.quantity) ?: run {
            val id = Identifier.tryParse(item.itemId)
            val itemType = if (id != null && Registries.ITEM.containsId(id))
                Registries.ITEM.get(id) else Items.STONE
            ItemStack(itemType, item.quantity)
        }

        val displayName = TextFormat.displayItemName(stack, item.itemId)

        // 中央展示物品（槽位 22）
        val displayItem = GuiElementBuilder.from(stack)
            .setName(Text.literal("§e$displayName"))
            .addLoreLine(Text.literal(""))
            .addLoreLine(Text.translatable("servermarket.ui.price", MoneyFormat.format(item.price, 2)))
            .addLoreLine(Text.translatable("servermarket.ui.quantity", item.quantity.toString()))
            .addLoreLine(Text.literal(""))
            .addLoreLine(
                Text.translatable(
                    "servermarket.menu.myshop.detail.total_value",
                    MoneyFormat.format(item.price * item.quantity, 2)
                )
            )
        gui.setSlot(22, displayItem)

        // 数量调整区域
        renderQuantityControls()

        // 操作按钮区域
        renderActionButtons()
    }

    private fun renderQuantityControls() {
        val item = currentItem ?: return

        // 左侧：下架按钮组（减少库存，发送到快递驿站）
        setActionButton(
            19,
            Items.RED_CONCRETE,
            Text.translatable("servermarket.menu.myshop.detail.unlist_64"),
            Text.translatable("servermarket.menu.myshop.detail.unlist_tip")
        ) { handlePartialUnlist(64) }

        setActionButton(
            28,
            Items.ORANGE_CONCRETE,
            Text.translatable("servermarket.menu.myshop.detail.unlist_16"),
            Text.translatable("servermarket.menu.myshop.detail.unlist_tip")
        ) { handlePartialUnlist(16) }

        setActionButton(
            37,
            Items.YELLOW_CONCRETE,
            Text.translatable("servermarket.menu.myshop.detail.unlist_1"),
            Text.translatable("servermarket.menu.myshop.detail.unlist_tip")
        ) { handlePartialUnlist(1) }

        // 中央显示（槽位 31）
        val infoDisplay = GuiElementBuilder(Items.CHEST)
            .setName(Text.translatable("servermarket.menu.myshop.detail.current_stock").copy().formatted(net.minecraft.util.Formatting.GOLD))
            .addLoreLine(Text.literal("§e${item.quantity}"))
            .addLoreLine(Text.literal(""))
            .addLoreLine(Text.translatable("servermarket.menu.myshop.detail.left_unlist").copy().formatted(net.minecraft.util.Formatting.GRAY))
            .addLoreLine(Text.translatable("servermarket.menu.myshop.detail.right_restock").copy().formatted(net.minecraft.util.Formatting.GRAY))
        gui.setSlot(31, infoDisplay)

        // 右侧：补货按钮组（从背包扣除，增加库存）
        setActionButton(
            21,
            Items.LIME_CONCRETE,
            Text.translatable("servermarket.menu.myshop.detail.restock_1"),
            Text.translatable("servermarket.menu.myshop.detail.restock_tip")
        ) { handleRestock(1) }

        setActionButton(
            30,
            Items.GREEN_CONCRETE,
            Text.translatable("servermarket.menu.myshop.detail.restock_16"),
            Text.translatable("servermarket.menu.myshop.detail.restock_tip")
        ) { handleRestock(16) }

        setActionButton(
            39,
            Items.CYAN_CONCRETE,
            Text.translatable("servermarket.menu.myshop.detail.restock_64"),
            Text.translatable("servermarket.menu.myshop.detail.restock_tip")
        ) { handleRestock(64) }
    }

    private fun renderActionButtons() {
        val item = currentItem ?: return

        // 完全下架（槽位 46）
        val unlistButton = GuiElementBuilder(Items.BARRIER)
            .setName(Text.translatable("servermarket.menu.myshop.detail.unlist_all").copy().formatted(net.minecraft.util.Formatting.RED))
            .addLoreLine(Text.translatable("servermarket.menu.myshop.detail.unlist_all_confirm", item.quantity))
            .setCallback { _, _, _ -> handleUnlistAll() }
        gui.setSlot(46, unlistButton)

        // 返回按钮（槽位 47）
        setNavButton(47, Items.NETHER_STAR, Text.translatable("servermarket.menu.back")) {
            gui.showMyShop(false)
        }

        // 关闭按钮（槽位 49）
        setNavButton(49, Items.BARRIER, Text.translatable("servermarket.menu.close")) {
            gui.close()
        }
    }

    /**
     * 处理部分下架（减少库存，发送到快递驿站）
     */
    private fun handlePartialUnlist(amount: Int) {
        val item = currentItem ?: return
        val db = ServerMarket.instance.database
        val playerUuid = gui.player.uuid

        // 计算实际下架数量
        val actualAmount = amount.coerceAtMost(item.quantity)

        if (actualAmount <= 0) {
            gui.player.sendMessage(
                Text.translatable("servermarket.menu.myshop.detail.no_stock").copy().formatted(net.minecraft.util.Formatting.RED),
                false
            )
            return
        }

        val displayName = TextFormat.displayItemName(
            ItemKey.tryBuildFullStackFromSnbt(item.nbt, 1) ?: ItemStack.EMPTY,
            item.itemId
        )

        db.runAsync {
            // 减少库存
            db.marketRepository.incrementPlayerItemQuantity(
                playerUuid, item.itemId, item.nbt, -actualAmount
            )

            // 发送到快递驿站
            db.parcelRepository.addParcel(
                playerUuid,
                gui.player.name.string,
                item.itemId,
                item.nbt,
                actualAmount,
                Text.translatable("servermarket.menu.myshop.detail.unlist_reason").string
            )

            gui.serverExecute {
                gui.player.sendMessage(
                    Text.translatable(
                        "servermarket.menu.myshop.detail.unlist_success",
                        actualAmount,
                        displayName
                    ),
                    false
                )

                // 刷新当前项目数据
                val updatedQuantity = item.quantity - actualAmount
                if (updatedQuantity > 0) {
                    currentItem = item.copy(quantity = updatedQuantity)
                    renderDetailView()
                } else {
                    // 库存为0，返回列表
                    gui.showMyShop(false)
                }
            }
        }
    }

    /**
     * 处理补货（从玩家背包扣除物品，增加库存）
     */
    private fun handleRestock(amount: Int) {
        val item = currentItem ?: return
        val db = ServerMarket.instance.database
        val playerUuid = gui.player.uuid
        val player = gui.player

        val displayName = TextFormat.displayItemName(
            ItemKey.tryBuildFullStackFromSnbt(item.nbt, 1) ?: ItemStack.EMPTY,
            item.itemId
        )

        db.runAsync {
            // 构建要查找的物品
            val targetStack = ItemKey.tryBuildFullStackFromSnbt(item.nbt, amount)

            if (targetStack == null) {
                gui.serverExecute {
                    player.sendMessage(
                        Text.translatable("servermarket.menu.myshop.detail.invalid_item").copy().formatted(net.minecraft.util.Formatting.RED),
                        false
                    )
                }
                return@runAsync
            }

            // 在主线程检查并扣除玩家背包物品
            gui.serverExecute {
                val removedAmount = removeItemsFromInventory(player, targetStack, amount)

                if (removedAmount <= 0) {
                    player.sendMessage(
                        Text.translatable("servermarket.menu.myshop.detail.insufficient_items", amount)
                            .copy().formatted(net.minecraft.util.Formatting.RED),
                        false
                    )
                    return@serverExecute
                }

                // 在数据库线程增加库存
                db.runAsync {
                    db.marketRepository.incrementPlayerItemQuantity(
                        playerUuid, item.itemId, item.nbt, removedAmount
                    )

                    gui.serverExecute {
                        player.sendMessage(
                            Text.translatable(
                                "servermarket.menu.myshop.detail.restock_success",
                                removedAmount,
                                displayName
                            ),
                            false
                        )

                        // 刷新当前项目数据
                        currentItem = item.copy(quantity = item.quantity + removedAmount)
                        renderDetailView()
                    }
                }
            }
        }
    }

    /**
     * 从玩家背包移除指定数量的物品
     * @return 实际移除的数量
     */
    private fun removeItemsFromInventory(
        player: net.minecraft.server.network.ServerPlayerEntity,
        targetStack: ItemStack,
        requestedAmount: Int
    ): Int {
        var remaining = requestedAmount
        val inventory = player.inventory

        for (i in 0 until inventory.size()) {
            if (remaining <= 0) break

            val stack = inventory.getStack(i)
            if (stack.isEmpty) continue

            // 检查物品是否匹配（包括NBT）
            if (ItemStack.areItemsAndComponentsEqual(stack, targetStack)) {
                val toRemove = remaining.coerceAtMost(stack.count)
                stack.decrement(toRemove)
                remaining -= toRemove
            }
        }

        return requestedAmount - remaining
    }

    /**
     * 完全下架（移除所有库存并发送到快递驿站）
     */
    private fun handleUnlistAll() {
        val item = currentItem ?: return
        val db = ServerMarket.instance.database
        val playerUuid = gui.player.uuid

        db.runAsync {
            val quantity = db.marketRepository.removePlayerItem(playerUuid, item.itemId, item.nbt)

            // 返还库存到快递驿站
            if (quantity > 0) {
                ServerMarket.instance.parcelService.addParcel(
                    playerUuid,
                    gui.player.name.string,
                    item.itemId,
                    item.nbt,
                    quantity,
                    Text.translatable("servermarket.menu.myshop.detail.unlist_all_reason").string
                )
            }

            gui.serverExecute {
                // Prefer localized, clean display name
                val stack = ItemKey.tryBuildFullStackFromSnbt(item.nbt, 1) ?: ItemStack.EMPTY
                val displayName = TextFormat.displayItemName(stack, item.itemId)

                gui.player.sendMessage(
                    Text.translatable("servermarket.menu.myshop.detail.unlist_all_success", displayName, quantity),
                    false
                )
                // 返回列表
                gui.showMyShop(false)
            }
        }
    }

    private fun setActionButton(slot: Int, item: net.minecraft.item.Item, name: Text, lore: Text, callback: () -> Unit) {
        val element = GuiElementBuilder(item)
            .setName(name)
            .addLoreLine(lore.copy().formatted(net.minecraft.util.Formatting.GRAY))
            .setCallback { _, _, _ -> callback() }
        gui.setSlot(slot, element)
    }

    private fun setNavButton(slot: Int, item: net.minecraft.item.Item, name: Text, callback: () -> Unit) {
        val element = GuiElementBuilder(item)
            .setName(name)
            .setCallback { _, _, _ -> callback() }
        gui.setSlot(slot, element)
    }
}
