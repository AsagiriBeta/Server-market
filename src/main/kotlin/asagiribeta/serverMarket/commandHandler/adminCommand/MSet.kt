package asagiribeta.serverMarket.commandHandler.adminCommand

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.util.Language
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.command.CommandSource
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import asagiribeta.serverMarket.util.PermissionUtil

class MSet {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            CommandManager.literal("mset")
                .requires(PermissionUtil.require("servermarket.admin.mset", 4))
                .then(
                    CommandManager.argument("player", StringArgumentType.string())
                        .suggests { context, builder ->
                            val server = context.source.server
                            val names = server.playerManager.playerNames
                            CommandSource.suggestMatching(names, builder)
                        }
                        .then(
                            CommandManager.argument("amount", DoubleArgumentType.doubleArg(0.0))
                                .executes(this::execute)
                        )
                )
        )
    }

    private fun execute(context: CommandContext<ServerCommandSource>): Int {
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

        ServerMarket.instance.database.setBalanceAsync(targetPlayer.uuid, amount).whenComplete { _, ex ->
            server.execute {
                if (ex != null) {
                    context.source.sendError(Text.literal(Language.get("command.mset.failed")))
                    ServerMarket.LOGGER.error("mset命令执行失败", ex)
                } else {
                    context.source.sendMessage(
                        Text.literal(Language.get("command.mset.success", targetPlayer.name.string, "%.2f".format(amount)))
                    )
                }
            }
        }
        return 1
    }
}
