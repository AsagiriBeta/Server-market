package asagiribeta.serverMarket.commandHandler

import com.mojang.brigadier.suggestion.SuggestionProvider
import net.minecraft.server.command.ServerCommandSource
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.CommandManager.argument
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.text.Text
import asagiribeta.serverMarket.ServerMarket

class MSearch {
    companion object {
        val ITEM_ID_SUGGESTIONS = SuggestionProvider<ServerCommandSource> { context, builder ->
            val server = context.source.server
            val registryManager = server.registryManager
            val itemRegistry = registryManager.get(net.minecraft.registry.RegistryKeys.ITEM)
            val remaining = builder.remaining.lowercase()
            itemRegistry.ids.forEach { identifier ->
                val idStr = identifier.toString()
                if (idStr.contains(remaining)) {
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
                source.sendMessage(Text.literal("没有找到物品 $itemId 的销售信息"))
                return 1
            }

            source.sendMessage(Text.literal("=== 全服 $itemId 销售列表 ===").styled { 
                it.withBold(true)
                    .withColor(0xA020F0)
            })
            items.forEach { (_, sellerName, price, quantity) ->
                val sellerType = if (sellerName == "SERVER") "系统市场" else "玩家市场"
                source.sendMessage(
                    Text.literal("▸ $sellerType")
                        .append(Text.literal(" 卖家: $sellerName").styled { it.withColor(0x00FF00) })
                        .append(Text.literal(" 单价: ${"%.2f".format(price)}").styled { it.withColor(0xFFA500) })
                        .append(Text.literal(" 数量: $quantity").styled { it.withColor(0xADD8E6) }))
            }
            1
        } catch (e: Exception) {
            source.sendError(Text.literal("搜索失败"))
            ServerMarket.LOGGER.error("msearch命令执行失败", e)
            0
        }
    }
}