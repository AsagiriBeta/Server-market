package asagiribeta.serverMarket.commandHandler

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
            context.source.sendError(Text.literal("该命令只能由玩家执行"))
            return 0
        }
        val quantity = IntegerArgumentType.getInteger(context, "quantity")
        val mainHandStack = player.mainHandStack
        
        // 新增：提前获取物品名称
        val itemName = mainHandStack.name.string  // 在扣除前获取物品名称
        
        // 新增：获取所有同类物品堆栈
        val itemId = Registries.ITEM.getId(mainHandStack.item).toString()
        
        // 新增：提前检查物品是否已上架
        val marketRepo = ServerMarket.instance.database.marketRepository
        val uuid = player.uuid
        if (!marketRepo.hasPlayerItem(uuid, itemId)) {  // 移动检查到物品扣除之前
            context.source.sendError(Text.literal("该物品尚未在您的店铺上架"))
            return 0
        }

        // 修改：使用正确的物品栏访问方式
        val allStacks = (0 until player.inventory.size()).map { player.inventory.getStack(it) }.filter { 
            !it.isEmpty && Registries.ITEM.getId(it.item).toString() == itemId 
        }

        // 修改：检查总数量是否足够
        val totalAvailable = allStacks.sumOf { it.count }
        if (totalAvailable < quantity) {
            context.source.sendError(Text.literal("物品总数量不足（需要 $quantity 个）"))
            return 0
        }

        // 修改：依次扣除物品数量（优先扣除主手物品）
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
                Text.literal("成功补货 $quantity 个 $itemName")  // 使用预先保存的物品名称
            )
            return 1
        } catch (e: Exception) {
            context.source.sendError(Text.literal("补货失败"))
            ServerMarket.LOGGER.error("msell命令执行失败", e)
            return 0
        }
    }
}