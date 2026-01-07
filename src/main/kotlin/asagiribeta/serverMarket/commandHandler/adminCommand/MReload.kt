package asagiribeta.serverMarket.commandHandler.adminCommand

import asagiribeta.serverMarket.util.Config
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
            context.source.sendMessage(Text.translatable("servermarket.command.mreload.success"))
            1
        } catch (_: Exception) {
            context.source.sendError(Text.translatable("servermarket.command.mreload.failed"))
            0
        }
    }
}
