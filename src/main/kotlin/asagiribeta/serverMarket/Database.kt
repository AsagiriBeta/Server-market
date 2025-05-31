package asagiribeta.serverMarket

import java.sql.*
import java.util.*

class Database {
    internal val marketRepository = MarketRepository(this)
    internal val historyRepository = HistoryRepository(this)
    
    val connection: Connection = DriverManager.getConnection("jdbc:sqlite:market.db")
    
    init {
        // 初始化数据库表
        connection.createStatement().use {
            it.execute("""
                CREATE TABLE IF NOT EXISTS balances (
                    uuid TEXT PRIMARY KEY,
                    amount REAL NOT NULL
                )
            """)
            // 交易历史表
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
            it.execute("""
                CREATE TABLE IF NOT EXISTS system_market (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    item_id TEXT NOT NULL UNIQUE,
                    price REAL NOT NULL,
                    quantity INTEGER DEFAULT -1,
                    seller TEXT DEFAULT 'SERVER'  
                )
            """)
            // 玩家市场表
            it.execute("""
                CREATE TABLE IF NOT EXISTS player_market (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    seller TEXT NOT NULL,
                    seller_name TEXT NOT NULL,
                    item_id TEXT NOT NULL,
                    price REAL NOT NULL,
                    quantity INTEGER DEFAULT 0,
                    FOREIGN KEY(seller) REFERENCES balances(uuid)
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
    
    private fun addBalance(uuid: UUID, amount: Double) {
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

    // 余额设置方法
    fun setBalance(uuid: UUID, amount: Double) {
        try {
            connection.prepareStatement("""
                INSERT INTO balances(uuid, amount) 
                VALUES(?, ?) 
                ON CONFLICT(uuid) DO UPDATE SET amount = excluded.amount
            """).use { ps ->
                ps.setString(1, uuid.toString())
                ps.setDouble(2, amount)
                ps.executeUpdate()
            }
        } catch (e: SQLException) {
            ServerMarket.LOGGER.error("设置余额失败 UUID: $uuid 金额: $amount", e)
            throw e
        }
    }

    // 转账方法的事务管理逻辑
    fun transfer(fromUuid: UUID, toUuid: UUID, amount: Double) {
        val originalAutoCommit = connection.autoCommit
        try {
            connection.autoCommit = false
            val fromBalance = getBalance(fromUuid)
            if (fromBalance < amount) {
                throw SQLException("余额不足 UUID: $fromUuid 金额: $amount")
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

    // 同步保存方法
    fun syncSave(uuid: UUID) {
        try {
            if (!connection.autoCommit) {
                connection.commit()
            }
            // 将字符串模板改为使用占位符格式，避免日志参数拼接的性能损耗
            ServerMarket.LOGGER.debug("已保存玩家数据 UUID: {}", uuid.toString())
        } catch (e: SQLException) {
            ServerMarket.LOGGER.error("保存数据失败 UUID: $uuid", e)
        }
    }

    // 玩家存在性检查方法
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

    // 专用初始化方法（避免使用冲突更新逻辑）
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

    // 新增查询执行方法
    private fun <T> executeQuery(sql: String, block: (PreparedStatement) -> Unit, resultBlock: (ResultSet) -> T): T? {
        return try {
            connection.prepareStatement(sql).use { ps ->
                block(ps)
                ps.executeQuery().use { rs ->
                    resultBlock(rs)
                }
            }
        } catch (e: SQLException) {
            ServerMarket.LOGGER.error("执行SQL查询失败: $sql", e)
            null
        }
    }

    // 修改原查询方法签名以适配新调用方式
    internal fun executeQuery(
        sql: String, 
        parameterSetter: (PreparedStatement) -> Unit
    ): String? {
        return executeQuery(sql, parameterSetter) { rs ->
            if (rs.next()) rs.getString("uuid") else null
        }
    }

    // 通用执行方法（添加注解消除安全警告）
    @Suppress("SqlSourceToSinkFlow")
    internal fun executeUpdate(sql: String, block: (PreparedStatement) -> Unit) {
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


    fun close() {
        try {
            connection.close()
            ServerMarket.LOGGER.info("数据库连接已释放")
        } catch (e: SQLException) {
            ServerMarket.LOGGER.error("关闭连接时发生错误", e)
        }
    }
}
