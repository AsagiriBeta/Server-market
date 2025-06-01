package asagiribeta.serverMarket

import asagiribeta.serverMarket.commandHandler.AdminCommand
import asagiribeta.serverMarket.commandHandler.Command
import asagiribeta.serverMarket.repository.Database
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import org.slf4j.LoggerFactory
import org.slf4j.Logger

class ServerMarket : ModInitializer {
    internal val database = Database()
    private val command = Command()
    private val adminCommand = AdminCommand()

    companion object {
        val LOGGER: Logger = LoggerFactory.getLogger(ServerMarket::class.java)
        lateinit var instance: ServerMarket
    }

    override fun onInitialize() {
        instance = this
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            val player = handler.player
            val uuid = player.uuid
            if (!database.playerExists(uuid)) {
                database.initializeBalance(uuid, 100.0)
                LOGGER.info("初始化新玩家余额 UUID: $uuid")
            }
        }

        // 注册服务器停止事件关闭数据库连接
        ServerLifecycleEvents.SERVER_STOPPING.register {
            database.close()
            LOGGER.info("Database connection closed")
        }

        // 注册玩家离线事件保存数据
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            val uuid = handler.player.uuid
            database.syncSave(uuid)
        }

        // 注册命令
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            command.register(dispatcher)
            adminCommand.register(dispatcher) // 注册管理员命令
        }
    }
}