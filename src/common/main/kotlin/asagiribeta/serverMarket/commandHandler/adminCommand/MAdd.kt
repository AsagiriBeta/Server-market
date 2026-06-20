package asagiribeta.serverMarket.commandHandler.adminCommand

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.util.CommandSuggestions
import asagiribeta.serverMarket.util.MoneyFormat
import asagiribeta.serverMarket.util.PermissionUtil
import asagiribeta.serverMarket.util.whenCompleteOnServerThread
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text

class MAdd {
    fun buildSubCommand(): LiteralArgumentBuilder<ServerCommandSource> {
        return CommandManager.literal("add")
            .requires(PermissionUtil.require("servermarket.admin.add", 4))
            .then(
                CommandManager.argument("player", StringArgumentType.string())
                    .suggests(CommandSuggestions.PLAYER_NAME_SUGGESTIONS)
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
        val db = ServerMarket.instance.database

        db.supplyAsync0 { db.playerLookupService.getUuidByPlayerName(targetName) }
            .whenCompleteOnServerThread(server) { uuid, ex ->
                if (ex != null || uuid == null) {
                    context.source.sendError(
                        if (uuid == null) Text.translatable("servermarket.command.mbalance.player_not_found")
                        else Text.translatable("servermarket.command.madd.failed")
                    )
                    if (ex != null) ServerMarket.LOGGER.error("/svm admin add lookup failed", ex)
                    return@whenCompleteOnServerThread
                }

                ServerMarket.instance.economyService.deposit(uuid, amount, reason = "admin_add")
                    .whenCompleteOnServerThread(server) { result, ex2 ->
                        if (ex2 != null || result?.success != true) {
                            context.source.sendError(Text.translatable("servermarket.command.madd.failed"))
                            if (ex2 != null) ServerMarket.LOGGER.error("/svm admin add failed", ex2)
                            return@whenCompleteOnServerThread
                        }

                        val sign = if (amount >= 0) "+" else ""
                        val formatted = sign + MoneyFormat.format(kotlin.math.abs(amount), 2)
                        context.source.sendMessage(
                            Text.translatable("servermarket.command.madd.success", formatted, targetName)
                        )
                        server.playerManager.getPlayer(uuid)?.sendMessage(
                            Text.translatable("servermarket.command.madd.received", formatted)
                        )
                    }
            }
        return 1
    }
}
