package asagiribeta.serverMarket.commandHandler.command

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.util.Language
import asagiribeta.serverMarket.repository.MarketItem
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.text.Text
import java.util.*
import asagiribeta.serverMarket.util.ItemKey
import asagiribeta.serverMarket.util.CommandSuggestions
import java.time.LocalDate
import net.minecraft.command.argument.IdentifierArgumentType
import java.util.concurrent.CompletableFuture

class MBuy {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            literal("mbuy")
                .then(argument("quantity", DoubleArgumentType.doubleArg(1.0))
                    .then(argument("item", IdentifierArgumentType.identifier())
                        .suggests(CommandSuggestions.ITEM_ID_SUGGESTIONS)
                        .executes(this::executeBuy)
                        .then(argument("seller", StringArgumentType.string())
                            .suggests(CommandSuggestions.SELLER_SUGGESTIONS)
                            .executes(this::executeBuyWithSeller)
                        )
                    )
                )
        )
    }

    // 统一的购买流程（查询 -> 校验 -> 结算），不负责发放物品
    private data class BuyPlan(val toGive: List<Triple<String, String, Int>>, val totalCost: Double)

    private fun processBuyAsync(
        playerId: UUID,
        playerName: String,
        itemId: String,
        quantity: Int,
        seller: String? = null
    ): CompletableFuture<BuyPlan?> {
        val db = ServerMarket.instance.database
        val repo = db.marketRepository
        val today = LocalDate.now().toString()

        return db.supplyAsync {
            val items = if (seller == null) repo.searchForTransaction(itemId) else repo.searchForTransaction(itemId, seller)
            if (items.isEmpty()) return@supplyAsync null

            var totalAvailable = 0
            for (entry in items) {
                if (entry.sellerName == "SERVER") {
                    val limit = repo.getSystemLimitPerDay(entry.itemId, entry.nbt)
                    if (limit < 0) {
                        totalAvailable = Int.MAX_VALUE
                        break
                    } else {
                        val purchased = repo.getSystemPurchasedOn(today, playerId, entry.itemId, entry.nbt)
                        val remaining = (limit - purchased).coerceAtLeast(0)
                        totalAvailable = totalAvailable.saturatingAdd(remaining)
                    }
                } else {
                    totalAvailable = if (totalAvailable == Int.MAX_VALUE) Int.MAX_VALUE else totalAvailable.saturatingAdd(entry.quantity)
                }
            }
            if (totalAvailable < quantity) return@supplyAsync null

            var remaining = quantity
            var totalCost = 0.0
            val purchaseList = mutableListOf<Pair<MarketItem, Int>>()
            for (entry in items) {
                if (remaining <= 0) break
                val purchaseAmount = if (entry.sellerName == "SERVER") {
                    val limit = repo.getSystemLimitPerDay(entry.itemId, entry.nbt)
                    if (limit < 0) remaining else {
                        val purchased = repo.getSystemPurchasedOn(today, playerId, entry.itemId, entry.nbt)
                        val allowed = (limit - purchased).coerceAtLeast(0)
                        minOf(remaining, allowed)
                    }
                } else {
                    minOf(remaining, entry.quantity)
                }
                if (purchaseAmount <= 0) continue
                totalCost += entry.price * purchaseAmount
                purchaseList.add(entry to purchaseAmount)
                remaining -= purchaseAmount
            }

            val balance = db.getBalance(playerId)
            if (balance < totalCost) return@supplyAsync null

            // 结算：玩家 -> 系统
            db.transfer(playerId, UUID(0, 0), totalCost)
            val dtg = System.currentTimeMillis()
            db.historyRepository.postHistory(
                dtg = dtg,
                fromId = playerId,
                fromType = "player",
                fromName = playerName,
                toId = UUID(0, 0),
                toType = "system",
                toName = "MARKET",
                price = totalCost,
                item = "$itemId x$quantity"
            )

            for ((mi, amount) in purchaseList) {
                if (mi.sellerName != "SERVER") {
                    db.marketRepository.incrementPlayerItemQuantity(UUID.fromString(mi.sellerName), mi.itemId, mi.nbt, -amount)
                    db.transfer(UUID(0, 0), UUID.fromString(mi.sellerName), mi.price * amount)
                    db.historyRepository.postHistory(
                        dtg = dtg,
                        fromId = UUID(0, 0),
                        fromType = "system",
                        fromName = "MARKET",
                        toId = UUID.fromString(mi.sellerName),
                        toType = "player",
                        toName = mi.sellerName,
                        price = mi.price * amount,
                        item = "${mi.itemId} x$amount"
                    )
                } else {
                    db.marketRepository.incrementSystemPurchasedOn(today, playerId, mi.itemId, mi.nbt, amount)
                }
            }

            BuyPlan(
                toGive = purchaseList.map { Triple(it.first.itemId, it.first.nbt, it.second) },
                totalCost = totalCost
            )
        }
    }

    private fun buildStackFromRecord(pid: String, nbt: String, amount: Int): ItemStack {
        val stack = ItemKey.tryBuildFullStackFromSnbt(nbt, amount) ?: run {
            val id = Identifier.tryParse(pid)
            val itemType = if (id != null && Registries.ITEM.containsId(id)) Registries.ITEM.get(id) else Items.AIR
            val fallback = ItemStack(itemType, amount)
            try { if (nbt.isNotEmpty()) ItemKey.applySnbt(fallback, nbt) } catch (_: Exception) {}
            fallback
        }
        return stack
    }

    private fun executeBuy(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: return 0
        val quantity = DoubleArgumentType.getDouble(context, "quantity").toInt()
        val itemId = IdentifierArgumentType.getIdentifier(context, "item").toString()

        processBuyAsync(player.uuid, player.name.string, itemId, quantity, null)
            .whenComplete { plan, ex ->
                context.source.server.execute {
                    if (ex != null || plan == null) {
                        context.source.sendError(Text.literal(Language.get("command.mbuy.error")))
                        if (ex != null) ServerMarket.LOGGER.error("MBuy命令执行失败", ex)
                        return@execute
                    }
                    // 发物品
                    for ((pid, nbt, amount) in plan.toGive) {
                        val stack = buildStackFromRecord(pid, nbt, amount)
                        player.giveItemStack(stack)
                    }
                    context.source.sendMessage(Text.literal(Language.get("command.mbuy.success", quantity, itemId, "%.2f".format(plan.totalCost))))
                }
            }
        return 1
    }

    // 带卖家过滤的执行
    private fun executeBuyWithSeller(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: return 0
        val quantity = DoubleArgumentType.getDouble(context, "quantity").toInt()
        val itemId = IdentifierArgumentType.getIdentifier(context, "item").toString()
        val seller = StringArgumentType.getString(context, "seller")

        processBuyAsync(player.uuid, player.name.string, itemId, quantity, seller)
            .whenComplete { plan, ex ->
                context.source.server.execute {
                    if (ex != null || plan == null) {
                        context.source.sendError(Text.literal(Language.get("command.mbuy.error")))
                        if (ex != null) ServerMarket.LOGGER.error("MBuy命令执行失败(带卖家)", ex)
                        return@execute
                    }
                    for ((pid, nbt, amount) in plan.toGive) {
                        val stack = buildStackFromRecord(pid, nbt, amount)
                        player.giveItemStack(stack)
                    }
                    context.source.sendMessage(Text.literal(Language.get("command.mbuy.success", quantity, "$itemId@$seller", "%.2f".format(plan.totalCost))))
                }
            }
        return 1
    }
}

// 内联扩展：饱和加法避免溢出
private fun Int.saturatingAdd(other: Int): Int {
    val sum = this.toLong() + other.toLong()
    return when {
        sum > Int.MAX_VALUE -> Int.MAX_VALUE
        sum < Int.MIN_VALUE -> Int.MIN_VALUE
        else -> sum.toInt()
    }
}
