package asagiribeta.serverMarket

import asagiribeta.serverMarket.commandHandler.Command
import asagiribeta.serverMarket.repository.Database
import asagiribeta.serverMarket.util.Language
import asagiribeta.serverMarket.util.Config
import asagiribeta.serverMarket.config.ConfigManager
import asagiribeta.serverMarket.service.MarketService
import asagiribeta.serverMarket.service.CurrencyService
import asagiribeta.serverMarket.service.TransferService
import asagiribeta.serverMarket.service.PurchaseService
import asagiribeta.serverMarket.service.ParcelService
import asagiribeta.serverMarket.integration.PlaceholderIntegration
import asagiribeta.serverMarket.api.ServerMarketApiProvider
import asagiribeta.serverMarket.api.internal.ServerMarketApiImpl
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.MinecraftServer
import org.slf4j.LoggerFactory
import org.slf4j.Logger

class ServerMarket : ModInitializer {
    internal lateinit var configManager: ConfigManager
    internal lateinit var database: Database
    internal lateinit var marketService: MarketService
    internal lateinit var currencyService: CurrencyService
    internal lateinit var transferService: TransferService
    internal lateinit var purchaseService: PurchaseService
    internal lateinit var parcelService: ParcelService

    private val command = Command()
    internal var server: MinecraftServer? = null

    companion object {
        val LOGGER: Logger = LoggerFactory.getLogger(ServerMarket::class.java)
        lateinit var instance: ServerMarket
    }

    override fun onInitialize() {
        instance = this

        // 1. 初始化配置管理器
        configManager = ConfigManager()
        LOGGER.info("Configuration loaded")

        // 2. 兼容旧代码：同步配置到 Config object
        Config.reloadConfig()

        // 3. 初始化数据库（根据 storage_type 创建 SQLite 或 MySQL 连接）
        database = Database()
        LOGGER.info("Database initialized using storage_type={} (MySQL={})", Config.storageType, database.isMySQL)

        // 4. 初始化业务服务层
        marketService = MarketService(database)
        currencyService = CurrencyService(database)
        transferService = TransferService(database)
        purchaseService = PurchaseService(database)
        parcelService = ParcelService(database)
        LOGGER.info("Business services initialized")

        // Public API for other mods
        ServerMarketApiProvider.set(ServerMarketApiImpl(this))

        // 记录服务器引用
        ServerLifecycleEvents.SERVER_STARTING.register { srv -> server = srv }
        ServerLifecycleEvents.SERVER_STOPPED.register { srv -> if (server === srv) server = null }

        // 初始化语言系统
        LOGGER.info("Initializing language system, current language: {}", Language.getCurrentLanguage())
        
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            val player = handler.player
            val uuid = player.uuid
            val name = player.gameProfile.name // 获取玩家名
            // Avoid blocking IO on main thread
            database.runAsync {
                val exists = database.playerExists(uuid)
                if (!exists) {
                    database.initializeBalance(uuid, name, Config.initialPlayerBalance)
                } else {
                    database.upsertPlayerName(uuid, name)
                }
            }.exceptionally { e ->
                LOGGER.error("Failed to initialize player data on join. uuid={} name={}", uuid, name, e)
                null
            }
        }

        // 注册服务器停止事件关闭数据库连接
        ServerLifecycleEvents.SERVER_STOPPING.register {
            database.close()
            LOGGER.info("Database connection closed")
        }

        // 注册玩家离线事件保存数据（异步）
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            val uuid = handler.player.uuid
            database.runAsync { database.syncSave(uuid) }.exceptionally { e ->
                LOGGER.warn("Failed to save player data on disconnect. uuid={}", uuid, e)
                null
            }
        }

        // Placeholder API integration
        try {
            PlaceholderIntegration.register()
            LOGGER.info("Placeholder API integration enabled")
        } catch (t: Throwable) {
            LOGGER.warn("Placeholder API integration failed to initialize", t)
        }

        // 注册命令 - 所有命令现在都在 /svm 下
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            command.register(dispatcher)
        }
    }
}
