package asagiribeta.serverMarket.commandHandler.command

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.util.Language
import asagiribeta.serverMarket.model.PurchaseResult
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.text.Text
import asagiribeta.serverMarket.util.ItemKey
import asagiribeta.serverMarket.util.CommandSuggestions
import net.minecraft.command.argument.IdentifierArgumentType
import asagiribeta.serverMarket.util.PermissionUtil

class MBuy {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            literal("mbuy")
                .requires(PermissionUtil.requirePlayer("servermarket.command.mbuy", 0))
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

    private fun executeBuy(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: return 0
        val quantity = DoubleArgumentType.getDouble(context, "quantity").toInt()
        val itemId = IdentifierArgumentType.getIdentifier(context, "item").toString()

        // Use MarketService instead of direct database access
        ServerMarket.instance.marketService.purchaseItemAsync(
            playerUuid = player.uuid,
            playerName = player.name.string,
            itemId = itemId,
            quantity = quantity,
            seller = null
        ).whenComplete { result, ex ->
            context.source.server.execute {
                if (ex != null) {
                    context.source.sendError(Text.literal(Language.get("command.mbuy.error")))
                    ServerMarket.LOGGER.error("MBuy命令执行失败", ex)
                    return@execute
                }

                when (result) {
                    is PurchaseResult.Success -> {
                        // Give items to player
                        for ((pid, nbt, amount) in result.items) {
                            val stack = buildStackFromRecord(pid, nbt, amount)
                            player.giveItemStack(stack)
                        }
                        context.source.sendMessage(
                            Text.literal(
                                Language.get(
                                    "command.mbuy.success",
                                    quantity,
                                    itemId,
                                    "%.2f".format(result.totalCost)
                                )
                            )
                        )
                    }
                    is PurchaseResult.InsufficientFunds -> {
                        context.source.sendError(
                            Text.literal(
                                Language.get(
                                    "command.mbuy.insufficient_funds",
                                    "%.2f".format(result.required)
                                )
                            )
                        )
                    }
                    is PurchaseResult.InsufficientStock -> {
                        context.source.sendError(
                            Text.literal(
                                Language.get(
                                    "command.mbuy.insufficient_stock",
                                    result.available
                                )
                            )
                        )
                    }
                    is PurchaseResult.LimitExceeded -> {
                        context.source.sendError(
                            Text.literal(
                                Language.get(
                                    "command.mbuy.limit_exceeded",
                                    result.remaining
                                )
                            )
                        )
                    }
                    is PurchaseResult.NotFound -> {
                        context.source.sendError(
                            Text.literal(Language.get("command.mbuy.not_found"))
                        )
                    }
                    is PurchaseResult.Error -> {
                        context.source.sendError(
                            Text.literal(Language.get("command.mbuy.error"))
                        )
                        ServerMarket.LOGGER.error("购买失败: ${result.message}")
                    }
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
        ServerMarket.instance.marketService.purchaseItemAsync(
            playerUuid = player.uuid,
            playerName = player.name.string,
            itemId = itemId,
            quantity = quantity,
            seller = seller
        ).whenComplete { result, ex ->
            context.source.server.execute {
                if (ex != null) {
                    context.source.sendError(Text.literal(Language.get("command.mbuy.error")))
                    ServerMarket.LOGGER.error("MBuy命令执行失败(带卖家)", ex)
                    return@execute
                }

                when (result) {
                    is PurchaseResult.Success -> {
                        for ((pid, nbt, amount) in result.items) {
                            val stack = buildStackFromRecord(pid, nbt, amount)
                            player.giveItemStack(stack)
                        }
                        context.source.sendMessage(
                            Text.literal(
                                Language.get(
                                    "command.mbuy.success",
                                    quantity,
                                    "$itemId@$seller",
                                    "%.2f".format(result.totalCost)
                                )
                            )
                        )
                    }
                    is PurchaseResult.InsufficientFunds -> {
                        context.source.sendError(
                            Text.literal(
                                Language.get(
                                    "command.mbuy.insufficient_funds",
                                    "%.2f".format(result.required)
                                )
                            )
                        )
                    }
                    is PurchaseResult.InsufficientStock -> {
                        context.source.sendError(
                            Text.literal(
                                Language.get(
                                    "command.mbuy.insufficient_stock",
                                    result.available
                                )
                            )
                        )
                    }
                    is PurchaseResult.LimitExceeded -> {
                        context.source.sendError(
                            Text.literal(
                                Language.get(
                                    "command.mbuy.limit_exceeded",
                                    result.remaining
                                )
                            )
                        )
                    }
                    is PurchaseResult.NotFound -> {
                        context.source.sendError(
                            Text.literal(Language.get("command.mbuy.not_found"))
                        )
                    }
                    is PurchaseResult.Error -> {
                        context.source.sendError(
                            Text.literal(Language.get("command.mbuy.error"))
                        )
                        ServerMarket.LOGGER.error("购买失败: ${result.message}")
                    }
                }
            }
        }
        return 1
    }
}

