package asagiribeta.serverMarket.commandHandler

import com.mojang.brigadier.suggestion.SuggestionProvider
import net.minecraft.server.command.ServerCommandSource
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.CommandManager.argument
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.text.Text
import net.minecraft.registry.RegistryKeys
import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.util.Language

class MSearch {
    companion object {
        val ITEM_ID_SUGGESTIONS = SuggestionProvider<ServerCommandSource> { context, builder ->
            val server = context.source.server
            val registry = server.registryManager.get(RegistryKeys.ITEM)
            val remaining = builder.remaining.lowercase()
            // 遍历所有物品的 Identifier（如 minecraft:stone）进行建议
            registry.ids.forEach { id ->
                val idStr = id.toString()
                if (remaining.isEmpty() || idStr.contains(remaining)) {
                    builder.suggest(idStr)
                }
            }
            builder.buildFuture()
        }
    }

    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            literal("msearch")
                .then(argument("item_id", StringArgumentType.greedyString())
                    .suggests(ITEM_ID_SUGGESTIONS)  // 物品ID自动补全建议
                    .executes(this::execute)
                )
        )
    }

    private fun execute(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val itemId = StringArgumentType.getString(context, "item_id")
        val marketRepo = ServerMarket.instance.database.marketRepository

        return try {
            val items = marketRepo.searchForDisplay(itemId)  // 显示专用方法

            if (items.isEmpty()) {
                source.sendMessage(Text.literal(Language.get("command.msearch.not_found", itemId)))
                return 1
            }

            source.sendMessage(Text.literal(Language.get("command.msearch.title", itemId)).styled { 
                it.withBold(true)
                    .withColor(0xA020F0)
            })
            items.forEach { (_, sellerName, price, quantity) ->
                val sellerType = if (sellerName == "SERVER") 
                    Language.get("command.msearch.system_market")
                else 
                    Language.get("command.msearch.player_market")
                
                source.sendMessage(
                    Text.literal("▸ $sellerType")
                        .append(Text.literal(Language.get("ui.seller", sellerName)).styled { it.withColor(0x00FF00) })
                        .append(Text.literal(Language.get("ui.price", "%.2f".format(price))).styled { it.withColor(0xFFA500) })
                        .append(Text.literal(Language.get("ui.quantity", quantity.toString())).styled { it.withColor(0xADD8E6) }))
            }
            1
        } catch (e: Exception) {
            source.sendError(Text.literal(Language.get("command.msearch.search_failed")))
            ServerMarket.LOGGER.error("msearch命令执行失败", e)
            0
        }
    }
}
