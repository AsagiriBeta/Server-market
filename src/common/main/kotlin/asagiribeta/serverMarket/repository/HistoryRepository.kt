package asagiribeta.serverMarket.repository

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.util.Config
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.util.*

class HistoryRepository(private val database: Database) {
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