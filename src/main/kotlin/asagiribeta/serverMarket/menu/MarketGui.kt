package asagiribeta.serverMarket.menu

import asagiribeta.serverMarket.menu.view.*
import asagiribeta.serverMarket.util.Language
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import kotlin.math.min

/**
 * 使用 SGUI 库重写的市场菜单
 * 三级导航：HOME 首页 -> SELLER_LIST 卖家列表 -> SELLER_SHOP 卖家店铺
 *
 * 重构后作为主控制器，协调各个视图
 */
class MarketGui(player: ServerPlayerEntity) : SimpleGui(ScreenHandlerType.GENERIC_9X6, player, false) {

    companion object {
        const val PAGE_SIZE = 45
    }

    internal var mode: ViewMode = ViewMode.HOME
    internal var page: Int = 0

    // 视图实例
    private val homeView = HomeView(this)
    private val sellerListView = SellerListView(this)
    private val sellerShopView = SellerShopView(this)
    private val purchaseListView = PurchaseListView(this)
    private val parcelStationView = ParcelStationView(this)
    private val myShopView = MyShopView(this)
    private val myShopDetailView = MyShopDetailView(this)
    private val myPurchaseView = MyPurchaseView(this)

    init {
        this.title = Text.literal(Language.get("menu.title"))
        this.setLockPlayerInventory(true)
        showHome()
    }

    // ==================== 工具方法 ====================

    internal fun pageCountOf(totalItems: Int): Int =
        if (totalItems <= 0) 1 else (totalItems - 1) / PAGE_SIZE + 1

    internal fun clampPage(page: Int, totalPages: Int): Int = when {
        totalPages <= 0 -> 0
        page < 0 -> 0
        page >= totalPages -> totalPages - 1
        else -> page
    }

    internal fun <T> pageSlice(list: List<T>, page: Int): List<T> {
        val start = page * PAGE_SIZE
        return if (start >= list.size) emptyList()
               else list.subList(start, min(list.size, start + PAGE_SIZE))
    }

    internal fun clearContent() {
        for (i in 0 until PAGE_SIZE) {
            this.clearSlot(i)
        }
    }

    internal fun clearNav() {
        for (i in PAGE_SIZE until 54) {
            this.clearSlot(i)
        }
    }

    internal fun serverExecute(block: () -> Unit) {
        player.entityWorld.server.execute(block)
    }

    // ==================== 视图切换方法 ====================

    internal fun showHome() {
        homeView.show()
    }

    internal fun showSellerList(resetPage: Boolean = true) {
        sellerListView.show(resetPage)
    }

    internal fun showSellerShop(sellerId: String, resetPage: Boolean = true) {
        sellerShopView.show(sellerId, resetPage)
    }

    internal fun showPurchaseList(resetPage: Boolean = true) {
        purchaseListView.show(resetPage)
    }

    internal fun showParcelStation(resetPage: Boolean = true) {
        parcelStationView.show(resetPage)
    }

    internal fun showMyShop(resetPage: Boolean = true) {
        myShopView.show(resetPage)
    }

    internal fun showMyShopDetail(item: asagiribeta.serverMarket.repository.MarketItem) {
        myShopDetailView.show(item)
    }

    internal fun showMyPurchase(resetPage: Boolean = true) {
        myPurchaseView.show(resetPage)
    }
}

