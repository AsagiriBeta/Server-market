package asagiribeta.serverMarket.commandHandler.command

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.util.Language
import asagiribeta.serverMarket.util.ItemKey
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import asagiribeta.serverMarket.util.PermissionUtil

class MCash {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            literal("mcash")
                .requires(PermissionUtil.requirePlayer("servermarket.command.mcash", 0))
                .then(argument("value", DoubleArgumentType.doubleArg(0.0))
                    .then(argument("quantity", IntegerArgumentType.integer(1))
                        .executes(this::execute)
                    )
                )
        )
    }

    private fun execute(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val player = source.player ?: run {
            source.sendError(Text.literal(Language.get("command.mcash.player_only")))
            return 0
        }
        val value = DoubleArgumentType.getDouble(context, "value")
        val quantity = IntegerArgumentType.getInteger(context, "quantity")
        if (value <= 0.0) {
            source.sendError(Text.literal(Language.get("command.mcash.invalid_value")))
            return 0
        }
        if (quantity <= 0) {
            source.sendError(Text.literal(Language.get("command.mcash.invalid_quantity")))
            return 0
        }

        val db = ServerMarket.instance.database
        val currencyRepo = db.currencyRepository
        val uuid = player.uuid
        val totalCost = value * quantity

        // Use CurrencyService to find and exchange currency
        currencyRepo.findByValueAsync(value)
            .whenComplete { mapping, ex ->
                source.server.execute {
                    if (ex != null) {
                        ServerMarket.LOGGER.error("/mcash 查询失败", ex)
                        source.sendError(Text.literal(Language.get("command.mcash.failed")))
                        return@execute
                    }

                    if (mapping == null) {
                        source.sendError(Text.literal(Language.get("command.mcash.value_not_found", value)))
                        return@execute
                    }

                    // Use CurrencyService to exchange balance to currency
                    ServerMarket.instance.currencyService.exchangeBalanceToCurrency(
                        uuid, mapping.itemId, mapping.nbt, quantity
                    ).whenComplete { deducted, ex2 ->
                        source.server.execute {
                            if (ex2 != null) {
                                ServerMarket.LOGGER.error("/mcash 执行失败", ex2)
                                source.sendError(Text.literal(Language.get("command.mcash.failed")))
                                return@execute
                            }

                            if (deducted == null) {
                                source.sendError(
                                    Text.literal(
                                        Language.get(
                                            "command.mcash.insufficient_balance",
                                            String.format("%.2f", totalCost)
                                        )
                                    )
                                )
                                return@execute
                            }

                            // Give currency items to player
                            val id = Identifier.of(mapping.itemId)
                            val item = Registries.ITEM.get(id)
                            if (item.defaultStack.isEmpty) {
                                // Refund if item is invalid
                                db.depositAsync(uuid, deducted)
                                source.sendError(Text.literal(Language.get("command.mcash.failed")))
                                return@execute
                            }

                            var remaining = quantity
                            val maxStack = item.maxCount
                            val itemName = item.name.string
                            val cleanNbt = ItemKey.normalizeSnbt(mapping.nbt)

                            try {
                                while (remaining > 0) {
                                    val give = kotlin.math.min(remaining, maxStack)
                                    val rebuilt = ItemKey.tryBuildFullStackFromSnbt(cleanNbt, give)
                                    val stack = rebuilt ?: run {
                                        val s = ItemStack(item, give)
                                        ItemKey.applySnbt(s, cleanNbt)
                                        s
                                    }
                                    player.giveItemStack(stack)
                                    remaining -= give
                                }
                                source.sendMessage(
                                    Text.literal(
                                        Language.get(
                                            "command.mcash.success",
                                            quantity,
                                            itemName,
                                            String.format("%.2f", totalCost)
                                        )
                                    )
                                )
                            } catch (e: Exception) {
                                // Refund on failure
                                db.depositAsync(uuid, deducted)
                                ServerMarket.LOGGER.error("/mcash 物品发放失败，已退款", e)
                                source.sendError(Text.literal(Language.get("command.mcash.failed")))
                            }
                        }
                    }
                }
            }
        return 1
    }
}
