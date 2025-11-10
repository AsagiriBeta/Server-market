package asagiribeta.serverMarket.commandHandler.command

import asagiribeta.serverMarket.util.Language
import asagiribeta.serverMarket.model.SellResult
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.CommandManager.argument
import com.mojang.brigadier.context.CommandContext
import net.minecraft.registry.Registries
import asagiribeta.serverMarket.ServerMarket
import net.minecraft.text.Text
import asagiribeta.serverMarket.util.ItemKey
import asagiribeta.serverMarket.util.PermissionUtil

class MSell {
    // 构建 /svm sell 子命令
    fun buildSubCommand(): LiteralArgumentBuilder<ServerCommandSource> {
        return literal("sell")
            .requires(PermissionUtil.requirePlayer("servermarket.command.sell", 0))
            .then(argument("quantity", IntegerArgumentType.integer(1))
                .executes(this::execute)
            )
    }

    private fun execute(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: run {
            context.source.sendError(Text.literal(Language.get("command.msell.player_only")))
            return 0
        }
        val quantity = IntegerArgumentType.getInteger(context, "quantity")
        val mainHandStack = player.mainHandStack
        if (mainHandStack.isEmpty) {
            context.source.sendError(Text.literal(Language.get("command.msell.hold_item")))
            return 0
        }

        val itemName = mainHandStack.name.string
        val itemId = Registries.ITEM.getId(mainHandStack.item).toString()
        val snbt = ItemKey.snbtOf(mainHandStack)

        // Check inventory for sufficient items
        val allStacks = (0 until player.inventory.size()).map { player.inventory.getStack(it) }.filter {
            !it.isEmpty && Registries.ITEM.getId(it.item).toString() == itemId && ItemKey.snbtOf(it) == snbt
        }

        val totalAvailable = allStacks.sumOf { it.count }
        if (totalAvailable < quantity) {
            context.source.sendError(Text.literal(Language.get("command.msell.insufficient_items", quantity)))
            return 0
        }

        // Use MarketService to list/restock item
        ServerMarket.instance.marketService.listItemForSale(
            playerUuid = player.uuid,
            playerName = player.name.string,
            itemId = itemId,
            nbt = snbt,
            quantity = quantity,
            price = null  // Keep existing price if already listed
        ).whenComplete { result, ex ->
            context.source.server.execute {
                if (ex != null) {
                    context.source.sendError(Text.literal(Language.get("command.msell.operation_failed")))
                    ServerMarket.LOGGER.error("msell命令执行失败", ex)
                    return@execute
                }

                when (result) {
                    is SellResult.Success -> {
                        // Deduct items from inventory
                        var remaining = quantity
                        for (stack in allStacks) {
                            if (remaining <= 0) break
                            val deduct = minOf(remaining, stack.count)
                            stack.decrement(deduct)
                            remaining -= deduct
                        }

                        context.source.sendMessage(
                            Text.literal(
                                Language.get("command.msell.success", quantity, itemName)
                            )
                        )
                    }
                    SellResult.InvalidPrice -> {
                        context.source.sendError(
                            Text.literal(Language.get("command.msell.not_listed"))
                        )
                    }
                    is SellResult.Error -> {
                        context.source.sendError(
                            Text.literal(Language.get("command.msell.operation_failed"))
                        )
                        ServerMarket.LOGGER.error("补货失败: ${result.message}")
                    }
                    else -> {
                        context.source.sendError(
                            Text.literal(Language.get("command.msell.operation_failed"))
                        )
                    }
                }
            }
        }
        return 1
    }
}
