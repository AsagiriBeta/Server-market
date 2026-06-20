package asagiribeta.serverMarket.commandHandler.command

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.model.SellResult
import asagiribeta.serverMarket.util.ItemKey
import asagiribeta.serverMarket.util.ItemStackUtil
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
    /** /svm restock <qty> — legacy name; prefer /svm stock */
    fun buildSubCommand(): LiteralArgumentBuilder<ServerCommandSource> {
        return literal("restock")
            .requires(PermissionUtil.requirePlayer("servermarket.command.restock", 0))
            .then(quantityArgument())
    }

    /** /svm stock <qty> — clearer alias for adding listing quantity */
    fun buildStockAlias(): LiteralArgumentBuilder<ServerCommandSource> {
        return literal("stock")
            .requires(PermissionUtil.requirePlayer("servermarket.command.restock", 0))
            .then(quantityArgument())
    }

    private fun quantityArgument() =
        argument("quantity", IntegerArgumentType.integer(1))
            .executes(this::execute)

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

        var remaining = quantity
        for (stack in allStacks) {
            if (remaining <= 0) break
            val deduct = minOf(remaining, stack.count)
            ItemStackUtil.decrement(stack, deduct)
            remaining -= deduct
        }

        val overviewService = ServerMarket.instance.marketOverviewService
        ServerMarket.instance.marketService.listItemForSale(
            playerUuid = player.uuid,
            playerName = player.name.string,
            itemId = itemId,
            nbt = snbt,
            quantity = quantity,
            price = null
        ).thenCompose { result ->
            if (result is SellResult.Success) {
                ServerMarket.instance.database.supplyAsync {
                    result to overviewService.getOverview(itemId, snbt)
                }
            } else {
                java.util.concurrent.CompletableFuture.completedFuture(result to null)
            }
        }.whenCompleteOnServerThread(context.source.server) { payload, ex ->
            if (ex != null) {
                player.giveItemStack(mainHandStack.copy().apply { count = quantity })
                context.source.sendError(Text.translatable("servermarket.command.mrestock.operation_failed"))
                ServerMarket.LOGGER.error("/svm stock failed", ex)
                return@whenCompleteOnServerThread
            }

            val (result, overview) = payload ?: run {
                player.giveItemStack(mainHandStack.copy().apply { count = quantity })
                context.source.sendError(Text.translatable("servermarket.command.mrestock.operation_failed"))
                return@whenCompleteOnServerThread
            }
            when (result) {
                is SellResult.Success -> {
                    context.source.sendMessage(
                        Text.translatable(
                            "servermarket.command.mrestock.success",
                            quantity,
                            itemName
                        )
                    )
                    if (overview != null) {
                        context.source.sendMessage(overviewService.formatListingHint(overview))
                    }
                }
                SellResult.InvalidPrice -> {
                    player.giveItemStack(mainHandStack.copy().apply { count = quantity })
                    context.source.sendError(Text.translatable("servermarket.command.mrestock.not_listed"))
                }
                is SellResult.Error -> {
                    player.giveItemStack(mainHandStack.copy().apply { count = quantity })
                    context.source.sendError(Text.translatable("servermarket.command.mrestock.operation_failed"))
                    ServerMarket.LOGGER.error("/svm stock error: {}", result.message)
                }
                else -> {
                    player.giveItemStack(mainHandStack.copy().apply { count = quantity })
                    context.source.sendError(Text.translatable("servermarket.command.mrestock.operation_failed"))
                }
            }
        }

        return 1
    }
}
