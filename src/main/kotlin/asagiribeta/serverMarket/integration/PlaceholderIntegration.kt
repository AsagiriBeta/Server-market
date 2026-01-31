package asagiribeta.serverMarket.integration

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.repository.BalanceRepository
import asagiribeta.serverMarket.util.MoneyFormat
import eu.pb4.placeholders.api.PlaceholderContext
import eu.pb4.placeholders.api.PlaceholderResult
import eu.pb4.placeholders.api.Placeholders
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

object PlaceholderIntegration {
    private const val REFRESH_INTERVAL_MS = 1000L
    private const val TOP_BALANCE_LIMIT = 10

    private data class Cached<T>(val value: T, val atMs: Long)

    private val balanceCache = ConcurrentHashMap<UUID, Cached<Double>>()
    private val parcelCountCache = ConcurrentHashMap<UUID, Cached<Int>>()
    private val refreshingBalance = ConcurrentHashMap<UUID, AtomicBoolean>()
    private val refreshingParcels = ConcurrentHashMap<UUID, AtomicBoolean>()
    @Volatile
    private var topBalanceCache: Cached<List<BalanceRepository.BalanceRankEntry>>? = null
    private val refreshingTopBalances = AtomicBoolean(false)

    fun register() {
        // %server-market:balance% (requires player)
        Placeholders.register(Identifier.of("server-market", "balance")) { ctx, _ ->
            val player = ctx.player ?: return@register PlaceholderResult.invalid("No player")
            val value = getBalanceCached(player.uuid)
            if (value == null) PlaceholderResult.value("...")
            else PlaceholderResult.value(MoneyFormat.format(value, 2))
        }

        // %server-market:balance_short% (requires player)
        Placeholders.register(Identifier.of("server-market", "balance_short")) { ctx, _ ->
            val player = ctx.player ?: return@register PlaceholderResult.invalid("No player")
            val value = getBalanceCached(player.uuid)
            if (value == null) PlaceholderResult.value("...")
            else PlaceholderResult.value(MoneyFormat.formatShort(value))
        }

        // %server-market:parcel_count% (requires player)
        Placeholders.register(Identifier.of("server-market", "parcel_count")) { ctx, _ ->
            val player = ctx.player ?: return@register PlaceholderResult.invalid("No player")
            val value = getParcelCountCached(player.uuid)
            PlaceholderResult.value((value ?: "...").toString())
        }

        // %server-market:player_name% (requires player)
        Placeholders.register(Identifier.of("server-market", "player_name")) { ctx, _ ->
            val player = ctx.player ?: return@register PlaceholderResult.invalid("No player")
            PlaceholderResult.value(player.name)
        }

        // %server-market:top_name:<rank>% (rank 1-10)
        Placeholders.register(Identifier.of("server-market", "top_name")) { _, arg ->
            val rank = parseRank(arg) ?: return@register PlaceholderResult.invalid("Invalid rank")
            val list = getTopBalancesCached(TOP_BALANCE_LIMIT) ?: return@register PlaceholderResult.value("...")
            val entry = list.getOrNull(rank - 1) ?: return@register PlaceholderResult.value("-")
            PlaceholderResult.value(entry.name.ifBlank { entry.uuid.toString() })
        }

        // %server-market:top_balance:<rank>% (rank 1-10)
        Placeholders.register(Identifier.of("server-market", "top_balance")) { _, arg ->
            val rank = parseRank(arg) ?: return@register PlaceholderResult.invalid("Invalid rank")
            val list = getTopBalancesCached(TOP_BALANCE_LIMIT) ?: return@register PlaceholderResult.value("...")
            val entry = list.getOrNull(rank - 1) ?: return@register PlaceholderResult.value("-")
            PlaceholderResult.value(MoneyFormat.format(entry.balance, 2))
        }

        // %server-market:top_balance_short:<rank>% (rank 1-10)
        Placeholders.register(Identifier.of("server-market", "top_balance_short")) { _, arg ->
            val rank = parseRank(arg) ?: return@register PlaceholderResult.invalid("Invalid rank")
            val list = getTopBalancesCached(TOP_BALANCE_LIMIT) ?: return@register PlaceholderResult.value("...")
            val entry = list.getOrNull(rank - 1) ?: return@register PlaceholderResult.value("-")
            PlaceholderResult.value(MoneyFormat.formatShort(entry.balance))
        }
    }

    fun parse(input: Text, ctx: PlaceholderContext): Text = Placeholders.parseText(input, ctx)

    private fun parseRank(arg: String?): Int? {
        val rank = arg?.toIntOrNull() ?: return null
        return if (rank in 1..TOP_BALANCE_LIMIT) rank else null
    }

    private fun getTopBalancesCached(limit: Int): List<BalanceRepository.BalanceRankEntry>? {
        val now = System.currentTimeMillis()
        val cached = topBalanceCache
        if (cached != null && now - cached.atMs <= REFRESH_INTERVAL_MS) return cached.value

        if (refreshingTopBalances.compareAndSet(false, true)) {
            ServerMarket.instance.database.supplyAsync0 {
                ServerMarket.instance.database.getTopBalances(limit)
            }.whenComplete { v, _ ->
                if (v != null) topBalanceCache = Cached(v, System.currentTimeMillis())
                refreshingTopBalances.set(false)
            }
        }

        return cached?.value
    }

    private fun getBalanceCached(uuid: UUID): Double? {
        val now = System.currentTimeMillis()
        val cached = balanceCache[uuid]
        if (cached != null && now - cached.atMs <= REFRESH_INTERVAL_MS) return cached.value

        refreshingBalance.computeIfAbsent(uuid) { AtomicBoolean(false) }.let { flag ->
            if (flag.compareAndSet(false, true)) {
                ServerMarket.instance.database.supplyAsync0 {
                    ServerMarket.instance.database.getBalance(uuid)
                }.whenComplete { v, _ ->
                    if (v != null) balanceCache[uuid] = Cached(v, System.currentTimeMillis())
                    flag.set(false)
                }
            }
        }

        return cached?.value
    }

    private fun getParcelCountCached(uuid: UUID): Int? {
        val now = System.currentTimeMillis()
        val cached = parcelCountCache[uuid]
        if (cached != null && now - cached.atMs <= REFRESH_INTERVAL_MS) return cached.value

        refreshingParcels.computeIfAbsent(uuid) { AtomicBoolean(false) }.let { flag ->
            if (flag.compareAndSet(false, true)) {
                ServerMarket.instance.database.supplyAsync0 {
                    ServerMarket.instance.database.parcelRepository.getParcelCountForPlayer(uuid)
                }.whenComplete { v, _ ->
                    if (v != null) parcelCountCache[uuid] = Cached(v, System.currentTimeMillis())
                    flag.set(false)
                }
            }
        }

        return cached?.value
    }
}
