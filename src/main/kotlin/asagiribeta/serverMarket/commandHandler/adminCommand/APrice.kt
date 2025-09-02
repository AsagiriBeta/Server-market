package asagiribeta.serverMarket.commandHandler.adminCommand

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.util.Language
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.registry.Registries
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text

class APrice {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            CommandManager.literal("aprice")
                .requires { it.hasPermissionLevel(4) }
                .then(
                    CommandManager.argument("price", DoubleArgumentType.doubleArg(0.0))
                        .executes(this::execute)
                )
        )
    }

    private fun execute(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val player = source.player ?: run {
            source.sendError(Text.literal(Language.get("command.aprice.player_only")))
            return 0
        }

        val itemStack = player.mainHandStack
        if (itemStack.isEmpty) {
            source.sendError(Text.literal(Language.get("command.aprice.hold_item")))
            return 0
        }

        val price = DoubleArgumentType.getDouble(context, "price")
        val itemId = Registries.ITEM.getId(itemStack.item).toString()
        val marketRepo = ServerMarket.instance.database.marketRepository
        return try {
            if (!marketRepo.hasSystemItem(itemId)) {
                marketRepo.addSystemItem(itemId, price)
                source.sendMessage(Text.literal(Language.get("command.aprice.add_success", itemStack.name.string, price)))
            } else {
                marketRepo.addSystemItem(itemId, price) // upsert 行为
                source.sendMessage(Text.literal(Language.get("command.aprice.update_success", itemStack.name.string, price)))
            }
            1
        } catch (e: Exception) {
            source.sendError(Text.literal(Language.get("command.aprice.operation_failed")))
            ServerMarket.LOGGER.error("aprice命令执行失败", e)
            0
        }
    }
}

