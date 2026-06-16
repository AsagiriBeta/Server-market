package asagiribeta.serverMarket.commandHandler.command

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.util.CommandSuggestions
import asagiribeta.serverMarket.util.MoneyFormat
import asagiribeta.serverMarket.util.PermissionUtil
import asagiribeta.serverMarket.util.whenCompleteOnServerThread
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MHistory {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT)

    fun buildSubCommand(): LiteralArgumentBuilder<ServerCommandSource> {
        return literal("history")
            .requires(PermissionUtil.requirePlayer("servermarket.command.history", 0))
            .executes { ctx -> execute(ctx, ctx.source.player!!.uuid, 1) }
            .then(
                argument("page", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                    .executes { ctx ->
                        val page = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "page")
                        execute(ctx, ctx.source.player!!.uuid, page)
                    }
            )
    }

    fun buildAdminSubCommand(): LiteralArgumentBuilder<ServerCommandSource> {
        return literal("history")
            .requires(PermissionUtil.require("servermarket.admin.history", 4))
            .then(
                argument("player", StringArgumentType.string())
                    .suggests(CommandSuggestions.PLAYER_NAME_SUGGESTIONS)
                    .executes { ctx ->
                        val name = StringArgumentType.getString(ctx, "player")
                        lookupAndShow(ctx, name, 1)
                        1
                    }
                    .then(
                        argument("page", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                            .executes { ctx ->
                                val name = StringArgumentType.getString(ctx, "player")
                                val page = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "page")
                                lookupAndShow(ctx, name, page)
                                1
                            }
                    )
            )
    }

    private fun lookupAndShow(ctx: CommandContext<ServerCommandSource>, playerName: String, page: Int) {
        val db = ServerMarket.instance.database
        db.supplyAsync0 { db.playerLookupService.getUuidByPlayerName(playerName) }
            .whenCompleteOnServerThread(ctx.source.server) { uuid, ex ->
                if (ex != null || uuid == null) {
                    ctx.source.sendError(Text.translatable("servermarket.command.mhistory.player_not_found"))
                    return@whenCompleteOnServerThread
                }
                execute(ctx, uuid, page, playerName)
            }
    }

    private fun execute(ctx: CommandContext<ServerCommandSource>, uuid: java.util.UUID, page: Int, displayName: String? = null): Int {
        ServerMarket.instance.economyService.getHistory(uuid, page, 10)
            .whenCompleteOnServerThread(ctx.source.server) { records, ex ->
                if (ex != null) {
                    ctx.source.sendError(Text.translatable("servermarket.command.mhistory.failed"))
                    return@whenCompleteOnServerThread
                }
                val list = records ?: emptyList()
                if (list.isEmpty()) {
                    ctx.source.sendMessage(Text.translatable("servermarket.command.mhistory.empty"))
                    return@whenCompleteOnServerThread
                }
                ctx.source.sendMessage(
                    Text.translatable(
                        "servermarket.command.mhistory.header",
                        displayName ?: ctx.source.player?.name?.string ?: uuid.toString(),
                        page.toString()
                    )
                )
                for (rec in list) {
                    val time = dateFormat.format(Date(rec.dtg))
                    val amount = MoneyFormat.format(rec.amount, 2)
                    ctx.source.sendMessage(
                        Text.translatable(
                            "servermarket.command.mhistory.entry",
                            time,
                            rec.fromName,
                            rec.toName,
                            amount,
                            rec.item ?: "-"
                        )
                    )
                }
            }
        return 1
    }
}
