package asagiribeta.serverMarket.config

import asagiribeta.serverMarket.util.PropertyLoader

/**
 * 配置属性数据类
 * 封装所有配置项，用于减少重复代码
 */
data class ConfigProperties(
    var maxTransferAmount: Double = 1000000.0,
    var initialPlayerBalance: Double = 100.0,
    var enableTransactionHistory: Boolean = true,
    var maxHistoryRecords: Int = 10000,
    var enableDebugLogging: Boolean = false,
    var marketTaxRate: Double = 0.05,
    var enableTax: Boolean = false,
    var storageType: String = "sqlite",
    var sqlitePath: String = "market.db",
    var mysqlHost: String = "localhost",
    var mysqlPort: Int = 3306,
    var mysqlDatabase: String = "server_market",
    var mysqlUser: String = "root",
    var mysqlPassword: String = "",
    var mysqlUseSSL: Boolean = false,
    var mysqlJdbcParams: String = "",
    var xconomyPlayerTable: String = "xconomy",
    var xconomyNonPlayerTable: String = "xconomynon",
    var xconomyRecordTable: String = "xconomyrecord",
    var xconomyLoginTable: String = "xconomylogin",
    var xconomySystemAccount: String = "SERVER",
    var xconomyWriteRecord: Boolean = false
) {
    /**
     * 从 PropertyLoader 加载所有配置
     */
    fun loadFrom(loader: PropertyLoader) {
        maxTransferAmount = loader.doubleKey("max_transfer_amount", maxTransferAmount)
        initialPlayerBalance = loader.doubleKey("initial_player_balance", initialPlayerBalance)
        enableTransactionHistory = loader.boolKey("enable_transaction_history", enableTransactionHistory)
        maxHistoryRecords = loader.intKey("max_history_records", maxHistoryRecords)
        enableDebugLogging = loader.boolKey("enable_debug_logging", enableDebugLogging)
        marketTaxRate = loader.doubleKey("market_tax_rate", marketTaxRate)
        enableTax = loader.boolKey("enable_tax", enableTax)
        storageType = loader.stringKey("storage_type", storageType)

        // SQLite
        sqlitePath = loader.stringKey("sqlite_path", sqlitePath)

        // MySQL
        mysqlHost = loader.stringKey("mysql_host", mysqlHost)
        mysqlPort = loader.intKey("mysql_port", mysqlPort)
        mysqlDatabase = loader.stringKey("mysql_database", mysqlDatabase)
        mysqlUser = loader.stringKey("mysql_user", mysqlUser)
        mysqlPassword = loader.stringKey("mysql_password", mysqlPassword)
        mysqlUseSSL = loader.boolKey("mysql_use_ssl", mysqlUseSSL)
        mysqlJdbcParams = loader.stringKey("mysql_jdbc_params", mysqlJdbcParams)

        // XConomy
        xconomyPlayerTable = loader.stringKey("xconomy_player_table", xconomyPlayerTable)
        xconomyNonPlayerTable = loader.stringKey("xconomy_non_player_table", xconomyNonPlayerTable)
        xconomyRecordTable = loader.stringKey("xconomy_record_table", xconomyRecordTable)
        xconomyLoginTable = loader.stringKey("xconomy_login_table", xconomyLoginTable)
        xconomySystemAccount = loader.stringKey("xconomy_system_account", xconomySystemAccount)
        xconomyWriteRecord = loader.boolKey("xconomy_write_record", xconomyWriteRecord)
    }

    /**
     * 保存所有配置到 PropertyLoader
     */
    fun saveTo(loader: PropertyLoader) {
        loader.setDouble("max_transfer_amount", maxTransferAmount)
        loader.setDouble("initial_player_balance", initialPlayerBalance)
        loader.setBool("enable_transaction_history", enableTransactionHistory)
        loader.setInt("max_history_records", maxHistoryRecords)
        loader.setBool("enable_debug_logging", enableDebugLogging)
        loader.setDouble("market_tax_rate", marketTaxRate)
        loader.setBool("enable_tax", enableTax)
        loader.setString("storage_type", storageType)

        // SQLite
        loader.setString("sqlite_path", sqlitePath)

        // MySQL
        loader.setString("mysql_host", mysqlHost)
        loader.setInt("mysql_port", mysqlPort)
        loader.setString("mysql_database", mysqlDatabase)
        loader.setString("mysql_user", mysqlUser)
        loader.setString("mysql_password", mysqlPassword)
        loader.setBool("mysql_use_ssl", mysqlUseSSL)
        loader.setString("mysql_jdbc_params", mysqlJdbcParams)

        // XConomy
        loader.setString("xconomy_player_table", xconomyPlayerTable)
        loader.setString("xconomy_non_player_table", xconomyNonPlayerTable)
        loader.setString("xconomy_record_table", xconomyRecordTable)
        loader.setString("xconomy_login_table", xconomyLoginTable)
        loader.setString("xconomy_system_account", xconomySystemAccount)
        loader.setBool("xconomy_write_record", xconomyWriteRecord)
    }
}

