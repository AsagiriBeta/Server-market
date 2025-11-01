package asagiribeta.serverMarket.menu

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.model.PurchaseResult
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
 * 使用 SGUI 库重写的市场菜单
 * 三级导航：HOME 首页 -> SELLER_LIST 卖家列表 -> SELLER_SHOP 卖家店铺
 */
class MarketGui(player: ServerPlayerEntity) : SimpleGui(ScreenHandlerType.GENERIC_9X6, player, false) {

    companion object {
        private const val PAGE_SIZE = 45
    }

    private enum class ViewMode { HOME, SELLER_LIST, SELLER_SHOP }

    private var mode: ViewMode = ViewMode.HOME
    private var page: Int = 0
    
    // 卖家列表缓存
    private var sellerEntries: List<SellerMenuEntry> = emptyList()
    private var selectedSellerId: String? = null
    
    // 商品列表缓存
    private var shopEntries: List<MarketMenuEntry> = emptyList()

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
        setNavButton(53, Items.CHEST, Language.get("menu.enter_market_sellers")) {
            showSellerList(true)
        }

        // 异步加载余额
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

    // ==================== 辅助方法 ====================

    private fun setNavButton(slot: Int, item: net.minecraft.item.Item, name: String, callback: () -> Unit) {
        val element = GuiElementBuilder(item)
            .setName(Text.literal(name))
            .setCallback { _, _, _ -> callback() }
        this.setSlot(slot, element)
    }
}

