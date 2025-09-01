package asagiribeta.serverMarket.api

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.repository.MarketItem
import java.util.*

/**
 * ServerMarket 对外公开的稳定接口。
 * 外部模组应通过 [ServerMarketAPI] （或调用 ServerMarket.INSTANCE 的入口方法）获取实例而不要直接访问内部数据库。
 * 所有方法期望在服务端主线程调用（和 Fabric 常规逻辑一致），未做额外线程安全包装。
 */
interface IServerMarketAPI {
    // 余额相关
    fun getBalance(uuid: UUID): Double
    fun setBalance(uuid: UUID, amount: Double)
    /**
     * 转账，成功返回 true，失败返回 false（失败会在日志中记录）。
     */
    fun transfer(from: UUID, to: UUID, amount: Double): Boolean

    // 系统市场
    fun addOrUpdateSystemItem(itemId: String, price: Double)
    fun removeSystemItem(itemId: String)
    fun hasSystemItem(itemId: String): Boolean
    fun listSystemItems(): List<MarketItem>

    // 玩家市场
    fun addPlayerItem(sellerUuid: UUID, sellerName: String, itemId: String, price: Double)
    fun updatePlayerItemPrice(sellerUuid: UUID, itemId: String, newPrice: Double)
    fun incrementPlayerItemQuantity(sellerUuid: UUID, itemId: String, quantity: Int)
    /**
     * 移除玩家物品，返回被移除前的数量（若不存在返回 0）。
     */
    fun removePlayerItem(sellerUuid: UUID, itemId: String): Int
    fun listPlayerItems(sellerUuid: UUID): List<MarketItem>

    // 搜索（显示 / 交易）
    fun searchForDisplay(itemId: String): List<MarketItem>
    fun searchForTransaction(itemId: String): List<MarketItem>

    // 历史记录
    fun postHistory(
        fromId: UUID,
        fromType: String,
        fromName: String,
        toId: UUID,
        toType: String,
        toName: String,
        price: Double,
        item: String,
        dtg: Long = System.currentTimeMillis()
    )
}

/**
 * API 单例实现。
 */
object ServerMarketAPI : IServerMarketAPI {
    private val db get() = ServerMarket.instance.database

    override fun getBalance(uuid: UUID): Double = db.getBalance(uuid)

    override fun setBalance(uuid: UUID, amount: Double) = db.setBalance(uuid, amount)

    override fun transfer(from: UUID, to: UUID, amount: Double): Boolean = try {
        db.transfer(from, to, amount)
        true
    } catch (e: Exception) {
        ServerMarket.LOGGER.error("API transfer failed: $from -> $to amount=$amount", e)
        false
    }

    override fun addOrUpdateSystemItem(itemId: String, price: Double) = db.marketRepository.addSystemItem(itemId, price)

    override fun removeSystemItem(itemId: String) = db.marketRepository.removeSystemItem(itemId)

    override fun hasSystemItem(itemId: String): Boolean = db.marketRepository.hasSystemItem(itemId)

    override fun listSystemItems(): List<MarketItem> = db.marketRepository.getSystemItems()

    override fun addPlayerItem(sellerUuid: UUID, sellerName: String, itemId: String, price: Double) =
        db.marketRepository.addPlayerItem(sellerUuid, sellerName, itemId, price)

    override fun updatePlayerItemPrice(sellerUuid: UUID, itemId: String, newPrice: Double) =
        db.marketRepository.updatePlayerItemPrice(sellerUuid, itemId, newPrice)

    override fun incrementPlayerItemQuantity(sellerUuid: UUID, itemId: String, quantity: Int) =
        db.marketRepository.incrementPlayerItemQuantity(sellerUuid, itemId, quantity)

    override fun removePlayerItem(sellerUuid: UUID, itemId: String): Int =
        db.marketRepository.removePlayerItem(sellerUuid, itemId)

    override fun listPlayerItems(sellerUuid: UUID): List<MarketItem> =
        db.marketRepository.getPlayerItems(sellerUuid.toString())

    override fun searchForDisplay(itemId: String): List<MarketItem> =
        db.marketRepository.searchForDisplay(itemId)

    override fun searchForTransaction(itemId: String): List<MarketItem> =
        db.marketRepository.searchForTransaction(itemId)

    override fun postHistory(
        fromId: UUID,
        fromType: String,
        fromName: String,
        toId: UUID,
        toType: String,
        toName: String,
        price: Double,
        item: String,
        dtg: Long
    ) = db.historyRepository.postHistory(dtg, fromId, fromType, fromName, toId, toType, toName, price, item)
}

/**
 * 获取 API 的静态方法，便于 Java 端调用： ServerMarketApiProvider.get()
 */
object ServerMarketApiProvider {
    @JvmStatic
    fun get(): IServerMarketAPI = ServerMarketAPI
}

