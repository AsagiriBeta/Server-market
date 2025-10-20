// filepath: /Users/asagiri/IdeaProjects/Server-market/src/main/kotlin/asagiribeta/serverMarket/commandHandler/command/MPay.kt
package asagiribeta.serverMarket.commandHandler.command

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.util.Config
import asagiribeta.serverMarket.util.Language
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import net.minecraft.command.CommandSource
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import asagiribeta.serverMarket.util.PermissionUtil

class MPay {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            literal("mpay")
                .requires(PermissionUtil.requirePlayer("servermarket.command.mpay", 0))
                .then(argument("player", StringArgumentType.string())
                    .suggests { context, builder ->
                        val names = context.source.server.playerManager.playerNames
                        CommandSource.suggestMatching(names, builder)
                    }
                    .then(argument("amount", DoubleArgumentType.doubleArg())
                        .executes(this::execute)
                    )
                )
        )
    }

    internal fun execute(context: CommandContext<ServerCommandSource>): Int {
        val sender = context.source.player ?: return 0
        val amount = DoubleArgumentType.getDouble(context, "amount")

        if (amount <= 0) {
            context.source.sendMessage(Text.literal(Language.get("command.mpay.amount_must_be_positive")))
            return 0
        }

        if (amount > Config.maxTransferAmount) {
            context.source.sendError(Text.literal(Language.get("command.mpay.amount_too_large", Config.maxTransferAmount.toString())))
            return 0
        }

        val targetName = StringArgumentType.getString(context, "player")
        if (targetName.equals(sender.name.string, ignoreCase = true)) {
            context.source.sendError(Text.literal(Language.get("command.mpay.cannot_pay_self")))
            return 0
        }

        val targetPlayer = context.source.server.playerManager.getPlayer(targetName) ?: run {
            context.source.sendError(Text.literal(Language.get("command.mpay.player_offline")))
            return 0
        }

        val database = ServerMarket.instance.database
        val fromUuid = sender.uuid
        val toUuid = targetPlayer.uuid

        database.transferAsync(fromUuid, toUuid, amount).whenComplete { _, ex ->
            context.source.server.execute {
                if (ex != null) {
                    context.source.sendError(Text.literal(Language.get("command.mpay.transfer_failed")))
                    ServerMarket.LOGGER.error("mpay命令执行失败", ex)
                } else {
                    context.source.sendMessage(Text.literal(Language.get("command.mpay.success", targetPlayer.name.string, "%.2f".format(amount))))
                    targetPlayer.sendMessage(Text.literal(Language.get("command.mpay.received", sender.name.string, "%.2f".format(amount))))
                }
            }
        }
        return 1
    }
}
