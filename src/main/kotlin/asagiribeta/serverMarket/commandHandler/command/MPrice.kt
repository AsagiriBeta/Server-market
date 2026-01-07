package asagiribeta.serverMarket.commandHandler.command

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.CommandManager.argument
import com.mojang.brigadier.arguments.DoubleArgumentType
import asagiribeta.serverMarket.ServerMarket
import net.minecraft.registry.Registries
import asagiribeta.serverMarket.util.ItemKey
import asagiribeta.serverMarket.util.MoneyFormat
import asagiribeta.serverMarket.util.PermissionUtil
import asagiribeta.serverMarket.util.whenCompleteOnServerThread
import net.minecraft.text.Text

class MPrice {
    // 构建 /svm sell 子命令（原 /svm price）
    fun buildSubCommand(): LiteralArgumentBuilder<ServerCommandSource> {
        return literal("sell")
            .requires(PermissionUtil.requirePlayer("servermarket.command.sell", 0))
            .then(argument("price", DoubleArgumentType.doubleArg(0.0))
                .executes(this::execute)
            )
    }

    internal fun execute(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val player = source.player ?: run {
            source.sendError(Text.translatable("servermarket.command.msell.player_only"))
            return 0
        }

        val price = DoubleArgumentType.getDouble(context, "price")
        val itemStack = player.mainHandStack
        if (itemStack.isEmpty) {
            source.sendError(Text.translatable("servermarket.command.msell.hold_item"))
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
                source.sendError(Text.translatable("servermarket.command.msell.operation_failed"))
                ServerMarket.LOGGER.error("msell命令执行失败", err)
                return@whenCompleteOnServerThread
            }

            if (op == "add") {
                source.sendMessage(
                    Text.translatable(
                        "servermarket.command.msell.add_success",
                        itemStack.name,
                        MoneyFormat.format(price, 2)
                    )
                )
            } else {
                source.sendMessage(
                    Text.translatable(
                        "servermarket.command.msell.update_success",
                        itemStack.name,
                        MoneyFormat.format(price, 2)
                    )
                )
            }
        }

        return 1
    }
}
