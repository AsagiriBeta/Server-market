package asagiribeta.serverMarket.repository

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.model.TransactionRecord
import asagiribeta.serverMarket.util.Config
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.util.*

class HistoryRepository(private val database: Database) {

    /**
     * Query transaction history for a player (as sender or receiver), newest first.
     */
    fun queryHistory(uuid: UUID, page: Int, pageSize: Int): List<TransactionRecord> {
        val offset = (page - 1) * pageSize
        val sql = """
            SELECT dtg, from_id, from_type, from_name, to_id, to_type, to_name, price, item
            FROM history
            WHERE from_id = ? OR to_id = ?
            ORDER BY dtg DESC
            LIMIT ? OFFSET ?
        """.trimIndent()

        return try {
            database.connection.prepareStatement(sql).use { ps ->
                ps.setString(1, uuid.toString())
                ps.setString(2, uuid.toString())
                ps.setInt(3, pageSize)
                ps.setInt(4, offset)
                ps.executeQuery().use { rs ->
                    val result = mutableListOf<TransactionRecord>()
                    while (rs.next()) {
                        result.add(
                            TransactionRecord(
                                dtg = rs.getLong("dtg"),
                                fromId = UUID.fromString(rs.getString("from_id")),
                                fromType = rs.getString("from_type"),
                                fromName = rs.getString("from_name"),
                                toId = UUID.fromString(rs.getString("to_id")),
                                toType = rs.getString("to_type"),
                                toName = rs.getString("to_name"),
                                amount = rs.getDouble("price"),
                                item = rs.getString("item")
                            )
                        )
                    }
                    result
                }
            }
        } catch (e: Exception) {
            ServerMarket.LOGGER.error("Failed to query history. uuid={} page={}", uuid, page, e)
            emptyList()
        }
    }

    /**
     * Keep only the newest [maxRecords] entries (XConomy-style retention).
     */
    fun pruneOldRecords(maxRecords: Int) {
        if (maxRecords <= 0) return
        val countSql = "SELECT COUNT(*) FROM history"
        val total = database.queryIntOne(countSql, {}, 0)
        if (total <= maxRecords) return

        val toDelete = total - maxRecords
        val deleteSql = if (database.isMySQL) {
            """
            DELETE FROM history WHERE id IN (
                SELECT id FROM (
                    SELECT id FROM history ORDER BY dtg ASC LIMIT ?
                ) AS old_rows
            )
            """.trimIndent()
        } else {
            """
            DELETE FROM history WHERE id IN (
                SELECT id FROM history ORDER BY dtg ASC LIMIT ?
            )
            """.trimIndent()
        }

        try {
            database.executeUpdate(deleteSql) { ps -> ps.setInt(1, toDelete) }
        } catch (e: Exception) {
            ServerMarket.LOGGER.warn("Failed to prune history records (limit={})", maxRecords, e)
        }
    }
    fun postHistory(
        dtg: Long,
        fromId: UUID,
        fromType: String,
        fromName: String,
        toId: UUID,
        toType: String,
        toName: String,
        price: Double,
        item: String
    ) {
        val sql = """
            INSERT INTO history 
            (dtg, from_id, from_type, from_name, to_id, to_type, to_name, price, item)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        database.executeUpdate(sql) { ps: PreparedStatement ->
            ps.setLong(1, dtg)
            ps.setString(2, fromId.toString())
            ps.setString(3, fromType)
            ps.setString(4, fromName)
            ps.setString(5, toId.toString())
            ps.setString(6, toType)
            ps.setString(7, toName)
            ps.setDouble(8, price)
            ps.setString(9, item)
        }

        // 可选：写入 XConomy record 表（仅 MySQL 且启用写入）
        if (database.isMySQL && Config.xconomyWriteRecord) {
            fun isSystem(uuid: UUID): Boolean = uuid.mostSignificantBits == 0L && uuid.leastSignificantBits == 0L
            try {
                val recordTable = Config.xconomyRecordTable
                val insert = """
                    INSERT INTO $recordTable
                    (type, uid, player, balance, amount, operation, command, comment, datetime)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
                // 仅为玩家侧写入（from 玩家扣款、to 玩家入账），避免非玩家账户/系统账户与列约束不符
                if (!isSystem(fromId)) {
                    val fromBal = database.getBalance(fromId)
                    database.executeUpdate(insert) { ps ->
                        ps.setString(1, "ServerMarket") // type
                        ps.setString(2, fromId.toString()) // uid
                        ps.setString(3, fromName) // player
                        ps.setBigDecimal(4, java.math.BigDecimal.valueOf(fromBal).setScale(2)) // balance
                        ps.setBigDecimal(5, java.math.BigDecimal.valueOf(-price).setScale(2)) // amount
                        ps.setString(6, "WITHDRAW") // operation
                        ps.setString(7, "ServerMarket") // command
                        ps.setString(8, item) // comment
                        ps.setTimestamp(9, Timestamp(dtg)) // datetime
                    }
                }
                if (!isSystem(toId)) {
                    val toBal = database.getBalance(toId)
                    database.executeUpdate(insert) { ps ->
                        ps.setString(1, "ServerMarket")
                        ps.setString(2, toId.toString())
                        ps.setString(3, toName)
                        ps.setBigDecimal(4, java.math.BigDecimal.valueOf(toBal).setScale(2))
                        ps.setBigDecimal(5, java.math.BigDecimal.valueOf(price).setScale(2))
                        ps.setString(6, "DEPOSIT")
                        ps.setString(7, "ServerMarket")
                        ps.setString(8, item)
                        ps.setTimestamp(9, Timestamp(dtg))
                    }
                }
            } catch (e: Exception) {
                ServerMarket.LOGGER.warn("Failed to write XConomy transaction record (ignored; does not affect the trade)", e)
            }
        }
    }
}