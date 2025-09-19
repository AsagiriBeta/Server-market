package asagiribeta.serverMarket.menu

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.repository.MarketMenuEntry
import asagiribeta.serverMarket.repository.MarketRepository
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
import net.minecraft.screen.slot.Slot
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import java.time.LocalDate
import java.util.*
import kotlin.math.min
// 新增: 头像组件 & GameProfile
import net.minecraft.component.type.ProfileComponent
import com.mojang.authlib.GameProfile

/**
 * 市场主菜单 ScreenHandler
 * 三级：
 *  HOME 首页 -> SELLER_LIST 卖家分类 -> SELLER_SHOP 卖家店铺(其全部商品分页)
 */
class MarketMenuScreenHandler(
    syncId: Int,
    private val playerInventory: PlayerInventory,
    private var entries: List<MarketMenuEntry>, // 当前卖家店铺商品列表
    private var page: Int = 0
) : ScreenHandler(TYPE, syncId) {

    companion object {
        const val PAGE_SIZE = 45
        // 明确指定非空类型，避免平台类型可空性警告
        val TYPE: net.minecraft.screen.ScreenHandlerType<*> = net.minecraft.screen.ScreenHandlerType.GENERIC_9X6
    }

    private enum class ViewMode { HOME, SELLER_LIST, SELLER_SHOP }

    // 统一的分页工具方法
    private fun pageCountOf(totalItems: Int): Int = if (totalItems <= 0) 1 else (totalItems - 1) / PAGE_SIZE + 1
    private fun clampPage(page: Int, totalPages: Int): Int = when {
        totalPages <= 0 -> 0
        page < 0 -> 0
        page >= totalPages -> totalPages - 1
        else -> page
    }
    private fun pageStart(page: Int): Int = page * PAGE_SIZE
    private fun <T> pageSlice(list: List<T>, page: Int): List<T> {
        val start = pageStart(page)
        return if (start >= list.size) emptyList() else list.subList(start, min(list.size, start + PAGE_SIZE))
    }

    private var mode: ViewMode = ViewMode.HOME
    private val dummyInventories: MutableList<SimpleInventory> = MutableList(54) { SimpleInventory(1) }

    // 卖家列表缓存
    private var sellerEntries: List<MarketRepository.SellerMenuEntry> = emptyList()
    private var selectedSellerId: String? = null

    init {
        // 商品/内容槽位 0..44
        for (i in 0 until PAGE_SIZE) {
            val x = 8 + (i % 9) * 18
            val y = 18 + (i / 9) * 18
            addSlot(ReadOnlySlot(dummyInventories[i], x, y))
        }
        // 导航槽位 45..53
        for (i in PAGE_SIZE until 54) {
            val x = 8 + (i % 9) * 18
            val y = 18 + (i / 9) * 18
            addSlot(ReadOnlySlot(dummyInventories[i], x, y))
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
        showHome()
    }

    override fun canUse(player: PlayerEntity?): Boolean = true

    private fun serverExecute(block: () -> Unit) {
        playerInventory.player.server?.execute(block)
    }

    private fun refreshSellerEntriesAsync(onDone: () -> Unit) {
        // 加载卖家列表（异步）
        val db = ServerMarket.instance.database
        db.supplyAsync { db.marketRepository.getAllSellersForMenu() }
            .whenComplete { list, ex ->
                serverExecute {
                    if (ex != null || list == null) {
                        sellerEntries = emptyList()
                    } else sellerEntries = list
                    onDone()
                }
            }
    }

    private fun refreshShopEntriesAsync(sellerId: String, onDone: () -> Unit) {
        // 加载某卖家的全部商品（异步）
        val db = ServerMarket.instance.database
        db.supplyAsync { db.marketRepository.getAllListingsForSeller(sellerId) }
            .whenComplete { list, ex ->
                serverExecute {
                    if (ex != null || list == null) {
                        entries = emptyList()
                    } else entries = list
                    onDone()
                }
            }
    }

    private fun clearListingSlots() {
        for (i in 0 until 54) {
            (slots[i] as? ReadOnlySlot)?.apply {
                currentEntryIndex = -1
                inventory.setStack(0, ItemStack.EMPTY)
            }
        }
    }

    private fun showHome() {
        mode = ViewMode.HOME
        page = 0
        clearListingSlots()
        // 先显示占位余额，随后异步刷新真实余额
        val balStack = ItemStack(Items.GOLD_INGOT)
        balStack.set(
            DataComponentTypes.CUSTOM_NAME,
            Text.literal(Language.get("menu.balance", "..."))
        )
        (slots[4] as ReadOnlySlot).apply { inventory.setStack(0, balStack) }
        setHelpItem(
            22,
            Language.get("menu.home.help_title"),
            listOf(
                Language.get("menu.home.help1"),
                Language.get("menu.home.help2"),
                Language.get("menu.home.help3")
            )
        )
        setNavButton(49, Items.BARRIER, Language.get("menu.close"))
        setNavButton(53, Items.CHEST, Language.get("menu.enter_market_sellers"))
        sendContentUpdates()

        // 异步查余额
        val db = ServerMarket.instance.database
        val uuid = playerInventory.player.uuid
        db.getBalanceAsync(uuid).whenComplete { bal, _ ->
            serverExecute {
                val stack = ItemStack(Items.GOLD_INGOT)
                stack.set(
                    DataComponentTypes.CUSTOM_NAME,
                    Text.literal(Language.get("menu.balance", "%.2f".format(bal)))
                )
                (slots[4] as ReadOnlySlot).apply { inventory.setStack(0, stack) }
                sendContentUpdates()
            }
        }
    }

    private fun showSellerList(resetPage: Boolean = true) {
        mode = ViewMode.SELLER_LIST
        if (resetPage) page = 0
        clearListingSlots()
        // 显示加载中提示
        setHelpItem(46, Language.get("menu.loading"), listOf(Language.get("menu.loading.detail")))
        buildSellerListNav()
        sendContentUpdates()

        refreshSellerEntriesAsync {
            val tp = pageCountOf(sellerEntries.size)
            page = clampPage(page, tp)
            clearListingSlots()
            val sub = pageSlice(sellerEntries, page)
            sub.forEachIndexed { idx, seller ->
                val slot = slots[idx] as ReadOnlySlot
                slot.currentEntryIndex = idx // 本页内部索引
                slot.inventory.setStack(0, buildSellerStack(seller))
            }
            buildSellerListNav()
            sendContentUpdates()
        }
    }

    private fun showSellerShop(sellerId: String, resetPage: Boolean = true) {
        mode = ViewMode.SELLER_SHOP
        selectedSellerId = sellerId
        if (resetPage) page = 0
        clearListingSlots()
        // 显示加载中提示
        setHelpItem(46, Language.get("menu.loading"), listOf(Language.get("menu.loading.detail")))
        buildShopNav()
        sendContentUpdates()

        refreshShopEntriesAsync(sellerId) {
            val tp = pageCountOf(entries.size)
            page = clampPage(page, tp)
            clearListingSlots()
            val start = pageStart(page)
            val sub = pageSlice(entries, page)
            sub.forEachIndexed { idx, entry ->
                val slot = slots[idx] as ReadOnlySlot
                slot.currentEntryIndex = start + idx
                slot.inventory.setStack(0, buildDisplayStack(entry))
            }
            buildShopNav()
            sendContentUpdates()
        }
    }

    private fun buildSellerStack(entry: MarketRepository.SellerMenuEntry): ItemStack {
        val item = if (entry.sellerId == "SERVER") Items.NETHER_STAR else Items.PLAYER_HEAD
        val stack = ItemStack(item)
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(entry.sellerName))
        // 为玩家卖家设置皮肤
        if (item == Items.PLAYER_HEAD) {
            val profile = obtainSellerGameProfile(entry)
            if (profile != null) {
                try {
                    stack.set(DataComponentTypes.PROFILE, ProfileComponent(profile))
                } catch (_: Exception) { /* 忽略设置失败，继续使用默认头像 */ }
            }
        }
        val loreLines = listOf(
            Text.literal(Language.get("menu.seller.items", entry.itemCount)),
            Text.literal(Language.get("menu.seller.open_shop"))
        )
        stack.set(DataComponentTypes.LORE, LoreComponent(loreLines))
        return stack
    }

    // 获取卖家 GameProfile (优先在线，其次缓存)
    private fun obtainSellerGameProfile(entry: MarketRepository.SellerMenuEntry): GameProfile? {
        if (entry.sellerId.equals("SERVER", ignoreCase = true)) return null
        val uuid = try { UUID.fromString(entry.sellerId) } catch (_: Exception) { return null }
        val server = playerInventory.player.server ?: return null
        server.playerManager.getPlayer(uuid)?.let { return it.gameProfile }
        val cache = server.userCache
        val cached = try { cache?.getByUuid(uuid)?.orElse(null) } catch (_: Exception) { null }
        if (cached != null) return cached
        return GameProfile(uuid, entry.sellerName)
    }

    private fun buildSellerListNav() {
        val totalPages = pageCountOf(sellerEntries.size)
        setNavButton(45, Items.ARROW, Language.get("menu.prev"))
        setHelpItem(
            46,
            Language.get("menu.seller_list.title"),
            listOf(
                Language.get("menu.seller_list.tip1"),
                Language.get("menu.seller_list.tip2"),
                Language.get("menu.seller_list.tip3"),
                Language.get("menu.seller_list.tip4")
            )
        )
        setNavButton(47, Items.NETHER_STAR, Language.get("menu.back_home"))
        setNavButton(49, Items.BARRIER, Language.get("menu.close"))
        setNavButton(53, Items.ARROW, Language.get("menu.next", "${page + 1}/$totalPages"))
    }

    private fun buildShopNav() {
        val totalPages = pageCountOf(entries.size)
        setNavButton(45, Items.ARROW, Language.get("menu.prev"))
        setHelpItem(
            46,
            Language.get("menu.shop.title"),
            listOf(
                Language.get("menu.shop.tip_left"),
                Language.get("menu.shop.tip_right"),
                Language.get("menu.shop.tip_back"),
                Language.get("menu.shop.tip_close")
            )
        )
        setNavButton(47, Items.NETHER_STAR, Language.get("menu.back_sellers"))
        setNavButton(49, Items.BARRIER, Language.get("menu.close"))
        setNavButton(53, Items.ARROW, Language.get("menu.next", "${page + 1}/$totalPages"))
    }

    private fun setNavButton(slotIndex: Int, item: net.minecraft.item.Item, name: String) {
        val stack = ItemStack(item)
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name))
        val slot = slots[slotIndex] as ReadOnlySlot
        slot.currentEntryIndex = -1
        slot.inventory.setStack(0, stack)
    }

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
            Text.literal(Language.get("menu.shop.click_tip"))
        )
        stack.set(DataComponentTypes.LORE, LoreComponent(loreLines))
        return stack
    }

    override fun onSlotClick(slotIndex: Int, button: Int, actionType: SlotActionType, player: PlayerEntity) {
        if (player !is ServerPlayerEntity) return
        if (actionType != SlotActionType.PICKUP && actionType != SlotActionType.QUICK_MOVE) return

        when (mode) {
            ViewMode.HOME -> {
                when (slotIndex) {
                    53 -> showSellerList(true)
                    49 -> player.closeHandledScreen()
                }
            }
            ViewMode.SELLER_LIST -> {
                when (slotIndex) {
                    in 0 until PAGE_SIZE -> {
                        val start = page * PAGE_SIZE
                        val globalIndex = start + (slotIndex % PAGE_SIZE)
                        if (globalIndex < sellerEntries.size) {
                            val sellerId = sellerEntries[globalIndex].sellerId
                            showSellerShop(sellerId, true)
                        }
                    }
                    45 -> if (page > 0) { page--; showSellerList(false) }
                    47 -> showHome()
                    49 -> player.closeHandledScreen()
                    53 -> {
                        val totalPages = pageCountOf(sellerEntries.size)
                        if (page + 1 < totalPages) { page++; showSellerList(false) }
                    }
                }
            }
            ViewMode.SELLER_SHOP -> {
                when (slotIndex) {
                    in 0 until PAGE_SIZE -> {
                        val slot = slots[slotIndex] as? ReadOnlySlot ?: return
                        val globalIndex = slot.currentEntryIndex
                        if (globalIndex >= 0 && globalIndex < entries.size) {
                            val entry = entries[globalIndex]
                            handlePurchase(player, entry, buyAll = (button == 1))
                        }
                    }
                    45 -> if (page > 0) { page--; showSellerShop(selectedSellerId ?: return, false) }
                    47 -> showSellerList(false)
                    49 -> player.closeHandledScreen()
                    53 -> {
                        val totalPages = pageCountOf(entries.size)
                        if (page + 1 < totalPages) { page++; showSellerShop(selectedSellerId ?: return, false) }
                    }
                }
            }
        }
    }

    private fun handlePurchase(player: ServerPlayerEntity, entry: MarketMenuEntry, buyAll: Boolean) {
        val repo = ServerMarket.instance.database.marketRepository
        val db = ServerMarket.instance.database
        val today = LocalDate.now().toString()
        val desired = if (buyAll) 64 else 1

        // 将整个校验与扣款、发放流程放入数据库线程，避免阻塞主线程
        db.supplyAsync {
            try {
                val maxQty: Int = if (entry.isSystem) {
                    val limit = repo.getSystemLimitPerDay(entry.itemId, entry.nbt)
                    val remainLimit = if (limit < 0) desired else {
                        val purchased = repo.getSystemPurchasedOn(today, player.uuid, entry.itemId, entry.nbt)
                        (limit - purchased).coerceAtLeast(0)
                    }
                    if (remainLimit <= 0) 0 else {
                        val systemStock = entry.quantity
                        if (systemStock >= 0) min(systemStock, remainLimit) else remainLimit
                    }
                } else {
                    val singleList = repo.searchForTransaction(entry.itemId, entry.sellerId)
                    val updated = singleList.firstOrNull { it.nbt == entry.nbt }
                    updated?.quantity ?: 0
                }
                val actual = min(desired, maxQty)
                if (actual <= 0) return@supplyAsync Pair(false, "limit_or_stock")
                val totalCost = entry.price * actual
                val balance = db.getBalance(player.uuid)
                if (balance < totalCost) return@supplyAsync Pair(false, "no_money:${"%.2f".format(totalCost)}")

                val dtg = System.currentTimeMillis()
                // 玩家 -> 系统
                db.transfer(player.uuid, UUID(0,0), totalCost)
                db.historyRepository.postHistory(
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
                    // 系统 -> 卖家
                    db.transfer(UUID(0,0), sellerUuid, entry.price * actual)
                    db.historyRepository.postHistory(
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
                Pair(true, actual.toString())
            } catch (e: Exception) {
                ServerMarket.LOGGER.error("菜单购买失败", e)
                Pair(false, "error")
            }
        }.whenComplete { result, _ ->
            serverExecute {
                val (ok, info) = result ?: Pair(false, "error")
                if (!ok) {
                    when {
                        info == "limit_or_stock" -> player.sendMessage(Text.literal(Language.get("menu.out_of_stock")), false)
                        info.startsWith("no_money:") -> {
                            val need = info.substringAfter(':')
                            player.sendMessage(Text.literal(Language.get("menu.no_money", need)), false)
                        }
                        else -> player.sendMessage(Text.literal(Language.get("menu.buy_error")), false)
                    }
                    refreshShopAfterPurchase()
                    return@serverExecute
                }
                val actual = info.toIntOrNull() ?: 0
                // 发物品
                val stack = ItemKey.tryBuildFullStackFromSnbt(entry.nbt, actual) ?: run {
                    val id = Identifier.tryParse(entry.itemId)
                    val itemType = if (id != null && Registries.ITEM.containsId(id)) Registries.ITEM.get(id) else Items.AIR
                    ItemStack(itemType, actual)
                }
                player.giveItemStack(stack)
                val totalCost = entry.price * actual
                player.sendMessage(Text.literal(Language.get("menu.buy_ok", actual, entry.itemId, "%.2f".format(totalCost))), false)
                refreshShopAfterPurchase()
            }
        }
    }

    private fun refreshShopAfterPurchase() {
        if (mode == ViewMode.SELLER_SHOP) {
            val sid = selectedSellerId ?: return
            val oldPage = page
            // 重新异步加载当前店铺
            refreshShopEntriesAsync(sid) {
                // 保持当前页
                showSellerShop(sid, resetPage = false)
                page = oldPage.coerceAtMost(pageCountOf(entries.size) - 1)
            }
        }
    }

    override fun quickMove(player: PlayerEntity?, index: Int): ItemStack = ItemStack.EMPTY

    /** 只读槽位，用于展示商品 */
    private class ReadOnlySlot(inv: SimpleInventory, x: Int, y: Int) : Slot(inv, 0, x, y) {
        var currentEntryIndex: Int = -1
        override fun canInsert(stack: ItemStack): Boolean = false
        override fun canTakeItems(playerEntity: PlayerEntity): Boolean = false
    }
}
