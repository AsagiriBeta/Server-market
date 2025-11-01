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

class MAdd {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            CommandManager.literal("madd")
                .requires(PermissionUtil.require("servermarket.admin.madd", 4))
                .then(
                    CommandManager.argument("player", StringArgumentType.string())
                        .suggests { context, builder ->
                            val server = context.source.server
                            val names = server.playerManager.playerNames
                            CommandSource.suggestMatching(names, builder)
                        }
                        .then(
                            CommandManager.argument("amount", DoubleArgumentType.doubleArg())
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
            context.source.sendError(Text.literal(Language.get("command.madd.player_offline")))
            return 0
        }

        // 使用 TransferService 增加余额
        ServerMarket.instance.transferService.addBalance(targetPlayer.uuid, amount).whenComplete { success, ex ->
            server.execute {
                if (ex != null) {
                    context.source.sendError(Text.literal(Language.get("command.madd.failed")))
                    ServerMarket.LOGGER.error("madd命令执行失败", ex)
                } else if (success) {
                    val sign = if (amount >= 0) "+" else ""
                    context.source.sendMessage(
                        Text.literal(Language.get("command.madd.success", targetPlayer.name.string, "$sign%.2f".format(amount)))
                    )
                    // 通知目标玩家
                    targetPlayer.sendMessage(
                        Text.literal(Language.get("command.madd.received", "$sign%.2f".format(amount)))
                    )
                } else {
                    context.source.sendError(Text.literal(Language.get("command.madd.failed")))
                }
            }
        }
        return 1
    }
}

