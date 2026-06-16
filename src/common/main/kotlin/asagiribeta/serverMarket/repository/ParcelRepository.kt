package asagiribeta.serverMarket.repository

import asagiribeta.serverMarket.model.ParcelEntry
import asagiribeta.serverMarket.util.ItemKey
import java.sql.Connection
import java.util.UUID

/**
 * 快递包裹仓库
 *
 * 职责：管理玩家待领取的物品包裹
 */
class ParcelRepository(private val db: Database) {

    private val connection: Connection
        get() = db.connection

    /**
     * 添加包裹
     */
    fun addParcel(
        recipientUuid: UUID,
        recipientName: String,
        itemId: String,
        nbt: String,
        quantity: Int,
        reason: String
    ): Long {
        val normalizedNbt = ItemKey.normalizeSnbt(nbt)
        val sql = """
            INSERT INTO parcels (recipient_uuid, recipient_name, item_id, nbt, quantity, timestamp, reason)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { ps ->
            ps.setString(1, recipientUuid.toString())
            ps.setString(2, recipientName)
            ps.setString(3, itemId)
            ps.setString(4, normalizedNbt)
            ps.setInt(5, quantity)
            ps.setLong(6, System.currentTimeMillis())
            ps.setString(7, reason)
            ps.executeUpdate()

            ps.generatedKeys.use { rs ->
                if (rs.next()) {
                    return rs.getLong(1)
                }
            }
        }

        return -1
    }

    /**
     * 获取玩家的所有包裹
     */
    fun getParcelsForPlayer(uuid: UUID): List<ParcelEntry> {
        val sql = """
            SELECT id, recipient_uuid, recipient_name, item_id, nbt, quantity, timestamp, reason
            FROM parcels
            WHERE recipient_uuid = ?
            ORDER BY timestamp ASC
        """.trimIndent()

        val parcels = mutableListOf<ParcelEntry>()

        connection.prepareStatement(sql).use { ps ->
            ps.setString(1, uuid.toString())
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    parcels.add(ParcelEntry(
                        id = rs.getLong("id"),
                        recipientUuid = UUID.fromString(rs.getString("recipient_uuid")),
                        recipientName = rs.getString("recipient_name"),
                        itemId = rs.getString("item_id"),
                        nbt = rs.getString("nbt"),
                        quantity = rs.getInt("quantity"),
                        timestamp = rs.getLong("timestamp"),
                        reason = rs.getString("reason")
                    ))
                }
            }
        }

        return parcels
    }

    /**
     * 获取玩家的所有包裹（合并相同物品）
     */
    fun getParcelsForPlayerMerged(uuid: UUID): List<ParcelEntry> {
        // 先获取所有包裹
        val allParcels = getParcelsForPlayer(uuid)

        // 按 itemId + nbt 分组合并（nbt 使用 normalize，避免字符串差异导致无法合并）
        val mergedMap = mutableMapOf<Pair<String, String>, MutableList<ParcelEntry>>()

        for (parcel in allParcels) {
            val key = Pair(parcel.itemId, ItemKey.normalizeSnbt(parcel.nbt))
            mergedMap.getOrPut(key) { mutableListOf() }.add(parcel)
        }

        // 合并相同物品
        val result = mutableListOf<ParcelEntry>()
        for ((_, parcels) in mergedMap) {
            val totalQuantity = parcels.sumOf { it.quantity }
            val latestTimestamp = parcels.maxOf { it.timestamp }
            val firstParcel = parcels.first()
            val reasons = parcels.map { it.reason }.distinct().joinToString("; ")

            result.add(ParcelEntry(
                id = firstParcel.id, // 使用第一个包裹的ID作为代表
                recipientUuid = firstParcel.recipientUuid,
                recipientName = firstParcel.recipientName,
                itemId = firstParcel.itemId,
                nbt = firstParcel.nbt,
                quantity = totalQuantity,
                timestamp = latestTimestamp,
                reason = reasons
            ))
        }

        return result.sortedBy { it.timestamp }
    }

    /**
     * 删除指定玩家的指定物品的所有包裹
     */
    fun removeParcelsByItem(uuid: UUID, itemId: String, nbt: String): Int {
        val normalizedNbt = ItemKey.normalizeSnbt(nbt)
        val sql = "DELETE FROM parcels WHERE recipient_uuid = ? AND item_id = ? AND nbt = ?"
        connection.prepareStatement(sql).use { ps ->
            ps.setString(1, uuid.toString())
            ps.setString(2, itemId)
            ps.setString(3, normalizedNbt)
            return ps.executeUpdate()
        }
    }

    /**
     * 获取玩家包裹数量
     */
    fun getParcelCountForPlayer(uuid: UUID): Int {
        val sql = "SELECT COUNT(*) FROM parcels WHERE recipient_uuid = ?"
        connection.prepareStatement(sql).use { ps ->
            ps.setString(1, uuid.toString())
            ps.executeQuery().use { rs ->
                if (rs.next()) {
                    return rs.getInt(1)
                }
            }
        }
        return 0
    }

    /**
     * 删除包裹
     */
    fun removeParcel(id: Long): Boolean {
        val sql = "DELETE FROM parcels WHERE id = ?"
        connection.prepareStatement(sql).use { ps ->
            ps.setLong(1, id)
            return ps.executeUpdate() > 0
        }
    }

    /**
     * 批量删除包裹
     */
    fun removeParcels(ids: List<Long>): Int {
        if (ids.isEmpty()) return 0

        val placeholders = ids.joinToString(",") { "?" }
        val sql = "DELETE FROM parcels WHERE id IN ($placeholders)"

        connection.prepareStatement(sql).use { ps ->
            ids.forEachIndexed { index, id ->
                ps.setLong(index + 1, id)
            }
            return ps.executeUpdate()
        }
    }
}
