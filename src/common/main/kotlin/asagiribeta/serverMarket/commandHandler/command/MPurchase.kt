package asagiribeta.serverMarket.commandHandler.command

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.util.ItemKey
import asagiribeta.serverMarket.util.MoneyFormat
import asagiribeta.serverMarket.util.PermissionUtil
import asagiribeta.serverMarket.util.whenCompleteOnServerThread
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.registry.Registries
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text

/**
 * 玩家收购命令：/svm purchase <price> <amount>
 */
class MPurchase {
    /**
     * Builds the argument subtree (price -> amount) for use by both /svm purchase and its alias.
     */
    fun buildArgs(): com.mojang.brigadier.builder.ArgumentBuilder<ServerCommandSource, *> {
        return argument("price", DoubleArgumentType.doubleArg(0.01))
            .then(
                argument("amount", IntegerArgumentType.integer(1))
                    .executes(this::execute)
            )
    }

    fun buildSubCommand(): LiteralArgumentBuilder<ServerCommandSource> {
        return literal("purchase")
            .requires(PermissionUtil.requirePlayer("servermarket.command.purchase", 0))
            .then(buildArgs())
    }

    private fun execute(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: run {
            context.source.sendError(Text.translatable("servermarket.command.mpurchase.player_only"))
            return 0
        }

        val price = DoubleArgumentType.getDouble(context, "price")
        val amount = IntegerArgumentType.getInteger(context, "amount")

        val mainHandStack = player.mainHandStack
        if (mainHandStack.isEmpty) {
            context.source.sendError(Text.translatable("servermarket.command.mpurchase.hold_item"))
            return 0
        }

        val itemName = mainHandStack.name.string
        val itemId = Registries.ITEM.getId(mainHandStack.item).toString()
        val snbt = ItemKey.normalizeSnbt(ItemKey.snbtOf(mainHandStack))

        // 添加收购订单
        ServerMarket.instance.database.supplyAsync {
            ServerMarket.instance.database.purchaseRepository.addPlayerPurchase(
                buyerUuid = player.uuid,
                buyerName = player.name.string,
                itemId = itemId,
                nbt = snbt,
                price = price,
                targetAmount = amount
            )
        }.whenCompleteOnServerThread(context.source.server) { _, ex ->
            if (ex != null) {
                context.source.sendError(Text.translatable("servermarket.command.mpurchase.failed"))
                ServerMarket.LOGGER.error("/svm purchase-order create failed", ex)
                return@whenCompleteOnServerThread
            }

            context.source.sendMessage(
                Text.translatable(
                    "servermarket.command.mpurchase.success",
                    itemName,
                    MoneyFormat.format(price, 2),
                    amount
                )
            )
        }

        return 1
    }
}
