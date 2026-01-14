package asagiribeta.serverMarket.commandHandler.command

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.model.SellResult
import asagiribeta.serverMarket.util.ItemKey
import asagiribeta.serverMarket.util.PermissionUtil
import asagiribeta.serverMarket.util.whenCompleteOnServerThread
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.registry.Registries
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text

class MSell {
    // 构建 /svm restock 子命令（原 /svm sell）
    fun buildSubCommand(): LiteralArgumentBuilder<ServerCommandSource> {
        return literal("restock")
            .requires(PermissionUtil.requirePlayer("servermarket.command.restock", 0))
            .then(argument("quantity", IntegerArgumentType.integer(1))
                .executes(this::execute)
            )
    }

    private fun execute(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: run {
            context.source.sendError(Text.translatable("servermarket.command.mrestock.player_only"))
            return 0
        }
        val quantity = IntegerArgumentType.getInteger(context, "quantity")
        val mainHandStack = player.mainHandStack
        if (mainHandStack.isEmpty) {
            context.source.sendError(Text.translatable("servermarket.command.mrestock.hold_item"))
            return 0
        }

        val itemName = mainHandStack.name.string
        val itemId = Registries.ITEM.getId(mainHandStack.item).toString()
        val snbt = ItemKey.normalizeSnbt(ItemKey.snbtOf(mainHandStack))

        // Check inventory for sufficient items
        val allStacks = (0 until player.inventory.size()).map { player.inventory.getStack(it) }.filter {
            !it.isEmpty &&
                Registries.ITEM.getId(it.item).toString() == itemId &&
                ItemKey.normalizeSnbt(ItemKey.snbtOf(it)) == snbt
        }

        val totalAvailable = allStacks.sumOf { it.count }
        if (totalAvailable < quantity) {
            context.source.sendError(Text.translatable("servermarket.command.mrestock.insufficient_items", quantity))
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
        ).whenCompleteOnServerThread(context.source.server) { result, ex ->
            if (ex != null) {
                context.source.sendError(Text.translatable("servermarket.command.mrestock.operation_failed"))
                ServerMarket.LOGGER.error("/svm restock failed", ex)
                return@whenCompleteOnServerThread
            }

            when (result) {
                null -> {
                    context.source.sendError(Text.translatable("servermarket.command.mrestock.operation_failed"))
                }
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
                        Text.translatable(
                            "servermarket.command.mrestock.success",
                            quantity,
                            itemName
                        )
                    )
                }
                SellResult.InvalidPrice -> {
                    context.source.sendError(Text.translatable("servermarket.command.mrestock.not_listed"))
                }
                is SellResult.Error -> {
                    context.source.sendError(Text.translatable("servermarket.command.mrestock.operation_failed"))
                    ServerMarket.LOGGER.error("/svm restock error: {}", result.message)
                }
                else -> {
                    context.source.sendError(Text.translatable("servermarket.command.mrestock.operation_failed"))
                }
            }
        }

        return 1
    }
}
