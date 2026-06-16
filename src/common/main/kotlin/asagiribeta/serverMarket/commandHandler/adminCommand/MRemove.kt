package asagiribeta.serverMarket.commandHandler.adminCommand

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.util.CommandSuggestions
import asagiribeta.serverMarket.util.MoneyFormat
import asagiribeta.serverMarket.util.PermissionUtil
import asagiribeta.serverMarket.util.whenCompleteOnServerThread
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text

/**
 * Admin command: /svm admin remove <player> <amount>
 *
 * Deducts balance from a player. Supports offline players who have joined before.
 */
class MRemove {
    fun buildSubCommand(): LiteralArgumentBuilder<ServerCommandSource> {
        return CommandManager.literal("remove")
            .requires(PermissionUtil.require("servermarket.admin.remove", 4))
            .then(
                CommandManager.argument("player", StringArgumentType.string())
                    .suggests(CommandSuggestions.PLAYER_NAME_SUGGESTIONS)
                    .then(
                        CommandManager.argument("amount", DoubleArgumentType.doubleArg(0.0))
                            .executes(this::execute)
                    )
            )
    }

    private fun execute(context: CommandContext<ServerCommandSource>): Int {
        val targetName = StringArgumentType.getString(context, "player")
        val amount = DoubleArgumentType.getDouble(context, "amount")
        val server = context.source.server

        if (amount <= 0.0) {
            context.source.sendError(Text.translatable("servermarket.command.mremove.invalid_amount"))
            return 0
        }

        val db = ServerMarket.instance.database
        db.supplyAsync0 { db.playerLookupService.getUuidByPlayerName(targetName) }
            .whenCompleteOnServerThread(server) { uuid, ex ->
                if (ex != null) {
                    context.source.sendError(Text.translatable("servermarket.command.mremove.failed"))
                    ServerMarket.LOGGER.error("/svm admin remove db-lookup failed. player={}", targetName, ex)
                    return@whenCompleteOnServerThread
                }

                if (uuid == null) {
                    context.source.sendError(Text.translatable("servermarket.command.mremove.player_not_found"))
                    return@whenCompleteOnServerThread
                }

                ServerMarket.instance.economyService.withdraw(
                    uuid, amount, reason = "admin_remove",
                    history = asagiribeta.serverMarket.service.EconomyService.HistoryContext(
                        fromId = uuid, fromType = "player", fromName = targetName,
                        toId = ServerMarket.instance.economyService.systemUuid,
                        toType = "system", toName = "ADMIN",
                        price = amount, item = "admin_remove"
                    )
                ).whenCompleteOnServerThread(server) { result, ex2 ->
                    if (ex2 != null || result?.success != true) {
                        val msg = if (result?.error == "Insufficient funds") {
                            Text.translatable("servermarket.command.mremove.insufficient_funds")
                        } else {
                            Text.translatable("servermarket.command.mremove.failed")
                        }
                        context.source.sendError(msg)
                        if (ex2 != null) {
                            ServerMarket.LOGGER.error("/svm admin remove failed. player={} uuid={}", targetName, uuid, ex2)
                        }
                        return@whenCompleteOnServerThread
                    }

                    val formatted = MoneyFormat.format(amount, 2)
                    context.source.sendMessage(
                        Text.translatable("servermarket.command.mremove.success", formatted, targetName)
                    )
                    server.playerManager.getPlayer(uuid)?.sendMessage(
                        Text.translatable("servermarket.command.mremove.notified", formatted)
                    )
                }
            }

        return 1
    }
}
