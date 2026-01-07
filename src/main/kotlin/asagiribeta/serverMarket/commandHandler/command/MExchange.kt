package asagiribeta.serverMarket.commandHandler.command

import asagiribeta.serverMarket.ServerMarket
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

class MExchange {
    // 构建 /svm exchange 子命令
    fun buildSubCommand(): LiteralArgumentBuilder<ServerCommandSource> {
        return literal("exchange")
            .requires(PermissionUtil.requirePlayer("servermarket.command.exchange", 0))
            .then(argument("quantity", IntegerArgumentType.integer(1))
                .executes(this::execute)
            )
    }

    private fun execute(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val player = source.player ?: run {
            source.sendError(Text.translatable("servermarket.command.mexchange.player_only"))
            return 0
        }
        val quantity = IntegerArgumentType.getInteger(context, "quantity")
        if (quantity <= 0) {
            source.sendError(Text.translatable("servermarket.command.mexchange.invalid_amount"))
            return 0
        }

        val main = player.mainHandStack
        if (main.isEmpty) {
            source.sendError(Text.translatable("servermarket.command.mexchange.hold_item"))
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
            source.sendError(Text.translatable("servermarket.command.mexchange.insufficient_items", quantity))
            return 0
        }

        // Use CurrencyService to exchange currency to balance
        ServerMarket.instance.currencyService.exchangeCurrencyToBalance(
            player.uuid, itemId, nbt, quantity
        ).whenCompleteOnServerThread(source.server) { totalGain, ex ->
            if (ex != null) {
                ServerMarket.LOGGER.error("/mexchange 执行失败", ex)
                source.sendError(Text.translatable("servermarket.command.mexchange.failed"))
                return@whenCompleteOnServerThread
            }

            if (totalGain == null) {
                source.sendError(Text.translatable("servermarket.command.mexchange.not_currency"))
                return@whenCompleteOnServerThread
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
                Text.translatable(
                    "servermarket.command.mexchange.success",
                    quantity,
                    itemName,
                    String.format("%.2f", totalGain)
                )
            )
        }
        return 1
    }
}
