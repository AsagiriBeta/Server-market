package asagiribeta.serverMarket.commandHandler.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.text.Text
import asagiribeta.serverMarket.ServerMarket
import net.minecraft.registry.Registries
import asagiribeta.serverMarket.util.Language
import asagiribeta.serverMarket.util.ItemKey
import asagiribeta.serverMarket.util.PermissionUtil

class MPull {
    // 构建 /svm pull 子命令
    fun buildSubCommand(): LiteralArgumentBuilder<ServerCommandSource> {
        return literal("pull")
            .requires(PermissionUtil.requirePlayer("servermarket.command.pull", 0))
            .executes(this::execute)
    }

    fun execute(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val player = source.player ?: run {
            source.sendError(Text.literal(Language.get("command.mpull.player_only")))
            return 0
        }

        val itemStack = player.mainHandStack
        if (itemStack.isEmpty) {
            source.sendError(Text.literal(Language.get("command.mpull.hold_item")))
            return 0
        }

        val itemId = Registries.ITEM.getId(itemStack.item).toString()
        val nbt = ItemKey.snbtOf(itemStack)
        val itemName = itemStack.name.string

        // Use MarketService to remove item from sale
        ServerMarket.instance.marketService.removeItemFromSale(
            playerUuid = player.uuid,
            itemId = itemId,
            nbt = nbt,
            quantity = Int.MAX_VALUE  // Remove all
        ).whenComplete { returnedQuantity, ex ->
            source.server.execute {
                if (ex != null) {
                    source.sendError(Text.literal(Language.get("command.mpull.operation_failed")))
                    ServerMarket.LOGGER.error("mpull命令执行失败", ex)
                    return@execute
                }

                if (returnedQuantity <= 0) {
                    source.sendError(Text.literal(Language.get("command.mpull.not_listed")))
                    return@execute
                }

                // Return items to player
                val returnStack = itemStack.copy().apply { count = returnedQuantity }
                player.giveItemStack(returnStack)

                source.sendMessage(
                    Text.literal(
                        Language.get("command.mpull.success", itemName, returnedQuantity)
                    )
                )
            }
        }
        return 1
    }
}
