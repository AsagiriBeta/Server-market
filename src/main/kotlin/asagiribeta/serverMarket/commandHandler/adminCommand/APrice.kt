package asagiribeta.serverMarket.commandHandler.adminCommand

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.util.Language
import asagiribeta.serverMarket.util.ItemKey
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.registry.Registries
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text

class APrice {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            CommandManager.literal("aprice")
                .requires { it.hasPermissionLevel(4) }
                .then(
                    CommandManager.argument("price", DoubleArgumentType.doubleArg(0.0))
                        .executes(this::execute)
                        .then(
                            CommandManager.argument("limitPerDay", IntegerArgumentType.integer(-1))
                                .executes(this::executeWithLimit)
                        )
                )
        )
    }

    private fun execute(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val player = source.player ?: run {
            source.sendError(Text.literal(Language.get("command.aprice.player_only")))
            return 0
        }

        val itemStack = player.mainHandStack
        if (itemStack.isEmpty) {
            source.sendError(Text.literal(Language.get("command.aprice.hold_item")))
            return 0
        }

        val price = DoubleArgumentType.getDouble(context, "price")
        val itemId = Registries.ITEM.getId(itemStack.item).toString()
        val nbt = ItemKey.snbtOf(itemStack)
        val marketRepo = ServerMarket.instance.database.marketRepository
        return try {
            // 使用无限制（-1）作为默认每日限购
            if (!marketRepo.hasSystemItem(itemId, nbt)) {
                marketRepo.addSystemItem(itemId, nbt, price, -1)
                source.sendMessage(Text.literal(Language.get("command.aprice.add_success", itemStack.name.string, price)))
            } else {
                marketRepo.addSystemItem(itemId, nbt, price, -1)
                source.sendMessage(Text.literal(Language.get("command.aprice.update_success", itemStack.name.string, price)))
            }
            1
        } catch (e: Exception) {
            source.sendError(Text.literal(Language.get("command.aprice.operation_failed")))
            ServerMarket.LOGGER.error("aprice命令执行失败", e)
            0
        }
    }

    private fun executeWithLimit(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val player = source.player ?: run {
            source.sendError(Text.literal(Language.get("command.aprice.player_only")))
            return 0
        }

        val itemStack = player.mainHandStack
        if (itemStack.isEmpty) {
            source.sendError(Text.literal(Language.get("command.aprice.hold_item")))
            return 0
        }

        val price = DoubleArgumentType.getDouble(context, "price")
        val limitPerDay = IntegerArgumentType.getInteger(context, "limitPerDay")
        val itemId = Registries.ITEM.getId(itemStack.item).toString()
        val nbt = ItemKey.snbtOf(itemStack)
        val marketRepo = ServerMarket.instance.database.marketRepository
        return try {
            marketRepo.addSystemItem(itemId, nbt, price, limitPerDay)
            val msgKey = if (limitPerDay < 0) "command.aprice.update_success" else "command.aprice.update_success"
            source.sendMessage(Text.literal(Language.get(msgKey, itemStack.name.string, price)))
            1
        } catch (e: Exception) {
            source.sendError(Text.literal(Language.get("command.aprice.operation_failed")))
            ServerMarket.LOGGER.error("aprice命令执行失败", e)
            0
        }
    }
}
