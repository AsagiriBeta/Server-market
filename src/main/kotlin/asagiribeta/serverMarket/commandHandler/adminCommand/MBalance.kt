package asagiribeta.serverMarket.commandHandler.adminCommand

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.util.CommandSuggestions
import asagiribeta.serverMarket.util.MoneyFormat
import asagiribeta.serverMarket.util.PermissionUtil
import asagiribeta.serverMarket.util.whenCompleteOnServerThread
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text

/**
 * Admin command: /svm admin balance <player>
 *
 * Design choice:
 * - We only support players who have joined this server before (i.e., have a row in the balance table).
 * - This avoids any Mojang profile lookups and keeps the behavior deterministic.
 */
class MBalance {
    fun buildSubCommand(): LiteralArgumentBuilder<ServerCommandSource> {
        return CommandManager.literal("balance")
            .requires(PermissionUtil.require("servermarket.admin.balance", 4))
            .then(
                CommandManager.argument("player", StringArgumentType.string())
                    .suggests(CommandSuggestions.PLAYER_NAME_SUGGESTIONS)
                    .executes(this::execute)
            )
    }

    private fun execute(context: CommandContext<ServerCommandSource>): Int {
        val server = context.source.server
        val name = StringArgumentType.getString(context, "player")

        val db = ServerMarket.instance.database
        db.supplyAsync0 { db.playerLookupService.getUuidByPlayerName(name) }
            .whenCompleteOnServerThread(server) { uuid, ex ->
                if (ex != null) {
                    context.source.sendError(Text.translatable("servermarket.command.mbalance.failed"))
                    ServerMarket.LOGGER.error("/svm admin balance db-lookup failed. player={}", name, ex)
                    return@whenCompleteOnServerThread
                }

                if (uuid == null) {
                    context.source.sendError(Text.translatable("servermarket.command.mbalance.player_not_found"))
                    return@whenCompleteOnServerThread
                }

                ServerMarket.instance.transferService.getBalance(uuid)
                    .whenCompleteOnServerThread(server) { balance, ex2 ->
                        if (ex2 != null) {
                            context.source.sendError(Text.translatable("servermarket.command.mbalance.failed"))
                            ServerMarket.LOGGER.error("/svm admin balance failed. player={} uuid={}", name, uuid, ex2)
                            return@whenCompleteOnServerThread
                        }
                        context.source.sendMessage(
                            Text.translatable(
                                "servermarket.command.mbalance.success",
                                Text.literal(name),
                                uuid.toString(),
                                MoneyFormat.format(balance ?: 0.0, 2)
                            )
                        )
                    }
            }

        return 1
    }
}
