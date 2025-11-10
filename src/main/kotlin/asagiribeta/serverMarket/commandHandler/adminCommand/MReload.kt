package asagiribeta.serverMarket.commandHandler.adminCommand

import asagiribeta.serverMarket.util.Config
import asagiribeta.serverMarket.util.Language
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import asagiribeta.serverMarket.util.PermissionUtil

class MReload {
    // 构建 /svm edit reload 子命令
    fun buildSubCommand(): LiteralArgumentBuilder<ServerCommandSource> {
        return CommandManager.literal("reload")
            .requires(PermissionUtil.require("servermarket.admin.reload", 4))
            .executes(this::execute)
    }

    private fun execute(context: CommandContext<ServerCommandSource>): Int {
        return try {
            Config.reloadConfig()
            context.source.sendMessage(Text.literal(Language.get("command.mreload.success")))
            1
        } catch (_: Exception) {
            context.source.sendError(Text.literal(Language.get("command.mreload.failed")))
            0
        }
    }
}
