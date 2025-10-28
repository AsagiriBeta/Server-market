package asagiribeta.serverMarket.repository

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.util.Config
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.*
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class Database {
    internal val marketRepository = MarketRepository(this)
    internal val historyRepository = HistoryRepository(this)
    internal val currencyRepository = CurrencyRepository(this)

    internal val isMySQL: Boolean
    val connection: Connection

    // XConomy 兼容（MySQL模式）
    private val xcoPlayerTable = Config.xconomyPlayerTable
    private val xcoNonPlayerTable = Config.xconomyNonPlayerTable
    private val xcoRecordTable = Config.xconomyRecordTable
    private val xcoSystemAccount = Config.xconomySystemAccount

    // SQLite 模式自有余额表名
    private val sqliteBalanceTable = "balances"

    // 数据库单线程执行器，串行化所有 DB 交互
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ServerMarket-DB").apply { isDaemon = true }
    }

    init {
        val storage = Config.storageType.lowercase()
        isMySQL = storage == "mysql"
        if (isMySQL) {
            // 构建 MySQL JDBC URL（XConomy 兼容表）
            try { Class.forName("com.mysql.cj.jdbc.Driver") } catch (_: Throwable) {}
            val ssl = if (Config.mysqlUseSSL) "true" else "false"
            val baseParams = mutableListOf(
                "useSSL=$ssl",
                "useUnicode=true",
                "characterEncoding=UTF-8",
                "serverTimezone=UTC",
                "allowPublicKeyRetrieval=true"
            )
            if (Config.mysqlJdbcParams.isNotBlank()) baseParams.add(Config.mysqlJdbcParams.trim('&', '?'))
            val url = "jdbc:mysql://${Config.mysqlHost}:${Config.mysqlPort}/${Config.mysqlDatabase}?${baseParams.joinToString("&")}"
            connection = DriverManager.getConnection(url, Config.mysqlUser, Config.mysqlPassword)
            createTablesMySQL()
        } else {
            // SQLite 模式
            try { Class.forName("org.sqlite.JDBC") } catch (_: Throwable) {}
            val dbFile = File(Config.sqlitePath)
            dbFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
            val url = "jdbc:sqlite:${dbFile.path}"
            connection = DriverManager.getConnection(url)
            // 推荐的性能/一致性设置
            connection.createStatement().use { st ->
                st.execute("PRAGMA foreign_keys = ON")
            }
            createTablesSQLite()
        }
    }

    private fun createTablesMySQL() {
        connection.createStatement().use { st ->
            val suffix = "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            // 始终创建 XConomy 表（兼容 Paper XConomy 插件）
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS $xcoPlayerTable (
                    UID VARCHAR(50) NOT NULL,
                    player VARCHAR(50) NOT NULL DEFAULT '',
                    balance DOUBLE(20,2) NOT NULL,
                    hidden INT(5) NOT NULL DEFAULT '0',
                    PRIMARY KEY (UID)
                ) $suffix
                """.trimIndent()
            )
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS $xcoNonPlayerTable (
                    account VARCHAR(50) NOT NULL,
                    balance DOUBLE(20,2) NOT NULL,
                    PRIMARY KEY (account)
                ) $suffix
                """.trimIndent()
            )
            // 可选：交易记录与登录记录表，仅在需要时创建
            if (Config.xconomyWriteRecord) {
                st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS ${Config.xconomyRecordTable} (
                        id INT(20) NOT NULL AUTO_INCREMENT,
                        type VARCHAR(50) NOT NULL,
                        uid VARCHAR(50) NOT NULL,
                        player VARCHAR(50) NOT NULL,
                        balance DOUBLE(20,2),
                        amount DOUBLE(20,2) NOT NULL,
                        operation VARCHAR(50) NOT NULL,
                        command VARCHAR(255) NOT NULL,
                        comment VARCHAR(255) NOT NULL,
                        datetime DATETIME NOT NULL,
                        PRIMARY KEY (id)
                    ) $suffix
                    """.trimIndent()
                )
                st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS ${Config.xconomyLoginTable} (
                        UUID VARCHAR(50) NOT NULL,
                        last_time DATETIME NOT NULL,
                        PRIMARY KEY (UUID)
                    ) $suffix
                    """.trimIndent()
                )
            }
            // 业务自有表
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

    private fun createTablesSQLite() {
        connection.createStatement().use { st ->
            // 自有余额表（玩家与系统同表，uid=全0代表系统）
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS $sqliteBalanceTable (
                    uid TEXT PRIMARY KEY,
                    player TEXT NOT NULL DEFAULT '',
                    balance REAL NOT NULL
                )
                """.trimIndent()
            )
            // 初始化系统账户（全 0 UUID）
            val systemUuid = UUID(0L, 0L).toString()
            connection.prepareStatement("INSERT OR IGNORE INTO $sqliteBalanceTable(uid, player, balance) VALUES(?, 'SERVER', 0)").use { ps ->
                ps.setString(1, systemUuid)
                ps.executeUpdate()
            }

            // 业务表
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    dtg INTEGER NOT NULL,
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
            st.execute("CREATE INDEX IF NOT EXISTS idx_history_from ON history(from_id)")
            st.execute("CREATE INDEX IF NOT EXISTS idx_history_to ON history(to_id)")
            st.execute("CREATE INDEX IF NOT EXISTS idx_history_dtg ON history(dtg)")

            st.execute(
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
            st.execute(
                """
                CREATE TABLE IF NOT EXISTS player_market (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    seller TEXT NOT NULL,
                    seller_name TEXT NOT NULL,
                    item_id TEXT NOT NULL,
                    nbt TEXT NOT NULL DEFAULT '',
                    price REAL NOT NULL,
                    quantity INTEGER DEFAULT 0,
                    UNIQUE(seller, item_id, nbt)
                )
                """.trimIndent()
            )
            st.execute("CREATE INDEX IF NOT EXISTS idx_player_seller ON player_market(seller)")

            st.execute(
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

            st.execute(
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

    // ============== 异步执行器封装 ==============
    fun <T> supplyAsync(block: (Connection) -> T): CompletableFuture<T> {
        return CompletableFuture.supplyAsync({
            try { block(connection) } catch (e: Exception) {
                ServerMarket.LOGGER.error("异步数据库任务执行失败", e); throw e
            }
        }, executor)
    }

    fun runAsync(block: (Connection) -> Unit): CompletableFuture<Void> {
        return CompletableFuture.runAsync({
            try { block(connection) } catch (e: Exception) {
                ServerMarket.LOGGER.error("异步数据库任务执行失败", e); throw e
            }
        }, executor)
    }

    // ============== 余额相关（MySQL 使用 XConomy；SQLite 使用自有表） ==============
    private fun toMoney(amount: Double): BigDecimal =
        BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP)

    private fun getMoney(rs: ResultSet, column: String): Double = rs.getDouble(column)

    private fun bindMoneyXco(ps: PreparedStatement, index: Int, amount: Double) {
        // XConomy 表为 DOUBLE(20,2)，显式用 setDouble，先四舍五入到两位
        ps.setDouble(index, toMoney(amount).toDouble())
    }

    private fun isSystem(uuid: UUID): Boolean = uuid.mostSignificantBits == 0L && uuid.leastSignificantBits == 0L

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

    fun getBalance(uuid: UUID): Double = try { queryBalance(uuid) } catch (e: SQLException) {
        ServerMarket.LOGGER.error("查询余额失败 UUID: $uuid", e); 0.0
    }

    fun getBalanceAsync(uuid: UUID): CompletableFuture<Double> = supplyAsync { queryBalance(uuid) }

    private fun addBalance(uuid: UUID, amount: Double) {
        try {
            if (isMySQL) {
                if (isSystem(uuid)) {
                    connection.prepareStatement(
                        "UPDATE $xcoNonPlayerTable SET balance = balance + ? WHERE account = ?"
                    ).use { ps ->
                        bindMoneyXco(ps, 1, amount); ps.setString(2, xcoSystemAccount)
                        val updated = ps.executeUpdate()
                        if (updated == 0) {
                            connection.prepareStatement(
                                "INSERT INTO $xcoNonPlayerTable(account, balance) VALUES(?, ?)"
                            ).use { ins ->
                                ins.setString(1, xcoSystemAccount); bindMoneyXco(ins, 2, amount); ins.executeUpdate()
                            }
                        }
                    }
                } else {
                    connection.prepareStatement(
                        "UPDATE $xcoPlayerTable SET balance = balance + ? WHERE UID = ?"
                    ).use { ps ->
                        bindMoneyXco(ps, 1, amount); ps.setString(2, uuid.toString())
                        val updated = ps.executeUpdate()
                        if (updated == 0) {
                            connection.prepareStatement(
                                "INSERT INTO $xcoPlayerTable(UID, player, balance, hidden) VALUES(?, ?, ?, 0)"
                            ).use { ins ->
                                ins.setString(1, uuid.toString()); ins.setString(2, ""); bindMoneyXco(ins, 3, amount); ins.executeUpdate()
                            }
                        }
                    }
                }
            } else {
                connection.prepareStatement(
                    "UPDATE $sqliteBalanceTable SET balance = balance + ? WHERE uid = ?"
                ).use { ps ->
                    ps.setDouble(1, toMoney(amount).toDouble()); ps.setString(2, uuid.toString())
                    val updated = ps.executeUpdate()
                    if (updated == 0) {
                        connection.prepareStatement(
                            "INSERT INTO $sqliteBalanceTable(uid, player, balance) VALUES(?, ?, ?)"
                        ).use { ins ->
                            ins.setString(1, uuid.toString()); ins.setString(2, if (isSystem(uuid)) "SERVER" else ""); ins.setDouble(3, toMoney(amount).toDouble()); ins.executeUpdate()
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            ServerMarket.LOGGER.error("更新余额失败 UUID: $uuid 金额: $amount", e)
            throw e
        }
    }

    private fun addBalanceAsync(uuid: UUID, amount: Double): CompletableFuture<Void> = runAsync { addBalance(uuid, amount) }

    fun depositAsync(uuid: UUID, amount: Double): CompletableFuture<Void> {
        if (amount.isNaN() || amount.isInfinite() || amount <= 0.0) return CompletableFuture.completedFuture(null)
        return addBalanceAsync(uuid, amount)
    }

    private fun withdrawIfEnough(conn: Connection, uuid: UUID, amount: Double): Boolean {
        return if (isMySQL) {
            if (isSystem(uuid)) {
                conn.prepareStatement(
                    "UPDATE $xcoNonPlayerTable SET balance = balance - ? WHERE account = ? AND balance >= ?"
                ).use { ps ->
                    bindMoneyXco(ps, 1, amount); ps.setString(2, xcoSystemAccount); bindMoneyXco(ps, 3, amount)
                    ps.executeUpdate() > 0
                }
            } else {
                conn.prepareStatement(
                    "UPDATE $xcoPlayerTable SET balance = balance - ? WHERE UID = ? AND balance >= ?"
                ).use { ps ->
                    bindMoneyXco(ps, 1, amount); ps.setString(2, uuid.toString()); bindMoneyXco(ps, 3, amount)
                    ps.executeUpdate() > 0
                }
            }
        } else {
            conn.prepareStatement(
                "UPDATE $sqliteBalanceTable SET balance = balance - ? WHERE uid = ? AND balance >= ?"
            ).use { ps ->
                val amt = toMoney(amount).toDouble()
                ps.setDouble(1, amt); ps.setString(2, uuid.toString()); ps.setDouble(3, amt)
                ps.executeUpdate() > 0
            }
        }
    }

    fun tryWithdrawAsync(uuid: UUID, amount: Double): CompletableFuture<Boolean> = supplyAsync {
        if (amount.isNaN() || amount.isInfinite() || amount <= 0.0) return@supplyAsync false
        try { withdrawIfEnough(it, uuid, amount) } catch (e: SQLException) {
            ServerMarket.LOGGER.error("扣款失败 UUID: $uuid 金额: $amount", e); false
        }
    }

    fun setBalance(uuid: UUID, amount: Double) {
        try {
            if (isMySQL) {
                if (isSystem(uuid)) {
                    connection.prepareStatement("UPDATE $xcoNonPlayerTable SET balance = ? WHERE account = ?").use { ps ->
                        bindMoneyXco(ps, 1, amount); ps.setString(2, xcoSystemAccount)
                        val updated = ps.executeUpdate()
                        if (updated == 0) {
                            connection.prepareStatement("INSERT INTO $xcoNonPlayerTable(account, balance) VALUES(?, ?)").use { ins ->
                                ins.setString(1, xcoSystemAccount); bindMoneyXco(ins, 2, amount); ins.executeUpdate()
                            }
                        }
                    }
                } else {
                    connection.prepareStatement("UPDATE $xcoPlayerTable SET balance = ? WHERE UID = ?").use { ps ->
                        bindMoneyXco(ps, 1, amount); ps.setString(2, uuid.toString())
                        val updated = ps.executeUpdate()
                        if (updated == 0) {
                            connection.prepareStatement(
                                "INSERT INTO $xcoPlayerTable(UID, player, balance, hidden) VALUES(?, ?, ?, 0)"
                            ).use { ins ->
                                ins.setString(1, uuid.toString()); ins.setString(2, ""); bindMoneyXco(ins, 3, amount); ins.executeUpdate()
                            }
                        }
                    }
                }
            } else {
                connection.prepareStatement("UPDATE $sqliteBalanceTable SET balance = ? WHERE uid = ?").use { ps ->
                    ps.setDouble(1, toMoney(amount).toDouble()); ps.setString(2, uuid.toString())
                    val updated = ps.executeUpdate()
                    if (updated == 0) {
                        connection.prepareStatement("INSERT INTO $sqliteBalanceTable(uid, player, balance) VALUES(?, ?, ?)").use { ins ->
                            ins.setString(1, uuid.toString()); ins.setString(2, if (isSystem(uuid)) "SERVER" else ""); ins.setDouble(3, toMoney(amount).toDouble()); ins.executeUpdate()
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            ServerMarket.LOGGER.error("设置余额失败 UUID: $uuid 金额: $amount", e)
            throw e
        }
    }

    fun setBalanceAsync(uuid: UUID, amount: Double): CompletableFuture<Void> = runAsync { setBalance(uuid, amount) }

    fun transfer(fromUuid: UUID, toUuid: UUID, amount: Double) {
        if (amount.isNaN() || amount.isInfinite() || amount <= 0.0) throw SQLException("非法金额: $amount")
        val originalAutoCommit = connection.autoCommit
        connection.autoCommit = false
        try {
            if (!withdrawIfEnough(connection, fromUuid, amount)) throw SQLException("余额不足 UUID: $fromUuid 金额: $amount")
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

    fun transferAsync(fromUuid: UUID, toUuid: UUID, amount: Double): CompletableFuture<Void> = runAsync { transfer(fromUuid, toUuid, amount) }

    fun syncSave(uuid: UUID) {
        try {
            if (!connection.autoCommit) connection.commit()
            ServerMarket.LOGGER.debug("已保存玩家数据 UUID: {}", uuid.toString())
        } catch (e: SQLException) {
            ServerMarket.LOGGER.error("保存数据失败 UUID: $uuid", e)
        }
    }

    fun syncSaveAsync(uuid: UUID): CompletableFuture<Void> = runAsync { syncSave(uuid) }

    fun playerExistsAsync(uuid: UUID): CompletableFuture<Boolean> = supplyAsync {
        try {
            if (isMySQL) {
                it.prepareStatement("SELECT 1 FROM $xcoPlayerTable WHERE UID = ?").use { ps ->
                    ps.setString(1, uuid.toString()); val rs = ps.executeQuery(); rs.next()
                }
            } else {
                it.prepareStatement("SELECT 1 FROM $sqliteBalanceTable WHERE uid = ?").use { ps ->
                    ps.setString(1, uuid.toString()); val rs = ps.executeQuery(); rs.next()
                }
            }
        } catch (e: SQLException) {
            ServerMarket.LOGGER.error("检查玩家存在性失败 UUID: $uuid", e); false
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
                    ps.setString(1, uuid.toString()); ps.setString(2, playerName); bindMoneyXco(ps, 3, initialAmount); ps.executeUpdate()
                }
            } else {
                connection.prepareStatement(
                    "INSERT OR IGNORE INTO $sqliteBalanceTable(uid, player, balance) VALUES(?, ?, ?)"
                ).use { ps ->
                    ps.setString(1, uuid.toString()); ps.setString(2, playerName); ps.setDouble(3, toMoney(initialAmount).toDouble()); ps.executeUpdate()
                }
            }
            connection.commit()
        } catch (e: SQLException) {
            ServerMarket.LOGGER.error("初始化余额失败 UUID: $uuid", e); throw e
        } finally { connection.autoCommit = originalAutoCommit }
    }

    fun initializeBalanceAsync(uuid: UUID, playerName: String, initialAmount: Double): CompletableFuture<Void> = runAsync {
        initializeBalance(uuid, playerName, initialAmount)
    }

    fun upsertPlayerName(uuid: UUID, playerName: String) {
        try {
            if (isMySQL) {
                connection.prepareStatement(
                    "INSERT INTO $xcoPlayerTable(UID, player, balance, hidden) VALUES(?, ?, 0, 0) ON DUPLICATE KEY UPDATE player = VALUES(player)"
                ).use { ps ->
                    ps.setString(1, uuid.toString()); ps.setString(2, playerName); ps.executeUpdate()
                }
            } else {
                connection.prepareStatement(
                    "INSERT INTO $sqliteBalanceTable(uid, player, balance) VALUES(?, ?, 0) ON CONFLICT(uid) DO UPDATE SET player = excluded.player"
                ).use { ps ->
                    ps.setString(1, uuid.toString()); ps.setString(2, playerName); ps.executeUpdate()
                }
            }
        } catch (e: SQLException) { ServerMarket.LOGGER.error("更新玩家名失败 UUID: $uuid Name: $playerName", e) }
    }

    fun upsertPlayerNameAsync(uuid: UUID, playerName: String): CompletableFuture<Void> = runAsync { upsertPlayerName(uuid, playerName) }

    // ============== SQL 执行封装 ==============
    private fun <T> executeQuery(sql: String, block: (PreparedStatement) -> Unit, resultBlock: (ResultSet) -> T): T? {
        return try {
            connection.prepareStatement(sql).use { ps ->
                block(ps); ps.executeQuery().use { rs -> resultBlock(rs) }
            }
        } catch (e: SQLException) { ServerMarket.LOGGER.error("执行SQL查询失败: $sql", e); null }
    }

    // 兼容旧用法：执行查询并返回 uuid 列（用于 MarketRepository 的名称->UUID 解析）
    internal fun executeQuery(sql: String, parameterSetter: (PreparedStatement) -> Unit): String? {
        return executeQuery(sql, parameterSetter) { rs -> if (rs.next()) rs.getString("uuid") else null }
    }

    internal fun <T> executeQueryAsync(sql: String, binder: (PreparedStatement) -> Unit, mapper: (ResultSet) -> T): CompletableFuture<T?> = supplyAsync {
        try {
            it.prepareStatement(sql).use { ps -> binder(ps); ps.executeQuery().use { rs -> mapper(rs) } }
        } catch (e: SQLException) { ServerMarket.LOGGER.error("执行SQL查询失败: $sql", e); null }
    }

    @Suppress("SqlSourceToSinkFlow")
    internal fun executeUpdate(sql: String, block: (PreparedStatement) -> Unit) {
        try { connection.prepareStatement(sql).use { ps -> block(ps); ps.executeUpdate() } }
        catch (e: SQLException) { ServerMarket.LOGGER.error("执行SQL更新失败: $sql", e); throw e }
    }

    @Suppress("SqlSourceToSinkFlow")
    internal fun executeUpdateAsync(sql: String, block: (PreparedStatement) -> Unit): CompletableFuture<Void> = runAsync {
        try { it.prepareStatement(sql).use { ps -> block(ps); ps.executeUpdate() } }
        catch (e: SQLException) { ServerMarket.LOGGER.error("执行SQL更新失败: $sql", e); throw e }
    }

    fun close() {
        try {
            val future = CompletableFuture.runAsync({
                try { connection.close(); ServerMarket.LOGGER.info("数据库连接已释放") }
                catch (e: SQLException) { ServerMarket.LOGGER.error("关闭连接时发生错误", e) }
            }, executor)
            future.get(5, TimeUnit.SECONDS)
        } catch (e: Exception) {
            ServerMarket.LOGGER.warn("等待数据库线程关闭连接超时，尝试强制关闭", e)
        } finally {
            executor.shutdown()
            try { if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow() }
            catch (_: InterruptedException) { executor.shutdownNow(); Thread.currentThread().interrupt() }
        }
    }
}
