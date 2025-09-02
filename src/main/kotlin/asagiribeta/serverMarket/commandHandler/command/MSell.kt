package asagiribeta.serverMarket.commandHandler.command

import asagiribeta.serverMarket.util.Language
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.CommandManager.argument
import com.mojang.brigadier.context.CommandContext
import net.minecraft.registry.Registries
import asagiribeta.serverMarket.ServerMarket
import net.minecraft.text.Text

class MSell {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            literal("msell")
                .then(argument("quantity", IntegerArgumentType.integer(1))
                    .executes(this::execute)
                )
        )
    }

    private fun execute(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: run {
            context.source.sendError(Text.literal(Language.get("command.msell.player_only")))
            return 0
        }
        val quantity = IntegerArgumentType.getInteger(context, "quantity")
        val mainHandStack = player.mainHandStack
        
        // 提前获取物品名称
        val itemName = mainHandStack.name.string  // 在扣除前获取物品名称
        
        // 获取所有同类物品堆栈
        val itemId = Registries.ITEM.getId(mainHandStack.item).toString()
        
        // 提前检查物品是否已上架
        val marketRepo = ServerMarket.instance.database.marketRepository
        val uuid = player.uuid
        if (!marketRepo.hasPlayerItem(uuid, itemId)) {
            context.source.sendError(Text.literal(Language.get("command.msell.not_listed")))
            return 0
        }

        // 物品栏访问
        val allStacks = (0 until player.inventory.size()).map { player.inventory.getStack(it) }.filter { 
            !it.isEmpty && Registries.ITEM.getId(it.item).toString() == itemId 
        }

        // 检查总数量是否足够
        val totalAvailable = allStacks.sumOf { it.count }
        if (totalAvailable < quantity) {
            context.source.sendError(Text.literal(Language.get("command.msell.insufficient_items", quantity)))
            return 0
        }

        // 依次扣除物品数量（优先扣除主手物品）
        var remaining = quantity
        for (stack in allStacks) {
            if (remaining <= 0) break
            val deduct = minOf(remaining, stack.count)
            stack.count -= deduct
            remaining -= deduct
        }

        try {
            marketRepo.incrementPlayerItemQuantity(uuid, itemId, quantity)
            context.source.sendMessage(
                Text.literal(Language.get("command.msell.success", quantity, itemName))
            )
            return 1
        } catch (e: Exception) {
            context.source.sendError(Text.literal(Language.get("command.msell.operation_failed")))
            ServerMarket.LOGGER.error("msell命令执行失败", e)
            return 0
        }
    }
}
