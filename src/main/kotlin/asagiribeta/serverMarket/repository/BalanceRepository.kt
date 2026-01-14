package asagiribeta.serverMarket.repository

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.util.Config
import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.util.*

/**
 * 余额管理仓库
 *
 * 职责：
 * - 查询、设置、增加余额
 * - 转账操作
 * - 玩家数据初始化
 *
 * 设计原则：
 * 1. Database 层提供**同步方法**（getBalance, setBalance, transfer 等）
 * 2. Service 层使用 database.supplyAsync { ... } 来异步化**整个业务流程**
 * 3. 不在 Database 层提供过多 *Async 方法，保持职责单一
 */
internal class BalanceRepository(
    private val connection: Connection,
    private val isMySQL: Boolean
) {

    // XConomy 兼容（MySQL模式）
    private val xcoPlayerTable = Config.xconomyPlayerTable
    private val xcoNonPlayerTable = Config.xconomyNonPlayerTable
    private val xcoSystemAccount = Config.xconomySystemAccount

    // SQLite 模式自有余额表名
    private val sqliteBalanceTable = "balances"

    // ============== 工具方法 ==============

    private fun toMoney(amount: Double): BigDecimal =
        BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP)

    private fun getMoney(rs: ResultSet, column: String): Double = rs.getDouble(column)

    private fun bindMoneyXco(ps: PreparedStatement, index: Int, amount: Double) {
        // XConomy 表为 DOUBLE(20,2)，显式用 setDouble，先四舍五入到两位
        ps.setDouble(index, toMoney(amount).toDouble())
    }

    fun isSystem(uuid: UUID): Boolean =
        uuid.mostSignificantBits == 0L && uuid.leastSignificantBits == 0L

    /**
     * 执行 UPDATE 或 INSERT 操作的通用辅助方法
     * 如果 UPDATE 没有更新任何行，则执行 INSERT
     */
    private inline fun updateOrInsert(
        updateSql: String,
        insertSql: String,
        updateBinder: (PreparedStatement) -> Unit,
        insertBinder: (PreparedStatement) -> Unit
    ) {
        connection.prepareStatement(updateSql).use { ps ->
            updateBinder(ps)
            val updated = ps.executeUpdate()
            if (updated == 0) {
                connection.prepareStatement(insertSql).use { ins ->
                    insertBinder(ins)
                    ins.executeUpdate()
                }
            }
        }
    }

    // ============== 余额查询 ==============

    private fun queryBalance(uuid: UUID): Double {
        return if (isMySQL) {
            if (isSystem(uuid)) {
                val sql = "SELECT balance FROM $xcoNonPlayerTable WHERE account = ?"
                executeQuery(sql, { ps -> ps.setString(1, xcoSystemAccount) }) { rs ->
                    if (rs.next()) getMoney(rs, "balance") else 0.0
                } ?: 0.0
            } else {
                val sql = "SELECT balance FROM $xcoPlayerTable WHERE UID = ?"
                executeQuery(sql, { ps -> ps.setString(1, uuid.toString()) }) { rs ->
                    if (rs.next()) getMoney(rs, "balance") else 0.0
                } ?: 0.0
            }
        } else {
            val sql = "SELECT balance FROM $sqliteBalanceTable WHERE uid = ?"
            executeQuery(sql, { ps -> ps.setString(1, uuid.toString()) }) { rs ->
                if (rs.next()) rs.getDouble(1) else 0.0
            } ?: 0.0
        }
    }

    fun getBalance(uuid: UUID): Double = try {
        queryBalance(uuid)
    } catch (e: SQLException) {
        ServerMarket.LOGGER.error("Failed to query balance. uuid={}", uuid, e)
        0.0
    }


    // ============== 余额修改 ==============

    fun addBalance(uuid: UUID, amount: Double) {
        try {
            if (isMySQL) {
                addBalanceMySQL(uuid, amount)
            } else {
                addBalanceSQLite(uuid, amount)
            }
        } catch (e: SQLException) {
            ServerMarket.LOGGER.error("Failed to update balance. uuid={} amount={}", uuid, amount, e)
            throw e
        }
    }

    private fun addBalanceMySQL(uuid: UUID, amount: Double) {
        if (isSystem(uuid)) {
            updateOrInsert(
                updateSql = "UPDATE $xcoNonPlayerTable SET balance = balance + ? WHERE account = ?",
                insertSql = "INSERT INTO $xcoNonPlayerTable(account, balance) VALUES(?, ?)",
                updateBinder = { ps ->
                    bindMoneyXco(ps, 1, amount)
                    ps.setString(2, xcoSystemAccount)
                },
                insertBinder = { ins ->
                    ins.setString(1, xcoSystemAccount)
                    bindMoneyXco(ins, 2, amount)
                }
            )
        } else {
            updateOrInsert(
                updateSql = "UPDATE $xcoPlayerTable SET balance = balance + ? WHERE UID = ?",
                insertSql = "INSERT INTO $xcoPlayerTable(UID, player, balance, hidden) VALUES(?, ?, ?, 0)",
                updateBinder = { ps ->
                    bindMoneyXco(ps, 1, amount)
                    ps.setString(2, uuid.toString())
                },
                insertBinder = { ins ->
                    ins.setString(1, uuid.toString())
                    ins.setString(2, "")
                    bindMoneyXco(ins, 3, amount)
                }
            )
        }
    }

    private fun addBalanceSQLite(uuid: UUID, amount: Double) {
        updateOrInsert(
            updateSql = "UPDATE $sqliteBalanceTable SET balance = balance + ? WHERE uid = ?",
            insertSql = "INSERT INTO $sqliteBalanceTable(uid, player, balance) VALUES(?, ?, ?)",
            updateBinder = { ps ->
                ps.setDouble(1, toMoney(amount).toDouble())
                ps.setString(2, uuid.toString())
            },
            insertBinder = { ins ->
                ins.setString(1, uuid.toString())
                ins.setString(2, if (isSystem(uuid)) "SERVER" else "")
                ins.setDouble(3, toMoney(amount).toDouble())
            }
        )
    }


    fun setBalance(uuid: UUID, amount: Double) {
        try {
            if (isMySQL) {
                setBalanceMySQL(uuid, amount)
            } else {
                setBalanceSQLite(uuid, amount)
            }
        } catch (e: SQLException) {
            ServerMarket.LOGGER.error("Failed to set balance. uuid={} amount={}", uuid, amount, e)
            throw e
        }
    }

    private fun setBalanceMySQL(uuid: UUID, amount: Double) {
        if (isSystem(uuid)) {
            updateOrInsert(
                updateSql = "UPDATE $xcoNonPlayerTable SET balance = ? WHERE account = ?",
                insertSql = "INSERT INTO $xcoNonPlayerTable(account, balance) VALUES(?, ?)",
                updateBinder = { ps ->
                    bindMoneyXco(ps, 1, amount)
                    ps.setString(2, xcoSystemAccount)
                },
                insertBinder = { ins ->
                    ins.setString(1, xcoSystemAccount)
                    bindMoneyXco(ins, 2, amount)
                }
            )
        } else {
            updateOrInsert(
                updateSql = "UPDATE $xcoPlayerTable SET balance = ? WHERE UID = ?",
                insertSql = "INSERT INTO $xcoPlayerTable(UID, player, balance, hidden) VALUES(?, ?, ?, 0)",
                updateBinder = { ps ->
                    bindMoneyXco(ps, 1, amount)
                    ps.setString(2, uuid.toString())
                },
                insertBinder = { ins ->
                    ins.setString(1, uuid.toString())
                    ins.setString(2, "")
                    bindMoneyXco(ins, 3, amount)
                }
            )
        }
    }

    private fun setBalanceSQLite(uuid: UUID, amount: Double) {
        updateOrInsert(
            updateSql = "UPDATE $sqliteBalanceTable SET balance = ? WHERE uid = ?",
            insertSql = "INSERT INTO $sqliteBalanceTable(uid, player, balance) VALUES(?, ?, ?)",
            updateBinder = { ps ->
                ps.setDouble(1, toMoney(amount).toDouble())
                ps.setString(2, uuid.toString())
            },
            insertBinder = { ins ->
                ins.setString(1, uuid.toString())
                ins.setString(2, if (isSystem(uuid)) "SERVER" else "")
                ins.setDouble(3, toMoney(amount).toDouble())
            }
        )
    }

    // ============== 转账操作 ==============

    fun withdrawIfEnough(conn: Connection, uuid: UUID, amount: Double): Boolean {
        return if (isMySQL) {
            withdrawIfEnoughMySQL(conn, uuid, amount)
        } else {
            withdrawIfEnoughSQLite(conn, uuid, amount)
        }
    }

    private fun withdrawIfEnoughMySQL(conn: Connection, uuid: UUID, amount: Double): Boolean {
        return if (isSystem(uuid)) {
            conn.prepareStatement(
                "UPDATE $xcoNonPlayerTable SET balance = balance - ? WHERE account = ? AND balance >= ?"
            ).use { ps ->
                bindMoneyXco(ps, 1, amount)
                ps.setString(2, xcoSystemAccount)
                bindMoneyXco(ps, 3, amount)
                ps.executeUpdate() > 0
            }
        } else {
            conn.prepareStatement(
                "UPDATE $xcoPlayerTable SET balance = balance - ? WHERE UID = ? AND balance >= ?"
            ).use { ps ->
                bindMoneyXco(ps, 1, amount)
                ps.setString(2, uuid.toString())
                bindMoneyXco(ps, 3, amount)
                ps.executeUpdate() > 0
            }
        }
    }

    private fun withdrawIfEnoughSQLite(conn: Connection, uuid: UUID, amount: Double): Boolean {
        return conn.prepareStatement(
            "UPDATE $sqliteBalanceTable SET balance = balance - ? WHERE uid = ? AND balance >= ?"
        ).use { ps ->
            val amt = toMoney(amount).toDouble()
            ps.setDouble(1, amt)
            ps.setString(2, uuid.toString())
            ps.setDouble(3, amt)
            ps.executeUpdate() > 0
        }
    }

    fun transfer(fromUuid: UUID, toUuid: UUID, amount: Double) {
        if (amount.isNaN() || amount.isInfinite() || amount <= 0.0) {
            throw SQLException("非法金额: $amount")
        }

        val originalAutoCommit = connection.autoCommit
        connection.autoCommit = false
        try {
            // 系统账户 (00000000-0000-0000-0000-000000000000) 转账时不检查余额
            val isSystemAccount = fromUuid == UUID(0, 0)

            if (isSystemAccount) {
                // 系统账户直接扣除（可以为负），不检查余额
                addBalance(fromUuid, -amount)
            } else {
                // 普通账户需要检查余额
                if (!withdrawIfEnough(connection, fromUuid, amount)) {
                    throw SQLException("余额不足 UUID: $fromUuid 金额: $amount")
                }
            }

            addBalance(toUuid, amount)
            connection.commit()
        } catch (e: SQLException) {
            try {
                connection.rollback()
            } catch (_: SQLException) {}
            ServerMarket.LOGGER.error("Transfer failed. from={} to={} amount={}", fromUuid, toUuid, amount, e)
            throw e
        } finally {
            try {
                connection.autoCommit = originalAutoCommit
            } catch (_: SQLException) {}
        }
    }

    // ============== 玩家数据管理 ==============

    fun syncSave(uuid: UUID) {
        try {
            if (!connection.autoCommit) connection.commit()
            ServerMarket.LOGGER.debug("Player data saved. uuid={}", uuid.toString())
        } catch (e: SQLException) {
            ServerMarket.LOGGER.error("Failed to save player data. uuid={}", uuid, e)
        }
    }

    fun playerExists(uuid: UUID): Boolean {
        return try {
            if (isMySQL) {
                connection.prepareStatement("SELECT 1 FROM $xcoPlayerTable WHERE UID = ?").use { ps ->
                    ps.setString(1, uuid.toString())
                    ps.executeQuery().use { rs -> rs.next() }
                }
            } else {
                connection.prepareStatement("SELECT 1 FROM $sqliteBalanceTable WHERE uid = ?").use { ps ->
                    ps.setString(1, uuid.toString())
                    ps.executeQuery().use { rs -> rs.next() }
                }
            }
        } catch (e: SQLException) {
            ServerMarket.LOGGER.error("Failed to check player existence. uuid={}", uuid, e)
            false
        }
    }

    fun initializeBalance(uuid: UUID, playerName: String, initialAmount: Double) {
        val originalAutoCommit = connection.autoCommit
        try {
            connection.autoCommit = false
            if (isMySQL) {
                connection.prepareStatement(
                    "INSERT IGNORE INTO $xcoPlayerTable(UID, player, balance, hidden) VALUES(?, ?, ?, 0)"
                ).use { ps ->
                    ps.setString(1, uuid.toString())
                    ps.setString(2, playerName)
                    bindMoneyXco(ps, 3, initialAmount)
                    ps.executeUpdate()
                }
            } else {
                connection.prepareStatement(
                    "INSERT OR IGNORE INTO $sqliteBalanceTable(uid, player, balance) VALUES(?, ?, ?)"
                ).use { ps ->
                    ps.setString(1, uuid.toString())
                    ps.setString(2, playerName)
                    ps.setDouble(3, toMoney(initialAmount).toDouble())
                    ps.executeUpdate()
                }
            }
            connection.commit()
        } catch (e: SQLException) {
            ServerMarket.LOGGER.error("Failed to initialize balance. uuid={}", uuid, e)
            throw e
        } finally {
            connection.autoCommit = originalAutoCommit
        }
    }


    fun upsertPlayerName(uuid: UUID, playerName: String) {
        try {
            if (isMySQL) {
                connection.prepareStatement(
                    "INSERT INTO $xcoPlayerTable(UID, player, balance, hidden) VALUES(?, ?, 0, 0) " +
                    "ON DUPLICATE KEY UPDATE player = VALUES(player)"
                ).use { ps ->
                    ps.setString(1, uuid.toString())
                    ps.setString(2, playerName)
                    ps.executeUpdate()
                }
            } else {
                connection.prepareStatement(
                    "INSERT INTO $sqliteBalanceTable(uid, player, balance) VALUES(?, ?, 0) " +
                    "ON CONFLICT(uid) DO UPDATE SET player = excluded.player"
                ).use { ps ->
                    ps.setString(1, uuid.toString())
                    ps.setString(2, playerName)
                    ps.executeUpdate()
                }
            }
        } catch (e: SQLException) {
            ServerMarket.LOGGER.error("Failed to update player name. uuid={} name={}", uuid, playerName, e)
        }
    }


    // ============== 辅助方法 ==============

    private fun <T> executeQuery(
        sql: String,
        block: (PreparedStatement) -> Unit,
        resultBlock: (ResultSet) -> T
    ): T? {
        return try {
            connection.prepareStatement(sql).use { ps ->
                block(ps)
                ps.executeQuery().use { rs -> resultBlock(rs) }
            }
        } catch (e: SQLException) {
            ServerMarket.LOGGER.error("SQL query failed: {}", sql, e)
            null
        }
    }
}
