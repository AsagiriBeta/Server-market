package asagiribeta.serverMarket.commandHandler.command

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.repository.CurrencyRepository
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

class MCash {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            literal("mcash")
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
        val repo = db.currencyRepository
        val uuid = player.uuid
        val totalCost = value * quantity

        // 1) 异步查找面值映射 -> 2) 异步扣款 -> 3) 主线程发放物品，失败则异步退款
        repo.findByValueAsync(value)
            .thenCompose { mapping: CurrencyRepository.CurrencyItem? ->
                if (mapping == null) {
                    source.server.execute {
                        source.sendError(Text.literal(Language.get("command.mcash.value_not_found", value)))
                    }
                    // 返回占位，让下游知道无需继续
                    java.util.concurrent.CompletableFuture.completedFuture(false to null)
                } else {
                    db.tryWithdrawAsync(uuid, totalCost).thenApply { success -> success to mapping }
                }
            }
            .whenComplete { pair, ex ->
                source.server.execute {
                    if (ex != null) {
                        ServerMarket.LOGGER.error("/mcash 执行失败", ex)
                        source.sendError(Text.literal(Language.get("command.mcash.failed")))
                        return@execute
                    }
                    val success = pair?.first ?: false
                    val mapping = pair?.second
                    if (mapping == null) return@execute // 已在上游提示
                    if (!success) {
                        source.sendError(Text.literal(Language.get("command.mcash.insufficient_balance", String.format("%.2f", totalCost))))
                        return@execute
                    }

                    // 扣费成功，发放物品
                    val id = Identifier.of(mapping.itemId)
                    val item = Registries.ITEM.get(id)
                    if (item.defaultStack.isEmpty) {
                        // 退款
                        db.depositAsync(uuid, totalCost)
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
                            // 优先：按清洗后的 SNBT 尝试完整重建物品（包含 components）
                            val rebuilt = ItemKey.tryBuildFullStackFromSnbt(cleanNbt, give)
                            val stack = rebuilt ?: run {
                                val s = ItemStack(item, give)
                                // 兜底：仅应用自定义数据和附魔，避免污染其它组件
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
                        // 发放失败，退款
                        db.depositAsync(uuid, totalCost)
                        ServerMarket.LOGGER.error("/mcash 物品发放失败，已退款", e)
                        source.sendError(Text.literal(Language.get("command.mcash.failed")))
                    }
                }
            }
        return 1
    }
}
