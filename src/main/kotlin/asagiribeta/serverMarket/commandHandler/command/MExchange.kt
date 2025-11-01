package asagiribeta.serverMarket.commandHandler.command

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.util.Language
import asagiribeta.serverMarket.util.ItemKey
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.registry.Registries
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import asagiribeta.serverMarket.util.PermissionUtil

class MExchange {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            literal("mexchange")
                .requires(PermissionUtil.requirePlayer("servermarket.command.mexchange", 0))
                .then(argument("quantity", IntegerArgumentType.integer(1))
                    .executes(this::execute)
                )
        )
    }

    private fun execute(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val player = source.player ?: run {
            source.sendError(Text.literal(Language.get("command.mexchange.player_only")))
            return 0
        }
        val quantity = IntegerArgumentType.getInteger(context, "quantity")
        if (quantity <= 0) {
            source.sendError(Text.literal(Language.get("command.mexchange.invalid_amount")))
            return 0
        }

        val main = player.mainHandStack
        if (main.isEmpty) {
            source.sendError(Text.literal(Language.get("command.mexchange.hold_item")))
            return 0
        }

        val itemName = main.name.string
        val itemId = Registries.ITEM.getId(main.item).toString()
        val nbt = ItemKey.snbtOf(main)

        // First check inventory for sufficient items
        val inv = player.inventory
        val matchingStacks = (0 until inv.size()).map { inv.getStack(it) }.filter {
            !it.isEmpty && Registries.ITEM.getId(it.item).toString() == itemId && ItemKey.snbtOf(it) == nbt
        }
        val totalAvailable = matchingStacks.sumOf { it.count }
        if (totalAvailable < quantity) {
            source.sendError(Text.literal(Language.get("command.mexchange.insufficient_items", quantity)))
            return 0
        }

        // Use CurrencyService to exchange currency to balance
        ServerMarket.instance.currencyService.exchangeCurrencyToBalance(
            player.uuid, itemId, nbt, quantity
        ).whenComplete { totalGain, ex ->
            source.server.execute {
                if (ex != null) {
                    ServerMarket.LOGGER.error("/mexchange 执行失败", ex)
                    source.sendError(Text.literal(Language.get("command.mexchange.failed")))
                    return@execute
                }

                if (totalGain == null) {
                    source.sendError(Text.literal(Language.get("command.mexchange.not_currency")))
                    return@execute
                }

                // Deduct items from inventory
                var remaining = quantity
                for (stack in matchingStacks) {
                    if (remaining <= 0) break
                    val deduct = kotlin.math.min(remaining, stack.count)
                    stack.count -= deduct
                    remaining -= deduct
                }

                source.sendMessage(
                    Text.literal(
                        Language.get(
                            "command.mexchange.success",
                            quantity,
                            itemName,
                            String.format("%.2f", totalGain)
                        )
                    )
                )
            }
        }
        return 1
    }
}
