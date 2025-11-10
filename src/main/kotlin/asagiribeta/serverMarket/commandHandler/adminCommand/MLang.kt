package asagiribeta.serverMarket.commandHandler.adminCommand

import asagiribeta.serverMarket.util.Language
import asagiribeta.serverMarket.util.PermissionUtil
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.command.CommandSource
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text

class MLang {
    // 构建 /svm edit lang 子命令
    fun buildSubCommand(): LiteralArgumentBuilder<ServerCommandSource> {
        return CommandManager.literal("lang")
            .requires(PermissionUtil.require("servermarket.admin.lang", 4))
            .then(
                CommandManager.argument("lang", StringArgumentType.word())
                    .suggests { _, builder ->
                        CommandSource.suggestMatching(listOf("zh", "en"), builder)
                    }
                    .executes(this::execute)
            )
    }

    private fun execute(context: CommandContext<ServerCommandSource>): Int {
        val lang = StringArgumentType.getString(context, "lang")
        return if (Language.setLanguage(lang)) {
            context.source.sendMessage(Text.literal(Language.get("command.mlang.success", lang)))
            1
        } else {
            context.source.sendError(Text.literal(Language.get("command.mlang.invalid")))
            0
        }
    }
}
