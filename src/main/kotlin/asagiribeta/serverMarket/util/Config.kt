package asagiribeta.serverMarket.util

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.config.ConfigProperties
import java.io.File
import java.util.Properties

object Config {
    private val configFile = File("config/server-market/config.properties")
    private val properties = Properties()
    private val config = ConfigProperties()

    // 通过委托访问配置属性
    val maxTransferAmount: Double get() = config.maxTransferAmount
    val initialPlayerBalance: Double get() = config.initialPlayerBalance
    @Suppress("unused")
    val enableTransactionHistory: Boolean get() = config.enableTransactionHistory
    @Suppress("unused")
    val maxHistoryRecords: Int get() = config.maxHistoryRecords
    @Suppress("unused")
    val enableDebugLogging: Boolean get() = config.enableDebugLogging
    @Suppress("unused")
    val marketTaxRate: Double get() = config.marketTaxRate
    @Suppress("unused")
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
        loadConfig()
    }

    private fun loadConfig() {
        try {
            if (configFile.exists()) {
                configFile.inputStream().use { properties.load(it) }

                val loader = PropertyLoader(properties)
                config.loadFrom(loader)

                // 如果发现缺失或非法的键，写回文件补全
                if (loader.changed) {
                    saveConfig()
                    ServerMarket.LOGGER.info("Configuration file was updated (missing/invalid keys fixed and saved)")
                } else {
                    ServerMarket.LOGGER.info("Configuration loaded successfully (storage: {})", storageType)
                }
            } else {
                saveConfig() // 写出默认
            }
        } catch (e: Exception) {
            ServerMarket.LOGGER.error("Failed to load configuration, using defaults", e)
            saveConfig()
        }
    }

    private fun saveConfig() {
        try {
            val dataDir = configFile.parentFile
            if (!dataDir.exists()) {
                dataDir.mkdirs()
            }

            val loader = PropertyLoader(properties)
            config.saveTo(loader)

            configFile.outputStream().use {
                properties.store(it, "ServerMarket Configuration File")
            }
            ServerMarket.LOGGER.info("Configuration saved")
        } catch (e: Exception) {
            ServerMarket.LOGGER.error("Failed to save configuration", e)
        }
    }

    fun reloadConfig() {
        loadConfig()
    }
}
