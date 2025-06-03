package asagiribeta.serverMarket.commandHandler

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.util.Language
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import com.mojang.brigadier.context.CommandContext
import net.minecraft.command.CommandSource
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.registry.Registries

class AdminCommand {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            literal("mset")
                .requires { it.hasPermissionLevel(4) }
                .then(argument("player", StringArgumentType.string())
                    .suggests { context, builder ->  // 玩家名称自动补全
                        val server = context.source.server
                        val names = server.playerManager.playerNames
                        CommandSource.suggestMatching(names, builder)
                    }
                    .then(argument("amount", DoubleArgumentType.doubleArg(0.0))
                        .executes(this::executeMSetCommand)
                    )
                )
        )

        dispatcher.register(
            literal("aprice")
                .requires { it.hasPermissionLevel(4) }
                .then(argument("price", DoubleArgumentType.doubleArg(0.0))
                    .executes(this::executeAPriceCommand)
                )
        )

        dispatcher.register(
            literal("apull")
                .requires { it.hasPermissionLevel(4) }
                .executes(this::executeAPullCommand)
        )

        dispatcher.register(
            literal("mlang")
                .requires { it.hasPermissionLevel(4) }
                .then(argument("language", StringArgumentType.word())
                    .suggests { _, builder ->
                        CommandSource.suggestMatching(listOf("zh", "en"), builder)
                    }
                    .executes(this::executeMLangCommand)
                )
        )
    }

    private fun executeMLangCommand(context: CommandContext<ServerCommandSource>): Int {
        val lang = StringArgumentType.getString(context, "language")
        
        if (Language.setLanguage(lang)) {
            context.source.sendMessage(
                Text.literal(Language.get("command.mlang.success", lang))
            )
            ServerMarket.LOGGER.info("Language changed to: $lang")
            return 1
        } else {
            context.source.sendError(
                Text.literal(Language.get("command.mlang.invalid"))
            )
            return 0
        }
    }

    private fun executeMSetCommand(context: CommandContext<ServerCommandSource>): Int {
        val targetName = StringArgumentType.getString(context, "player")
        val amount = DoubleArgumentType.getDouble(context, "amount")
        val server = context.source.server
        val targetPlayer = server.playerManager.getPlayer(targetName) ?: run {
            context.source.sendError(Text.literal(Language.get("command.mset.player_offline")))
            return 0
        }

        if (amount < 0) {
            context.source.sendError(Text.literal(Language.get("command.mset.negative_amount")))
            return 0
        }

        try {
            ServerMarket.instance.database.setBalance(targetPlayer.uuid, amount)
            context.source.sendMessage(
                Text.literal(Language.get("command.mset.success", targetPlayer.name.string, "%.2f".format(amount)))
            )
            return 1
        } catch (e: Exception) {
            context.source.sendError(Text.literal(Language.get("command.mset.failed")))
            ServerMarket.LOGGER.error("mset命令执行失败", e)
            return 0
        }
    }

    private fun executeAPriceCommand(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val player = source.player ?: run {
            source.sendError(Text.literal(Language.get("command.aprice.player_only")))
            return 0
        }

        val price = DoubleArgumentType.getDouble(context, "price")
        val itemStack = player.mainHandStack
        if (itemStack.isEmpty) {
            source.sendError(Text.literal(Language.get("command.aprice.hold_item")))
            return 0
        }

        try {
            val itemId = Registries.ITEM.getId(itemStack.item).toString()
            val marketRepo = ServerMarket.instance.database.marketRepository
            
            if (!marketRepo.hasSystemItem(itemId)) {
                marketRepo.addSystemItem(itemId, price)
                source.sendMessage(Text.literal(Language.get("command.aprice.add_success", itemStack.name.string, price)))
            } else {
                marketRepo.addSystemItem(itemId, price)
                source.sendMessage(Text.literal(Language.get("command.aprice.update_success", itemStack.name.string, price)))
            }
            return 1
        } catch (e: Exception) {
            source.sendError(Text.literal(Language.get("command.aprice.operation_failed")))
            ServerMarket.LOGGER.error("aprice命令执行失败", e)
            return 0
        }
    }

    private fun executeAPullCommand(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val player = source.player ?: run {
            source.sendError(Text.literal(Language.get("command.apull.player_only")))
            return 0
        }

        val itemStack = player.mainHandStack
        if (itemStack.isEmpty) {
            source.sendError(Text.literal(Language.get("command.apull.hold_item")))
            return 0
        }

        try {
            val itemId = Registries.ITEM.getId(itemStack.item).toString()
            val marketRepo = ServerMarket.instance.database.marketRepository
            
            if (marketRepo.hasSystemItem(itemId)) {
                marketRepo.removeSystemItem(itemId)
                source.sendMessage(Text.literal(Language.get("command.apull.success", itemStack.name.string)))
                return 1
            }
            source.sendError(Text.literal(Language.get("command.apull.not_listed")))
            return 0
        } catch (e: Exception) {
            source.sendError(Text.literal(Language.get("command.apull.operation_failed")))
            ServerMarket.LOGGER.error("apull命令执行失败", e)
            return 0
        }
    }
}
