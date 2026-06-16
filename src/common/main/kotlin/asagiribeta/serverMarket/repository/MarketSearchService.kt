package asagiribeta.serverMarket.repository
import asagiribeta.serverMarket.util.Config
import java.util.*
/**
 * 市场搜索服务
 * 
 * 职责：
 * - 商品搜索（按物品ID、卖家等）
 * - 卖家查询
 * - 卖家UUID解析
 */
internal class MarketSearchService(
    private val database: Database,
    private val queryMapper: MarketQueryMapper
) {
    /**
     * 供 msearch 使用的显示用搜索
     * 
     * 返回指定物品的所有商品（系统+玩家），保留 seller_name 字段用于显示
     */
    fun searchForDisplay(itemId: String): List<MarketItem> {
        return database.connection.prepareStatement("""
            SELECT sm.item_id, sm.nbt, 'SERVER' as seller_name, sm.price, sm.quantity
            FROM system_market sm
            WHERE sm.item_id = ?
            UNION ALL
            SELECT pm.item_id, pm.nbt, pm.seller_name as seller_name, pm.price, pm.quantity
            FROM player_market pm
            WHERE pm.item_id = ?
            ORDER BY price
        """).use { ps ->
            ps.setString(1, itemId)
            ps.setString(2, itemId)
            val rs = ps.executeQuery()
            queryMapper.mapToMarketItems(rs)
        }
    }
    /**
     * 供 mbuy 使用的交易用搜索（无卖家过滤）
     * 
     * 返回指定物品的所有商品，使用 seller 字段（UUID）用于交易
     */
    fun searchForTransaction(itemId: String): List<MarketItem> {
        return database.connection.prepareStatement("""
            SELECT sm.item_id, sm.nbt, 'SERVER' as seller_name, sm.price, sm.quantity
            FROM system_market sm
            WHERE sm.item_id = ?
            UNION ALL
            SELECT pm.item_id, pm.nbt, pm.seller as seller_name, pm.price, pm.quantity
            FROM player_market pm
            WHERE pm.item_id = ?
            ORDER BY price
        """).use { ps ->
            ps.setString(1, itemId)
            ps.setString(2, itemId)
            val rs = ps.executeQuery()
            queryMapper.mapToMarketItems(rs)
        }
    }
    /**
     * 按卖家过滤的交易用搜索
     * 
     * @param itemId 物品ID
     * @param sellerFilter 卖家过滤器（'SERVER' 或 玩家UUID/名称）
     */
    fun searchForTransaction(itemId: String, sellerFilter: String): List<MarketItem> {
        // 如果指定SERVER，仅返回系统市场
        if (sellerFilter.equals("SERVER", ignoreCase = true)) {
            return database.connection.prepareStatement("""
                SELECT sm.item_id, sm.nbt, 'SERVER' as seller_name, sm.price, sm.quantity
                FROM system_market sm
                WHERE sm.item_id = ?
                ORDER BY price
            """).use { ps ->
                ps.setString(1, itemId)
                val rs = ps.executeQuery()
                queryMapper.mapToMarketItems(rs)
            }
        }
        // 其他情况：匹配玩家UUID或名称
        return database.connection.prepareStatement("""
            SELECT pm.item_id, pm.nbt, pm.seller as seller_name, pm.price, pm.quantity
            FROM player_market pm
            WHERE pm.item_id = ? AND (pm.seller = ? OR pm.seller_name = ?)
            ORDER BY price
        """).use { ps ->
            ps.setString(1, itemId)
            ps.setString(2, sellerFilter)
            ps.setString(3, sellerFilter)
            val rs = ps.executeQuery()
            queryMapper.mapToMarketItems(rs)
        }
    }
    /**
     * 获取去重的卖家名称列表（用于命令补全）
     */
    fun getDistinctSellerNames(): List<String> {
        return database.connection.prepareStatement(
            "SELECT DISTINCT seller_name FROM player_market ORDER BY seller_name"
        ).use { ps ->
            val rs = ps.executeQuery()
            queryMapper.mapToSellerNames(rs)
        }
    }
    /**
     * 查询所有卖家（系统与玩家）
     * 
     * 用于菜单显示，系统卖家优先，其余按名称排序
     */
    fun getAllSellersForMenu(): List<SellerMenuEntry> {
        val sql = """
            SELECT * FROM (
              SELECT 'SERVER' AS seller_id, 'SERVER' AS seller_name, COUNT(*) AS item_count FROM system_market
              UNION ALL
              SELECT seller AS seller_id, seller_name, COUNT(*) AS item_count FROM player_market GROUP BY seller, seller_name
            ) AS sellers ORDER BY CASE WHEN seller_id='SERVER' THEN 0 ELSE 1 END, seller_name
        """.trimIndent()
        return database.connection.prepareStatement(sql).use { ps ->
            val rs = ps.executeQuery()
            queryMapper.mapToSellerEntries(rs)
        }
    }
    /**
     * 按卖家获取全部商品（用于菜单）
     */
    fun getAllListingsForSeller(sellerId: String): List<MarketMenuEntry> {
        return if (sellerId.equals("SERVER", ignoreCase = true)) {
            database.connection.prepareStatement("""
                SELECT item_id, nbt, price, quantity, 'SERVER' as seller_id, 'SERVER' as seller_name, 1 as is_system
                FROM system_market
                ORDER BY item_id, price
            """.trimIndent()).use { ps ->
                val rs = ps.executeQuery()
                queryMapper.mapToMarketMenuEntries(rs)
            }
        } else {
            database.connection.prepareStatement("""
                SELECT item_id, nbt, price, quantity, seller as seller_id, seller_name, 0 as is_system
                FROM player_market
                WHERE seller = ?
                ORDER BY item_id, price
            """.trimIndent()).use { ps ->
                ps.setString(1, sellerId)
                val rs = ps.executeQuery()
                queryMapper.mapToMarketMenuEntries(rs)
            }
        }
    }
    /**
     * 根据卖家名称或UUID解析成卖家UUID
     * 
     * 优先直接解析 UUID，回退到按名称在数据库中查询
     * 
     * @param input 输入（UUID 或 玩家名称）
     * @return 卖家UUID，未找到返回 null
     */
    fun findSellerUuidByName(input: String): String? {
        // 直接是 UUID 则返回
        runCatching { UUID.fromString(input) }.onSuccess { return it.toString() }
        // MySQL 使用 XConomy 表；SQLite 仅从 player_market 和 balances 表解析
        return if (database.isMySQL) {
            val table = Config.xconomyPlayerTable
            val sql = """
                SELECT UID AS uuid FROM $table WHERE UID = ? OR player = ? OR LOWER(player) = LOWER(?)
                UNION ALL
                SELECT seller AS uuid FROM player_market WHERE seller_name = ? OR LOWER(seller_name) = LOWER(?)
                LIMIT 1
            """.trimIndent()
            database.executeQuery(sql) { ps ->
                ps.setString(1, input)
                ps.setString(2, input)
                ps.setString(3, input)
                ps.setString(4, input)
                ps.setString(5, input)
            }
        } else {
            // SQLite 模式：仅从 player_market 或 balances 表查询
            val sql = """
                SELECT seller AS uuid FROM player_market WHERE seller_name = ? OR LOWER(seller_name) = LOWER(?)
                UNION
                SELECT uid AS uuid FROM balances WHERE player = ? OR LOWER(player) = LOWER(?)
                LIMIT 1
            """.trimIndent()
            database.executeQuery(sql) { ps ->
                ps.setString(1, input)
                ps.setString(2, input)
                ps.setString(3, input)
                ps.setString(4, input)
            }
        }
    }
}
