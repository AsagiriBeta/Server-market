package asagiribeta.serverMarket.commandHandler.adminCommand

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.util.Language
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.registry.Registries
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import asagiribeta.serverMarket.util.ItemKey
import asagiribeta.serverMarket.util.PermissionUtil
import asagiribeta.serverMarket.util.whenCompleteOnServerThread

class APull {
    // 构建 /svm edit pull 子命令
    fun buildSubCommand(): LiteralArgumentBuilder<ServerCommandSource> {
        return CommandManager.literal("pull")
            .requires(PermissionUtil.require("servermarket.admin.pull", 4))
            .executes(this::execute)
    }

    private fun execute(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val player = source.player ?: run {
            source.sendError(Text.literal(Language.get("command.apull.player_only")))
            return 0
        }

        val itemStack = player.mainHandStack
        if (itemStack.isEmpty) {
            source.sendError(Text.literal(Language.get("command.apull.hold_item")))
            return 0
        }

        val itemId = Registries.ITEM.getId(itemStack.item).toString()
        val nbt = ItemKey.snbtOf(itemStack)
        val db = ServerMarket.instance.database
        val repo = db.marketRepository

        db.supplyAsync { repo.hasSystemItem(itemId, nbt) }
            .whenCompleteOnServerThread(source.server) { exists, ex ->
                if (ex != null) {
                    source.sendError(Text.literal(Language.get("command.apull.operation_failed")))
                    ServerMarket.LOGGER.error("apull命令执行失败", ex)
                    return@whenCompleteOnServerThread
                }
                if (exists != true) {
                    source.sendError(Text.literal(Language.get("command.apull.not_listed")))
                    return@whenCompleteOnServerThread
                }

                db.runAsync { repo.removeSystemItem(itemId, nbt) }
                    .whenCompleteOnServerThread(source.server) { _, ex2 ->
                        if (ex2 != null) {
                            source.sendError(Text.literal(Language.get("command.apull.operation_failed")))
                            ServerMarket.LOGGER.error("apull命令删除失败", ex2)
                        } else {
                            source.sendMessage(Text.literal(Language.get("command.apull.success", itemStack.name.string)))
                        }
                    }
            }
        return 1
    }
}
