package asagiribeta.serverMarket.menu

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.model.PurchaseResult
import asagiribeta.serverMarket.model.SellToBuyerResult
import asagiribeta.serverMarket.repository.MarketMenuEntry
import asagiribeta.serverMarket.repository.SellerMenuEntry
import asagiribeta.serverMarket.util.ItemKey
import asagiribeta.serverMarket.util.Language
import com.mojang.authlib.GameProfile
import eu.pb4.sgui.api.ClickType
import eu.pb4.sgui.api.elements.GuiElementBuilder
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.ProfileComponent
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import java.util.*
import kotlin.math.min

/**
 * 收购菜单条目
 */
data class PurchaseMenuEntry(
    val itemId: String,
    val nbt: String,
    val price: Double,
    val buyerName: String,
    val buyerUuid: UUID?,
    val limitPerDay: Int = -1,
    val targetAmount: Int = 0,
    val currentAmount: Int = 0
)

/**
 * 使用 SGUI 库重写的市场菜单
 * 三级导航：HOME 首页 -> SELLER_LIST 卖家列表 -> SELLER_SHOP 卖家店铺
 */
class MarketGui(player: ServerPlayerEntity) : SimpleGui(ScreenHandlerType.GENERIC_9X6, player, false) {

    companion object {
        private const val PAGE_SIZE = 45
    }

    private enum class ViewMode { HOME, SELLER_LIST, SELLER_SHOP, PURCHASE_LIST, PARCEL_STATION }

    private var mode: ViewMode = ViewMode.HOME
    private var page: Int = 0
    
    // 卖家列表缓存
    private var sellerEntries: List<SellerMenuEntry> = emptyList()
    private var selectedSellerId: String? = null
    
    // 商品列表缓存
    private var shopEntries: List<MarketMenuEntry> = emptyList()

    // 收购列表缓存
    private var purchaseEntries: List<PurchaseMenuEntry> = emptyList()

    // 快递包裹缓存
    private var parcelEntries: List<asagiribeta.serverMarket.model.ParcelEntry> = emptyList()

    init {
        this.title = Text.literal(Language.get("menu.title"))
        this.setLockPlayerInventory(true)
        showHome()
    }

    // ==================== 工具方法 ====================
    
    private fun pageCountOf(totalItems: Int): Int = 
        if (totalItems <= 0) 1 else (totalItems - 1) / PAGE_SIZE + 1

    private fun clampPage(page: Int, totalPages: Int): Int = when {
        totalPages <= 0 -> 0
        page < 0 -> 0
        page >= totalPages -> totalPages - 1
        else -> page
    }

    private fun <T> pageSlice(list: List<T>, page: Int): List<T> {
        val start = page * PAGE_SIZE
        return if (start >= list.size) emptyList() 
               else list.subList(start, min(list.size, start + PAGE_SIZE))
    }

    private fun clearContent() {
        for (i in 0 until PAGE_SIZE) {
            this.clearSlot(i)
        }
    }

    private fun clearNav() {
        for (i in PAGE_SIZE until 54) {
            this.clearSlot(i)
        }
    }

    private fun serverExecute(block: () -> Unit) {
        player.server?.execute(block)
    }

    // ==================== 首页视图 ====================

    private fun showHome() {
        mode = ViewMode.HOME
        page = 0
        clearContent()
        clearNav()

        // 余额显示（槽位 4）
        val balanceItem = GuiElementBuilder(Items.GOLD_INGOT)
            .setName(Text.literal(Language.get("menu.balance", "...")))
        this.setSlot(4, balanceItem)

        // 帮助信息（槽位 22）
        val helpItem = GuiElementBuilder(Items.BOOK)
            .setName(Text.literal(Language.get("menu.home.help_title")))
            .addLoreLine(Text.literal(Language.get("menu.home.help1")))
            .addLoreLine(Text.literal(Language.get("menu.home.help2")))
            .addLoreLine(Text.literal(Language.get("menu.home.help3")))
        this.setSlot(22, helpItem)

        // 导航按钮
        setNavButton(49, Items.BARRIER, Language.get("menu.close")) {
            this.close()
        }
        setNavButton(50, Items.CHEST_MINECART, Language.get("menu.enter_parcel")) {
            showParcelStation(true)
        }
        setNavButton(51, Items.EMERALD, Language.get("menu.enter_purchase")) {
            showPurchaseList(true)
        }
        setNavButton(53, Items.CHEST, Language.get("menu.enter_market_sellers")) {
            showSellerList(true)
        }

        // 异步加载余额和包裹数量
        val db = ServerMarket.instance.database
        db.getBalanceAsync(player.uuid).whenComplete { balance, _ ->
            serverExecute {
                if (mode == ViewMode.HOME) {
                    val updatedBalance = GuiElementBuilder(Items.GOLD_INGOT)
                        .setName(Text.literal(Language.get("menu.balance", "%.2f".format(balance))))
                    this.setSlot(4, updatedBalance)
                }
            }
        }

        // 异步显示包裹数量提示
        ServerMarket.instance.parcelService.getParcelCountForPlayerAsync(player.uuid).whenComplete { count, _ ->
            serverExecute {
                if (mode == ViewMode.HOME && count != null && count > 0) {
                    // 重新创建按钮，保留点击事件
                    val updated = GuiElementBuilder(Items.CHEST_MINECART)
                        .setName(Text.literal(Language.get("menu.enter_parcel")))
                        .addLoreLine(Text.literal("§e${Language.get("menu.parcel.count", count)}"))
                        .setCallback { _, _, _ -> showParcelStation(true) }
                    this.setSlot(50, updated)
                }
            }
        }
    }

    // ==================== 卖家列表视图 ====================

    private fun showSellerList(resetPage: Boolean = true) {
        mode = ViewMode.SELLER_LIST
        if (resetPage) page = 0
        clearContent()
        clearNav()

        // 显示加载提示
        setNavButton(46, Items.BOOK, Language.get("menu.loading")) {}
        buildSellerListNav()

        // 异步加载卖家列表
        val db = ServerMarket.instance.database
        db.supplyAsync { db.marketRepository.getAllSellersForMenu() }
            .whenComplete { list, _ ->
                serverExecute {
                    if (mode != ViewMode.SELLER_LIST) return@serverExecute
                    
                    sellerEntries = list ?: emptyList()
                    val totalPages = pageCountOf(sellerEntries.size)
                    page = clampPage(page, totalPages)
                    
                    clearContent()
                    
                    // 显示当前页的卖家
                    pageSlice(sellerEntries, page).forEachIndexed { idx, seller ->
                        this.setSlot(idx, buildSellerElement(seller))
                    }
                    
                    buildSellerListNav()
                }
            }
    }

    private fun buildSellerElement(entry: SellerMenuEntry): GuiElementBuilder {
        val item = if (entry.sellerId == "SERVER") Items.NETHER_STAR else Items.PLAYER_HEAD
        val element = GuiElementBuilder(item)
            .setName(Text.literal(entry.sellerName))
            .addLoreLine(Text.literal(Language.get("menu.seller.items", entry.itemCount)))
            .addLoreLine(Text.literal(Language.get("menu.seller.open_shop")))
            .setCallback { _, _, _ ->
                showSellerShop(entry.sellerId, true)
            }

        // 为玩家卖家设置皮肤
        if (item == Items.PLAYER_HEAD) {
            val profile = obtainSellerGameProfile(entry)
            if (profile != null) {
                try {
                    element.setComponent(DataComponentTypes.PROFILE, ProfileComponent(profile))
                } catch (_: Exception) { }
            }
        }

        return element
    }

    private fun obtainSellerGameProfile(entry: SellerMenuEntry): GameProfile? {
        if (entry.sellerId.equals("SERVER", ignoreCase = true)) return null
        val uuid = try { UUID.fromString(entry.sellerId) } catch (_: Exception) { return null }
        val server = player.server ?: return null
        
        // 优先获取在线玩家的 GameProfile
        server.playerManager.getPlayer(uuid)?.let { return it.gameProfile }
        
        // 其次尝试从缓存获取
        val cache = server.userCache
        val cached = try { cache?.getByUuid(uuid)?.orElse(null) } catch (_: Exception) { null }
        if (cached != null) return cached
        
        return GameProfile(uuid, entry.sellerName)
    }

    private fun buildSellerListNav() {
        val totalPages = pageCountOf(sellerEntries.size)
        
        setNavButton(45, Items.ARROW, Language.get("menu.prev")) {
            if (page > 0) {
                page--
                showSellerList(false)
            }
        }
        
        val helpItem = GuiElementBuilder(Items.BOOK)
            .setName(Text.literal(Language.get("menu.seller_list.title")))
            .addLoreLine(Text.literal(Language.get("menu.seller_list.tip1")))
            .addLoreLine(Text.literal(Language.get("menu.seller_list.tip2")))
            .addLoreLine(Text.literal(Language.get("menu.seller_list.tip3")))
            .addLoreLine(Text.literal(Language.get("menu.seller_list.tip4")))
        this.setSlot(46, helpItem)
        
        setNavButton(47, Items.NETHER_STAR, Language.get("menu.back_home")) {
            showHome()
        }
        
        setNavButton(49, Items.BARRIER, Language.get("menu.close")) {
            this.close()
        }
        
        setNavButton(53, Items.ARROW, Language.get("menu.next", "${page + 1}/$totalPages")) {
            if (page + 1 < totalPages) {
                page++
                showSellerList(false)
            }
        }
    }

    // ==================== 卖家店铺视图 ====================

    private fun showSellerShop(sellerId: String, resetPage: Boolean = true) {
        mode = ViewMode.SELLER_SHOP
        selectedSellerId = sellerId
        if (resetPage) page = 0
        clearContent()
        clearNav()

        // 显示加载提示
        setNavButton(46, Items.BOOK, Language.get("menu.loading")) {}
        buildShopNav()

        // 异步加载商品列表
        val db = ServerMarket.instance.database
        db.supplyAsync { db.marketRepository.getAllListingsForSeller(sellerId) }
            .whenComplete { list, _ ->
                serverExecute {
                    if (mode != ViewMode.SELLER_SHOP || selectedSellerId != sellerId) return@serverExecute
                    
                    shopEntries = list ?: emptyList()
                    val totalPages = pageCountOf(shopEntries.size)
                    page = clampPage(page, totalPages)
                    
                    clearContent()
                    
                    // 显示当前页的商品
                    pageSlice(shopEntries, page).forEachIndexed { idx, entry ->
                        this.setSlot(idx, buildProductElement(entry))
                    }
                    
                    buildShopNav()
                }
            }
    }

    private fun buildProductElement(entry: MarketMenuEntry): GuiElementBuilder {
        // 构建商品物品栈
        val stack = ItemKey.tryBuildFullStackFromSnbt(entry.nbt, 1) ?: run {
            val id = Identifier.tryParse(entry.itemId)
            val itemType = if (id != null && Registries.ITEM.containsId(id)) 
                           Registries.ITEM.get(id) else Items.STONE
            ItemStack(itemType)
        }

        val stockStr = if (entry.isSystem && entry.quantity < 0) "∞" else entry.quantity.toString()
        
        val element = GuiElementBuilder.from(stack)
            .setName(Text.literal(entry.itemId))
            .addLoreLine(Text.literal(Language.get("ui.seller", entry.sellerName)))
            .addLoreLine(Text.literal(Language.get("ui.price", String.format(Locale.ROOT, "%.2f", entry.price))))
            .addLoreLine(Text.literal(Language.get("ui.quantity", stockStr)))
            .addLoreLine(Text.literal(Language.get("menu.shop.click_tip")))
            .setCallback { _, clickType, _ ->
                val buyAll = clickType == ClickType.MOUSE_RIGHT || clickType == ClickType.MOUSE_RIGHT_SHIFT
                handlePurchase(entry, buyAll)
            }

        return element
    }

    private fun buildShopNav() {
        val totalPages = pageCountOf(shopEntries.size)
        
        setNavButton(45, Items.ARROW, Language.get("menu.prev")) {
            if (page > 0) {
                page--
                showSellerShop(selectedSellerId ?: return@setNavButton, false)
            }
        }
        
        val helpItem = GuiElementBuilder(Items.BOOK)
            .setName(Text.literal(Language.get("menu.shop.title")))
            .addLoreLine(Text.literal(Language.get("menu.shop.tip_left")))
            .addLoreLine(Text.literal(Language.get("menu.shop.tip_right")))
            .addLoreLine(Text.literal(Language.get("menu.shop.tip_back")))
            .addLoreLine(Text.literal(Language.get("menu.shop.tip_close")))
        this.setSlot(46, helpItem)
        
        setNavButton(47, Items.NETHER_STAR, Language.get("menu.back_sellers")) {
            showSellerList(false)
        }
        
        setNavButton(49, Items.BARRIER, Language.get("menu.close")) {
            this.close()
        }
        
        setNavButton(53, Items.ARROW, Language.get("menu.next", "${page + 1}/$totalPages")) {
            if (page + 1 < totalPages) {
                page++
                showSellerShop(selectedSellerId ?: return@setNavButton, false)
            }
        }
    }

    // ==================== 购买逻辑 ====================

    private fun handlePurchase(entry: MarketMenuEntry, buyAll: Boolean) {
        val desired = if (buyAll) 64 else 1

        // 使用 MarketService 处理购买逻辑
        ServerMarket.instance.marketService.purchaseItemAsync(
            playerUuid = player.uuid,
            playerName = player.name.string,
            itemId = entry.itemId,
            quantity = desired,
            seller = entry.sellerId
        ).whenComplete { result, _ ->
            serverExecute {
                when (result) {
                    is PurchaseResult.Success -> {
                        // 发放物品
                        result.items.forEach { (itemId, nbt, count) ->
                            val stack = ItemKey.tryBuildFullStackFromSnbt(nbt, count) ?: run {
                                val id = Identifier.tryParse(itemId)
                                val itemType = if (id != null && Registries.ITEM.containsId(id))
                                               Registries.ITEM.get(id) else Items.AIR
                                ItemStack(itemType, count)
                            }
                            player.giveItemStack(stack)
                        }

                        player.sendMessage(
                            Text.literal(Language.get(
                                "menu.buy_ok",
                                result.amount,
                                entry.itemId,
                                "%.2f".format(result.totalCost)
                            )),
                            false
                        )

                        refreshShopAfterPurchase()
                    }

                    is PurchaseResult.InsufficientFunds -> {
                        player.sendMessage(
                            Text.literal(Language.get("menu.no_money", "%.2f".format(result.required))),
                            false
                        )
                    }

                    is PurchaseResult.InsufficientStock -> {
                        player.sendMessage(
                            Text.literal(Language.get("menu.out_of_stock")),
                            false
                        )
                    }

                    is PurchaseResult.LimitExceeded -> {
                        player.sendMessage(
                            Text.literal(Language.get("menu.limit_exceeded")),
                            false
                        )
                    }

                    PurchaseResult.NotFound -> {
                        player.sendMessage(
                            Text.literal(Language.get("menu.item_not_found")),
                            false
                        )
                    }

                    is PurchaseResult.Error -> {
                        player.sendMessage(
                            Text.literal(Language.get("menu.buy_error")),
                            false
                        )
                    }
                }
            }
        }
    }

    private fun refreshShopAfterPurchase() {
        if (mode == ViewMode.SELLER_SHOP) {
            val sid = selectedSellerId ?: return
            val oldPage = page
            showSellerShop(sid, resetPage = false)
            page = oldPage.coerceAtMost(pageCountOf(shopEntries.size) - 1)
        }
    }

    // ==================== 收购列表视图 ====================

    private fun showPurchaseList(resetPage: Boolean = true) {
        mode = ViewMode.PURCHASE_LIST
        if (resetPage) page = 0
        clearContent()
        clearNav()

        // 显示加载提示
        setNavButton(46, Items.BOOK, Language.get("menu.loading")) {}
        buildPurchaseListNav()

        // 异步加载收购列表
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
        }.whenComplete { list, _ ->
            serverExecute {
                if (mode != ViewMode.PURCHASE_LIST) return@serverExecute

                purchaseEntries = list ?: emptyList()
                val totalPages = pageCountOf(purchaseEntries.size)
                page = clampPage(page, totalPages)

                clearContent()

                // 显示当前页的收购订单
                pageSlice(purchaseEntries, page).forEachIndexed { idx, entry ->
                    this.setSlot(idx, buildPurchaseElement(entry))
                }

                buildPurchaseListNav()
            }
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

        val element = GuiElementBuilder.from(stack)
            .setName(Text.literal(entry.itemId))
            .addLoreLine(Text.literal(Language.get("menu.purchase.buyer", entry.buyerName)))
            .addLoreLine(Text.literal(Language.get("menu.purchase.price", String.format(java.util.Locale.ROOT, "%.2f", entry.price))))
            .addLoreLine(Text.literal(Language.get("menu.purchase.limit", if (entry.buyerName == "SERVER") limitStr else remainingStr)))
            .addLoreLine(Text.literal(Language.get("menu.purchase.click_tip")))
            .setCallback { _, clickType, _ ->
                val sellAll = clickType == ClickType.MOUSE_RIGHT || clickType == ClickType.MOUSE_RIGHT_SHIFT
                handleSellToPurchase(entry, sellAll)
            }

        return element
    }

    private fun buildPurchaseListNav() {
        val totalPages = pageCountOf(purchaseEntries.size)

        setNavButton(45, Items.ARROW, Language.get("menu.prev")) {
            if (page > 0) {
                page--
                showPurchaseList(false)
            }
        }

        val helpItem = GuiElementBuilder(Items.BOOK)
            .setName(Text.literal(Language.get("menu.purchase_list.title")))
            .addLoreLine(Text.literal(Language.get("menu.purchase_list.tip1")))
            .addLoreLine(Text.literal(Language.get("menu.purchase_list.tip2")))
            .addLoreLine(Text.literal(Language.get("menu.purchase_list.tip3")))
            .addLoreLine(Text.literal(Language.get("menu.purchase_list.tip4")))
        this.setSlot(46, helpItem)

        setNavButton(47, Items.NETHER_STAR, Language.get("menu.back_home")) {
            showHome()
        }

        setNavButton(49, Items.BARRIER, Language.get("menu.close")) {
            this.close()
        }

        setNavButton(53, Items.ARROW, Language.get("menu.next", "${page + 1}/$totalPages")) {
            if (page + 1 < totalPages) {
                page++
                showPurchaseList(false)
            }
        }
    }

    // ==================== 出售逻辑 ====================

    private fun handleSellToPurchase(entry: PurchaseMenuEntry, sellAll: Boolean) {
        val desired = if (sellAll) 64 else 1
        val itemId = entry.itemId
        val nbt = entry.nbt

        // 检查玩家背包中的物品数量
        val allStacks = (0 until player.inventory.size()).map { player.inventory.getStack(it) }.filter {
            !it.isEmpty && Registries.ITEM.getId(it.item).toString() == itemId && ItemKey.snbtOf(it) == nbt
        }

        val totalAvailable = allStacks.sumOf { it.count }
        if (totalAvailable < desired) {
            player.sendMessage(
                Text.literal(Language.get("menu.purchase.not_enough_items")),
                false
            )
            return
        }

        // 使用 PurchaseService 处理出售逻辑
        val buyerFilter = if (entry.buyerName == "SERVER") "SERVER" else entry.buyerUuid?.toString()

        ServerMarket.instance.purchaseService.sellToBuyerAsync(
            sellerUuid = player.uuid,
            sellerName = player.name.string,
            itemId = itemId,
            nbt = nbt,
            quantity = desired,
            buyerFilter = buyerFilter
        ).whenComplete { result: SellToBuyerResult?, _ ->
            serverExecute {
                when (result) {
                    is SellToBuyerResult.Success -> {
                        // 扣除物品
                        var remaining = result.amount
                        for (stack in allStacks) {
                            if (remaining <= 0) break
                            val deduct = minOf(remaining, stack.count)
                            stack.decrement(deduct)
                            remaining -= deduct
                        }

                        player.sendMessage(
                            Text.literal(Language.get(
                                "menu.purchase.sell_ok",
                                result.amount,
                                itemId,
                                "%.2f".format(result.totalEarned)
                            )),
                            false
                        )

                        refreshPurchaseListAfterSell()
                    }

                    SellToBuyerResult.NotFound -> {
                        player.sendMessage(
                            Text.literal(Language.get("menu.purchase.error")),
                            false
                        )
                    }

                    is SellToBuyerResult.LimitExceeded -> {
                        player.sendMessage(
                            Text.literal(Language.get("menu.purchase.limit_exceeded")),
                            false
                        )
                    }

                    is SellToBuyerResult.InsufficientFunds -> {
                        player.sendMessage(
                            Text.literal(Language.get("menu.purchase.buyer_no_money")),
                            false
                        )
                    }

                    SellToBuyerResult.InsufficientItems -> {
                        player.sendMessage(
                            Text.literal(Language.get("menu.purchase.not_enough_items")),
                            false
                        )
                    }

                    is SellToBuyerResult.Error -> {
                        player.sendMessage(
                            Text.literal(Language.get("menu.purchase.error")),
                            false
                        )
                    }

                    null -> {
                        player.sendMessage(
                            Text.literal(Language.get("menu.purchase.error")),
                            false
                        )
                    }
                }
            }
        }
    }

    private fun refreshPurchaseListAfterSell() {
        if (mode == ViewMode.PURCHASE_LIST) {
            val oldPage = page
            showPurchaseList(resetPage = false)
            page = oldPage.coerceAtMost(pageCountOf(purchaseEntries.size) - 1)
        }
    }

    // ==================== 快递驿站视图 ====================

    private fun showParcelStation(resetPage: Boolean = true) {
        mode = ViewMode.PARCEL_STATION
        if (resetPage) page = 0
        clearContent()
        clearNav()

        // 显示加载提示
        setNavButton(46, Items.BOOK, Language.get("menu.loading")) {}
        buildParcelStationNav()

        // 异步加载包裹列表（合并相同物品）
        ServerMarket.instance.parcelService.getParcelsForPlayerMergedAsync(player.uuid)
            .whenComplete { list, _ ->
            serverExecute {
                if (mode != ViewMode.PARCEL_STATION) return@serverExecute

                parcelEntries = list ?: emptyList()
                val totalPages = pageCountOf(parcelEntries.size)
                page = clampPage(page, totalPages)

                clearContent()

                if (parcelEntries.isEmpty()) {
                    // 显示空包裹提示
                    val emptyItem = GuiElementBuilder(Items.BARRIER)
                        .setName(Text.literal(Language.get("menu.parcel.empty")))
                    this.setSlot(22, emptyItem)
                } else {
                    // 显示当前页的包裹
                    pageSlice(parcelEntries, page).forEachIndexed { idx, entry ->
                        this.setSlot(idx, buildParcelElement(entry))
                    }
                }

                buildParcelStationNav()
            }
        }
    }

    private fun buildParcelElement(entry: asagiribeta.serverMarket.model.ParcelEntry): GuiElementBuilder {
        // 构建物品栈
        val stack = ItemKey.tryBuildFullStackFromSnbt(entry.nbt, entry.quantity) ?: run {
            val id = Identifier.tryParse(entry.itemId)
            val itemType = if (id != null && Registries.ITEM.containsId(id))
                           Registries.ITEM.get(id) else Items.CHEST
            ItemStack(itemType, entry.quantity)
        }

        // 格式化时间
        val timeStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(java.util.Date(entry.timestamp))

        val element = GuiElementBuilder.from(stack)
            .setName(Text.literal(entry.itemId))
            .addLoreLine(Text.literal(Language.get("menu.parcel.reason", entry.reason)))
            .addLoreLine(Text.literal(Language.get("menu.parcel.time", timeStr)))
            .addLoreLine(Text.literal(Language.get("ui.quantity", entry.quantity)))
            .addLoreLine(Text.literal("§a${Language.get("menu.parcel.click_tip")}"))
            .setCallback { _, _, _ ->
                handleParcelReceive(entry)
            }

        return element
    }

    private fun buildParcelStationNav() {
        val totalPages = pageCountOf(parcelEntries.size)

        setNavButton(45, Items.ARROW, Language.get("menu.prev")) {
            if (page > 0) {
                page--
                showParcelStation(false)
            }
        }

        val helpItem = GuiElementBuilder(Items.BOOK)
            .setName(Text.literal(Language.get("menu.parcel.title")))
            .addLoreLine(Text.literal(Language.get("menu.parcel.tip1")))
            .addLoreLine(Text.literal(Language.get("menu.parcel.tip2")))
            .addLoreLine(Text.literal(Language.get("menu.parcel.tip3")))
            .addLoreLine(Text.literal(Language.get("menu.parcel.tip4")))
        this.setSlot(46, helpItem)

        setNavButton(47, Items.NETHER_STAR, Language.get("menu.back_home")) {
            showHome()
        }

        setNavButton(49, Items.BARRIER, Language.get("menu.close")) {
            this.close()
        }

        setNavButton(53, Items.ARROW, Language.get("menu.next", "${page + 1}/$totalPages")) {
            if (page + 1 < totalPages) {
                page++
                showParcelStation(false)
            }
        }
    }

    // ==================== 快递领取逻辑 ====================

    private fun handleParcelReceive(entry: asagiribeta.serverMarket.model.ParcelEntry) {
        // 删除该玩家的所有相同物品的包裹
        ServerMarket.instance.parcelService.removeParcelsByItemAsync(
            player.uuid,
            entry.itemId,
            entry.nbt
        ).whenComplete { count, _ ->
            serverExecute {
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
                    refreshParcelStationAfterReceive()
                } else {
                    player.sendMessage(
                        Text.literal(Language.get("menu.parcel.error")),
                        false
                    )
                }
            }
        }
    }

    private fun refreshParcelStationAfterReceive() {
        if (mode == ViewMode.PARCEL_STATION) {
            val oldPage = page
            showParcelStation(resetPage = false)
            page = oldPage.coerceAtMost(pageCountOf(parcelEntries.size) - 1)
        }
    }

    // ==================== 辅助方法 ====================

    private fun setNavButton(slot: Int, item: net.minecraft.item.Item, name: String, callback: () -> Unit) {
        val element = GuiElementBuilder(item)
            .setName(Text.literal(name))
            .setCallback { _, _, _ -> callback() }
        this.setSlot(slot, element)
    }
}
