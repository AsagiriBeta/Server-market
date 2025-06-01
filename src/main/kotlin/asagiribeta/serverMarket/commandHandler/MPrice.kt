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
            source.sendError(Text.literal("只有玩家可以执行此命令"))
            return 0
        }

        val price = DoubleArgumentType.getDouble(context, "price")
        val itemStack = player.mainHandStack
        if (itemStack.isEmpty) {
            source.sendError(Text.literal("请手持要上架的物品"))
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
                source.sendMessage(Text.literal("成功上架 ${itemStack.name.string} 单价为 $price"))
            } else {
                marketRepo.updatePlayerItemPrice(player.uuid, itemId, price)
                source.sendMessage(Text.literal("成功更新 ${itemStack.name.string} 单价为 $price"))
            }
            1
        } catch (e: Exception) {
            source.sendError(Text.literal("操作失败"))
            ServerMarket.LOGGER.error("mprice命令执行失败", e)
            0
        }
    }
}