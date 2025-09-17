package asagiribeta.serverMarket.repository

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.util.Config
import java.sql.*
import java.util.*

class Database {
    internal val marketRepository = MarketRepository(this)
    internal val historyRepository = HistoryRepository(this)
    internal val currencyRepository = CurrencyRepository(this)

    internal val isMySQL: Boolean
    val connection: Connection

    init {
        val storage = Config.storageType.lowercase()
        isMySQL = storage == "mysql"
        connection = if (isMySQL) {
            // 构建 MySQL JDBC URL
            try { Class.forName("com.mysql.cj.jdbc.Driver") } catch (_: Throwable) {}
            val ssl = if (Config.mysqlUseSSL) "true" else "false"
            val baseParams = mutableListOf(
                "useSSL=$ssl",
                "useUnicode=true",
                "characterEncoding=UTF-8",
                "serverTimezone=UTC",
                "allowPublicKeyRetrieval=true"
            )
            if (Config.mysqlJdbcParams.isNotBlank()) {
                baseParams.add(Config.mysqlJdbcParams.trim('&', '?'))
            }
            val paramStr = baseParams.joinToString("&")
            val url = "jdbc:mysql://${Config.mysqlHost}:${Config.mysqlPort}/${Config.mysqlDatabase}?$paramStr"
            DriverManager.getConnection(url, Config.mysqlUser, Config.mysqlPassword)
        } else {
            try { Class.forName("org.sqlite.JDBC") } catch (_: Throwable) {}
            DriverManager.getConnection("jdbc:sqlite:market.db")
        }

        createTables()
        applyMigrations()

        if (!isMySQL) {
            // SQLite 专属 PRAGMA
            connection.createStatement().use {
                it.execute("PRAGMA foreign_keys = ON")
                it.execute("PRAGMA journal_mode = WAL")
                it.execute("PRAGMA synchronous = NORMAL")
                it.execute("PRAGMA busy_timeout = 5000")
            }
        }
    }

    private fun createTables() {
        if (isMySQL) createTablesMySQL() else createTablesSQLite()
    }

    private fun createTablesSQLite() {
        connection.createStatement().use {
            // balances
            it.execute(
                """
                CREATE TABLE IF NOT EXISTS balances (
                    uuid TEXT PRIMARY KEY,
                    player TEXT,
                    amount REAL NOT NULL
                )
                """.trimIndent()
            )
            // history
            it.execute(
                """
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
                """.trimIndent()
            )
            // system_market
            it.execute(
                """
                CREATE TABLE IF NOT EXISTS system_market (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    item_id TEXT NOT NULL,
                    nbt TEXT NOT NULL DEFAULT '',
                    price REAL NOT NULL,
                    quantity INTEGER DEFAULT -1,
                    seller TEXT DEFAULT 'SERVER',
                    limit_per_day INTEGER NOT NULL DEFAULT -1,
                    UNIQUE(item_id, nbt)
                )
                """.trimIndent()
            )
            // player_market
            it.execute(
                """
                CREATE TABLE IF NOT EXISTS player_market (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    seller TEXT NOT NULL,
                    seller_name TEXT NOT NULL,
                    item_id TEXT NOT NULL,
                    nbt TEXT NOT NULL DEFAULT '',
                    price REAL NOT NULL,
                    quantity INTEGER DEFAULT 0,
                    FOREIGN KEY(seller) REFERENCES balances(uuid)
                )
                """.trimIndent()
            )
            it.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_player_market_unique_v2 ON player_market(seller, item_id, nbt)")
            // currency_items
            it.execute(
                """
                CREATE TABLE IF NOT EXISTS currency_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    item_id TEXT NOT NULL,
                    nbt TEXT NOT NULL DEFAULT '',
                    value REAL NOT NULL,
                    UNIQUE(item_id, nbt)
                )
                """.trimIndent()
            )
            // system_daily_purchase
            it.execute(
                """
                CREATE TABLE IF NOT EXISTS system_daily_purchase (
                    date TEXT NOT NULL,
                    player_uuid TEXT NOT NULL,
                    item_id TEXT NOT NULL,
                    nbt TEXT NOT NULL DEFAULT '',
                    purchased INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(date, player_uuid, item_id, nbt)
                )
                """.trimIndent()
            )
        }
    }

    private fun createTablesMySQL() {
        connection.createStatement().use { st ->
            // 统一字符集
            val suffix = "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS balances (
                    uuid CHAR(36) PRIMARY KEY,
                    player VARCHAR(64),
                    amount DOUBLE NOT NULL
                ) $suffix
                """.trimIndent()
            )
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS history (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    dtg BIGINT NOT NULL,
                    from_id CHAR(36) NOT NULL,
                    from_type VARCHAR(32) NOT NULL,
                    from_name VARCHAR(64) NOT NULL,
                    to_id CHAR(36) NOT NULL,
                    to_type VARCHAR(32) NOT NULL,
                    to_name VARCHAR(64) NOT NULL,
                    price DOUBLE NOT NULL,
                    item VARCHAR(255) NOT NULL,
                    INDEX idx_history_from (from_id),
                    INDEX idx_history_to (to_id),
                    INDEX idx_history_dtg (dtg)
                ) $suffix
                """.trimIndent()
            )
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS system_market (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    item_id VARCHAR(100) NOT NULL,
                    nbt VARCHAR(512) NOT NULL DEFAULT '',
                    price DOUBLE NOT NULL,
                    quantity INT DEFAULT -1,
                    seller VARCHAR(32) DEFAULT 'SERVER',
                    limit_per_day INT NOT NULL DEFAULT -1,
                    UNIQUE KEY uk_system_item (item_id, nbt)
                ) $suffix
                """.trimIndent()
            )
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS player_market (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    seller CHAR(36) NOT NULL,
                    seller_name VARCHAR(64) NOT NULL,
                    item_id VARCHAR(100) NOT NULL,
                    nbt VARCHAR(512) NOT NULL DEFAULT '',
                    price DOUBLE NOT NULL,
                    quantity INT DEFAULT 0,
                    UNIQUE KEY uk_player_item (seller, item_id, nbt),
                    INDEX idx_player_seller (seller)
                ) $suffix
                """.trimIndent()
            )
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS currency_items (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    item_id VARCHAR(100) NOT NULL,
                    nbt VARCHAR(512) NOT NULL DEFAULT '',
                    value DOUBLE NOT NULL,
                    UNIQUE KEY uk_currency_item (item_id, nbt)
                ) $suffix
                """.trimIndent()
            )
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS system_daily_purchase (
                    date CHAR(10) NOT NULL,
                    player_uuid CHAR(36) NOT NULL,
                    item_id VARCHAR(100) NOT NULL,
                    nbt VARCHAR(512) NOT NULL DEFAULT '',
                    purchased INT NOT NULL DEFAULT 0,
                    PRIMARY KEY(date, player_uuid, item_id, nbt)
                ) $suffix
                """.trimIndent()
            )
        }
    }

    private fun applyMigrations() {
        if (isMySQL) return // 目前仅 SQLite 需要简单 ALTER 忽略
        // SQLite 迁移
        try {
            connection.createStatement().use { it.execute("ALTER TABLE system_market ADD COLUMN limit_per_day INTEGER NOT NULL DEFAULT -1") }
        } catch (_: SQLException) {}
        try {
            connection.createStatement().use { it.execute("ALTER TABLE balances ADD COLUMN player TEXT") }
        } catch (_: SQLException) {}
    }

    // ============== 余额相关 ==============
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
            val sql = if (isMySQL) {
                "INSERT INTO balances(uuid, amount) VALUES(?, ?) ON DUPLICATE KEY UPDATE amount = amount + VALUES(amount)"
            } else {
                """
                INSERT INTO balances(uuid, amount)
                VALUES(?, ?)
                ON CONFLICT(uuid) DO UPDATE SET amount = amount + excluded.amount
                """.trimIndent()
            }
            connection.prepareStatement(sql).use { ps ->
                ps.setString(1, uuid.toString())
                ps.setDouble(2, amount)
                ps.executeUpdate()
            }
        } catch (e: SQLException) {
            ServerMarket.LOGGER.error("更新余额失败 UUID: $uuid 金额: $amount", e)
            throw e
        }
    }

    fun deposit(uuid: UUID, amount: Double) {
        if (amount.isNaN() || amount.isInfinite() || amount <= 0.0) return
        addBalance(uuid, amount)
    }

    fun tryWithdraw(uuid: UUID, amount: Double): Boolean {
        if (amount.isNaN() || amount.isInfinite() || amount <= 0.0) return false
        return try {
            connection.prepareStatement(
                "UPDATE balances SET amount = amount - ? WHERE uuid = ? AND amount >= ?"
            ).use { ps ->
                ps.setDouble(1, amount)
                ps.setString(2, uuid.toString())
                ps.setDouble(3, amount)
                ps.executeUpdate() > 0
            }
        } catch (e: SQLException) {
            ServerMarket.LOGGER.error("扣款失败 UUID: $uuid 金额: $amount", e)
            false
        }
    }

    fun setBalance(uuid: UUID, amount: Double) {
        try {
            val sql = if (isMySQL) {
                "INSERT INTO balances(uuid, amount) VALUES(?, ?) ON DUPLICATE KEY UPDATE amount = VALUES(amount)"
            } else {
                """
                INSERT INTO balances(uuid, amount)
                VALUES(?, ?)
                ON CONFLICT(uuid) DO UPDATE SET amount = excluded.amount
                """.trimIndent()
            }
            connection.prepareStatement(sql).use { ps ->
                ps.setString(1, uuid.toString())
                ps.setDouble(2, amount)
                ps.executeUpdate()
            }
        } catch (e: SQLException) {
            ServerMarket.LOGGER.error("设置余额失败 UUID: $uuid 金额: $amount", e)
            throw e
        }
    }

    fun transfer(fromUuid: UUID, toUuid: UUID, amount: Double) {
        if (amount.isNaN() || amount.isInfinite() || amount <= 0.0) {
            throw SQLException("非法金额: $amount")
        }
        val originalAutoCommit = connection.autoCommit
        connection.autoCommit = false
        try {
            connection.prepareStatement(
                "UPDATE balances SET amount = amount - ? WHERE uuid = ? AND amount >= ?"
            ).use { ps ->
                ps.setDouble(1, amount)
                ps.setString(2, fromUuid.toString())
                ps.setDouble(3, amount)
                val updated = ps.executeUpdate()
                if (updated == 0) {
                    throw SQLException("余额不足 UUID: $fromUuid 金额: $amount")
                }
            }
            addBalance(toUuid, amount)
            connection.commit()
        } catch (e: SQLException) {
            try { connection.rollback() } catch (_: SQLException) {}
            ServerMarket.LOGGER.error("转账失败 UUID: $fromUuid -> $toUuid 金额: $amount", e)
            throw e
        } finally {
            try { connection.autoCommit = originalAutoCommit } catch (_: SQLException) {}
        }
    }

    fun syncSave(uuid: UUID) {
        try {
            if (!connection.autoCommit) {
                connection.commit()
            }
            ServerMarket.LOGGER.debug("已保存玩家数据 UUID: {}", uuid.toString())
        } catch (e: SQLException) {
            ServerMarket.LOGGER.error("保存数据失败 UUID: $uuid", e)
        }
    }

    fun playerExists(uuid: UUID): Boolean {
        return try {
            connection.prepareStatement("SELECT 1 FROM balances WHERE uuid = ?").use { ps ->
                ps.setString(1, uuid.toString())
                val rs = ps.executeQuery()
                rs.next()
            }
        } catch (e: SQLException) {
            ServerMarket.LOGGER.error("检查玩家存在性失败 UUID: $uuid", e)
            false
        }
    }

    fun initializeBalance(uuid: UUID, playerName: String, initialAmount: Double) {
        val originalAutoCommit = connection.autoCommit
        try {
            connection.autoCommit = false
            val sql = if (isMySQL) {
                "INSERT IGNORE INTO balances(uuid, player, amount) VALUES(?, ?, ?)"
            } else {
                """
                INSERT INTO balances(uuid, player, amount)
                VALUES(?, ?, ?)
                ON CONFLICT(uuid) DO NOTHING
                """.trimIndent()
            }
            connection.prepareStatement(sql).use { ps ->
                ps.setString(1, uuid.toString())
                ps.setString(2, playerName)
                ps.setDouble(3, initialAmount)
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
    fun initializeBalance(uuid: UUID, initialAmount: Double) = initializeBalance(uuid, "", initialAmount)

    fun upsertPlayerName(uuid: UUID, playerName: String) {
        try {
            val sql = if (isMySQL) {
                "INSERT INTO balances(uuid, player, amount) VALUES(?, ?, 0) ON DUPLICATE KEY UPDATE player = VALUES(player)"
            } else {
                """
                INSERT INTO balances(uuid, player, amount)
                VALUES(?, ?, 0)
                ON CONFLICT(uuid) DO UPDATE SET player = excluded.player
                """.trimIndent()
            }
            connection.prepareStatement(sql).use { ps ->
                ps.setString(1, uuid.toString())
                ps.setString(2, playerName)
                ps.executeUpdate()
            }
        } catch (e: SQLException) {
            ServerMarket.LOGGER.error("更新玩家名失败 UUID: $uuid Name: $playerName", e)
        }
    }

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

    internal fun executeQuery(sql: String, parameterSetter: (PreparedStatement) -> Unit): String? {
        return executeQuery(sql, parameterSetter) { rs ->
            if (rs.next()) rs.getString("uuid") else null
        }
    }

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
