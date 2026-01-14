package asagiribeta.serverMarket.config

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.util.PropertyLoader
import java.io.File
import java.util.Properties

/**
 * 配置管理器（可依赖注入版本）
 *
 * 优势：
 * - 支持依赖注入，便于单元测试
 * - 解耦配置加载逻辑
 * - 支持热重载
 */
@Suppress("unused")
class ConfigManager(private val configFile: File = File("config/server-market/config.properties")) {

    private val properties = Properties()
    private val config = ConfigProperties()

    // 通过委托访问配置属性
    val maxTransferAmount: Double get() = config.maxTransferAmount
    val initialPlayerBalance: Double get() = config.initialPlayerBalance
    val enableTransactionHistory: Boolean get() = config.enableTransactionHistory
    val maxHistoryRecords: Int get() = config.maxHistoryRecords
    val enableDebugLogging: Boolean get() = config.enableDebugLogging
    val marketTaxRate: Double get() = config.marketTaxRate
    val enableTax: Boolean get() = config.enableTax
    val storageType: String get() = config.storageType
    val sqlitePath: String get() = config.sqlitePath
    val mysqlHost: String get() = config.mysqlHost
    val mysqlPort: Int get() = config.mysqlPort
    val mysqlDatabase: String get() = config.mysqlDatabase
    val mysqlUser: String get() = config.mysqlUser
    val mysqlPassword: String get() = config.mysqlPassword
    val mysqlUseSSL: Boolean get() = config.mysqlUseSSL
    val mysqlJdbcParams: String get() = config.mysqlJdbcParams
    val xconomyPlayerTable: String get() = config.xconomyPlayerTable
    val xconomyNonPlayerTable: String get() = config.xconomyNonPlayerTable
    val xconomyRecordTable: String get() = config.xconomyRecordTable
    val xconomyLoginTable: String get() = config.xconomyLoginTable
    val xconomySystemAccount: String get() = config.xconomySystemAccount
    val xconomyWriteRecord: Boolean get() = config.xconomyWriteRecord

    init {
        reload()
    }

    /**
     * 重新加载配置文件
     */
    fun reload() {
        loadConfig()
    }

    private fun loadConfig() {
        try {
            if (configFile.exists()) {
                configFile.inputStream().use { properties.load(it) }

                val loader = PropertyLoader(properties)
                config.loadFrom(loader)

                if (loader.changed) {
                    saveConfig()
                }
            } else {
                // 首次创建配置文件
                configFile.parentFile?.mkdirs()
                setDefaults()
                saveConfig()
            }

            ServerMarket.LOGGER.info("Configuration loaded successfully")
        } catch (e: Exception) {
            ServerMarket.LOGGER.error("Failed to load configuration", e)
            setDefaults()
        }
    }

    private fun setDefaults() {
        val loader = PropertyLoader(properties)
        config.saveTo(loader)
    }

    private fun saveConfig() {
        try {
            val loader = PropertyLoader(properties)
            config.saveTo(loader)

            configFile.outputStream().use {
                properties.store(it, "Server Market Configuration")
            }
        } catch (e: Exception) {
            ServerMarket.LOGGER.error("Failed to save configuration", e)
        }
    }
}
