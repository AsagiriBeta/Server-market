package asagiribeta.serverMarket.commandHandler.command

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.util.MoneyFormat
import asagiribeta.serverMarket.model.PurchaseResult
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.text.Text
import asagiribeta.serverMarket.util.ItemKey
import asagiribeta.serverMarket.util.TextFormat
import asagiribeta.serverMarket.util.whenCompleteOnServerThread
import asagiribeta.serverMarket.util.CommandSuggestions
import net.minecraft.command.argument.IdentifierArgumentType
import asagiribeta.serverMarket.util.PermissionUtil

class MBuy {
    // 构建 /svm buy 子命令
    fun buildSubCommand(): LiteralArgumentBuilder<ServerCommandSource> {
        return literal("buy")
            .requires(PermissionUtil.requirePlayer("servermarket.command.buy", 0))
            .then(argument("quantity", DoubleArgumentType.doubleArg(1.0))
                .then(argument("item", IdentifierArgumentType.identifier())
                    .suggests(CommandSuggestions.ITEM_ID_SUGGESTIONS)
                    .executes(this::executeBuy)
                    .then(argument("seller", StringArgumentType.string())
                        .suggests(CommandSuggestions.SELLER_SUGGESTIONS)
                        .executes(this::executeBuyWithSeller)
                    )
                )
            )
    }


    private fun buildStackFromRecord(pid: String, nbt: String, amount: Int): ItemStack {
        val stack = ItemKey.tryBuildFullStackFromSnbt(nbt, amount) ?: run {
            val id = Identifier.tryParse(pid)
            val itemType = if (id != null && Registries.ITEM.containsId(id)) Registries.ITEM.get(id) else Items.AIR
            val fallback = ItemStack(itemType, amount)
            try { if (nbt.isNotEmpty()) ItemKey.applySnbt(fallback, nbt) } catch (_: Exception) {}
            fallback
        }
        return stack
    }

    private fun handlePurchaseSuccess(
        player: net.minecraft.server.network.ServerPlayerEntity,
        context: CommandContext<ServerCommandSource>,
        itemId: String,
        quantity: Int,
        result: PurchaseResult.Success,
        seller: String?
    ) {
        // Give items to player and derive a localized display name from the first returned stack
        var firstDisplayName: String? = null
        for ((pid, nbt, amount) in result.items) {
            val stack = buildStackFromRecord(pid, nbt, amount)
            if (firstDisplayName == null) {
                firstDisplayName = TextFormat.displayItemName(stack, pid)
            }
            player.giveItemStack(stack)
        }

        val baseName = firstDisplayName ?: TextFormat.displayItemName(ItemStack.EMPTY, itemId)
        val displayName = if (!seller.isNullOrBlank()) "$baseName@$seller" else baseName

        context.source.sendMessage(
            Text.translatable(
                "servermarket.command.mbuy.success",
                quantity,
                displayName,
                MoneyFormat.format(result.totalCost, 2)
            )
        )
    }

    private fun executeBuy(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: return 0
        val quantity = DoubleArgumentType.getDouble(context, "quantity").toInt()
        val itemId = IdentifierArgumentType.getIdentifier(context, "item").toString()

        // Use MarketService instead of direct database access
        ServerMarket.instance.marketService.purchaseItem(
            playerUuid = player.uuid,
            playerName = player.name.string,
            itemId = itemId,
            quantity = quantity,
            seller = null
        ).whenCompleteOnServerThread(context.source.server) { result, ex ->
            if (ex != null) {
                context.source.sendError(Text.translatable("servermarket.command.mbuy.error"))
                ServerMarket.LOGGER.error("/svm buy failed", ex)
                return@whenCompleteOnServerThread
            }

            when (result) {
                null -> {
                    context.source.sendError(Text.translatable("servermarket.command.mbuy.error"))
                }
                is PurchaseResult.Success -> {
                    handlePurchaseSuccess(
                        player = player,
                        context = context,
                        itemId = itemId,
                        quantity = quantity,
                        result = result,
                        seller = null
                    )
                }
                is PurchaseResult.InsufficientFunds -> {
                    context.source.sendError(
                        Text.translatable(
                            "servermarket.command.mbuy.insufficient_funds",
                            MoneyFormat.format(result.required, 2)
                        )
                    )
                }
                is PurchaseResult.InsufficientStock -> {
                    context.source.sendError(
                        Text.translatable(
                            "servermarket.command.mbuy.insufficient_stock",
                            result.available
                        )
                    )
                }
                is PurchaseResult.LimitExceeded -> {
                    context.source.sendError(
                        Text.translatable(
                            "servermarket.command.mbuy.limit_exceeded",
                            result.remaining
                        )
                    )
                }
                is PurchaseResult.NotFound -> {
                    context.source.sendError(
                        Text.translatable("servermarket.command.mbuy.not_found")
                    )
                }
                is PurchaseResult.CannotBuyOwnItem -> {
                    context.source.sendError(
                        Text.translatable("servermarket.command.mbuy.cannot_buy_own_item")
                    )
                }
                is PurchaseResult.Error -> {
                    context.source.sendError(
                        Text.translatable("servermarket.command.mbuy.error")
                    )
                    ServerMarket.LOGGER.error("/svm buy error: {}", result.message)
                }
                is PurchaseResult.AmbiguousVariants -> {
                    context.source.sendError(
                        Text.translatable(
                            "servermarket.command.mbuy.ambiguous_variants",
                            result.itemId,
                            result.variantCount
                        )
                    )
                }
            }
        }
        return 1
    }

    private fun executeBuyWithSeller(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: return 0
        val quantity = DoubleArgumentType.getDouble(context, "quantity").toInt()
        val itemId = IdentifierArgumentType.getIdentifier(context, "item").toString()
        val seller = StringArgumentType.getString(context, "seller")

        // Use MarketService with seller filter
        ServerMarket.instance.marketService.purchaseItem(
            playerUuid = player.uuid,
            playerName = player.name.string,
            itemId = itemId,
            quantity = quantity,
            seller = seller
        ).whenCompleteOnServerThread(context.source.server) { result, ex ->
            if (ex != null) {
                context.source.sendError(Text.translatable("servermarket.command.mbuy.error"))
                ServerMarket.LOGGER.error("/svm buy failed (with seller)", ex)
                return@whenCompleteOnServerThread
            }

            when (result) {
                null -> {
                    context.source.sendError(Text.translatable("servermarket.command.mbuy.error"))
                }

                is PurchaseResult.Success -> {
                    handlePurchaseSuccess(
                        player = player,
                        context = context,
                        itemId = itemId,
                        quantity = quantity,
                        result = result,
                        seller = seller
                    )
                }

                is PurchaseResult.InsufficientFunds -> {
                    context.source.sendError(
                        Text.translatable(
                            "servermarket.command.mbuy.insufficient_funds",
                            MoneyFormat.format(result.required, 2)
                        )
                    )
                }

                is PurchaseResult.InsufficientStock -> {
                    context.source.sendError(
                        Text.translatable(
                            "servermarket.command.mbuy.insufficient_stock",
                            result.available
                        )
                    )
                }

                is PurchaseResult.LimitExceeded -> {
                    context.source.sendError(
                        Text.translatable(
                            "servermarket.command.mbuy.limit_exceeded",
                            result.remaining
                        )
                    )
                }

                is PurchaseResult.NotFound -> {
                    context.source.sendError(Text.translatable("servermarket.command.mbuy.not_found"))
                }

                is PurchaseResult.CannotBuyOwnItem -> {
                    context.source.sendError(Text.translatable("servermarket.command.mbuy.cannot_buy_own_item"))
                }

                is PurchaseResult.Error -> {
                    context.source.sendError(Text.translatable("servermarket.command.mbuy.error"))
                    ServerMarket.LOGGER.error("/svm buy error: {}", result.message)
                }

                is PurchaseResult.AmbiguousVariants -> {
                    context.source.sendError(
                        Text.translatable(
                            "servermarket.command.mbuy.ambiguous_variants",
                            result.itemId,
                            result.variantCount
                        )
                    )
                }
            }
        }

        return 1
    }
}
