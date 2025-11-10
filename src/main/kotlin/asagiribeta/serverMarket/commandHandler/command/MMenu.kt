package asagiribeta.serverMarket.commandHandler.command

import asagiribeta.serverMarket.menu.MarketGui
import asagiribeta.serverMarket.util.Language
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import asagiribeta.serverMarket.util.PermissionUtil

class MMenu {
    // 构建 /svm menu 子命令
    fun buildSubCommand(): LiteralArgumentBuilder<ServerCommandSource> {
        return literal("menu")
            .requires(PermissionUtil.requirePlayer("servermarket.command.menu", 0))
            .executes(this::openMenu)
    }

    private fun openMenu(ctx: CommandContext<ServerCommandSource>): Int {
        val player = ctx.source.player as? ServerPlayerEntity ?: run {
            ctx.source.sendError(Text.literal(Language.get("error.player_only")))
            return 0
        }

        // 使用新的基于 sgui 的 GUI
        MarketGui(player).open()

        return 1
    }
}
