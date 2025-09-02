package asagiribeta.serverMarket.commandHandler.adminCommand

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.util.Language
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.minecraft.registry.Registries
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text

class APull {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            CommandManager.literal("apull")
                .requires { it.hasPermissionLevel(4) }
                .executes(this::execute)
        )
    }

    private fun execute(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val player = source.player ?: run {
            source.sendError(Text.literal(Language.get("command.apull.player_only")))
            return 0
        }

        val itemStack = player.mainHandStack
        if (itemStack.isEmpty) {
            source.sendError(Text.literal(Language.get("command.apull.hold_item")))
            return 0
        }

        val itemId = Registries.ITEM.getId(itemStack.item).toString()
        val marketRepo = ServerMarket.instance.database.marketRepository
        return try {
            if (!marketRepo.hasSystemItem(itemId)) {
                source.sendError(Text.literal(Language.get("command.apull.not_listed")))
                0
            } else {
                marketRepo.removeSystemItem(itemId)
                source.sendMessage(Text.literal(Language.get("command.apull.success", itemStack.name.string)))
                1
            }
        } catch (e: Exception) {
            source.sendError(Text.literal(Language.get("command.apull.operation_failed")))
            ServerMarket.LOGGER.error("apull命令执行失败", e)
            0
        }
    }
}

