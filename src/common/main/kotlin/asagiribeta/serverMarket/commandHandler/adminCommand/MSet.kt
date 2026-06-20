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

class MSet {
    fun buildSubCommand(): LiteralArgumentBuilder<ServerCommandSource> {
        return CommandManager.literal("set")
            .requires(PermissionUtil.require("servermarket.admin.set", 4))
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

        if (amount < 0) {
            context.source.sendError(Text.translatable("servermarket.command.mset.negative_amount"))
            return 0
        }

        val db = ServerMarket.instance.database
        db.supplyAsync0 { db.playerLookupService.getUuidByPlayerName(targetName) }
            .whenCompleteOnServerThread(server) { uuid, ex ->
                if (ex != null || uuid == null) {
                    context.source.sendError(
                        if (uuid == null) Text.translatable("servermarket.command.mbalance.player_not_found")
                        else Text.translatable("servermarket.command.mset.failed")
                    )
                    if (ex != null) ServerMarket.LOGGER.error("/svm admin set lookup failed", ex)
                    return@whenCompleteOnServerThread
                }

                ServerMarket.instance.economyService.setBalance(uuid, amount, reason = "admin_set")
                    .whenCompleteOnServerThread(server) { result, ex2 ->
                        if (ex2 != null || result?.success != true) {
                            context.source.sendError(Text.translatable("servermarket.command.mset.failed"))
                            if (ex2 != null) ServerMarket.LOGGER.error("/svm admin set failed", ex2)
                            return@whenCompleteOnServerThread
                        }
                        context.source.sendMessage(
                            Text.translatable(
                                "servermarket.command.mset.success",
                                targetName,
                                MoneyFormat.format(amount, 2)
                            )
                        )
                    }
            }
        return 1
    }
}
