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

class MBuy {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            literal("mbuy")
                .then(argument("quantity", DoubleArgumentType.doubleArg(1.0))
                    .then(argument("item", StringArgumentType.greedyString())
                        .suggests(CommandSuggestions.ITEM_ID_SUGGESTIONS)
                        .executes(this::executeBuy)
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

        // 当存在系统商品时视为无限库存
        val hasSystemItem = items.any { it.quantity == -1 }
        val totalAvailable = if (hasSystemItem) Int.MAX_VALUE else items.sumOf { it.quantity }
        
        if (totalAvailable < quantity) {
            context.source.sendError(Text.literal(Language.get("command.mbuy.insufficient_stock", totalAvailable)))
            return 0
        }

        // 计算总价格
        var remaining = quantity
        var totalCost = 0.0
        val purchaseList = mutableListOf<Pair<MarketItem, Int>>()

        for (item in items) {
            if (remaining <= 0) break
            
            val purchaseAmount = if (item.quantity == -1) {
                remaining
            } else {
                minOf(remaining, item.quantity)
            }
            
            totalCost += item.price * purchaseAmount
            purchaseList.add(item to purchaseAmount)
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
            // 扣除买家余额
            ServerMarket.instance.database.transfer(
                player.uuid,
                UUID(0, 0), // 系统账户UUID
                totalCost
            )

            // 新增交易历史记录
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
            for ((item, amount) in purchaseList) {
                if (item.sellerName != "SERVER") {
                    // 减少玩家市场库存（按 NBT 精确扣减）
                    ServerMarket.instance.database.marketRepository.incrementPlayerItemQuantity(
                        UUID.fromString(item.sellerName),
                        item.itemId,
                        item.nbt,
                        -amount
                    )
                    
                    // 结算给卖家
                    ServerMarket.instance.database.transfer(
                        UUID(0, 0),
                        UUID.fromString(item.sellerName),
                        item.price * amount
                    )

                    ServerMarket.instance.database.historyRepository.postHistory(
                        dtg = dtg,
                        fromId = UUID(0, 0),
                        fromType = "system",
                        fromName = "MARKET",
                        toId = UUID.fromString(item.sellerName),
                        toType = "player",
                        toName = item.sellerName,
                        price = item.price * amount,
                        item = "${item.itemId} x$amount"
                    )
                }
                
                // 给予玩家物品（尝试带回原始 NBT）
                val id = Identifier.tryParse(item.itemId)
                val itemType = if (id != null && Registries.ITEM.containsId(id)) Registries.ITEM.get(id) else Items.AIR
                val stack = ItemStack(itemType, amount)
                try {
                    // 尝试解析 SNBT；若失败则忽略
                    if (item.nbt.isNotEmpty()) {
                        ItemKey.applySnbt(stack, item.nbt)
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
}
