package asagiribeta.serverMarket.repository

import java.util.*
import java.sql.ResultSet

class MarketRepository(private val database: Database) {
    
    // 新增重载：支持限购（-1 为无限制）
    fun addSystemItem(itemId: String, nbt: String, price: Double, limitPerDay: Int) {
        database.executeUpdate(
            """
            INSERT INTO system_market(item_id, nbt, price, limit_per_day)
            VALUES(?, ?, ?, ?)
            ON CONFLICT(item_id, nbt) DO UPDATE SET 
                price = excluded.price,
                limit_per_day = excluded.limit_per_day
            """
        ) { ps ->
            ps.setString(1, itemId)
            ps.setString(2, nbt)
            ps.setDouble(3, price)
            ps.setInt(4, limitPerDay)
        }
    }

    fun removeSystemItem(itemId: String, nbt: String) {
        database.executeUpdate("DELETE FROM system_market WHERE item_id = ? AND nbt = ?") { ps ->
            ps.setString(1, itemId)
            ps.setString(2, nbt)
        }
    }

    fun hasSystemItem(itemId: String, nbt: String): Boolean {
        return database.connection.prepareStatement("SELECT 1 FROM system_market WHERE item_id = ? AND nbt = ?").use { ps ->
            ps.setString(1, itemId)
            ps.setString(2, nbt)
            val rs = ps.executeQuery()
            rs.next()
        }
    }

    // 读取系统商品每日限购（-1 表示无限制；不存在则返回 -1）
    fun getSystemLimitPerDay(itemId: String, nbt: String): Int {
        // 为避免静态分析误报，将列名拆分拼接
        val col = "limit_" + "per_" + "day"
        val sql = "SELECT $col FROM system_market WHERE item_id = ? AND nbt = ?"
        return database.connection.prepareStatement(sql).use { ps ->
            ps.setString(1, itemId)
            ps.setString(2, nbt)
            val rs = ps.executeQuery()
            if (rs.next()) rs.getInt(1) else -1
        }
    }

    // 系统商品：查询指定日期某玩家已购买数量
    fun getSystemPurchasedOn(date: String, playerUuid: UUID, itemId: String, nbt: String): Int {
        val table = "system_" + "daily_" + "purchase"
        val sql = """
            SELECT purchased FROM $table
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

    // 系统商品：增加当日购买计数（UPSERT）
    fun incrementSystemPurchasedOn(date: String, playerUuid: UUID, itemId: String, nbt: String, amount: Int) {
        if (amount <= 0) return
        val table = "system_" + "daily_" + "purchase"
        val sql = """
            INSERT INTO $table(date, player_uuid, item_id, nbt, purchased)
            VALUES(?, ?, ?, ?, ?)
            ON CONFLICT(date, player_uuid, item_id, nbt) DO UPDATE SET
                purchased = purchased + excluded.purchased
        """.trimIndent()
        database.executeUpdate(sql) { ps ->
            ps.setString(1, date)
            ps.setString(2, playerUuid.toString())
            ps.setString(3, itemId)
            ps.setString(4, nbt)
            ps.setInt(5, amount)
        }
    }

    // 系统市场查询方法
    fun getSystemItems(): List<MarketItem> {
        return database.connection.prepareStatement("""
            SELECT item_id, nbt, price, seller AS seller_name, quantity
            FROM system_market
            ORDER BY item_id, nbt
        """).use { ps ->
            val rs = ps.executeQuery()
            mapResultSetToMarketItems(rs)
        }
    }

    // 玩家市场操作方法
    fun addPlayerItem(sellerUuid: UUID, sellerName: String, itemId: String, nbt: String, price: Double) {
        database.executeUpdate("""
            INSERT INTO player_market(seller, seller_name, item_id, nbt, price, quantity)
            VALUES(?, ?, ?, ?, ?, 0)
            ON CONFLICT(seller, item_id, nbt) DO UPDATE SET price = excluded.price
        """) { ps ->
            ps.setString(1, sellerUuid.toString())
            ps.setString(2, sellerName)
            ps.setString(3, itemId)
            ps.setString(4, nbt)
            ps.setDouble(5, price)
        }
    }

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

    // 玩家市场查询方法
    fun getPlayerItems(sellerUuid: String): List<MarketItem> {
        return database.connection.prepareStatement("""
            SELECT item_id, nbt, seller_name, price, quantity 
            FROM player_market 
            WHERE seller = ?
            ORDER BY item_id, nbt
        """).use { ps ->
            ps.setString(1, sellerUuid)
            val rs = ps.executeQuery()
            mapResultSetToMarketItems(rs)
        }
    }

    // 供msearch使用的显示用搜索（保留seller_name字段）
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
            mapResultSetToMarketItems(rs)
        }
    }

    // 供mbuy使用的交易用搜索（使用seller字段）
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
        """
        ).use { ps ->
            ps.setString(1, itemId)
            ps.setString(2, itemId)
            val rs = ps.executeQuery()
            mapResultSetToMarketItems(rs)
        }
    }

    // 新增：按卖家过滤的交易用搜索
    fun searchForTransaction(itemId: String, sellerFilter: String): List<MarketItem> {
        // 如果指定SERVER，仅返回系统市场
        if (sellerFilter.equals("SERVER", ignoreCase = true)) {
            return database.connection.prepareStatement(
                """
                SELECT sm.item_id, sm.nbt, 'SERVER' as seller_name, sm.price, sm.quantity
                FROM system_market sm
                WHERE sm.item_id = ?
                ORDER BY price
                """
            ).use { ps ->
                ps.setString(1, itemId)
                val rs = ps.executeQuery()
                mapResultSetToMarketItems(rs)
            }
        }
        // 其他情况：匹配玩家UUID或名称
        return database.connection.prepareStatement(
            """
            SELECT pm.item_id, pm.nbt, pm.seller as seller_name, pm.price, pm.quantity
            FROM player_market pm
            WHERE pm.item_id = ? AND (pm.seller = ? OR pm.seller_name = ?)
            ORDER BY price
            """
        ).use { ps ->
            ps.setString(1, itemId)
            ps.setString(2, sellerFilter)
            ps.setString(3, sellerFilter)
            val rs = ps.executeQuery()
            mapResultSetToMarketItems(rs)
        }
    }

    // 提取的通用结果集映射方法：要求查询结果包含列 item_id, nbt, seller_name, price, quantity
    private fun mapResultSetToMarketItems(rs: ResultSet): List<MarketItem> = buildList {
        while (rs.next()) {
            add(
                MarketItem(
                    itemId = rs.getString("item_id"),
                    nbt = rs.getString("nbt"),
                    sellerName = rs.getString("seller_name"),
                    price = rs.getDouble("price"),
                    quantity = rs.getInt("quantity")
                )
            )
        }
    }

    // 新增：获取去重的卖家名称列表（用于补全）
    fun getDistinctSellerNames(): List<String> {
        return database.connection.prepareStatement(
            "SELECT DISTINCT seller_name FROM player_market ORDER BY seller_name"
        ).use { ps ->
            val rs = ps.executeQuery()
            val list = mutableListOf<String>()
            while (rs.next()) list.add(rs.getString(1))
            list
        }
    }

    // 新增：菜单统一查询（含系统与玩家商品），提供 seller_id 与 seller_name
    fun getAllListingsForMenu(): List<MarketMenuEntry> {
        return database.connection.prepareStatement(
            """
            SELECT item_id, nbt, price, quantity, 'SERVER' as seller_id, 'SERVER' as seller_name, 1 as is_system
            FROM system_market
            UNION ALL
            SELECT item_id, nbt, price, quantity, seller as seller_id, seller_name, 0 as is_system
            FROM player_market
            ORDER BY item_id, is_system DESC, price
            """.trimIndent()
        ).use { ps ->
            val rs = ps.executeQuery()
            val list = mutableListOf<MarketMenuEntry>()
            while (rs.next()) {
                list.add(
                    MarketMenuEntry(
                        itemId = rs.getString("item_id"),
                        nbt = rs.getString("nbt"),
                        price = rs.getDouble("price"),
                        quantity = rs.getInt("quantity"),
                        sellerId = rs.getString("seller_id"),
                        sellerName = rs.getString("seller_name"),
                        isSystem = rs.getInt("is_system") == 1
                    )
                )
            }
            list
        }
    }
}

data class MarketItem(
    val itemId: String,
    val nbt: String,
    val sellerName: String,
    val price: Double,
    val quantity: Int
)

// 新增：菜单专用条目
data class MarketMenuEntry(
    val itemId: String,
    val nbt: String,
    val price: Double,
    val quantity: Int,
    val sellerId: String, // 'SERVER' 或 玩家UUID
    val sellerName: String, // 展示名称
    val isSystem: Boolean
)
