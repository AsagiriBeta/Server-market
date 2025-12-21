package asagiribeta.serverMarket.commandHandler.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.CommandManager.argument
import com.mojang.brigadier.arguments.DoubleArgumentType
import net.minecraft.text.Text
import asagiribeta.serverMarket.ServerMarket
import net.minecraft.registry.Registries
import asagiribeta.serverMarket.util.Language
import asagiribeta.serverMarket.util.ItemKey
import asagiribeta.serverMarket.util.PermissionUtil
import asagiribeta.serverMarket.util.whenCompleteOnServerThread

class MPrice {
    // 构建 /svm price 子命令
    fun buildSubCommand(): LiteralArgumentBuilder<ServerCommandSource> {
        return literal("price")
            .requires(PermissionUtil.requirePlayer("servermarket.command.price", 0))
            .then(argument("price", DoubleArgumentType.doubleArg(0.0))
                .executes(this::execute)
            )
    }

    internal fun execute(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val player = source.player ?: run {
            source.sendError(Text.literal(Language.get("command.mprice.player_only")))
            return 0
        }

        val price = DoubleArgumentType.getDouble(context, "price")
        val itemStack = player.mainHandStack
        if (itemStack.isEmpty) {
            source.sendError(Text.literal(Language.get("command.mprice.hold_item")))
            return 0
        }

        val db = ServerMarket.instance.database
        val itemId = Registries.ITEM.getId(itemStack.item).toString()
        val nbt = ItemKey.snbtOf(itemStack)

        db.supplyAsync { _ ->
            val marketRepo = db.marketRepository
            if (!marketRepo.hasPlayerItem(player.uuid, itemId, nbt)) {
                marketRepo.addPlayerItem(
                    sellerUuid = player.uuid,
                    sellerName = player.name.string,
                    itemId = itemId,
                    nbt = nbt,
                    price = price
                )
                "add"
            } else {
                marketRepo.updatePlayerItemPrice(player.uuid, itemId, nbt, price)
                "update"
            }
        }.whenCompleteOnServerThread(source.server) { op, err ->
            if (err != null) {
                source.sendError(Text.literal(Language.get("command.mprice.operation_failed")))
                ServerMarket.LOGGER.error("mprice命令执行失败", err)
                return@whenCompleteOnServerThread
            }

            if (op == "add") {
                source.sendMessage(Text.literal(Language.get("command.mprice.add_success", itemStack.name.string, price)))
            } else {
                source.sendMessage(Text.literal(Language.get("command.mprice.update_success", itemStack.name.string, price)))
            }
        }

        return 1
    }
}
