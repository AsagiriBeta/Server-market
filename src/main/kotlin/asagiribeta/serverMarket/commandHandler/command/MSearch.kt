package asagiribeta.serverMarket.commandHandler.command

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.util.CommandSuggestions
import asagiribeta.serverMarket.util.MoneyFormat
import asagiribeta.serverMarket.util.PermissionUtil
import asagiribeta.serverMarket.util.TextFormat
import asagiribeta.serverMarket.util.whenCompleteOnServerThread
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier

class MSearch {
    // 构建 /svm search 子命令
    fun buildSubCommand(): LiteralArgumentBuilder<ServerCommandSource> {
        return literal("search")
            .requires(PermissionUtil.require("servermarket.command.search", 0))
            .then(argument("item_id", StringArgumentType.greedyString())
                .suggests(CommandSuggestions.ITEM_ID_SUGGESTIONS)
                .executes(this::execute)
            )
    }

    private fun resolveDisplayName(input: String): String {
        val id = Identifier.tryParse(input) ?: return input
        if (!Registries.ITEM.containsId(id)) return input
        val stack = ItemStack(Registries.ITEM.get(id))
        return TextFormat.displayItemName(stack, input)
    }

    private fun execute(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val itemId = StringArgumentType.getString(context, "item_id")
        val db = ServerMarket.instance.database

        val displayQuery = resolveDisplayName(itemId)

        db.supplyAsync { db.marketRepository.searchForDisplay(itemId) }
            .whenCompleteOnServerThread(source.server) { items, ex ->
                if (ex != null) {
                    source.sendError(Text.translatable("servermarket.command.msearch.search_failed"))
                    ServerMarket.LOGGER.error("/svm search failed", ex)
                    return@whenCompleteOnServerThread
                }

                val list = items ?: emptyList()
                if (list.isEmpty()) {
                    source.sendMessage(Text.translatable("servermarket.command.msearch.not_found", displayQuery))
                    return@whenCompleteOnServerThread
                }

                source.sendMessage(
                    Text.translatable("servermarket.command.msearch.title", displayQuery)
                        .styled { it.withBold(true).withColor(0xA020F0) }
                )

                list.forEach { (_, _, sellerName, price, quantity) ->
                    val sellerTypeKey = if (sellerName == "SERVER")
                        "servermarket.command.msearch.system_market"
                    else
                        "servermarket.command.msearch.player_market"

                    source.sendMessage(
                        Text.literal("▸ ").append(Text.translatable(sellerTypeKey))
                            .append(Text.translatable("servermarket.ui.seller", sellerName).styled { it.withColor(0x00FF00) })
                            .append(Text.translatable("servermarket.ui.price", MoneyFormat.format(price, 2)).styled { it.withColor(0xFFA500) })
                            .append(Text.translatable("servermarket.ui.quantity", quantity.toString()).styled { it.withColor(0xADD8E6) })
                    )
                }
            }
        return 1
    }
}
