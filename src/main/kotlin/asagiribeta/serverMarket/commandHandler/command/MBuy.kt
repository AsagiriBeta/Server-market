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

class MBuy {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            literal("mbuy")
                .then(argument("quantity", DoubleArgumentType.doubleArg(1.0))
                    .then(argument("item", StringArgumentType.string())
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

    private fun executeBuy(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: return 0
        val quantity = DoubleArgumentType.getDouble(context, "quantity").toInt()
        val itemId = StringArgumentType.getString(context, "item")
        val marketRepo = ServerMarket.instance.database.marketRepository

        val items = marketRepo.searchForTransaction(itemId)
        if (items.isEmpty()) {
            context.source.sendError(Text.literal(Language.get("command.mbuy.not_found", itemId)))
            return 0
        }

        // 计算对该玩家真实可用库存：
        val today = LocalDate.now().toString()
        var totalAvailable = 0
        for (entry in items) {
            if (entry.sellerName == "SERVER") {
                val limit = marketRepo.getSystemLimitPerDay(entry.itemId, entry.nbt)
                if (limit < 0) {
                    // 无限库存且无限制
                    totalAvailable = Int.MAX_VALUE
                    break
                } else {
                    val purchased = marketRepo.getSystemPurchasedOn(today, player.uuid, entry.itemId, entry.nbt)
                    val remaining = (limit - purchased).coerceAtLeast(0)
                    totalAvailable = totalAvailable.saturatingAdd(remaining)
                }
            } else {
                // 玩家物品按真实库存
                totalAvailable = if (totalAvailable == Int.MAX_VALUE) Int.MAX_VALUE else totalAvailable.saturatingAdd(entry.quantity)
            }
        }
        if (totalAvailable < quantity) {
            context.source.sendError(Text.literal(Language.get("command.mbuy.insufficient_stock", totalAvailable)))
            return 0
        }

        // 计算总价格与购买分配
        var remaining = quantity
        var totalCost = 0.0
        val purchaseList = mutableListOf<Pair<MarketItem, Int>>()

        for (entry in items) {
            if (remaining <= 0) break
            val purchaseAmount = if (entry.sellerName == "SERVER") {
                val limit = marketRepo.getSystemLimitPerDay(entry.itemId, entry.nbt)
                if (limit < 0) {
                    remaining
                } else {
                    val purchased = marketRepo.getSystemPurchasedOn(today, player.uuid, entry.itemId, entry.nbt)
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

        // 检查余额
        val balance = ServerMarket.instance.database.getBalance(player.uuid)
        if (balance < totalCost) {
            context.source.sendError(Text.literal(Language.get("command.mbuy.insufficient_balance", "%.2f".format(totalCost))))
            return 0
        }

        // 执行购买
        try {
            // 扣除买家余额（转入系统账户）
            ServerMarket.instance.database.transfer(
                player.uuid,
                UUID(0, 0), // 系统账户UUID
                totalCost
            )

            // 新增交易历史记录（总单）
            val dtg = System.currentTimeMillis()
            ServerMarket.instance.database.historyRepository.postHistory(
                dtg = dtg,
                fromId = player.uuid,
                fromType = "player",
                fromName = player.name.string,
                toId = UUID(0, 0), // 系统账户
                toType = "system",
                toName = "MARKET",
                price = totalCost,
                item = "$itemId x$quantity"
            )

            // 处理每个卖家的交易
            for ((mi, amount) in purchaseList) {
                if (mi.sellerName != "SERVER") {
                    // 减少玩家市场库存（按 NBT 精确扣减）
                    ServerMarket.instance.database.marketRepository.incrementPlayerItemQuantity(
                        UUID.fromString(mi.sellerName),
                        mi.itemId,
                        mi.nbt,
                        -amount
                    )
                    // 结算给卖家
                    ServerMarket.instance.database.transfer(
                        UUID(0, 0),
                        UUID.fromString(mi.sellerName),
                        mi.price * amount
                    )
                    // 卖家历史
                    ServerMarket.instance.database.historyRepository.postHistory(
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
                    // 系统商品：记录当日购买量
                    ServerMarket.instance.database.marketRepository.incrementSystemPurchasedOn(
                        today,
                        player.uuid,
                        mi.itemId,
                        mi.nbt,
                        amount
                    )
                }

                // 给予玩家物品（尝试带回原始 NBT）
                val id = Identifier.tryParse(mi.itemId)
                val itemType = if (id != null && Registries.ITEM.containsId(id)) Registries.ITEM.get(id) else Items.AIR
                val stack = ItemStack(itemType, amount)
                try {
                    // 尝试解析 SNBT；若失败则忽略
                    if (mi.nbt.isNotEmpty()) {
                        ItemKey.applySnbt(stack, mi.nbt)
                    }
                } catch (_: Exception) { /* ignore */ }
                player.giveItemStack(stack)
            }

            context.source.sendMessage(
                Text.literal(Language.get("command.mbuy.success", quantity, itemId, "%.2f".format(totalCost)))
            )
            return 1
        } catch (e: Exception) {
            context.source.sendError(Text.literal(Language.get("command.mbuy.error")))
            ServerMarket.LOGGER.error("MBuy命令执行失败", e)
            return 0
        }
    }

    // 新增：带卖家过滤的执行
    private fun executeBuyWithSeller(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: return 0
        val quantity = DoubleArgumentType.getDouble(context, "quantity").toInt()
        val itemId = StringArgumentType.getString(context, "item")
        val seller = StringArgumentType.getString(context, "seller")
        val marketRepo = ServerMarket.instance.database.marketRepository

        val items = marketRepo.searchForTransaction(itemId, seller)
        if (items.isEmpty()) {
            context.source.sendError(Text.literal(Language.get("command.mbuy.not_found", "$itemId@$seller")))
            return 0
        }

        val today = LocalDate.now().toString()
        // 计算对该玩家真实可用库存（考虑系统每日限购）
        var totalAvailable = 0
        for (entry in items) {
            if (entry.sellerName == "SERVER") {
                val limit = marketRepo.getSystemLimitPerDay(entry.itemId, entry.nbt)
                if (limit < 0) {
                    totalAvailable = Int.MAX_VALUE
                    break
                } else {
                    val purchased = marketRepo.getSystemPurchasedOn(today, player.uuid, entry.itemId, entry.nbt)
                    val remaining = (limit - purchased).coerceAtLeast(0)
                    totalAvailable = totalAvailable.saturatingAdd(remaining)
                }
            } else {
                totalAvailable = if (totalAvailable == Int.MAX_VALUE) Int.MAX_VALUE else totalAvailable.saturatingAdd(entry.quantity)
            }
        }
        if (totalAvailable < quantity) {
            context.source.sendError(Text.literal(Language.get("command.mbuy.insufficient_stock", totalAvailable)))
            return 0
        }

        // 计算总价格
        var remaining = quantity
        var totalCost = 0.0
        val purchaseList = mutableListOf<Pair<MarketItem, Int>>()
        for (entry in items) {
            if (remaining <= 0) break
            val purchaseAmount = if (entry.sellerName == "SERVER") {
                val limit = marketRepo.getSystemLimitPerDay(entry.itemId, entry.nbt)
                if (limit < 0) {
                    remaining
                } else {
                    val purchased = marketRepo.getSystemPurchasedOn(today, player.uuid, entry.itemId, entry.nbt)
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

        // 检查余额
        val balance = ServerMarket.instance.database.getBalance(player.uuid)
        if (balance < totalCost) {
            context.source.sendError(Text.literal(Language.get("command.mbuy.insufficient_balance", "%.2f".format(totalCost))))
            return 0
        }

        // 执行购买（与无卖家版本一致）
        try {
            ServerMarket.instance.database.transfer(player.uuid, UUID(0, 0), totalCost)

            val dtg = System.currentTimeMillis()
            ServerMarket.instance.database.historyRepository.postHistory(
                dtg = dtg,
                fromId = player.uuid,
                fromType = "player",
                fromName = player.name.string,
                toId = UUID(0, 0),
                toType = "system",
                toName = "MARKET",
                price = totalCost,
                item = "$itemId x$quantity"
            )

            for ((mi, amount) in purchaseList) {
                if (mi.sellerName != "SERVER") {
                    ServerMarket.instance.database.marketRepository.incrementPlayerItemQuantity(
                        UUID.fromString(mi.sellerName),
                        mi.itemId,
                        mi.nbt,
                        -amount
                    )
                    ServerMarket.instance.database.transfer(UUID(0, 0), UUID.fromString(mi.sellerName), mi.price * amount)
                    ServerMarket.instance.database.historyRepository.postHistory(
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
                    // 系统商品：记录当日购买量
                    ServerMarket.instance.database.marketRepository.incrementSystemPurchasedOn(
                        today,
                        player.uuid,
                        mi.itemId,
                        mi.nbt,
                        amount
                    )
                }
                val id = Identifier.tryParse(mi.itemId)
                val itemType = if (id != null && Registries.ITEM.containsId(id)) Registries.ITEM.get(id) else Items.AIR
                val stack = ItemStack(itemType, amount)
                try { if (mi.nbt.isNotEmpty()) ItemKey.applySnbt(stack, mi.nbt) } catch (_: Exception) {}
                player.giveItemStack(stack)
            }

            context.source.sendMessage(
                Text.literal(Language.get("command.mbuy.success", quantity, "$itemId@$seller", "%.2f".format(totalCost)))
            )
            return 1
        } catch (e: Exception) {
            context.source.sendError(Text.literal(Language.get("command.mbuy.error")))
            ServerMarket.LOGGER.error("MBuy命令执行失败(带卖家)", e)
            return 0
        }
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
