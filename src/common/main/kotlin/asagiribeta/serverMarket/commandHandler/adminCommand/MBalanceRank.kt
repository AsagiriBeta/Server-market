package asagiribeta.serverMarket.commandHandler.adminCommand

import asagiribeta.serverMarket.menu.MarketGui
import asagiribeta.serverMarket.util.PermissionUtil
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text

/**
 * Admin command: /svm admin rank
 */
class MBalanceRank {
    fun buildSubCommand(): LiteralArgumentBuilder<ServerCommandSource> {
        return CommandManager.literal("rank")
            .requires(PermissionUtil.require("servermarket.admin.rank", 4))
            .executes { ctx ->
                val player = ctx.source.player ?: run {
                    ctx.source.sendError(Text.translatable("servermarket.error.player_only"))
                    return@executes 0
                }
                MarketGui(player).apply { showBalanceRank(true) }.open()
                1
            }
    }
}
