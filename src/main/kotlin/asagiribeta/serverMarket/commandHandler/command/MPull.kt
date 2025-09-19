package asagiribeta.serverMarket.commandHandler.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.text.Text
import asagiribeta.serverMarket.ServerMarket
import net.minecraft.registry.Registries
import asagiribeta.serverMarket.util.Language
import asagiribeta.serverMarket.util.ItemKey

class MPull {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            literal("mpull")
                .executes(this::execute)
        )
    }

    fun execute(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val player = source.player ?: run {
            source.sendError(Text.literal(Language.get("command.mpull.player_only")))
            return 0
        }

        val itemStack = player.mainHandStack
        if (itemStack.isEmpty) {
            source.sendError(Text.literal(Language.get("command.mpull.hold_item")))
            return 0
        }

        val itemId = Registries.ITEM.getId(itemStack.item).toString()
        val nbt = ItemKey.snbtOf(itemStack)
        val db = ServerMarket.instance.database
        val repo = db.marketRepository

        db.supplyAsync {
            if (repo.hasPlayerItem(player.uuid, itemId, nbt)) repo.removePlayerItem(player.uuid, itemId, nbt) else -1
        }.whenComplete { returnedQuantity, ex ->
            source.server.execute {
                if (ex != null) {
                    source.sendError(Text.literal(Language.get("command.mpull.operation_failed")))
                    ServerMarket.LOGGER.error("mpull命令执行失败", ex)
                    return@execute
                }
                if (returnedQuantity == null || returnedQuantity < 0) {
                    source.sendError(Text.literal(Language.get("command.mpull.not_listed")))
                    return@execute
                }
                if (returnedQuantity > 0) {
                    val returnStack = itemStack.copy().apply { count = returnedQuantity }
                    player.giveItemStack(returnStack)
                }
                source.sendMessage(Text.literal(Language.get("command.mpull.success", itemStack.name.string, returnedQuantity)))
            }
        }
        return 1
    }
}
