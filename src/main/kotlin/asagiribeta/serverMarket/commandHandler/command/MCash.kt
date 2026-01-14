package asagiribeta.serverMarket.commandHandler.command

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.util.ItemKey
import asagiribeta.serverMarket.util.MoneyFormat
import asagiribeta.serverMarket.util.PermissionUtil
import asagiribeta.serverMarket.util.whenCompleteOnServerThread
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.util.Identifier

class MCash {
    // 构建 /svm cash 子命令
    fun buildSubCommand(): LiteralArgumentBuilder<ServerCommandSource> {
        return literal("cash")
            .requires(PermissionUtil.requirePlayer("servermarket.command.cash", 0))
            .then(argument("value", DoubleArgumentType.doubleArg(0.0))
                .then(argument("quantity", IntegerArgumentType.integer(1))
                    .executes(this::execute)
                )
            )
    }

    private fun execute(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val player = source.player ?: run {
            source.sendError(Text.translatable("servermarket.command.mcash.player_only"))
            return 0
        }
        val value = DoubleArgumentType.getDouble(context, "value")
        val quantity = IntegerArgumentType.getInteger(context, "quantity")
        if (value <= 0.0) {
            source.sendError(Text.translatable("servermarket.command.mcash.invalid_value"))
            return 0
        }
        if (quantity <= 0) {
            source.sendError(Text.translatable("servermarket.command.mcash.invalid_quantity"))
            return 0
        }

        val db = ServerMarket.instance.database
        val currencyRepo = db.currencyRepository
        val uuid = player.uuid
        val totalCost = value * quantity

        currencyRepo.findByValueAsync(value)
            .whenCompleteOnServerThread(source.server) { mapping, ex ->
                if (ex != null) {
                    ServerMarket.LOGGER.error("/svm cash lookup failed", ex)
                    source.sendError(Text.translatable("servermarket.command.mcash.failed"))
                    return@whenCompleteOnServerThread
                }

                if (mapping == null) {
                    source.sendError(Text.translatable("servermarket.command.mcash.value_not_found", MoneyFormat.format(value, 2)))
                    return@whenCompleteOnServerThread
                }

                ServerMarket.instance.currencyService.exchangeBalanceToCurrency(
                    uuid, mapping.itemId, mapping.nbt, quantity
                ).whenCompleteOnServerThread(source.server) { deducted, ex2 ->
                    if (ex2 != null) {
                        ServerMarket.LOGGER.error("/svm cash execution failed", ex2)
                        source.sendError(Text.translatable("servermarket.command.mcash.failed"))
                        return@whenCompleteOnServerThread
                    }

                    if (deducted == null) {
                        source.sendError(
                            Text.translatable(
                                "servermarket.command.mcash.insufficient_balance",
                                MoneyFormat.format(totalCost, 2)
                            )
                        )
                        return@whenCompleteOnServerThread
                    }

                    val id = Identifier.of(mapping.itemId)
                    val item = Registries.ITEM.get(id)
                    if (item.defaultStack.isEmpty) {
                        db.supplyAsync0 { db.addBalance(uuid, deducted) }
                        source.sendError(Text.translatable("servermarket.command.mcash.failed"))
                        return@whenCompleteOnServerThread
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
                            Text.translatable(
                                "servermarket.command.mcash.success",
                                quantity,
                                itemName,
                                MoneyFormat.format(totalCost, 2)
                            )
                        )
                    } catch (e: Exception) {
                        db.supplyAsync0 { db.addBalance(uuid, deducted) }
                        ServerMarket.LOGGER.error("/svm cash item delivery failed; refunded", e)
                        source.sendError(Text.translatable("servermarket.command.mcash.failed"))
                    }
                }
            }
        return 1
    }
}
