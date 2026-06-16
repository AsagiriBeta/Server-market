package asagiribeta.serverMarket.commandHandler.adminCommand

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
 * 管理员系统收购命令：/svm edit purchase <price> `[limit]`
 *
 * 根据手持物品设置系统收购，可设置每日限额
 */
class APurchase {
    fun buildSubCommand(): LiteralArgumentBuilder<ServerCommandSource> {
        return literal("purchase")
            .requires(PermissionUtil.require("servermarket.admin.purchase", 4))
            .then(
                argument("price", DoubleArgumentType.doubleArg(0.01))
                    .executes(this::executeUnlimited)
                    .then(
                        argument("limit", IntegerArgumentType.integer(-1))
                            .executes(this::executeWithLimit)
                    )
            )
    }

    private fun executeUnlimited(context: CommandContext<ServerCommandSource>): Int {
        return execute(context, -1)
    }

    private fun executeWithLimit(context: CommandContext<ServerCommandSource>): Int {
        val limit = IntegerArgumentType.getInteger(context, "limit")
        return execute(context, limit)
    }

    private fun execute(context: CommandContext<ServerCommandSource>, limitPerDay: Int): Int {
        val player = context.source.player ?: run {
            context.source.sendError(Text.translatable("servermarket.command.apurchase.player_only"))
            return 0
        }

        val price = DoubleArgumentType.getDouble(context, "price")

        val mainHandStack = player.mainHandStack
        if (mainHandStack.isEmpty) {
            context.source.sendError(Text.translatable("servermarket.command.apurchase.hold_item"))
            return 0
        }

        val itemName = mainHandStack.name.string
        val itemId = Registries.ITEM.getId(mainHandStack.item).toString()
        val snbt = ItemKey.normalizeSnbt(ItemKey.snbtOf(mainHandStack))

        // 添加系统收购
        ServerMarket.instance.database.supplyAsync {
            val existed = ServerMarket.instance.database.purchaseRepository.hasSystemPurchase(itemId, snbt)
            ServerMarket.instance.database.purchaseRepository.addSystemPurchase(
                itemId = itemId,
                nbt = snbt,
                price = price,
                limitPerDay = limitPerDay
            )
            existed
        }.whenCompleteOnServerThread(context.source.server) { existed, ex ->
            if (ex != null) {
                context.source.sendError(Text.translatable("servermarket.command.apurchase.failed"))
                ServerMarket.LOGGER.error("/svm admin purchase failed", ex)
                return@whenCompleteOnServerThread
            }

            val limitStr = if (limitPerDay < 0) Text.translatable("servermarket.command.apurchase.unlimited") else Text.literal(limitPerDay.toString())
            val formattedPrice = MoneyFormat.format(price, 2)
            if (existed == true) {
                context.source.sendMessage(
                    Text.translatable("servermarket.command.apurchase.update_success", itemName, formattedPrice, limitStr)
                )
            } else {
                context.source.sendMessage(
                    Text.translatable("servermarket.command.apurchase.add_success", itemName, formattedPrice, limitStr)
                )
            }
        }

        return 1
    }
}
