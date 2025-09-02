package asagiribeta.serverMarket.commandHandler.adminCommand

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.server.command.ServerCommandSource

class AdminCommand {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        // 子命令模块化注册
        MSet().register(dispatcher)
        APrice().register(dispatcher)
        APull().register(dispatcher)
        MLang().register(dispatcher)
        MReload().register(dispatcher)
        ACash().register(dispatcher)
    }
}