package asagiribeta.serverMarket.commandHandler.adminCommand

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.util.MoneyFormat
import asagiribeta.serverMarket.util.whenCompleteOnServerThread
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.command.CommandSource
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import asagiribeta.serverMarket.util.PermissionUtil

class MAdd {
    // 构建 /svm edit add 子命令
    fun buildSubCommand(): LiteralArgumentBuilder<ServerCommandSource> {
        return CommandManager.literal("add")
            .requires(PermissionUtil.require("servermarket.admin.add", 4))
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
    }

    private fun execute(context: CommandContext<ServerCommandSource>): Int {
        val targetName = StringArgumentType.getString(context, "player")
        val amount = DoubleArgumentType.getDouble(context, "amount")
        val server = context.source.server
        val targetPlayer = server.playerManager.getPlayer(targetName) ?: run {
            context.source.sendError(Text.translatable("servermarket.command.madd.player_offline"))
            return 0
        }

        // 使用 TransferService 增加余额
        ServerMarket.instance.transferService.addBalance(targetPlayer.uuid, amount)
            .whenCompleteOnServerThread(server) { success, ex ->
                if (ex != null) {
                    context.source.sendError(Text.translatable("servermarket.command.madd.failed"))
                    ServerMarket.LOGGER.error("madd命令执行失败", ex)
                } else if (success == true) {
                    val sign = if (amount >= 0) "+" else ""
                    val formatted = sign + MoneyFormat.format(kotlin.math.abs(amount), 2)

                    context.source.sendMessage(
                        Text.translatable("servermarket.command.madd.success", formatted, targetPlayer.name)
                    )
                    targetPlayer.sendMessage(
                        Text.translatable("servermarket.command.madd.received", formatted)
                    )
                } else {
                    context.source.sendError(Text.translatable("servermarket.command.madd.failed"))
                }
            }
        return 1
    }
}
