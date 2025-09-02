package asagiribeta.serverMarket.commandHandler.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.text.Text
import asagiribeta.serverMarket.ServerMarket
import net.minecraft.registry.Registries
import asagiribeta.serverMarket.util.Language

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

        try {
            val itemId = Registries.ITEM.getId(itemStack.item).toString()
            val marketRepo = ServerMarket.instance.database.marketRepository
            
            ServerMarket.LOGGER.debug("尝试下架物品ID: {} 玩家UUID: {}", itemId, player.uuid)
            
            if (marketRepo.hasPlayerItem(player.uuid, itemId)) {
                // 获取下架前的库存数量并删除记录
                val returnedQuantity = marketRepo.removePlayerItem(player.uuid, itemId)
                
                // 返还物品给玩家
                if (returnedQuantity > 0) {
                    val returnStack = itemStack.copy().apply { count = returnedQuantity }
                    player.giveItemStack(returnStack)
                }
                
                source.sendMessage(Text.literal(Language.get("command.mpull.success", itemStack.name.string, returnedQuantity)))
                return 1
            }
            source.sendError(Text.literal(Language.get("command.mpull.not_listed")))
            return 0
        } catch (e: Exception) {
            source.sendError(Text.literal(Language.get("command.mpull.operation_failed")))
            ServerMarket.LOGGER.error("mpull命令执行失败", e)
            return 0
        }
    }
}
