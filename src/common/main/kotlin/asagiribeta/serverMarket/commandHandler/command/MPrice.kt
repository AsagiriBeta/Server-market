package asagiribeta.serverMarket.commandHandler.command

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.CommandManager.argument
import com.mojang.brigadier.arguments.DoubleArgumentType
import asagiribeta.serverMarket.ServerMarket
import net.minecraft.registry.Registries
import asagiribeta.serverMarket.service.MarketService
import asagiribeta.serverMarket.util.ItemKey
import asagiribeta.serverMarket.util.MoneyFormat
import asagiribeta.serverMarket.util.PermissionUtil
import asagiribeta.serverMarket.util.whenCompleteOnServerThread
import net.minecraft.text.Text

class MPrice {
    /** /svm sell <price> — legacy name; prefer /svm price */
    fun buildSubCommand(): LiteralArgumentBuilder<ServerCommandSource> {
        return literal("sell")
            .requires(PermissionUtil.requirePlayer("servermarket.command.sell", 0))
            .then(priceArgument())
    }

    /** /svm price <price> — clearer alias for setting unit price */
    fun buildPriceAlias(): LiteralArgumentBuilder<ServerCommandSource> {
        return literal("price")
            .requires(PermissionUtil.requirePlayer("servermarket.command.sell", 0))
            .then(priceArgument())
    }

    private fun priceArgument() =
        argument("price", DoubleArgumentType.doubleArg(0.01))
            .executes(this::execute)

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

        val marketService = ServerMarket.instance.marketService
        val overviewService = ServerMarket.instance.marketOverviewService
        val itemId = Registries.ITEM.getId(itemStack.item).toString()
        val nbt = ItemKey.normalizeSnbt(ItemKey.snbtOf(itemStack))

        marketService.setListingPrice(
            playerUuid = player.uuid,
            playerName = player.name.string,
            itemId = itemId,
            nbt = nbt,
            price = price
        ).thenApply { result ->
            when (result) {
                MarketService.SetPriceResult.Added -> "add"
                MarketService.SetPriceResult.Updated -> "update"
                else -> result
            }
        }.thenCompose { marker ->
            if (marker is String) {
                ServerMarket.instance.database.supplyAsync {
                    marker to overviewService.getOverview(itemId, nbt)
                }
            } else {
                java.util.concurrent.CompletableFuture.completedFuture(marker to null)
            }
        }.whenCompleteOnServerThread(source.server) { payload, err ->
            if (err != null) {
                source.sendError(Text.translatable("servermarket.command.msell.operation_failed"))
                ServerMarket.LOGGER.error("/svm price failed", err)
                return@whenCompleteOnServerThread
            }

            val (marker, overview) = payload ?: return@whenCompleteOnServerThread
            when (marker) {
                "add" -> {
                    source.sendMessage(
                        Text.translatable(
                            "servermarket.command.msell.add_success",
                            itemStack.name,
                            MoneyFormat.format(price, 2)
                        )
                    )
                    source.sendMessage(Text.translatable("servermarket.command.msell.zero_stock_hint"))
                }
                "update" -> {
                    source.sendMessage(
                        Text.translatable(
                            "servermarket.command.msell.update_success",
                            itemStack.name,
                            MoneyFormat.format(price, 2)
                        )
                    )
                }
                MarketService.SetPriceResult.InvalidPrice -> {
                    source.sendError(Text.translatable("servermarket.command.msell.invalid_price"))
                }
                is MarketService.SetPriceResult.Error -> {
                    source.sendError(Text.translatable("servermarket.command.msell.operation_failed"))
                    ServerMarket.LOGGER.error("/svm price error: {}", marker.message)
                }
            }
            if (overview != null) {
                source.sendMessage(overviewService.formatListingHint(overview, price))
            }
        }

        return 1
    }
}
