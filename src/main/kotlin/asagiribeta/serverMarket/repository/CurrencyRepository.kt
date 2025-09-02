package asagiribeta.serverMarket.repository

import asagiribeta.serverMarket.ServerMarket
import java.sql.PreparedStatement

class CurrencyRepository(private val database: Database) {

    fun upsertCurrency(itemId: String, nbtSnbt: String, value: Double) {
        database.executeUpdate(
            """
            INSERT INTO currency_items(item_id, nbt, value)
            VALUES(?, ?, ?)
            ON CONFLICT(item_id, nbt) DO UPDATE SET value = excluded.value
            """.trimIndent()
        ) { ps: PreparedStatement ->
            ps.setString(1, itemId)
            ps.setString(2, nbtSnbt)
            ps.setDouble(3, value)
        }
    }

    fun getCurrencyValue(itemId: String, nbtSnbt: String): Double? {
        return try {
            database.connection.prepareStatement(
                "SELECT value FROM currency_items WHERE item_id = ? AND nbt = ?"
            ).use { ps ->
                ps.setString(1, itemId)
                ps.setString(2, nbtSnbt)
                val rs = ps.executeQuery()
                if (rs.next()) rs.getDouble("value") else null
            }
        } catch (e: Exception) {
            ServerMarket.LOGGER.error("查询实体货币面值失败 item=$itemId", e)
            null
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

    data class CurrencyItem(
        val itemId: String,
        val nbt: String,
        val value: Double
    )

    fun listAll(limit: Int = 100, offset: Int = 0): List<CurrencyItem> {
        return queryMany(
            "SELECT item_id, nbt, value FROM currency_items ORDER BY item_id, nbt LIMIT ? OFFSET ?"
        ) { ps ->
            ps.setInt(1, limit)
            ps.setInt(2, offset)
        }
    }

    fun listByItemId(itemId: String, limit: Int = 100, offset: Int = 0): List<CurrencyItem> {
        return queryMany(
            "SELECT item_id, nbt, value FROM currency_items WHERE item_id = ? ORDER BY nbt LIMIT ? OFFSET ?"
        ) { ps ->
            ps.setString(1, itemId)
            ps.setInt(2, limit)
            ps.setInt(3, offset)
        }
    }

    private fun queryMany(sql: String, binder: (PreparedStatement) -> Unit): List<CurrencyItem> {
        return try {
            database.connection.prepareStatement(sql).use { ps ->
                binder(ps)
                ps.executeQuery().use { rs ->
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
                }
            }
        } catch (e: Exception) {
            ServerMarket.LOGGER.error("查询实体货币配置失败", e)
            emptyList()
        }
    }
}
