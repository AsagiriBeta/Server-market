package asagiribeta.serverMarket.repository

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.util.Config
import java.sql.ResultSet
import java.util.UUID

/**
 * Light-weight lookup queries for player info stored in the balance table.
 *
 * Goal: allow admin commands to resolve offline players without Mojang profile lookups.
 */
internal class PlayerLookupService(private val database: Database) {

    /**
     * Returns UUID for the given player name.
     *
     * Notes:
     * - We treat playerName as case-sensitive first; if not found, we try a case-insensitive match.
     * - If multiple rows match in case-insensitive mode (rare but possible), the first row is returned.
     */
    fun getUuidByPlayerName(playerName: String): UUID? {
        // Exact match first
        getUuidByPlayerNameExact(playerName)?.let { return it }
        // Fallback: case-insensitive
        return getUuidByPlayerNameIgnoreCase(playerName)
    }

    private fun getUuidByPlayerNameExact(playerName: String): UUID? {
        return if (database.isMySQL) {
            val playerTable = Config.xconomyPlayerTable
            val sql = "SELECT UID as uuid FROM $playerTable WHERE player = ? LIMIT 1"
            database.connection.prepareStatement(sql).use { ps ->
                ps.setString(1, playerName)
                ps.executeQuery().use { rs -> rs.readUuidColumnOrNull("uuid") }
            }
        } else {
            val sql = "SELECT uid as uuid FROM balances WHERE player = ? LIMIT 1"
            database.connection.prepareStatement(sql).use { ps ->
                ps.setString(1, playerName)
                ps.executeQuery().use { rs -> rs.readUuidColumnOrNull("uuid") }
            }
        }
    }

    private fun getUuidByPlayerNameIgnoreCase(playerName: String): UUID? {
        return if (database.isMySQL) {
            val playerTable = Config.xconomyPlayerTable
            // MySQL collation might already be case-insensitive, but we keep NOT relying on it.
            val sql = "SELECT UID as uuid FROM $playerTable WHERE LOWER(player) = LOWER(?) LIMIT 1"
            database.connection.prepareStatement(sql).use { ps ->
                ps.setString(1, playerName)
                ps.executeQuery().use { rs -> rs.readUuidColumnOrNull("uuid") }
            }
        } else {
            val sql = "SELECT uid as uuid FROM balances WHERE LOWER(player) = LOWER(?) LIMIT 1"
            database.connection.prepareStatement(sql).use { ps ->
                ps.setString(1, playerName)
                ps.executeQuery().use { rs -> rs.readUuidColumnOrNull("uuid") }
            }
        }
    }

    /**
     * Returns a list of distinct player names in the balance table.
     * Intended for command suggestions.
     */
    fun getDistinctPlayerNames(limit: Int = 1000): List<String> {
        val safeLimit = limit.coerceIn(1, 5000)
        val sql = if (database.isMySQL) {
            val playerTable = Config.xconomyPlayerTable
            "SELECT DISTINCT player FROM $playerTable ORDER BY player LIMIT $safeLimit"
        } else {
            "SELECT DISTINCT player FROM balances ORDER BY player LIMIT $safeLimit"
        }

        return try {
            database.connection.prepareStatement(sql).use { ps ->
                ps.executeQuery().use { rs ->
                    val out = ArrayList<String>()
                    while (rs.next()) {
                        val v = rs.getString(1) ?: continue
                        if (v.isNotBlank()) out.add(v)
                    }
                    out
                }
            }
        } catch (t: Throwable) {
            ServerMarket.LOGGER.warn("Failed to load distinct player names for suggestions", t)
            emptyList()
        }
    }

    private fun ResultSet.readUuidColumnOrNull(column: String): UUID? {
        if (!this.next()) return null
        val raw = this.getString(column) ?: return null
        return try { UUID.fromString(raw) } catch (_: Exception) { null }
    }
}
