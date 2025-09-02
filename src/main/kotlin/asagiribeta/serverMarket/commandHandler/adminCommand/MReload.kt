package asagiribeta.serverMarket.commandHandler.adminCommand

import asagiribeta.serverMarket.util.Config
import asagiribeta.serverMarket.util.Language
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text

class MReload {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            CommandManager.literal("mreload")
                .requires { it.hasPermissionLevel(4) }
                .executes(this::execute)
        )
    }

    private fun execute(context: CommandContext<ServerCommandSource>): Int {
        return try {
            Config.reloadConfig()
            context.source.sendMessage(Text.literal(Language.get("command.mreload.success")))
            1
        } catch (_: Exception) {
            context.source.sendError(Text.literal(Language.get("command.mreload.failed")))
            0
        }
    }
}
