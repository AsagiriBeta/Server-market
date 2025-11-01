package asagiribeta.serverMarket.repository

import java.util.*

/**
 * 市场数据访问仓库
 *
 * 职责：
 * - 系统市场 CRUD 操作
 * - 玩家市场 CRUD 操作
 * - 限购管理
 * - 基础查询
 *
 * 重构说明：
 * - 搜索逻辑 -> MarketSearchService
 * - 查询映射 -> MarketQueryMapper
 */
class MarketRepository(private val database: Database) {
    
    // 查询映射器和搜索服务
    private val queryMapper = MarketQueryMapper()
    private val searchService = MarketSearchService(database, queryMapper)

    // ============== 系统市场操作 ==============

    /**
     * 添加或更新系统商品
     *
     * @param itemId 物品ID
     * @param nbt NBT数据
     * @param price 价格
     * @param limitPerDay 每日限购（-1 表示无限制）
     */
    fun addSystemItem(itemId: String, nbt: String, price: Double, limitPerDay: Int) {
        val sql = if (database.isMySQL) {
            """
            INSERT INTO system_market(item_id, nbt, price, limit_per_day)
            VALUES(?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE 
              price = VALUES(price),
              limit_per_day = VALUES(limit_per_day)
            """.trimIndent()
        } else {
            """
            INSERT INTO system_market(item_id, nbt, price, limit_per_day)
            VALUES(?, ?, ?, ?)
            ON CONFLICT(item_id, nbt) DO UPDATE SET 
                price = excluded.price,
                limit_per_day = excluded.limit_per_day
            """.trimIndent()
        }
        database.executeUpdate(sql) { ps ->
            ps.setString(1, itemId)
            ps.setString(2, nbt)
            ps.setDouble(3, price)
            ps.setInt(4, limitPerDay)
        }
    }

    /**
     * 移除系统商品
     */
    fun removeSystemItem(itemId: String, nbt: String) {
        database.executeUpdate("DELETE FROM system_market WHERE item_id = ? AND nbt = ?") { ps ->
            ps.setString(1, itemId)
            ps.setString(2, nbt)
        }
    }

    /**
     * 检查系统商品是否存在
     */
    fun hasSystemItem(itemId: String, nbt: String): Boolean {
        return database.connection.prepareStatement(
            "SELECT 1 FROM system_market WHERE item_id = ? AND nbt = ?"
        ).use { ps ->
            ps.setString(1, itemId)
            ps.setString(2, nbt)
            val rs = ps.executeQuery()
            rs.next()
        }
    }

    /**
     * 读取系统商品每日限购（-1 表示无限制；不存在则返回 -1）
     */
    fun getSystemLimitPerDay(itemId: String, nbt: String): Int {
        val sql = "SELECT limit_per_day FROM system_market WHERE item_id = ? AND nbt = ? LIMIT 1"
        return database.connection.prepareStatement(sql).use { ps ->
            ps.setString(1, itemId)
            ps.setString(2, nbt)
            val rs = ps.executeQuery()
            if (rs.next()) rs.getInt(1) else -1
        }
    }

    /**
     * 获取所有系统商品
     */
    fun getSystemItems(): List<MarketItem> {
        return database.connection.prepareStatement("""
            SELECT item_id, nbt, price, seller AS seller_name, quantity
            FROM system_market
            ORDER BY item_id, nbt
        """).use { ps ->
            val rs = ps.executeQuery()
            queryMapper.mapToMarketItems(rs)
        }
    }

    // ============== 系统商品限购管理 ==============

    /**
     * 系统商品：查询指定日期某玩家已购买数量
     */
    fun getSystemPurchasedOn(date: String, playerUuid: UUID, itemId: String, nbt: String): Int {
        val sql = """
            SELECT purchased FROM system_daily_purchase
            WHERE date = ? AND player_uuid = ? AND item_id = ? AND nbt = ?
        """.trimIndent()
        return database.connection.prepareStatement(sql).use { ps ->
            ps.setString(1, date)
            ps.setString(2, playerUuid.toString())
            ps.setString(3, itemId)
            ps.setString(4, nbt)
            val rs = ps.executeQuery()
            if (rs.next()) rs.getInt(1) else 0
        }
    }

    /**
     * 系统商品：增加当日购买计数（UPSERT）
     */
    fun incrementSystemPurchasedOn(date: String, playerUuid: UUID, itemId: String, nbt: String, amount: Int) {
        if (amount <= 0) return

        val sql = if (database.isMySQL) {
            """
            INSERT INTO system_daily_purchase(date, player_uuid, item_id, nbt, purchased)
            VALUES(?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE purchased = purchased + VALUES(purchased)
            """.trimIndent()
        } else {
            """
            INSERT INTO system_daily_purchase(date, player_uuid, item_id, nbt, purchased)
            VALUES(?, ?, ?, ?, ?)
            ON CONFLICT(date, player_uuid, item_id, nbt) DO UPDATE SET
                purchased = purchased + excluded.purchased
            """.trimIndent()
        }
        database.executeUpdate(sql) { ps ->
            ps.setString(1, date)
            ps.setString(2, playerUuid.toString())
            ps.setString(3, itemId)
            ps.setString(4, nbt)
            ps.setInt(5, amount)
        }
    }

    // ============== 玩家市场操作 ==============

    /**
     * 添加玩家商品或更新价格
     */
    fun addPlayerItem(sellerUuid: UUID, sellerName: String, itemId: String, nbt: String, price: Double) {
        val sql = if (database.isMySQL) {
            """
            INSERT INTO player_market(seller, seller_name, item_id, nbt, price, quantity)
            VALUES(?, ?, ?, ?, ?, 0)
            ON DUPLICATE KEY UPDATE price = VALUES(price)
            """.trimIndent()
        } else {
            """
            INSERT INTO player_market(seller, seller_name, item_id, nbt, price, quantity)
            VALUES(?, ?, ?, ?, ?, 0)
            ON CONFLICT(seller, item_id, nbt) DO UPDATE SET price = excluded.price
            """.trimIndent()
        }
        database.executeUpdate(sql) { ps ->
            ps.setString(1, sellerUuid.toString())
            ps.setString(2, sellerName)
            ps.setString(3, itemId)
            ps.setString(4, nbt)
            ps.setDouble(5, price)
        }
    }

    /**
     * 更新玩家商品价格
     */
    fun updatePlayerItemPrice(sellerUuid: UUID, itemId: String, nbt: String, newPrice: Double) {
        database.executeUpdate("""
            UPDATE player_market 
            SET price = ?
            WHERE seller = ? AND item_id = ? AND nbt = ?
        """) { ps ->
            ps.setDouble(1, newPrice)
            ps.setString(2, sellerUuid.toString())
            ps.setString(3, itemId)
            ps.setString(4, nbt)
        }
    }

    /**
     * 增加玩家商品库存
     */
    fun incrementPlayerItemQuantity(sellerUuid: UUID, itemId: String, nbt: String, quantity: Int) {
        database.executeUpdate("""
            UPDATE player_market 
            SET quantity = quantity + ?
            WHERE seller = ? AND item_id = ? AND nbt = ?
        """) { ps ->
            ps.setInt(1, quantity)
            ps.setString(2, sellerUuid.toString())
            ps.setString(3, itemId)
            ps.setString(4, nbt)
        }
    }

    /**
     * 移除玩家商品并返回当前库存
     */
    fun removePlayerItem(sellerUuid: UUID, itemId: String, nbt: String): Int {
        val quantity = getPlayerItemQuantity(sellerUuid, itemId, nbt)

        database.executeUpdate("""
            DELETE FROM player_market 
            WHERE seller = ? AND item_id = ? AND nbt = ?
        """) { ps ->
            ps.setString(1, sellerUuid.toString())
            ps.setString(2, itemId)
            ps.setString(3, nbt)
        }
        return quantity
    }

    /**
     * 获取玩家商品库存
     */
    private fun getPlayerItemQuantity(sellerUuid: UUID, itemId: String, nbt: String): Int {
        return database.connection.prepareStatement("""
            SELECT quantity 
            FROM player_market 
            WHERE seller = ? AND item_id = ? AND nbt = ?
        """).use { ps ->
            ps.setString(1, sellerUuid.toString())
            ps.setString(2, itemId)
            ps.setString(3, nbt)
            val rs = ps.executeQuery()
            if (rs.next()) rs.getInt("quantity") else 0
        }
    }

    /**
     * 检查玩家商品是否存在
     */
    fun hasPlayerItem(sellerUuid: UUID, itemId: String, nbt: String): Boolean {
        return database.connection.prepareStatement("""
            SELECT 1 
            FROM player_market 
            WHERE seller = ? AND item_id = ? AND nbt = ?
        """).use { ps ->
            ps.setString(1, sellerUuid.toString())
            ps.setString(2, itemId)
            ps.setString(3, nbt)
            val rs = ps.executeQuery()
            rs.next()
        }
    }

    /**
     * 获取玩家的所有商品
     */
    fun getPlayerItems(sellerUuid: String): List<MarketItem> {
        return database.connection.prepareStatement("""
            SELECT item_id, nbt, seller_name, price, quantity 
            FROM player_market 
            WHERE seller = ?
            ORDER BY item_id, nbt
        """).use { ps ->
            ps.setString(1, sellerUuid)
            val rs = ps.executeQuery()
            queryMapper.mapToMarketItems(rs)
        }
    }

    // ============== 搜索功能（委托给 MarketSearchService） ==============

    /**
     * 供 msearch 使用的显示用搜索
     */
    fun searchForDisplay(itemId: String): List<MarketItem> =
        searchService.searchForDisplay(itemId)

    /**
     * 供 mbuy 使用的交易用搜索（无卖家过滤）
     */
    fun searchForTransaction(itemId: String): List<MarketItem> =
        searchService.searchForTransaction(itemId)

    /**
     * 按卖家过滤的交易用搜索
     */
    fun searchForTransaction(itemId: String, sellerFilter: String): List<MarketItem> =
        searchService.searchForTransaction(itemId, sellerFilter)

    /**
     * 获取去重的卖家名称列表（用于命令补全）
     */
    fun getDistinctSellerNames(): List<String> =
        searchService.getDistinctSellerNames()

    /**
     * 查询所有卖家（系统与玩家）
     */
    fun getAllSellersForMenu(): List<SellerMenuEntry> =
        searchService.getAllSellersForMenu()

    /**
     * 按卖家获取全部商品（用于菜单）
     */
    fun getAllListingsForSeller(sellerId: String): List<MarketMenuEntry> =
        searchService.getAllListingsForSeller(sellerId)

    /**
     * 根据卖家名称或UUID解析成卖家UUID
     */
    fun findSellerUuidByName(input: String): String? =
        searchService.findSellerUuidByName(input)
}
