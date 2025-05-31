package asagiribeta.serverMarket

import java.util.*

class MarketRepository(private val database: Database) {
    
    fun addSystemItem(itemId: String, price: Double) {
        database.executeUpdate("""
            INSERT INTO system_market(item_id, price) 
            VALUES(?, ?)
            ON CONFLICT(item_id) DO UPDATE SET price = excluded.price
        """) { ps ->
            ps.setString(1, itemId)
            ps.setDouble(2, price)
        }
    }

    fun removeSystemItem(itemId: String) {
        database.executeUpdate("DELETE FROM system_market WHERE item_id = ?") { ps ->
            ps.setString(1, itemId)
        }
    }

    fun hasSystemItem(itemId: String): Boolean {
        return database.connection.prepareStatement("SELECT 1 FROM system_market WHERE item_id = ?").use { ps ->
            ps.setString(1, itemId)
            val rs = ps.executeQuery()
            rs.next()
        }
    }

    // 系统市场查询方法
    fun getSystemItems(): List<MarketItem> {
        return database.connection.prepareStatement("""
            SELECT item_id, price, seller
            FROM system_market
        """).use { ps ->
            val rs = ps.executeQuery()
            buildList {
                while (rs.next()) {
                    add(
                        MarketItem(
                            itemId = rs.getString("item_id"),
                            sellerName = rs.getString("seller"),  // 使用seller字段
                            price = rs.getDouble("price"),
                            quantity = -1
                        )
                    )
                }
            }
        }
    }

    // 玩家市场操作方法
    fun addPlayerItem(sellerUuid: UUID, sellerName: String, itemId: String, price: Double) {
        database.executeUpdate("""
            INSERT INTO player_market(seller, seller_name, item_id, price, quantity)
            VALUES(?, ?, ?, ?, 0)
        """) { ps ->
            ps.setString(1, sellerUuid.toString())
            ps.setString(2, sellerName)
            ps.setString(3, itemId)
            ps.setDouble(4, price)
        }
    }

    fun updatePlayerItemPrice(sellerUuid: UUID, itemId: String, newPrice: Double) {
        database.executeUpdate("""
            UPDATE player_market 
            SET price = ?
            WHERE seller = ? AND item_id = ?
        """) { ps ->
            ps.setDouble(1, newPrice)
            ps.setString(2, sellerUuid.toString())
            ps.setString(3, itemId)
        }
    }

    fun removePlayerItem(sellerUuid: UUID, itemId: String) {
        database.executeUpdate("""
            DELETE FROM player_market 
            WHERE seller = ? AND item_id = ?
        """) { ps ->
            ps.setString(1, sellerUuid.toString())
            ps.setString(2, itemId)
        }
    }

    fun hasPlayerItem(sellerUuid: UUID, itemId: String): Boolean {
        return database.connection.prepareStatement("""
            SELECT 1 
            FROM player_market 
            WHERE seller = ? AND item_id = ?
        """).use { ps ->
            ps.setString(1, sellerUuid.toString())
            ps.setString(2, itemId)
            val rs = ps.executeQuery()
            rs.next()
        }
    }

    // 玩家市场查询方法
    fun getPlayerItems(sellerUuid: String): List<MarketItem> {
        return database.connection.prepareStatement("""
            SELECT item_id, seller_name, price, quantity 
            FROM player_market 
            WHERE seller = ?
        """).use { ps ->
            ps.setString(1, sellerUuid)
            val rs = ps.executeQuery()
            buildList {
                while (rs.next()) {
                    add(
                        MarketItem(
                            itemId = rs.getString("item_id"),
                            sellerName = rs.getString("seller_name"),
                            price = rs.getDouble("price"),
                            quantity = rs.getInt("quantity")
                        )
                    )
                }
            }
        }
    }
}

data class MarketItem(
    val itemId: String,
    val sellerName: String,
    val price: Double,
    val quantity: Int
)
