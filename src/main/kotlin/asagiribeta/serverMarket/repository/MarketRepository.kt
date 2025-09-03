@file:Suppress("SqlResolve")
package asagiribeta.serverMarket.repository

import java.util.*
import java.sql.ResultSet

class MarketRepository(private val database: Database) {
    
    fun addSystemItem(itemId: String, nbt: String, price: Double) {
        database.executeUpdate("""
            INSERT INTO system_market(item_id, nbt, price) 
            VALUES(?, ?, ?)
            ON CONFLICT(item_id, nbt) DO UPDATE SET price = excluded.price
        """) { ps ->
            ps.setString(1, itemId)
            ps.setString(2, nbt)
            ps.setDouble(3, price)
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
}

data class MarketItem(
    val itemId: String,
    val nbt: String,
    val sellerName: String,
    val price: Double,
    val quantity: Int
)
