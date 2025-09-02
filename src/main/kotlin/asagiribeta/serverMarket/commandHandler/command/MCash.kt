package asagiribeta.serverMarket.commandHandler.command

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.util.Language
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
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.NbtComponent
import net.minecraft.nbt.StringNbtReader
import net.minecraft.nbt.NbtCompound

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
        val mapping = repo.findByValue(value) ?: run {
            source.sendError(Text.literal(Language.get("command.mcash.value_not_found", value)))
            return 0
        }

        val totalCost = value * quantity
        val uuid = player.uuid

        if (!db.tryWithdraw(uuid, totalCost)) {
            source.sendError(Text.literal(Language.get("command.mcash.insufficient_balance", String.format("%.2f", totalCost))))
            return 0
        }

        return try {
            val id = Identifier.of(mapping.itemId)
            val item = Registries.ITEM.get(id)
            if (item.defaultStack.isEmpty) {
                db.deposit(uuid, totalCost)
                source.sendError(Text.literal(Language.get("command.mcash.failed")))
                return 0
            }

            val parsedNbt: NbtCompound? = if (mapping.nbt.isNotEmpty()) {
                try {
                    val el = StringNbtReader.parse(mapping.nbt)
                    if (el is NbtCompound) el else {
                        try { db.deposit(uuid, totalCost) } catch (_: Exception) {}
                        source.sendError(Text.literal(Language.get("command.mcash.failed")))
                        return 0
                    }
                } catch (e: Exception) {
                    try { db.deposit(uuid, totalCost) } catch (_: Exception) {}
                    ServerMarket.LOGGER.error("/mcash NBT解析失败 nbt=${mapping.nbt}", e)
                    source.sendError(Text.literal(Language.get("command.mcash.failed")))
                    return 0
                }
            } else null

            var remaining = quantity
            val maxStack = item.maxCount
            val itemName = item.name.string
            while (remaining > 0) {
                val give = kotlin.math.min(remaining, maxStack)
                val stack = ItemStack(item, give)
                if (parsedNbt != null) {
                    val nbtCopy = parsedNbt.copy() as NbtCompound
                    stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbtCopy))
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
            1
        } catch (e: Exception) {
            try { db.deposit(uuid, totalCost) } catch (_: Exception) {}
            ServerMarket.LOGGER.error("/mcash 执行失败", e)
            source.sendError(Text.literal(Language.get("command.mcash.failed")))
            0
        }
    }
}
