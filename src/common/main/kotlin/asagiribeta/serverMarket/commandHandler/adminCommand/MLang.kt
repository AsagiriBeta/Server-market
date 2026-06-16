package asagiribeta.serverMarket.commandHandler.adminCommand

import asagiribeta.serverMarket.util.PermissionUtil
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource

class MLang {
    // Deprecated: per-player language is controlled by the client and Server Translations API.
    // Keep the node hidden/removed to avoid confusion.
    fun buildSubCommand(): LiteralArgumentBuilder<ServerCommandSource> {
        return CommandManager.literal("lang")
            .requires(PermissionUtil.require("servermarket.admin.lang", 4))
            .executes { 0 }
    }
}
