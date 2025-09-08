package asagiribeta.serverMarket.menu

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.repository.MarketMenuEntry
import asagiribeta.serverMarket.util.ItemKey
import asagiribeta.serverMarket.util.Language
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.LoreComponent
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.inventory.SimpleInventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.ScreenHandlerContext
import net.minecraft.screen.slot.Slot
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import java.time.LocalDate
import java.util.*
import kotlin.math.min

/**
 * 市场主菜单 ScreenHandler (首页 + 市场分页)
 * 首页：显示玩家余额（槽位4） + 关闭(49) + 进入市场(53)
 * 市场结构：
 *  - 前 5 行(0..44) 商品槽位 (PAGE_SIZE = 45)
 *  - 第 6 行导航：
 *      45 <- 上一页 (箭头)
 *      47 返回首页 (下界之星)
 *      49 关闭 (Barrier)
 *      53 下一页 (箭头)
 */
class MarketMenuScreenHandler(
    syncId: Int,
    private val playerInventory: PlayerInventory,
    private val context: ScreenHandlerContext,
    private var entries: List<MarketMenuEntry>,
    private var page: Int = 0
) : ScreenHandler(TYPE, syncId) {

    companion object {
        const val PAGE_SIZE = 45
        // 占位类型；复用 GENERIC_9X6 的 type id 通过反射或常量困难，这里使用一个自定义 type 占位
        // 在纯服务端环境下只要不发起客户端自定义界面 JSON 渲染即可；这里使用 ScreenHandlerType.GENERIC_9X6
        val TYPE = net.minecraft.screen.ScreenHandlerType.GENERIC_9X6
    }

    private val dummyInventories: MutableList<SimpleInventory> = MutableList(54) { SimpleInventory(1) }
    private var onHomePage: Boolean = true

    init {
        // 商品槽位 0..44
        for (i in 0 until PAGE_SIZE) {
            val x = 8 + (i % 9) * 18
            val y = 18 + (i / 9) * 18
            addSlot(ReadOnlySlot(dummyInventories[i], i, x, y))
        }
        // 导航槽位 45..53
        for (i in PAGE_SIZE until 54) {
            val x = 8 + (i % 9) * 18
            val y = 18 + (i / 9) * 18
            addSlot(ReadOnlySlot(dummyInventories[i], i, x, y))
        }
        // 玩家背包
        val invY = 18 + 6 * 18 + 14
        for (row in 0 until 3) {
            for (col in 0 until 9) {
                addSlot(object : Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, invY + row * 18) {})
            }
        }
        val hotbarY = invY + 58
        for (col in 0 until 9) {
            addSlot(object : Slot(playerInventory, col, 8 + col * 18, hotbarY) {})
        }
        // 初始显示首页
        showHome()
    }

    override fun canUse(player: PlayerEntity?): Boolean = true

    private fun refreshEntries() {
        entries = ServerMarket.instance.database.marketRepository.getAllListingsForMenu()
        // 页码越界时回退
        val totalPages = if (entries.isEmpty()) 1 else (entries.size - 1) / PAGE_SIZE + 1
        if (page >= totalPages) page = totalPages - 1
        if (page < 0) page = 0
    }

    private fun clearListingSlots() {
        for (i in 0 until PAGE_SIZE) {
            (slots[i] as? ReadOnlySlot)?.apply {
                currentEntryIndex = -1
                inventory.setStack(0, ItemStack.EMPTY)
            }
        }
        // 清理导航行
        for (i in PAGE_SIZE until 54) {
            (slots[i] as? ReadOnlySlot)?.apply {
                currentEntryIndex = -1
                inventory.setStack(0, ItemStack.EMPTY)
            }
        }
    }

    private fun showHome() {
        onHomePage = true
        clearListingSlots()
        // 余额展示放在槽位 4 (第一行中间)
        val balance = ServerMarket.instance.database.getBalance(playerInventory.player.uuid)
        val balStack = ItemStack(Items.GOLD_INGOT)
        balStack.set(
            DataComponentTypes.CUSTOM_NAME,
            Text.literal(Language.get("menu.balance", "%.2f".format(balance)))
        )
        (slots[4] as ReadOnlySlot).apply {
            currentEntryIndex = -1
            inventory.setStack(0, balStack)
        }
        // 说明书 (帮助) 放在槽位 22 （居中位置）
        setHelpItem(
            22,
            "市场使用说明",
            listOf(
                "左键商品: 购买 1 个",
                "右键商品: 购买一组 (64 或剩余/限购量)",
                "箭头: 翻页",
                "下界之星: 返回首页",
                "屏障: 关闭菜单"
            )
        )
        // 关闭按钮 (49)
        setNavButton(49, Items.BARRIER, Language.get("menu.close"), true)
        // 进入市场 (53)
        setNavButton(53, Items.CHEST, Language.get("menu.enter_market"), true)
        sendContentUpdates()
    }

    private fun refreshPage() {
        onHomePage = false
        refreshEntries()
        clearListingSlots()
        val start = page * PAGE_SIZE
        val sub = if (start >= entries.size) emptyList() else entries.subList(start, min(entries.size, start + PAGE_SIZE))
        // 填充当前页
        sub.forEachIndexed { idx, entry ->
            val slot = slots[idx] as ReadOnlySlot
            slot.currentEntryIndex = start + idx
            slot.inventory.setStack(0, buildDisplayStack(entry))
        }
        buildNavButtons()
        sendContentUpdates()
    }

    private fun buildNavButtons() {
        // prev 45
        setNavButton(45, Items.ARROW, Language.get("menu.prev"), page > 0)
        // 帮助 46
        setHelpItem(
            46,
            "操作提示",
            listOf(
                "左键: 购买 1",
                "右键: 购买 64",
                "下界之星返回首页",
                "屏障关闭"
            )
        )
        // 返回首页 47
        setNavButton(47, Items.NETHER_STAR, Language.get("menu.back_home"), true)
        // close 49
        setNavButton(49, Items.BARRIER, Language.get("menu.close"), true)
        val totalPages = if (entries.isEmpty()) 1 else (entries.size - 1) / PAGE_SIZE + 1
        // next 53
        setNavButton(53, Items.ARROW, Language.get("menu.next", "${page + 1}/$totalPages"), page + 1 < totalPages)
    }

    private fun setNavButton(slotIndex: Int, item: net.minecraft.item.Item, name: String, enabled: Boolean) {
        val stack = ItemStack(item)
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name))
        val slot = slots[slotIndex] as ReadOnlySlot
        slot.currentEntryIndex = -1
        slot.inventory.setStack(0, stack)
    }

    // 新增：帮助物品（带多行 Lore）
    private fun setHelpItem(slotIndex: Int, title: String, lines: List<String>) {
        if (slotIndex !in 0 until 54) return
        val stack = ItemStack(Items.BOOK)
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(title))
        val loreTexts = lines.map { Text.literal(it) }
        stack.set(DataComponentTypes.LORE, LoreComponent(loreTexts))
        val slot = slots[slotIndex] as ReadOnlySlot
        slot.currentEntryIndex = -1
        slot.inventory.setStack(0, stack)
    }

    private fun buildDisplayStack(entry: MarketMenuEntry): ItemStack {
        val stack = ItemKey.tryBuildFullStackFromSnbt(entry.nbt, 1) ?: run {
            val id = Identifier.tryParse(entry.itemId)
            val itemType = if (id != null && Registries.ITEM.containsId(id)) Registries.ITEM.get(id) else Items.STONE
            ItemStack(itemType)
        }
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(entry.itemId))
        val stockStr = if (entry.isSystem && entry.quantity < 0) "∞" else entry.quantity.toString()
        val loreLines = listOf(
            Text.literal(Language.get("ui.seller", entry.sellerName)),
            Text.literal(Language.get("ui.price", String.format(Locale.ROOT, "%.2f", entry.price))),
            Text.literal(Language.get("ui.quantity", stockStr)),
            Text.literal("左键: +1  |  右键: +64")
        )
        stack.set(DataComponentTypes.LORE, LoreComponent(loreLines))
        return stack
    }

    override fun onSlotClick(slotIndex: Int, button: Int, actionType: SlotActionType, player: PlayerEntity) {
        if (player !is ServerPlayerEntity) return
        if (actionType != SlotActionType.PICKUP && actionType != SlotActionType.QUICK_MOVE) return

        if (onHomePage) {
            when (slotIndex) {
                53 -> { // 进入市场
                    page = 0
                    refreshPage()
                }
                49 -> player.closeHandledScreen()
            }
            return
        }

        when (slotIndex) {
            in 0 until PAGE_SIZE -> {
                val slot = slots[slotIndex] as? ReadOnlySlot ?: return
                val globalIndex = slot.currentEntryIndex
                if (globalIndex >= 0 && globalIndex < entries.size) {
                    val entry = entries[globalIndex]
                    handlePurchase(player, entry, buyAll = (button == 1))
                }
            }
            45 -> if (page > 0) { page--; refreshPage() }
            47 -> { showHome() }
            49 -> player.closeHandledScreen()
            53 -> {
                val totalPages = if (entries.isEmpty()) 1 else (entries.size - 1) / PAGE_SIZE + 1
                if (page + 1 < totalPages) { page++; refreshPage() }
            }
        }
    }

    private fun handlePurchase(player: ServerPlayerEntity, entry: MarketMenuEntry, buyAll: Boolean) {
        val repo = ServerMarket.instance.database.marketRepository
        val today = LocalDate.now().toString()
        val desired = if (buyAll) 64 else 1
        val maxQty: Int = if (entry.isSystem) {
            val limit = repo.getSystemLimitPerDay(entry.itemId, entry.nbt)
            val remainLimit = if (limit < 0) desired else {
                val purchased = repo.getSystemPurchasedOn(today, player.uuid, entry.itemId, entry.nbt)
                (limit - purchased).coerceAtLeast(0)
            }
            if (remainLimit <= 0) { player.sendMessage(Text.literal(Language.get("menu.limit_reached")), false); return }
            // 系统库存列若>=0也需限制
            val systemStock = entry.quantity
            if (systemStock >= 0) min(systemStock, remainLimit) else remainLimit
        } else {
            val singleList = repo.searchForTransaction(entry.itemId, entry.sellerId)
            val updated = singleList.firstOrNull { it.nbt == entry.nbt }
            if (updated == null || updated.quantity <= 0) { player.sendMessage(Text.literal(Language.get("menu.out_of_stock")), false); refreshPage(); return }
            updated.quantity
        }
        val actual = min(desired, maxQty)
        if (actual <= 0) return
        val totalCost = entry.price * actual
        val balance = ServerMarket.instance.database.getBalance(player.uuid)
        if (balance < totalCost) {
            player.sendMessage(Text.literal(Language.get("menu.no_money", "%.2f".format(totalCost))), false)
            return
        }
        val dtg = System.currentTimeMillis()
        try {
            ServerMarket.instance.database.transfer(player.uuid, UUID(0,0), totalCost)
            ServerMarket.instance.database.historyRepository.postHistory(
                dtg = dtg,
                fromId = player.uuid,
                fromType = "player",
                fromName = player.name.string,
                toId = UUID(0,0),
                toType = "system",
                toName = "MARKET",
                price = totalCost,
                item = "${entry.itemId} x$actual"
            )
            if (entry.isSystem) {
                repo.incrementSystemPurchasedOn(today, player.uuid, entry.itemId, entry.nbt, actual)
            } else {
                val sellerUuid = UUID.fromString(entry.sellerId)
                repo.incrementPlayerItemQuantity(sellerUuid, entry.itemId, entry.nbt, -actual)
                ServerMarket.instance.database.transfer(UUID(0,0), sellerUuid, entry.price * actual)
                ServerMarket.instance.database.historyRepository.postHistory(
                    dtg = dtg,
                    fromId = UUID(0,0),
                    fromType = "system",
                    fromName = "MARKET",
                    toId = sellerUuid,
                    toType = "player",
                    toName = entry.sellerName,
                    price = entry.price * actual,
                    item = "${entry.itemId} x$actual"
                )
            }
            val stack = ItemKey.tryBuildFullStackFromSnbt(entry.nbt, actual) ?: run {
                val id = Identifier.tryParse(entry.itemId)
                val itemType = if (id != null && Registries.ITEM.containsId(id)) Registries.ITEM.get(id) else Items.AIR
                ItemStack(itemType, actual)
            }
            player.giveItemStack(stack)
            player.sendMessage(Text.literal(Language.get("menu.buy_ok", actual, entry.itemId, "%.2f".format(totalCost))), false)
        } catch (e: Exception) {
            ServerMarket.LOGGER.error("菜单购买失败", e)
            player.sendMessage(Text.literal(Language.get("menu.buy_error")), false)
        } finally { refreshPage() }
    }

    override fun quickMove(player: PlayerEntity?, index: Int): ItemStack = ItemStack.EMPTY

    /** 只读槽位，用于展示商品 */
    private inner class ReadOnlySlot(inv: SimpleInventory, private val logicalIndex: Int, x: Int, y: Int) : Slot(inv, 0, x, y) {
        var currentEntryIndex: Int = -1
        override fun canInsert(stack: ItemStack): Boolean = false
        override fun canTakeItems(playerEntity: PlayerEntity): Boolean = false
    }
}
