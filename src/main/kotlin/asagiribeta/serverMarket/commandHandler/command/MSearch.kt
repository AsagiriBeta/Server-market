package asagiribeta.serverMarket.commandHandler.command

import net.minecraft.server.command.ServerCommandSource
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.CommandManager.argument
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.text.Text
import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.util.Language
import asagiribeta.serverMarket.util.CommandSuggestions
import asagiribeta.serverMarket.util.PermissionUtil
import asagiribeta.serverMarket.util.whenCompleteOnServerThread

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

    private fun execute(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val itemId = StringArgumentType.getString(context, "item_id")
        val db = ServerMarket.instance.database

        db.supplyAsync { db.marketRepository.searchForDisplay(itemId) }
            .whenCompleteOnServerThread(source.server) { items, ex ->
                if (ex != null) {
                    source.sendError(Text.literal(Language.get("command.msearch.search_failed")))
                    ServerMarket.LOGGER.error("msearch命令执行失败", ex)
                    return@whenCompleteOnServerThread
                }

                val list = items ?: emptyList()
                if (list.isEmpty()) {
                    source.sendMessage(Text.literal(Language.get("command.msearch.not_found", itemId)))
                    return@whenCompleteOnServerThread
                }

                source.sendMessage(Text.literal(Language.get("command.msearch.title", itemId)).styled {
                    it.withBold(true).withColor(0xA020F0)
                })

                list.forEach { (_, _, sellerName, price, quantity) ->
                    val sellerType = if (sellerName == "SERVER")
                        Language.get("command.msearch.system_market")
                    else
                        Language.get("command.msearch.player_market")

                    source.sendMessage(
                        Text.literal("▸ $sellerType")
                            .append(Text.literal(Language.get("ui.seller", sellerName)).styled { it.withColor(0x00FF00) })
                            .append(Text.literal(Language.get("ui.price", "%.2f".format(price))).styled { it.withColor(0xFFA500) })
                            .append(Text.literal(Language.get("ui.quantity", quantity.toString())).styled { it.withColor(0xADD8E6) })
                    )
                }
            }
        return 1
    }
}
