package asagiribeta.serverMarket.commandHandler

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.CommandManager.argument
import com.mojang.brigadier.arguments.DoubleArgumentType
import net.minecraft.text.Text
import asagiribeta.serverMarket.ServerMarket
import net.minecraft.registry.Registries
import asagiribeta.serverMarket.util.Language

class MPrice {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            literal("mprice")
                .then(argument("price", DoubleArgumentType.doubleArg(0.0))
                    .executes(this::execute)
                )
        )
    }

    internal fun execute(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val player = source.player ?: run {
            source.sendError(Text.literal(Language.get("command.mprice.player_only")))
            return 0
        }

        val price = DoubleArgumentType.getDouble(context, "price")
        val itemStack = player.mainHandStack
        if (itemStack.isEmpty) {
            source.sendError(Text.literal(Language.get("command.mprice.hold_item")))
            return 0
        }

        return try {
            val itemId = Registries.ITEM.getId(itemStack.item).toString()
            val marketRepo = ServerMarket.instance.database.marketRepository
            
            if (!marketRepo.hasPlayerItem(player.uuid, itemId)) {
                marketRepo.addPlayerItem(
                    sellerUuid = player.uuid,
                    sellerName = player.name.string,
                    itemId = itemId,
                    price = price
                )
                source.sendMessage(Text.literal(Language.get("command.mprice.add_success", itemStack.name.string, price)))
            } else {
                marketRepo.updatePlayerItemPrice(player.uuid, itemId, price)
                source.sendMessage(Text.literal(Language.get("command.mprice.update_success", itemStack.name.string, price)))
            }
            1
        } catch (e: Exception) {
            source.sendError(Text.literal(Language.get("command.mprice.operation_failed")))
            ServerMarket.LOGGER.error("mprice命令执行失败", e)
            0
        }
    }
}
