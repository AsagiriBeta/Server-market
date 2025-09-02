package asagiribeta.serverMarket.commandHandler.command

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.util.Language
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.component.DataComponentTypes
import net.minecraft.registry.Registries
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text

class MExchange {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            literal("mexchange")
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

        val db = ServerMarket.instance.database
        val repo = db.currencyRepository

        val main = player.mainHandStack
        if (main.isEmpty) {
            // 使用专门的 mexchange 持物提示文案
            source.sendError(Text.literal(Language.get("command.mexchange.hold_item")))
            return 0
        }

        val itemId = Registries.ITEM.getId(main.item).toString()
        val nbt = try {
            val customData = try { main.get(DataComponentTypes.CUSTOM_DATA) } catch (_: Throwable) { null }
            customData?.toString() ?: ""
        } catch (_: Throwable) { "" }

        val value = repo.getCurrencyValue(itemId, nbt) ?: run {
            source.sendError(Text.literal(Language.get("command.mexchange.not_currency")))
            return 0
        }

        // 统计背包中与主手相同签名的数量
        val inv = player.inventory
        val matchingStacks = (0 until inv.size()).map { inv.getStack(it) }.filter {
            !it.isEmpty && Registries.ITEM.getId(it.item).toString() == itemId && run {
                val cd = try { it.get(DataComponentTypes.CUSTOM_DATA) } catch (_: Throwable) { null }
                val sn = try { cd?.toString() ?: "" } catch (_: Throwable) { "" }
                sn == nbt
            }
        }
        val totalAvailable = matchingStacks.sumOf { it.count }
        if (totalAvailable < quantity) {
            source.sendError(Text.literal(Language.get("command.mexchange.insufficient_items", quantity)))
            return 0
        }

        // 扣除对应数量物品
        var remaining = quantity
        for (stack in matchingStacks) {
            if (remaining <= 0) break
            val deduct = kotlin.math.min(remaining, stack.count)
            stack.count -= deduct
            remaining -= deduct
        }

        val totalGain = value * quantity
        return try {
            db.deposit(player.uuid, totalGain)
            source.sendMessage(
                Text.literal(
                    Language.get(
                        "command.mexchange.success",
                        quantity,
                        main.name.string,
                        String.format("%.2f", totalGain)
                    )
                )
            )
            1
        } catch (e: Exception) {
            ServerMarket.LOGGER.error("/mexchange 执行失败", e)
            source.sendError(Text.literal(Language.get("command.mexchange.failed")))
            0
        }
    }
}
