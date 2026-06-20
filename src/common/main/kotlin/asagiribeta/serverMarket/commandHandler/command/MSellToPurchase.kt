package asagiribeta.serverMarket.commandHandler.command

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.model.SellToBuyerResult
import asagiribeta.serverMarket.util.ItemKey
import asagiribeta.serverMarket.util.ItemStackUtil
import asagiribeta.serverMarket.util.MoneyFormat
import asagiribeta.serverMarket.util.PermissionUtil
import asagiribeta.serverMarket.util.InventoryQuery
import asagiribeta.serverMarket.util.whenCompleteOnServerThread
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.registry.Registries
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text

/**
 * Sell held items to matching buy orders: /svm supply <quantity>
 * Legacy alias: /svm selltopurchase <quantity>
 */
class MSellToPurchase {
    fun buildSubCommand(): RequiredArgumentBuilder<ServerCommandSource, Int> {
        return argument("quantity", IntegerArgumentType.integer(1))
            .executes(this::execute)
    }

    fun buildLegacySubCommand(): LiteralArgumentBuilder<ServerCommandSource> {
        return literal("selltopurchase")
            .requires(PermissionUtil.requirePlayer("servermarket.command.selltopurchase", 0))
            .then(buildSubCommand())
    }

    private fun execute(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: run {
            context.source.sendError(Text.translatable("servermarket.command.mselltopurchase.player_only"))
            return 0
        }

        val quantity = IntegerArgumentType.getInteger(context, "quantity")

        val mainHandStack = player.mainHandStack
        if (mainHandStack.isEmpty) {
            context.source.sendError(Text.translatable("servermarket.command.mselltopurchase.hold_item"))
            return 0
        }

        val itemName = mainHandStack.name.string
        val itemId = Registries.ITEM.getId(mainHandStack.item).toString()
        val snbt = ItemKey.normalizeSnbt(ItemKey.snbtOf(mainHandStack))

        val allStacks = InventoryQuery.findMatchingStacks(player, itemId, snbt)

        val totalAvailable = InventoryQuery.countTotal(allStacks)
        if (totalAvailable < quantity) {
            context.source.sendError(Text.translatable("servermarket.command.mselltopurchase.insufficient_items", quantity))
            return 0
        }

        ServerMarket.instance.purchaseService.sellToBuyer(
            sellerUuid = player.uuid,
            sellerName = player.name.string,
            itemId = itemId,
            nbt = snbt,
            quantity = quantity,
            buyerFilter = null
        ).whenCompleteOnServerThread(context.source.server) { result: SellToBuyerResult?, ex ->
            if (ex != null) {
                context.source.sendError(Text.translatable("servermarket.command.mselltopurchase.failed"))
                ServerMarket.LOGGER.error("/svm supply failed", ex)
                return@whenCompleteOnServerThread
            }

            when (result) {
                is SellToBuyerResult.Success -> {
                    var remaining = result.amount
                    for (stack in allStacks) {
                        if (remaining <= 0) break
                        val deduct = minOf(remaining, stack.count)
                        ItemStackUtil.decrement(stack, deduct)
                        remaining -= deduct
                    }

                    context.source.sendMessage(
                        Text.translatable(
                            "servermarket.command.mselltopurchase.success",
                            result.amount,
                            itemName,
                            MoneyFormat.format(result.totalEarned, 2)
                        )
                    )
                }

                SellToBuyerResult.NotFound -> {
                    context.source.sendError(Text.translatable("servermarket.command.mselltopurchase.not_found"))
                }

                is SellToBuyerResult.LimitExceeded -> {
                    context.source.sendError(
                        Text.translatable("servermarket.command.mselltopurchase.limit_exceeded", result.remaining)
                    )
                }

                is SellToBuyerResult.InsufficientFunds -> {
                    context.source.sendError(
                        Text.translatable(
                            "servermarket.command.mselltopurchase.buyer_no_money",
                            MoneyFormat.format(result.required, 2)
                        )
                    )
                }

                SellToBuyerResult.InsufficientItems -> {
                    context.source.sendError(Text.translatable("servermarket.command.mselltopurchase.insufficient_items", quantity))
                }

                is SellToBuyerResult.Error -> {
                    context.source.sendError(Text.translatable("servermarket.command.mselltopurchase.error"))
                    ServerMarket.LOGGER.error("/svm supply error: {}", result.message)
                }

                null -> {
                    context.source.sendError(Text.translatable("servermarket.command.mselltopurchase.error"))
                }
            }
        }

        return 1
    }
}
