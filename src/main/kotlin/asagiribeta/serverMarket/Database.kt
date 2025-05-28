package asagiribeta.serverMarket

import java.util.*
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.PreparedStatement
import java.sql.ResultSet

class Database {
    private val connection = DriverManager.getConnection("jdbc:sqlite:market.db")
    
    init {
        // 初始化数据库表
        connection.createStatement().use {
            it.execute("""
                CREATE TABLE IF NOT EXISTS balances (
                    uuid TEXT PRIMARY KEY,
                    amount REAL NOT NULL
                )
            """)
            // 新增交易历史表（根据参考代码结构调整）
            it.execute("""
                CREATE TABLE IF NOT EXISTS history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    dtg BIGINT NOT NULL,
                    from_id TEXT NOT NULL,
                    from_type TEXT NOT NULL,
                    from_name TEXT NOT NULL,
                    to_id TEXT NOT NULL,
                    to_type TEXT NOT NULL,
                    to_name TEXT NOT NULL,
                    price REAL NOT NULL,
                    item TEXT NOT NULL
                )
            """)
        }
    }
    
    fun getBalance(uuid: UUID): Double {
        return try {
            connection.prepareStatement("SELECT amount FROM balances WHERE uuid = ?").use { ps ->
                ps.setString(1, uuid.toString())
                val rs = ps.executeQuery()
                if (rs.next()) rs.getDouble("amount") else 0.0
            }
        } catch (e: SQLException) {
            ServerMarket.LOGGER.error("查询余额失败 UUID: $uuid", e)
            0.0
        }
    }
    
    fun addBalance(uuid: UUID, amount: Double) {
        try {
            connection.prepareStatement("""
                INSERT INTO balances(uuid, amount) 
                VALUES(?, ?) 
                ON CONFLICT(uuid) DO UPDATE SET amount = amount + excluded.amount
            """).use { ps ->
                ps.setString(1, uuid.toString())
                ps.setDouble(2, amount)
                ps.executeUpdate()
            }
        } catch (e: SQLException) {
            ServerMarket.LOGGER.error("更新余额失败 UUID: $uuid 金额: $amount", e)
            throw e
        }
    }

    // 修改转账方法的事务管理逻辑
    fun transfer(fromUuid: UUID, toUuid: UUID, amount: Double) {
        val originalAutoCommit = connection.autoCommit
        try {
            connection.autoCommit = false
            val fromBalance = getBalance(fromUuid)
            if (fromBalance < amount) {
                throw SQLException("余额不足 UUID: ${fromUuid} 金额: $amount")
            }
            addBalance(fromUuid, -amount)
            addBalance(toUuid, amount)
            connection.commit()
        } catch (e: SQLException) {
            // 仅在非自动提交模式时才执行回滚
            if (!connection.autoCommit) {
                connection.rollback()
            }
            ServerMarket.LOGGER.error("转账失败 UUID: $fromUuid -> $toUuid 金额: $amount", e)
            throw e
        } finally {
            // 确保恢复原始自动提交状态
            connection.autoCommit = originalAutoCommit
        }
    }

    // 新增同步保存方法
    fun syncSave(uuid: UUID) {
        try {
            if (!connection.autoCommit) {
                connection.commit()
            }
            ServerMarket.LOGGER.debug("已保存玩家数据 UUID: $uuid")
        } catch (e: SQLException) {
            ServerMarket.LOGGER.error("保存数据失败 UUID: $uuid", e)
        }
    }

    // 新增玩家存在性检查方法
    fun playerExists(uuid: UUID): Boolean {
        return try {
            connection.prepareStatement("SELECT 1 FROM balances WHERE uuid = ?").use { ps ->
                ps.setString(1, uuid.toString())
                val rs = ps.executeQuery()
                rs.next() // 返回是否存在记录
            }
        } catch (e: SQLException) {
            ServerMarket.LOGGER.error("检查玩家存在性失败 UUID: $uuid", e)
            false
        }
    }

    // 新增专用初始化方法（避免使用冲突更新逻辑）
    fun initializeBalance(uuid: UUID, initialAmount: Double) {
        val originalAutoCommit = connection.autoCommit
        try {
            connection.autoCommit = false
            connection.prepareStatement("""
                INSERT INTO balances(uuid, amount)
                VALUES(?, ?)
                ON CONFLICT(uuid) DO NOTHING
            """).use { ps ->
                ps.setString(1, uuid.toString())
                ps.setDouble(2, initialAmount)
                ps.executeUpdate()
            }
            connection.commit()
        } catch (e: SQLException) {
            ServerMarket.LOGGER.error("初始化余额失败 UUID: $uuid", e)
            throw e
        } finally {
            connection.autoCommit = originalAutoCommit
        }
    }

    // 新增交易历史记录方法（根据参考代码逻辑适配）
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
        
        executeUpdate(sql) { ps: java.sql.PreparedStatement ->
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

    // 新增通用执行方法（优化代码复用）
    private fun executeUpdate(sql: String, block: (PreparedStatement) -> Unit) {
        try {
            connection.prepareStatement(sql).use { ps ->
                block(ps)
                ps.executeUpdate()
            }
        } catch (e: SQLException) {
            ServerMarket.LOGGER.error("执行SQL更新失败: $sql", e)
            throw e
        }
    }

    // 新增查询方法（优化异常处理）
    fun executeQuery(sql: String, block: (PreparedStatement) -> Unit): ResultSet? {
        return try {
            connection.prepareStatement(sql).use { ps ->
                block(ps)
                ps.executeQuery()
            }
        } catch (e: SQLException) {
            ServerMarket.LOGGER.error("执行SQL查询失败: $sql", e)
            null
        }
    }

    fun close() {
        try {
            connection.close()
            ServerMarket.LOGGER.info("数据库连接已释放")
        } catch (e: SQLException) {
            ServerMarket.LOGGER.error("关闭连接时发生错误", e)
        }
    }
}
