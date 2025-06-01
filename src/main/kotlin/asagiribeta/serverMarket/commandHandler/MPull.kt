package asagiribeta.serverMarket.commandHandler

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.text.Text
import asagiribeta.serverMarket.ServerMarket
import net.minecraft.registry.Registries

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
            source.sendError(Text.literal("只有玩家可以执行此命令"))
            return 0
        }

        val itemStack = player.mainHandStack
        if (itemStack.isEmpty) {
            source.sendError(Text.literal("请手持要下架的物品"))
            return 0
        }

        try {
            // 修复：改用与msell/mprice一致的物品ID获取方式
            val itemId = Registries.ITEM.getId(itemStack.item).toString()
            val marketRepo = ServerMarket.instance.database.marketRepository
            
            // 新增：增加调试日志辅助排查问题
            ServerMarket.LOGGER.debug("尝试下架物品ID: {} 玩家UUID: {}", itemId, player.uuid)
            
            if (marketRepo.hasPlayerItem(player.uuid, itemId)) {
                // 获取下架前的库存数量并删除记录
                val returnedQuantity = marketRepo.removePlayerItem(player.uuid, itemId)
                
                // 返还物品给玩家
                if (returnedQuantity > 0) {
                    val returnStack = itemStack.copy().apply { count = returnedQuantity }
                    player.giveItemStack(returnStack)
                }
                
                source.sendMessage(Text.literal("成功下架 ${itemStack.name.string}（返还 $returnedQuantity 个）"))
                return 1
            }
            source.sendError(Text.literal("该物品未上架"))
            return 0
        } catch (e: Exception) {
            source.sendError(Text.literal("操作失败"))
            ServerMarket.LOGGER.error("mpull命令执行失败", e)
            return 0
        }
    }
}