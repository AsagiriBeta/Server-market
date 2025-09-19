package asagiribeta.serverMarket.util

import com.mojang.brigadier.suggestion.SuggestionProvider
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.registry.Registries
import asagiribeta.serverMarket.ServerMarket
import java.util.concurrent.atomic.AtomicLong

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

    // 缓存卖家名称，避免在建议阶段阻塞主线程访问数据库
    @Volatile private var cachedSellerNames: List<String> = emptyList()
    private val lastRefreshNanos = AtomicLong(0)
    private const val REFRESH_INTERVAL_NANOS: Long = 60_000_000_000 // 60s

    private fun refreshSellerCacheIfStale() {
        val now = System.nanoTime()
        val last = lastRefreshNanos.get()
        if (now - last < REFRESH_INTERVAL_NANOS) return
        if (!lastRefreshNanos.compareAndSet(last, now)) return
        // 后台刷新缓存
        val db = ServerMarket.instance.database
        db.supplyAsync { db.marketRepository.getDistinctSellerNames() }
            .exceptionally { emptyList() }
            .thenAccept { names -> if (names != null) cachedSellerNames = names }
    }

    // 新增：卖家补全（包含 "SERVER" 与玩家卖家名），非阻塞
    val SELLER_SUGGESTIONS: SuggestionProvider<ServerCommandSource> = SuggestionProvider { _, builder ->
        val remaining = builder.remaining.lowercase()
        refreshSellerCacheIfStale()
        // 先建议 SERVER
        if ("server".contains(remaining)) builder.suggest("SERVER")
        // 使用缓存，不阻塞
        val names = cachedSellerNames
        names.forEach { name ->
            if (remaining.isEmpty() || name.lowercase().contains(remaining)) {
                builder.suggest(name)
            }
        }
        builder.buildFuture()
    }
}
