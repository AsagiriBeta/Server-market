package asagiribeta.serverMarket.util

import asagiribeta.serverMarket.ServerMarket
import java.io.File
import java.util.Properties

object Config {
    private val configFile = File("config/server-market/config.properties")
    private val properties = Properties()

    // 默认配置值
    var maxTransferAmount: Double = 1000000.0
        private set
    var initialPlayerBalance: Double = 100.0
        private set
    var enableTransactionHistory: Boolean = true
        private set
    var maxHistoryRecords: Int = 10000
        private set
    var enableDebugLogging: Boolean = false
        private set
    var marketTaxRate: Double = 0.05
        private set
    var enableTax: Boolean = false
        private set

    // 新增：存储类型（sqlite / mysql）
    var storageType: String = "sqlite"
        private set

    // 新增：MySQL 连接配置
    var mysqlHost: String = "localhost"
        private set
    var mysqlPort: Int = 3306
        private set
    var mysqlDatabase: String = "server_market"
        private set
    var mysqlUser: String = "root"
        private set
    var mysqlPassword: String = ""
        private set
    var mysqlUseSSL: Boolean = false
        private set
    var mysqlJdbcParams: String = ""
        private set

    init {
        loadConfig()
    }

    private fun loadConfig() {
        try {
            if (configFile.exists()) {
                configFile.inputStream().use { properties.load(it) }

                var changed = false

                fun doubleKey(key: String, default: Double): Double {
                    val raw = properties.getProperty(key)
                    if (raw == null) {
                        properties.setProperty(key, default.toString())
                        changed = true
                        return default
                    }
                    val v = raw.toDoubleOrNull()
                    return if (v == null) {
                        ServerMarket.LOGGER.warn("Config key '{}' value '{}' 非法，使用默认 {}", key, raw, default)
                        properties.setProperty(key, default.toString())
                        changed = true
                        default
                    } else v
                }
                fun intKey(key: String, default: Int): Int {
                    val raw = properties.getProperty(key)
                    if (raw == null) {
                        properties.setProperty(key, default.toString())
                        changed = true
                        return default
                    }
                    val v = raw.toIntOrNull()
                    return if (v == null) {
                        ServerMarket.LOGGER.warn("Config key '{}' value '{}' 非法，使用默认 {}", key, raw, default)
                        properties.setProperty(key, default.toString())
                        changed = true
                        default
                    } else v
                }
                fun boolKey(key: String, default: Boolean): Boolean {
                    val raw = properties.getProperty(key)
                    if (raw == null) {
                        properties.setProperty(key, default.toString())
                        changed = true
                        return default
                    }
                    return when (raw.lowercase()) {
                        "true", "false" -> raw.toBoolean()
                        else -> {
                            ServerMarket.LOGGER.warn("Config key '{}' value '{}' 非法，使用默认 {}", key, raw, default)
                            properties.setProperty(key, default.toString())
                            changed = true
                            default
                        }
                    }
                }
                fun stringKey(key: String, default: String): String {
                    val raw = properties.getProperty(key)
                    return if (raw == null) {
                        properties.setProperty(key, default)
                        changed = true
                        default
                    } else raw
                }

                maxTransferAmount = doubleKey("max_transfer_amount", maxTransferAmount)
                initialPlayerBalance = doubleKey("initial_player_balance", initialPlayerBalance)
                enableTransactionHistory = boolKey("enable_transaction_history", enableTransactionHistory)
                maxHistoryRecords = intKey("max_history_records", maxHistoryRecords)
                enableDebugLogging = boolKey("enable_debug_logging", enableDebugLogging)
                marketTaxRate = doubleKey("market_tax_rate", marketTaxRate)
                enableTax = boolKey("enable_tax", enableTax)
                storageType = stringKey("storage_type", storageType)

                mysqlHost = stringKey("mysql_host", mysqlHost)
                mysqlPort = intKey("mysql_port", mysqlPort)
                mysqlDatabase = stringKey("mysql_database", mysqlDatabase)
                mysqlUser = stringKey("mysql_user", mysqlUser)
                mysqlPassword = stringKey("mysql_password", mysqlPassword)
                mysqlUseSSL = boolKey("mysql_use_ssl", mysqlUseSSL)
                mysqlJdbcParams = stringKey("mysql_jdbc_params", mysqlJdbcParams)

                // 如果发现缺失或非��的键，写回文件补全
                if (changed) {
                    saveConfig() // saveConfig 会基于当前字段重新写入全量键值
                    ServerMarket.LOGGER.info("配置文件已补全缺失/修复非法项并保存")
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

            properties.setProperty("max_transfer_amount", maxTransferAmount.toString())
            properties.setProperty("initial_player_balance", initialPlayerBalance.toString())
            properties.setProperty("enable_transaction_history", enableTransactionHistory.toString())
            properties.setProperty("max_history_records", maxHistoryRecords.toString())
            properties.setProperty("enable_debug_logging", enableDebugLogging.toString())
            properties.setProperty("market_tax_rate", marketTaxRate.toString())
            properties.setProperty("enable_tax", enableTax.toString())
            properties.setProperty("storage_type", storageType)

            // MySQL 相关
            properties.setProperty("mysql_host", mysqlHost)
            properties.setProperty("mysql_port", mysqlPort.toString())
            properties.setProperty("mysql_database", mysqlDatabase)
            properties.setProperty("mysql_user", mysqlUser)
            properties.setProperty("mysql_password", mysqlPassword)
            properties.setProperty("mysql_use_ssl", mysqlUseSSL.toString())
            properties.setProperty("mysql_jdbc_params", mysqlJdbcParams)

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
