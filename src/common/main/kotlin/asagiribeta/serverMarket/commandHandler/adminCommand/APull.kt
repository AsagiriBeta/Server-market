package asagiribeta.serverMarket.commandHandler.adminCommand

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.util.ItemKey
import asagiribeta.serverMarket.util.PermissionUtil
import asagiribeta.serverMarket.util.whenCompleteOnServerThread
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.registry.Registries
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text

class APull {
    fun buildSubCommand(): LiteralArgumentBuilder<ServerCommandSource> {
        return CommandManager.literal("pull")
            .requires(PermissionUtil.require("servermarket.admin.pull", 4))
            .executes(this::execute)
    }

    private fun execute(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val player = source.player ?: run {
            source.sendError(Text.translatable("servermarket.command.apull.player_only"))
            return 0
        }

        val itemStack = player.mainHandStack
        if (itemStack.isEmpty) {
            source.sendError(Text.translatable("servermarket.command.apull.hold_item"))
            return 0
        }

        val itemId = Registries.ITEM.getId(itemStack.item).toString()
        val nbt = ItemKey.normalizeSnbt(ItemKey.snbtOf(itemStack))

        ServerMarket.instance.marketService.removeSystemListing(itemId, nbt)
            .whenCompleteOnServerThread(source.server) { removed, ex ->
                if (ex != null) {
                    source.sendError(Text.translatable("servermarket.command.apull.operation_failed"))
                    ServerMarket.LOGGER.error("/svm admin pull failed", ex)
                    return@whenCompleteOnServerThread
                }
                if (removed != true) {
                    source.sendError(Text.translatable("servermarket.command.apull.not_listed"))
                    return@whenCompleteOnServerThread
                }
                source.sendMessage(Text.translatable("servermarket.command.apull.success", itemStack.name))
            }
        return 1
    }
}
