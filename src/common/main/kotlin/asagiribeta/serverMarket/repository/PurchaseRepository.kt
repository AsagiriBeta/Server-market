package asagiribeta.serverMarket.repository

import asagiribeta.serverMarket.model.PurchaseOrder
import asagiribeta.serverMarket.util.ItemKey
import java.util.*

/**
 * 收购数据访问仓库
 *
 * 职责：
 * - 系统收购 CRUD 操作
 * - 玩家收购 CRUD 操作
 * - 收购限额管理
 */
class PurchaseRepository(private val database: Database) {

    // ============== 系统收购操作 ==============

    /**
     * 添加或更新系统收购
     *
     * @param itemId 物品ID
     * @param nbt NBT数据
     * @param price 收购价格
     * @param limitPerDay 每日限额（-1 表示无限制）
     */
    fun addSystemPurchase(itemId: String, nbt: String, price: Double, limitPerDay: Int) {
        val normalizedNbt = ItemKey.normalizeSnbt(nbt)
        val sql = if (database.isMySQL) {
            """
            INSERT INTO system_purchase(item_id, nbt, price, limit_per_day)
            VALUES(?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE 
              price = VALUES(price),
              limit_per_day = VALUES(limit_per_day)
            """.trimIndent()
        } else {
            """
            INSERT INTO system_purchase(item_id, nbt, price, limit_per_day)
            VALUES(?, ?, ?, ?)
            ON CONFLICT(item_id, nbt) DO UPDATE SET 
                price = excluded.price,
                limit_per_day = excluded.limit_per_day
            """.trimIndent()
        }
        database.executeUpdate(sql) { ps ->
            ps.setString(1, itemId)
            ps.setString(2, normalizedNbt)
            ps.setDouble(3, price)
            ps.setInt(4, limitPerDay)
        }
    }

    /**
     * 移除系统收购
     */
    fun removeSystemPurchase(itemId: String, nbt: String) {
        val normalizedNbt = ItemKey.normalizeSnbt(nbt)
        database.executeUpdate("DELETE FROM system_purchase WHERE item_id = ? AND nbt = ?") { ps ->
            ps.setString(1, itemId)
            ps.setString(2, normalizedNbt)
        }
    }

    /**
     * 检查系统收购是否存在
     */
    fun hasSystemPurchase(itemId: String, nbt: String): Boolean {
        val normalizedNbt = ItemKey.normalizeSnbt(nbt)
        val sql = "SELECT 1 FROM system_purchase WHERE item_id = ? AND nbt = ?"
        return database.queryExists(sql) { ps ->
            ps.setString(1, itemId)
            ps.setString(2, normalizedNbt)
        }
    }

    /**
     * 获取系统收购每日限额（-1 表示无限制；不存在则返回 -1）
     */
    fun getSystemPurchaseLimitPerDay(itemId: String, nbt: String): Int {
        val normalizedNbt = ItemKey.normalizeSnbt(nbt)
        val sql = "SELECT limit_per_day FROM system_purchase WHERE item_id = ? AND nbt = ? LIMIT 1"
        return database.queryIntOne(sql, { ps ->
            ps.setString(1, itemId)
            ps.setString(2, normalizedNbt)
        }, defaultValue = -1)
    }

    /**
     * 获取系统收购价格
     */
    fun getSystemPurchasePrice(itemId: String, nbt: String): Double? {
        val normalizedNbt = ItemKey.normalizeSnbt(nbt)
        val sql = "SELECT price FROM system_purchase WHERE item_id = ? AND nbt = ? LIMIT 1"
        return database.queryDoubleOne(sql) { ps ->
            ps.setString(1, itemId)
            ps.setString(2, normalizedNbt)
        }
    }

    /**
     * 获取所有系统收购订单
     */
    fun getAllSystemPurchases(): List<PurchaseOrder> {
        val sql = """
            SELECT item_id, nbt, price, limit_per_day
            FROM system_purchase
            ORDER BY item_id, nbt
        """.trimIndent()
        return database.query(sql, { /* no params */ }) { rs ->
            val result = mutableListOf<PurchaseOrder>()
            while (rs.next()) {
                result.add(
                    PurchaseOrder(
                        itemId = rs.getString("item_id"),
                        nbt = rs.getString("nbt"),
                        price = rs.getDouble("price"),
                        buyerUuid = null,
                        buyerName = "SERVER",
                        limitPerDay = rs.getInt("limit_per_day")
                    )
                )
            }
            result
        } ?: emptyList()
    }

    /**
     * 查询指定物品的系统收购订单
     */
    fun getSystemPurchaseOrder(itemId: String, nbt: String): PurchaseOrder? {
        val normalizedNbt = ItemKey.normalizeSnbt(nbt)
        val sql = "SELECT price, limit_per_day FROM system_purchase WHERE item_id = ? AND nbt = ? LIMIT 1"
        return database.queryOne(sql, { ps ->
            ps.setString(1, itemId)
            ps.setString(2, normalizedNbt)
        }) { rs ->
            if (rs.next()) {
                PurchaseOrder(
                    itemId = itemId,
                    nbt = normalizedNbt,
                    price = rs.getDouble("price"),
                    buyerUuid = null,
                    buyerName = "SERVER",
                    limitPerDay = rs.getInt("limit_per_day")
                )
            } else null
        }
    }

    // ============== 系统收购限额管理 ==============

    /**
     * 系统收购：查询指定日期某玩家已出售数量
     */
    fun getSystemSoldOn(date: String, playerUuid: UUID, itemId: String, nbt: String): Int {
        val normalizedNbt = ItemKey.normalizeSnbt(nbt)
        val sql = """
            SELECT sold FROM system_daily_sell
            WHERE date = ? AND player_uuid = ? AND item_id = ? AND nbt = ?
        """.trimIndent()
        return database.queryIntOne(sql, { ps ->
            ps.setString(1, date)
            ps.setString(2, playerUuid.toString())
            ps.setString(3, itemId)
            ps.setString(4, normalizedNbt)
        }, defaultValue = 0)
    }

    /**
     * 系统收购：增加当日出售计数（UPSERT）
     */
    fun incrementSystemSoldOn(date: String, playerUuid: UUID, itemId: String, nbt: String, amount: Int) {
        if (amount <= 0) return
        val normalizedNbt = ItemKey.normalizeSnbt(nbt)

        val sql = if (database.isMySQL) {
            """
            INSERT INTO system_daily_sell(date, player_uuid, item_id, nbt, sold)
            VALUES(?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE sold = sold + VALUES(sold)
            """.trimIndent()
        } else {
            """
            INSERT INTO system_daily_sell(date, player_uuid, item_id, nbt, sold)
            VALUES(?, ?, ?, ?, ?)
            ON CONFLICT(date, player_uuid, item_id, nbt) DO UPDATE SET
                sold = sold + excluded.sold
            """.trimIndent()
        }
        database.executeUpdate(sql) { ps ->
            ps.setString(1, date)
            ps.setString(2, playerUuid.toString())
            ps.setString(3, itemId)
            ps.setString(4, normalizedNbt)
            ps.setInt(5, amount)
        }
    }

    // ============== 玩家收购操作 ==============

    /**
     * 添加玩家收购订单或更新价格
     */
    fun addPlayerPurchase(buyerUuid: UUID, buyerName: String, itemId: String, nbt: String, price: Double, targetAmount: Int) {
        val normalizedNbt = ItemKey.normalizeSnbt(nbt)
        val sql = if (database.isMySQL) {
            """
            INSERT INTO player_purchase(buyer, buyer_name, item_id, nbt, price, target_amount, current_amount)
            VALUES(?, ?, ?, ?, ?, ?, 0)
            ON DUPLICATE KEY UPDATE 
                price = VALUES(price),
                target_amount = VALUES(target_amount)
            """.trimIndent()
        } else {
            """
            INSERT INTO player_purchase(buyer, buyer_name, item_id, nbt, price, target_amount, current_amount)
            VALUES(?, ?, ?, ?, ?, ?, 0)
            ON CONFLICT(buyer, item_id, nbt) DO UPDATE SET 
                price = excluded.price,
                target_amount = excluded.target_amount
            """.trimIndent()
        }
        database.executeUpdate(sql) { ps ->
            ps.setString(1, buyerUuid.toString())
            ps.setString(2, buyerName)
            ps.setString(3, itemId)
            ps.setString(4, normalizedNbt)
            ps.setDouble(5, price)
            ps.setInt(6, targetAmount)
        }
    }

    /**
     * 移除玩家收购订单
     */
    fun removePlayerPurchase(buyerUuid: UUID, itemId: String, nbt: String) {
        val normalizedNbt = ItemKey.normalizeSnbt(nbt)
        database.executeUpdate("DELETE FROM player_purchase WHERE buyer = ? AND item_id = ? AND nbt = ?") { ps ->
            ps.setString(1, buyerUuid.toString())
            ps.setString(2, itemId)
            ps.setString(3, normalizedNbt)
        }
    }

    /**
     * 获取玩家收购订单的当前收购量
     */
    fun getPlayerPurchaseCurrentAmount(buyerUuid: UUID, itemId: String, nbt: String): Int {
        val normalizedNbt = ItemKey.normalizeSnbt(nbt)
        val sql = "SELECT current_amount FROM player_purchase WHERE buyer = ? AND item_id = ? AND nbt = ? LIMIT 1"
        return database.queryIntOne(sql, { ps ->
            ps.setString(1, buyerUuid.toString())
            ps.setString(2, itemId)
            ps.setString(3, normalizedNbt)
        }, defaultValue = 0)
    }

    /**
     * 获取玩家收购订单的目标收购量
     */
    fun getPlayerPurchaseTargetAmount(buyerUuid: UUID, itemId: String, nbt: String): Int {
        val normalizedNbt = ItemKey.normalizeSnbt(nbt)
        val sql = "SELECT target_amount FROM player_purchase WHERE buyer = ? AND item_id = ? AND nbt = ? LIMIT 1"
        return database.queryIntOne(sql, { ps ->
            ps.setString(1, buyerUuid.toString())
            ps.setString(2, itemId)
            ps.setString(3, normalizedNbt)
        }, defaultValue = 0)
    }

    /**
     * 获取所有玩家收购订单
     */
    fun getAllPlayerPurchases(): List<PlayerPurchaseEntry> {
        val sql = """
            SELECT buyer, buyer_name, item_id, nbt, price, target_amount, current_amount
            FROM player_purchase
            ORDER BY buyer, item_id, nbt
        """.trimIndent()
        return database.query(sql, { /* no params */ }) { rs ->
            val result = mutableListOf<PlayerPurchaseEntry>()
            while (rs.next()) {
                result.add(
                    PlayerPurchaseEntry(
                        buyerUuid = UUID.fromString(rs.getString("buyer")),
                        buyerName = rs.getString("buyer_name"),
                        itemId = rs.getString("item_id"),
                        nbt = rs.getString("nbt"),
                        price = rs.getDouble("price"),
                        targetAmount = rs.getInt("target_amount"),
                        currentAmount = rs.getInt("current_amount")
                    )
                )
            }
            result
        } ?: emptyList()
    }

    /**
     * 查询指定物品的玩家收购订单（仅返回尚未完成的）
     */
    fun getPlayerPurchaseOrders(itemId: String, nbt: String): List<PurchaseOrder> {
        val normalizedNbt = ItemKey.normalizeSnbt(nbt)
        val sql = """
            SELECT buyer, buyer_name, price
            FROM player_purchase
            WHERE item_id = ? AND nbt = ? AND current_amount < target_amount
        """.trimIndent()
        return database.query(sql, { ps ->
            ps.setString(1, itemId)
            ps.setString(2, normalizedNbt)
        }) { rs ->
            val list = mutableListOf<PurchaseOrder>()
            while (rs.next()) {
                list.add(
                    PurchaseOrder(
                        itemId = itemId,
                        nbt = normalizedNbt,
                        price = rs.getDouble("price"),
                        buyerUuid = UUID.fromString(rs.getString("buyer")),
                        buyerName = rs.getString("buyer_name"),
                        limitPerDay = -1
                    )
                )
            }
            list
        } ?: emptyList()
    }

    /**
     * 查询指定玩家的收购订单
     */
    fun getPlayerPurchasesByBuyer(buyerUuid: UUID): List<PlayerPurchaseEntry> {
        val sql = """
            SELECT buyer_name, item_id, nbt, price, target_amount, current_amount
            FROM player_purchase
            WHERE buyer = ?
            ORDER BY item_id, nbt
        """.trimIndent()
        return database.query(sql, { ps ->
            ps.setString(1, buyerUuid.toString())
        }) { rs ->
            val result = mutableListOf<PlayerPurchaseEntry>()
            while (rs.next()) {
                result.add(
                    PlayerPurchaseEntry(
                        buyerUuid = buyerUuid,
                        buyerName = rs.getString("buyer_name"),
                        itemId = rs.getString("item_id"),
                        nbt = rs.getString("nbt"),
                        price = rs.getDouble("price"),
                        targetAmount = rs.getInt("target_amount"),
                        currentAmount = rs.getInt("current_amount")
                    )
                )
            }
            result
        } ?: emptyList()
    }

    /**
     * 检查玩家收购订单是否存在
     */
    fun hasPlayerPurchase(buyerUuid: UUID, itemId: String, nbt: String): Boolean {
        val normalizedNbt = ItemKey.normalizeSnbt(nbt)
        val sql = "SELECT 1 FROM player_purchase WHERE buyer = ? AND item_id = ? AND nbt = ?"
        return database.queryExists(sql) { ps ->
            ps.setString(1, buyerUuid.toString())
            ps.setString(2, itemId)
            ps.setString(3, normalizedNbt)
        }
    }

    /**
     * 查询指定玩家的单个收购订单
     */
    fun getPlayerPurchaseOrder(buyerUuid: UUID, itemId: String, nbt: String): PurchaseOrder? {
        val normalizedNbt = ItemKey.normalizeSnbt(nbt)
        val sql = "SELECT buyer_name, price FROM player_purchase WHERE buyer = ? AND item_id = ? AND nbt = ? LIMIT 1"
        return database.queryOne(sql, { ps ->
            ps.setString(1, buyerUuid.toString())
            ps.setString(2, itemId)
            ps.setString(3, normalizedNbt)
        }) { rs ->
            if (rs.next()) {
                PurchaseOrder(
                    itemId = itemId,
                    nbt = normalizedNbt,
                    price = rs.getDouble("price"),
                    buyerUuid = buyerUuid,
                    buyerName = rs.getString("buyer_name"),
                    limitPerDay = -1
                )
            } else null
        }
    }

    /**
     * 增加玩家收购订单的当前收购量
     */
    fun incrementPlayerPurchaseAmount(buyerUuid: UUID, itemId: String, nbt: String, amount: Int) {
        if (amount <= 0) return
        val normalizedNbt = ItemKey.normalizeSnbt(nbt)
        database.executeUpdate(
            "UPDATE player_purchase SET current_amount = current_amount + ? WHERE buyer = ? AND item_id = ? AND nbt = ?"
        ) { ps ->
            ps.setInt(1, amount)
            ps.setString(2, buyerUuid.toString())
            ps.setString(3, itemId)
            ps.setString(4, normalizedNbt)
        }
    }
}

/**
 * 玩家收购条目
 */
data class PlayerPurchaseEntry(
    val buyerUuid: UUID,
    val buyerName: String,
    val itemId: String,
    val nbt: String,
    val price: Double,
    val targetAmount: Int,
    val currentAmount: Int
) {
    val remaining: Int get() = (targetAmount - currentAmount).coerceAtLeast(0)
    val isCompleted: Boolean get() = currentAmount >= targetAmount
}
