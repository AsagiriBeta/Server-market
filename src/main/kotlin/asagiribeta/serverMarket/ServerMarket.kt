package asagiribeta.serverMarket

import asagiribeta.serverMarket.commandHandler.adminCommand.AdminCommand
import asagiribeta.serverMarket.commandHandler.command.Command
import asagiribeta.serverMarket.repository.Database
import asagiribeta.serverMarket.util.Language
import asagiribeta.serverMarket.util.Config
import asagiribeta.serverMarket.config.ConfigManager
import asagiribeta.serverMarket.service.MarketService
import asagiribeta.serverMarket.service.CurrencyService
import asagiribeta.serverMarket.service.TransferService
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

    private val command = Command()
    private val adminCommand = AdminCommand()
    // 新增：保存当前运行中的服务器引用（1.21+ 用于 ItemStack.CODEC 序列化组件）
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
        LOGGER.info("Business services initialized")

        // 记录服务器引用
        ServerLifecycleEvents.SERVER_STARTING.register { srv -> server = srv }
        ServerLifecycleEvents.SERVER_STOPPED.register { srv -> if (server === srv) server = null }

        // 初始化语言系统
        LOGGER.info("Initializing language system, current language: {}", Language.getCurrentLanguage())
        
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            val player = handler.player
            val uuid = player.uuid
            val name = player.gameProfile.name // 获取玩家名
            // 切换为异步：避免在主线程做阻塞 IO
            database.playerExistsAsync(uuid)
                .thenCompose { exists: Boolean ->
                    if (!exists) {
                        database.initializeBalanceAsync(uuid, name, Config.initialPlayerBalance)
                    } else {
                        database.upsertPlayerNameAsync(uuid, name)
                    }
                }
                .exceptionally { e ->
                    LOGGER.error("玩家进服时数据库初始化失败 UUID: {} Name: {}", uuid, name, e)
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
            database.syncSaveAsync(uuid).exceptionally { e ->
                LOGGER.warn("玩家离线时保存数据失败 UUID: {}", uuid, e)
                null
            }
        }

        // 注册命令
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            command.register(dispatcher)
            adminCommand.register(dispatcher) // 注册管理员命令
        }
    }
}
