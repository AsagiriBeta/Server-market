package asagiribeta.serverMarket.repository

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.model.CurrencyItem
import asagiribeta.serverMarket.util.ItemKey
import java.sql.PreparedStatement
import java.util.concurrent.CompletableFuture

/**
 * 货币配置数据访问层
 *
 * 设计说明：
 * - 查询操作（get, list, find）：提供 *Async 方法，可以被命令直接调用
 * - 修改操作（upsert, delete）：提供同步方法 + *Async 包装，通过 CurrencyService 调用
 *
 * 原因：查询操作较简单，允许直接调用以简化代码；
 *       修改操作需要通过 Service 层确保业务逻辑控制
 */
class CurrencyRepository(private val database: Database) {

    // 新增：异步 Upsert
    fun upsertCurrencyAsync(itemId: String, nbtSnbt: String, value: Double): CompletableFuture<Void> {
        val sql = if (database.isMySQL) {
            """
            INSERT INTO currency_items(item_id, nbt, value)
            VALUES(?, ?, ?)
            ON DUPLICATE KEY UPDATE value = VALUES(value)
            """.trimIndent()
        } else {
            """
            INSERT INTO currency_items(item_id, nbt, value)
            VALUES(?, ?, ?)
            ON CONFLICT(item_id, nbt) DO UPDATE SET value = excluded.value
            """.trimIndent()
        }
        return database.executeUpdateAsync(sql) { ps: PreparedStatement ->
            ps.setString(1, itemId)
            ps.setString(2, nbtSnbt)
            ps.setDouble(3, value)
        }
    }

    fun getCurrencyValue(itemId: String, nbtSnbt: String): Double? {
        return try {
            // 先按原始 NBT 精确匹配
            database.connection.prepareStatement(
                "SELECT value FROM currency_items WHERE item_id = ? AND nbt = ?"
            ).use { ps ->
                ps.setString(1, itemId)
                ps.setString(2, nbtSnbt)
                val rs = ps.executeQuery()
                if (rs.next()) rs.getDouble("value") else null
            } ?: run {
                // 回退：按归一化 NBT 匹配，兼容历史噪声
                findValueByNormalizedMatch(itemId, ItemKey.normalizeSnbt(nbtSnbt))
            }
        } catch (e: Exception) {
            ServerMarket.LOGGER.error("查询实体货币面值失败 item=$itemId", e)
            null
        }
    }

    private fun findValueByNormalizedMatch(itemId: String, normalized: String): Double? {
        // 优化：避免在应用层循环所有记录，改为在查询时使用 LIMIT
        return try {
            database.connection.prepareStatement(
                "SELECT nbt, value FROM currency_items WHERE item_id = ? ORDER BY CASE WHEN nbt = ? THEN 0 ELSE 1 END LIMIT 20"
            ).use { ps ->
                ps.setString(1, itemId)
                ps.setString(2, normalized)
                val rs = ps.executeQuery()
                var candidate: Double? = null
                while (rs.next()) {
                    val dbNbt = rs.getString("nbt") ?: ""
                    if (ItemKey.normalizeSnbt(dbNbt) == normalized) {
                        candidate = rs.getDouble("value")
                        break
                    }
                }
                candidate
            }
        } catch (e: Exception) {
            ServerMarket.LOGGER.error("归一化匹配实体货币失败 item=$itemId", e)
            null
        }
    }

    // 新增：异步查询面值
    fun getCurrencyValueAsync(itemId: String, nbtSnbt: String): CompletableFuture<Double?> {
        val sql = "SELECT value FROM currency_items WHERE item_id = ? AND nbt = ?"
        return database.executeQueryAsync(sql, { ps ->
            ps.setString(1, itemId)
            ps.setString(2, nbtSnbt)
        }) { rs ->
            if (rs.next()) rs.getDouble("value") else null
        }.thenCompose { found ->
            if (found != null) CompletableFuture.completedFuture(found)
            else database.supplyAsync { findValueByNormalizedMatch(itemId, ItemKey.normalizeSnbt(nbtSnbt)) }
        }
    }

    fun deleteCurrency(itemId: String, nbtSnbt: String): Boolean {
        return try {
            database.connection.prepareStatement(
                "DELETE FROM currency_items WHERE item_id = ? AND nbt = ?"
            ).use { ps ->
                ps.setString(1, itemId)
                ps.setString(2, nbtSnbt)
                ps.executeUpdate() > 0
            }
        } catch (e: Exception) {
            ServerMarket.LOGGER.error("删除实体货币配置失败 item=$itemId", e)
            false
        }
    }


    // 新增：异步 listAll
    fun listAllAsync(limit: Int = 100, offset: Int = 0): CompletableFuture<List<CurrencyItem>> {
        val sql = "SELECT item_id, nbt, value FROM currency_items ORDER BY item_id, nbt LIMIT ? OFFSET ?"
        return database.executeQueryAsync(sql, { ps ->
            ps.setInt(1, limit)
            ps.setInt(2, offset)
        }) { rs ->
            val list = mutableListOf<CurrencyItem>()
            while (rs.next()) {
                list.add(
                    CurrencyItem(
                        rs.getString("item_id"),
                        rs.getString("nbt"),
                        rs.getDouble("value")
                    )
                )
            }
            list
        }.thenApply { it ?: emptyList() }
    }

    // 新增：异步 listByItemId
    fun listByItemIdAsync(itemId: String, limit: Int = 100, offset: Int = 0): CompletableFuture<List<CurrencyItem>> {
        val sql = "SELECT item_id, nbt, value FROM currency_items WHERE item_id = ? ORDER BY nbt LIMIT ? OFFSET ?"
        return database.executeQueryAsync(sql, { ps ->
            ps.setString(1, itemId)
            ps.setInt(2, limit)
            ps.setInt(3, offset)
        }) { rs ->
            val list = mutableListOf<CurrencyItem>()
            while (rs.next()) {
                list.add(
                    CurrencyItem(
                        rs.getString("item_id"),
                        rs.getString("nbt"),
                        rs.getDouble("value")
                    )
                )
            }
            list
        }.thenApply { it ?: emptyList() }
    }

    // 新增：异步按面值查找
    fun findByValueAsync(value: Double): CompletableFuture<CurrencyItem?> {
        val sql = """
            SELECT item_id, nbt, value
            FROM currency_items
            WHERE value = ?
            ORDER BY CASE WHEN nbt = '' THEN 0 ELSE 1 END, item_id
            LIMIT 1
        """.trimIndent()
        return database.executeQueryAsync(sql, { ps ->
            ps.setDouble(1, value)
        }) { rs ->
            if (rs.next()) CurrencyItem(
                rs.getString("item_id"),
                rs.getString("nbt"),
                rs.getDouble("value")
            ) else null
        }
    }
}
