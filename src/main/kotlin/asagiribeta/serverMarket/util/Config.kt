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

    init {
        loadConfig()
    }

    private fun loadConfig() {
        try {
            if (configFile.exists()) {
                configFile.inputStream().use { properties.load(it) }

                maxTransferAmount = properties.getProperty("max_transfer_amount", "1000000.0").toDouble()
                initialPlayerBalance = properties.getProperty("initial_player_balance", "100.0").toDouble()
                enableTransactionHistory = properties.getProperty("enable_transaction_history", "true").toBoolean()
                maxHistoryRecords = properties.getProperty("max_history_records", "10000").toInt()
                enableDebugLogging = properties.getProperty("enable_debug_logging", "false").toBoolean()
                marketTaxRate = properties.getProperty("market_tax_rate", "0.05").toDouble()
                enableTax = properties.getProperty("enable_tax", "false").toBoolean()

                ServerMarket.LOGGER.info("Configuration loaded successfully")
            } else {
                saveConfig()
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
