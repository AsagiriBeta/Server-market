package asagiribeta.serverMarket.commandHandler.command

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.model.SellToBuyerResult
import asagiribeta.serverMarket.util.ItemKey
import asagiribeta.serverMarket.util.Language
import asagiribeta.serverMarket.util.PermissionUtil
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.registry.Registries
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text

/**
 * 玩家出售命令：/svm selltopurchase <quantity>
 *
 * 玩家可以向收购者出售手持物品
 */
class MSellToPurchase {
    fun buildSubCommand(): LiteralArgumentBuilder<ServerCommandSource> {
        return literal("selltopurchase")
            .requires(PermissionUtil.requirePlayer("servermarket.command.selltopurchase", 0))
            .then(
                argument("quantity", IntegerArgumentType.integer(1))
                    .executes(this::execute)
            )
    }

    private fun execute(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: run {
            context.source.sendError(Text.literal(Language.get("command.mselltopurchase.player_only")))
            return 0
        }

        val quantity = IntegerArgumentType.getInteger(context, "quantity")

        val mainHandStack = player.mainHandStack
        if (mainHandStack.isEmpty) {
            context.source.sendError(Text.literal(Language.get("command.mselltopurchase.hold_item")))
            return 0
        }

        val itemName = mainHandStack.name.string
        val itemId = Registries.ITEM.getId(mainHandStack.item).toString()
        val snbt = ItemKey.snbtOf(mainHandStack)

        // 检查物品数量
        val allStacks = (0 until player.inventory.size()).map { player.inventory.getStack(it) }.filter {
            !it.isEmpty && Registries.ITEM.getId(it.item).toString() == itemId && ItemKey.snbtOf(it) == snbt
        }

        val totalAvailable = allStacks.sumOf { it.count }
        if (totalAvailable < quantity) {
            context.source.sendError(Text.literal(Language.get("command.mselltopurchase.insufficient_items", quantity)))
            return 0
        }

        // 执行出售
        ServerMarket.instance.purchaseService.sellToBuyerAsync(
            sellerUuid = player.uuid,
            sellerName = player.name.string,
            itemId = itemId,
            nbt = snbt,
            quantity = quantity,
            buyerFilter = null  // 优先系统
        ).whenComplete { result: SellToBuyerResult?, ex ->
            context.source.server.execute {
                if (ex != null) {
                    context.source.sendError(Text.literal(Language.get("command.mselltopurchase.failed")))
                    ServerMarket.LOGGER.error("出售失败", ex)
                    return@execute
                }

                when (result) {
                    is SellToBuyerResult.Success -> {
                        // 扣除物品
                        var remaining = result.amount
                        for (stack in allStacks) {
                            if (remaining <= 0) break
                            val deduct = minOf(remaining, stack.count)
                            stack.decrement(deduct)
                            remaining -= deduct
                        }

                        context.source.sendMessage(
                            Text.literal(
                                Language.get(
                                    "command.mselltopurchase.success",
                                    result.amount,
                                    itemName,
                                    "%.2f".format(result.totalEarned)
                                )
                            )
                        )
                    }

                    SellToBuyerResult.NotFound -> {
                        context.source.sendError(Text.literal(Language.get("command.mselltopurchase.not_found")))
                    }

                    is SellToBuyerResult.LimitExceeded -> {
                        context.source.sendError(
                            Text.literal(Language.get("command.mselltopurchase.limit_exceeded", result.remaining))
                        )
                    }

                    is SellToBuyerResult.InsufficientFunds -> {
                        context.source.sendError(
                            Text.literal(Language.get("command.mselltopurchase.buyer_no_money", "%.2f".format(result.required)))
                        )
                    }

                    SellToBuyerResult.InsufficientItems -> {
                        context.source.sendError(Text.literal(Language.get("command.mselltopurchase.insufficient_items", quantity)))
                    }

                    is SellToBuyerResult.Error -> {
                        context.source.sendError(Text.literal(Language.get("command.mselltopurchase.error")))
                        ServerMarket.LOGGER.error("出售错误: ${result.message}")
                    }

                    null -> {
                        context.source.sendError(Text.literal(Language.get("command.mselltopurchase.error")))
                    }
                }
            }
        }

        return 1
    }
}

