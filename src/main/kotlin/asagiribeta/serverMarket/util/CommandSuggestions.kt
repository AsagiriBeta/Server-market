package asagiribeta.serverMarket.util

import com.mojang.brigadier.suggestion.SuggestionProvider
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.registry.Registries

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
}

