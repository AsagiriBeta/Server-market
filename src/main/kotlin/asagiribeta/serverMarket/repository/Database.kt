package asagiribeta.serverMarket.repository

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.util.Config
import java.io.File
import java.sql.*
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 数据库核心管理类
 *
 * 职责：
 * - 数据库连接管理
 * - 仓库实例管理
 * - 异步执行器
 * - SQL 执行工具方法
 *
 * 重构说明：
 * - 表结构管理 -> DatabaseSchemaManager
 * - 余额操作 -> BalanceRepository
 * - 市场操作 -> MarketRepository
 * - 交易历史 -> HistoryRepository
 * - 货币配置 -> CurrencyRepository
 */
class Database {
    // ============== 仓库实例 ==============
    internal val marketRepository = MarketRepository(this)
    internal val historyRepository = HistoryRepository(this)
    internal val currencyRepository = CurrencyRepository(this)
    internal val purchaseRepository = PurchaseRepository(this)
    internal val parcelRepository = ParcelRepository(this)

    // 余额仓库需要在 init 后初始化
    private val balanceRepository: BalanceRepository by lazy {
        BalanceRepository(connection, isMySQL)
    }

    // ============== 数据库连接 ==============
    internal val isMySQL: Boolean

    @Volatile
    private var allowAnyThreadConnectionAccess: Boolean = true

    private val rawConnection: Connection

    /**
     * Direct JDBC connection; must be used only on the DB executor thread after initialization.
     */
    val connection: Connection
        get() {
            if (!allowAnyThreadConnectionAccess) {
                check(Thread.currentThread().name.startsWith("ServerMarket-DB")) {
                    "Database connection accessed off DB thread: ${Thread.currentThread().name}"
                }
            }
            return rawConnection
        }

    // 数据库单线程执行器，串行化所有 DB 交互
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ServerMarket-DB").apply { isDaemon = true }
    }

    init {
        val storage = Config.storageType.lowercase()
        isMySQL = storage == "mysql"

        rawConnection = if (isMySQL) {
            initializeMySQLConnection()
        } else {
            initializeSQLiteConnection()
        }

        // 初始化表结构
        val schemaManager = DatabaseSchemaManager(rawConnection, isMySQL)
        schemaManager.createTables()

        allowAnyThreadConnectionAccess = false
    }

    private fun initializeMySQLConnection(): Connection {
        // 构建 MySQL JDBC URL（XConomy 兼容表）
        try {
            Class.forName("com.mysql.cj.jdbc.Driver")
        } catch (_: Throwable) {}

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

        val url = "jdbc:mysql://${Config.mysqlHost}:${Config.mysqlPort}/${Config.mysqlDatabase}?${baseParams.joinToString("&")}"
        return DriverManager.getConnection(url, Config.mysqlUser, Config.mysqlPassword)
    }

    private fun initializeSQLiteConnection(): Connection {
        // SQLite 模式
        try {
            Class.forName("org.sqlite.JDBC")
        } catch (_: Throwable) {}

        val dbFile = File(Config.sqlitePath)
        dbFile.parentFile?.let { if (!it.exists()) it.mkdirs() }

        val url = "jdbc:sqlite:${dbFile.path}"
        val conn = DriverManager.getConnection(url)

        // 推荐的性能/一致性设置（SQLite）
        conn.createStatement().use { st ->
            st.execute("PRAGMA foreign_keys = ON")
            st.execute("PRAGMA journal_mode = WAL")
            st.execute("PRAGMA synchronous = NORMAL")
            st.execute("PRAGMA busy_timeout = 3000")
            st.execute("PRAGMA temp_store = MEMORY")
        }

        return conn
    }

    // ============== 异步执行器封装 ==============

    fun <T> supplyAsync(block: (Connection) -> T): CompletableFuture<T> {
        return CompletableFuture.supplyAsync({
            try {
                block(rawConnection)
            } catch (e: Exception) {
                ServerMarket.LOGGER.error("异步数据库任务执行失败", e)
                throw e
            }
        }, executor)
    }

    fun <T> supplyAsync0(block: () -> T): CompletableFuture<T> = supplyAsync { _ -> block() }

    fun runAsync(block: (Connection) -> Unit): CompletableFuture<Void> {
        return CompletableFuture.runAsync({
            try {
                block(rawConnection)
            } catch (e: Exception) {
                ServerMarket.LOGGER.error("异步数据库任务执行失败", e)
                throw e
            }
        }, executor)
    }

    fun runAsync0(block: () -> Unit): CompletableFuture<Void> = runAsync { _ -> block() }

    // ============== 余额相关方法（委托给 BalanceRepository） ==============
    // 注意：这些方法在 Service 层的 lambda 中被使用，IDE 可能无法正确识别

    @Suppress("unused") // 在 TransferService 的 lambda 中使用
    internal fun isSystem(uuid: UUID): Boolean = balanceRepository.isSystem(uuid)

    internal fun getBalance(uuid: UUID): Double = balanceRepository.getBalance(uuid)

    @Suppress("unused") // 在 MarketService 的 lambda 中使用
    internal fun addBalance(uuid: UUID, amount: Double) = balanceRepository.addBalance(uuid, amount)

    @Suppress("unused") // 在 MarketService 的 lambda 中使用
    internal fun withdrawIfEnough(conn: Connection, uuid: UUID, amount: Double): Boolean =
        balanceRepository.withdrawIfEnough(conn, uuid, amount)

    internal fun setBalance(uuid: UUID, amount: Double) = balanceRepository.setBalance(uuid, amount)

    internal fun transfer(fromUuid: UUID, toUuid: UUID, amount: Double) =
        balanceRepository.transfer(fromUuid, toUuid, amount)

    @Suppress("unused") // 在事件处理器中使用
    fun syncSave(uuid: UUID) = balanceRepository.syncSave(uuid)

    fun playerExists(uuid: UUID): Boolean = balanceRepository.playerExists(uuid)

    @Suppress("unused") // 在玩家加入事件中使用
    fun initializeBalance(uuid: UUID, playerName: String, initialAmount: Double) =
        balanceRepository.initializeBalance(uuid, playerName, initialAmount)

    @Suppress("unused") // 在玩家加入事件中使用
    fun upsertPlayerName(uuid: UUID, playerName: String) =
        balanceRepository.upsertPlayerName(uuid, playerName)

    // ============== SQL 执行封装 ==============

    // 兼容旧用法：执行查询并返回 uuid 列（用于 MarketRepository 的名称->UUID 解析）
    internal fun executeQuery(sql: String, parameterSetter: (PreparedStatement) -> Unit): String? {
        return try {
            connection.prepareStatement(sql).use { ps ->
                parameterSetter(ps)
                ps.executeQuery().use { rs ->
                    if (rs.next()) rs.getString("uuid") else null
                }
            }
        } catch (e: SQLException) {
            ServerMarket.LOGGER.error("执行SQL查询失败: $sql", e)
            null
        }
    }

    internal fun <T> executeQueryAsync(
        sql: String,
        binder: (PreparedStatement) -> Unit,
        mapper: (ResultSet) -> T
    ): CompletableFuture<T?> = supplyAsync {
        try {
            it.prepareStatement(sql).use { ps ->
                binder(ps)
                ps.executeQuery().use { rs -> mapper(rs) }
            }
        } catch (e: SQLException) {
            ServerMarket.LOGGER.error("执行SQL查询失败: $sql", e)
            null
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

    @Suppress("SqlSourceToSinkFlow")
    internal fun executeUpdateAsync(sql: String, block: (PreparedStatement) -> Unit): CompletableFuture<Void> = runAsync {
        try {
            it.prepareStatement(sql).use { ps ->
                block(ps)
                ps.executeUpdate()
            }
        } catch (e: SQLException) {
            ServerMarket.LOGGER.error("执行SQL更新失败: $sql", e)
            throw e
        }
    }

    // ============== 资源释放 ==============

    fun close() {
        executor.execute {
            try {
                connection.close()
                ServerMarket.LOGGER.info("数据库连接已释放")
            } catch (e: SQLException) {
                ServerMarket.LOGGER.error("关闭连接时发生错误", e)
            }
        }

        executor.shutdown()
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                ServerMarket.LOGGER.warn("等待数据库线程关闭连接超时，尝试强制关闭")
                executor.shutdownNow()
            }
        } catch (_: InterruptedException) {
            executor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
}
