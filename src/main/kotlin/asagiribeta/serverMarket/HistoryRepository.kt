package asagiribeta.serverMarket

import java.sql.PreparedStatement
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
    }
}