package asagiribeta.serverMarket.repository

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.util.Config
import java.sql.Connection
import java.sql.SQLException
import java.util.*

/**
 * 数据库表结构和索引管理器
 *
 * 职责：
 * - 创建 MySQL/SQLite 表结构
 * - 管理和确保索引存在
 * - 初始化系统账户
 */
internal class DatabaseSchemaManager(
    private val connection: Connection,
    private val isMySQL: Boolean
) {

    /**
     * 创建所有必要的表结构
     */
    fun createTables() {
        if (isMySQL) {
            createTablesMySQL()
        } else {
            createTablesSQLite()
        }
        ensureIndices()
    }

    /**
     * 自动检查并创建缺失的关键索引
     * 确保关键表的查询性能，避免生产环境性能问题
     */
    private fun ensureIndices() {
        try {
            connection.createStatement().use { st ->
                if (isMySQL) {
                    ensureIndicesMySQL(st)
                } else {
                    ensureIndicesSQLite(st)
                }
            }
        } catch (e: SQLException) {
            ServerMarket.LOGGER.error("索引检查/创建失败", e)
        }
    }

    private fun ensureIndicesMySQL(st: java.sql.Statement) {
        // MySQL: 检查索引是否存在
        val indices = listOf(
            Triple("currency_items", "idx_currency_item", "CREATE INDEX idx_currency_item ON currency_items(item_id, nbt)"),
            Triple("system_market", "idx_system_item", "CREATE INDEX idx_system_item ON system_market(item_id, nbt)"),
            Triple("system_daily_purchase", "idx_purchase_date", "CREATE INDEX idx_purchase_date ON system_daily_purchase(date, player_uuid)")
        )

        for ((tableName, indexName, createSQL) in indices) {
            val existingIndices = mutableSetOf<String>()
            val rs = st.executeQuery(
                "SELECT INDEX_NAME FROM INFORMATION_SCHEMA.STATISTICS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '$tableName'"
            )
            while (rs.next()) {
                existingIndices.add(rs.getString(1).lowercase())
            }
            rs.close()

            if (indexName !in existingIndices) {
                st.execute(createSQL)
                ServerMarket.LOGGER.info("已创建索引: $indexName")
            }
        }
    }

    private fun ensureIndicesSQLite(st: java.sql.Statement) {
        // SQLite: 直接创建（IF NOT EXISTS）
        st.execute("CREATE INDEX IF NOT EXISTS idx_currency_item ON currency_items(item_id, nbt)")
        st.execute("CREATE INDEX IF NOT EXISTS idx_system_item ON system_market(item_id, nbt)")
        st.execute("CREATE INDEX IF NOT EXISTS idx_purchase_date ON system_daily_purchase(date, player_uuid)")
        ServerMarket.LOGGER.info("已确保 SQLite 关键索引存在")
    }

    private fun createTablesMySQL() {
        connection.createStatement().use { st ->
            val suffix = "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"

            // XConomy 兼容表
            createXconomyTables(st, suffix)

            // 业务自有表
            createBusinessTables(st, suffix)
        }
    }

    private fun createXconomyTables(st: java.sql.Statement, suffix: String) {
        val xcoPlayerTable = Config.xconomyPlayerTable
        val xcoNonPlayerTable = Config.xconomyNonPlayerTable

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

        // 可选：交易记录与登录记录表
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
    }

    private fun createBusinessTables(st: java.sql.Statement, suffix: String) {
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

    private fun createTablesSQLite() {
        connection.createStatement().use { st ->
            val sqliteBalanceTable = "balances"

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
            createBusinessTablesSQLite(st)
        }
    }

    private fun createBusinessTablesSQLite(st: java.sql.Statement) {
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

