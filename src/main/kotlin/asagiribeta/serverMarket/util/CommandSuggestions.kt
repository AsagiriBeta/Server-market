package asagiribeta.serverMarket.util

import com.mojang.brigadier.suggestion.SuggestionProvider
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.registry.Registries
import asagiribeta.serverMarket.ServerMarket

object CommandSuggestions {
    val ITEM_ID_SUGGESTIONS: SuggestionProvider<ServerCommandSource> = SuggestionProvider { _, builder ->
        val remaining = builder.remaining.lowercase()
        Registries.ITEM.ids.forEach { id ->
            val idStr = id.toString()
            if (remaining.isEmpty() || idStr.contains(remaining)) {
                builder.suggest(idStr)
            }
        }
        builder.buildFuture()
    }

    // 新增：卖家补全（包含 "SERVER" 与玩家卖家名）
    val SELLER_SUGGESTIONS: SuggestionProvider<ServerCommandSource> = SuggestionProvider { _, builder ->
        val remaining = builder.remaining.lowercase()
        // 先建议 SERVER
        if ("server".contains(remaining)) builder.suggest("SERVER")
        // 列出数据库中出现过的卖家名
        runCatching {
            val names = ServerMarket.instance.database.marketRepository.getDistinctSellerNames()
            names.forEach { name ->
                if (remaining.isEmpty() || name.lowercase().contains(remaining)) {
                    builder.suggest(name)
                }
            }
        }
        builder.buildFuture()
    }
}
