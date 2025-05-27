package asagiribeta.serverMarket

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.network.ServerPlayNetworkHandler
import org.slf4j.LoggerFactory
import java.util.*

class ServerMarket : ModInitializer {
    // 修改数据库实例可见性为internal（模块内可见）
    internal val database = Database()
    private val command = Command()

    companion object {
        val LOGGER = LoggerFactory.getLogger(ServerMarket::class.java)
        
        // 新增伴生对象实例用于访问数据库（可选方案）
        // internal lateinit var instance: ServerMarket
    }

    override fun onInitialize() {
        // 参数调整为标准的三个参数格式，并通过handler获取player实体
        ServerPlayConnectionEvents.JOIN.register { handler: ServerPlayNetworkHandler, _, _ ->
            val player = handler.player
            val uuid = player.uuid
            // 改为先检查玩家是否存在数据库记录，再进行初始化
            if (!database.playerExists(uuid)) {
                database.initializeBalance(uuid, 100.0) // 使用专用初始化方法
                LOGGER.info("初始化新玩家余额 UUID: $uuid")
            }
        }

        // 注册服务器停止事件关闭数据库连接
        ServerLifecycleEvents.SERVER_STOPPING.register {
            database.close()
            LOGGER.info("Database connection closed")
        }

        // 注册玩家离线事件保存数据
        ServerPlayConnectionEvents.DISCONNECT.register { handler: ServerPlayNetworkHandler, _ ->
            val uuid = handler.player.uuid
            database.syncSave(uuid)
        }

        // 注册命令
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            command.register(dispatcher)
        }
    }
}