package asagiribeta.serverMarket.util

import asagiribeta.serverMarket.config.ConfigManager

/**
 * Read-only facade over [ConfigManager].
 *
 * All config I/O lives in [ConfigManager]; this object exists so existing call sites
 * can keep using `Config.*` without duplicating load/save logic.
 */
object Config {
  @Volatile
  private var manager: ConfigManager? = null

  internal fun bind(configManager: ConfigManager) {
    manager = configManager
  }

  private val cfg: ConfigManager
    get() = manager ?: error("Config not initialized — ServerMarket has not started yet")

  val maxTransferAmount: Double get() = cfg.maxTransferAmount
  val initialPlayerBalance: Double get() = cfg.initialPlayerBalance
  val enableTransactionHistory: Boolean get() = cfg.enableTransactionHistory
  val maxHistoryRecords: Int get() = cfg.maxHistoryRecords
  val enableDebugLogging: Boolean get() = cfg.enableDebugLogging
  val marketTaxRate: Double get() = cfg.marketTaxRate
  val enableTax: Boolean get() = cfg.enableTax
  val storageType: String get() = cfg.storageType
  val sqlitePath: String get() = cfg.sqlitePath
  val mysqlHost: String get() = cfg.mysqlHost
  val mysqlPort: Int get() = cfg.mysqlPort
  val mysqlDatabase: String get() = cfg.mysqlDatabase
  val mysqlUser: String get() = cfg.mysqlUser
  val mysqlPassword: String get() = cfg.mysqlPassword
  val mysqlUseSSL: Boolean get() = cfg.mysqlUseSSL
  val mysqlJdbcParams: String get() = cfg.mysqlJdbcParams
  val xconomyPlayerTable: String get() = cfg.xconomyPlayerTable
  val xconomyNonPlayerTable: String get() = cfg.xconomyNonPlayerTable
  val xconomyRecordTable: String get() = cfg.xconomyRecordTable
  val xconomyLoginTable: String get() = cfg.xconomyLoginTable
  val xconomySystemAccount: String get() = cfg.xconomySystemAccount
  val xconomyWriteRecord: Boolean get() = cfg.xconomyWriteRecord

  fun reloadConfig() = cfg.reload()
}
