package asagiribeta.serverMarket

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.CommandManager.argument
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import net.minecraft.command.CommandSource
import net.minecraft.server.MinecraftServer
import net.minecraft.text.Text


class Command {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            literal("money")
                .executes(this::executeMoneyCommand)
        )
        
        // 参数层级结构
        dispatcher.register(
            literal("mpay")
                .requires { source -> source.player != null }
                .then(argument("player", StringArgumentType.string())
                    .suggests { context, builder ->
                        val server = context.source.server
                        val names = server.playerManager.playerNames
                        CommandSource.suggestMatching(names, builder)
                    }  // 玩家名称补全建议
                    .then(argument("amount", DoubleArgumentType.doubleArg())
                        .executes(this::executeMPayCommand)
                    )
                )
        )
        
        // mprice命令
        dispatcher.register(
            literal("mprice")
                .then(argument("price", DoubleArgumentType.doubleArg(0.0))
                    .executes(this::executeMPriceCommand)
                )
        )
        
        // mpull命令
        dispatcher.register(
            literal("mpull")
                .executes(this::executeMPullCommand)
        )

        // mlist命令
        dispatcher.register(
            literal("mlist")
                .then(argument("target", StringArgumentType.string())
                    .suggests { context, builder ->
                        val server = context.source.server
                        val names = server.playerManager.playerNames + "server"
                        CommandSource.suggestMatching(names, builder)
                    }
                    .executes(this::executeMListCommand)
                )
        )
    }

    private fun executeMoneyCommand(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: run {
            context.source.sendError(Text.literal("该命令只能由玩家执行"))
            return 0
        }
        val uuid = player.uuid
        val balance = ServerMarket().database.getBalance(uuid)
        
        context.source.sendMessage(
            Text.literal("您的当前余额: ${"%.2f".format(balance)}")
        )
        return 1
    }

    private fun executeMPayCommand(context: CommandContext<ServerCommandSource>): Int {
        val sender = context.source.player ?: return 0
        val amount = DoubleArgumentType.getDouble(context, "amount")
        
        if (amount <= 0) {
            context.source.sendMessage(Text.literal("金额必须大于0"))
            return 0
        }

        val targetName = StringArgumentType.getString(context, "player")
        val server = context.source.server
        val targetPlayer = server.playerManager.getPlayer(targetName) ?: run {
            context.source.sendError(Text.literal("目标玩家不在线"))
            return 0
        }

        val database = ServerMarket.instance.database
        val fromUuid = sender.uuid
        val toUuid = targetPlayer.uuid

        try {
            database.transfer(fromUuid, toUuid, amount)
            
            // 记录交易历史
            val dtg = System.currentTimeMillis()
            database.historyRepository.postHistory(
                dtg, fromUuid, "player", sender.name.string,
                toUuid, "player", targetPlayer.name.string,
                amount, "MPay Transfer"
            )

            context.source.sendMessage(
                Text.literal("成功向 ${targetPlayer.name.string} 转账 ${"%.2f".format(amount)} 金币")
            )
            
            // 接收者提示（在转账成功后添加）
            targetPlayer.sendMessage(
                Text.literal("${sender.name.string} 向您转账 ${"%.2f".format(amount)} 金币")
            )
            
            return 1
        } catch (e: Exception) {
            context.source.sendError(Text.literal("转账失败"))
            ServerMarket.LOGGER.error("MPay命令执行失败", e)
            return 0
        }
    }

    // mprice命令处理
    private fun executeMPriceCommand(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val player = source.player ?: run {
            source.sendError(Text.literal("只有玩家可以执行此命令"))
            return 0
        }

        val price = DoubleArgumentType.getDouble(context, "price")
        val itemStack = player.mainHandStack
        if (itemStack.isEmpty) {
            source.sendError(Text.literal("请手持要上架的物品"))
            return 0
        }

        try {
            val itemId = itemStack.item.translationKey
            val marketRepo = ServerMarket.instance.database.marketRepository
            
            if (!marketRepo.hasPlayerItem(player.uuid, itemId)) {
                marketRepo.addPlayerItem(
                    sellerUuid = player.uuid,
                    sellerName = player.name.string,
                    itemId = itemId,
                    price = price
                )
                source.sendMessage(Text.literal("成功上架 ${itemStack.name.string} 单价为 $price"))
            } else {
                marketRepo.updatePlayerItemPrice(player.uuid, itemId, price)
                source.sendMessage(Text.literal("成功更新 ${itemStack.name.string} 单价为 $price"))
            }
            return 1
        } catch (e: Exception) {
            source.sendError(Text.literal("操作失败"))
            ServerMarket.LOGGER.error("mprice命令执行失败", e)
            return 0
        }
    }

    // mpull命令处理
    private fun executeMPullCommand(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val player = source.player ?: run {
            source.sendError(Text.literal("只有玩家可以执行此命令"))
            return 0
        }

        val itemStack = player.mainHandStack
        if (itemStack.isEmpty) {
            source.sendError(Text.literal("请手持要下架的物品"))
            return 0
        }

        try {
            val itemId = itemStack.item.translationKey
            val marketRepo = ServerMarket.instance.database.marketRepository
            
            if (marketRepo.hasPlayerItem(player.uuid, itemId)) {
                marketRepo.removePlayerItem(player.uuid, itemId)
                source.sendMessage(Text.literal("成功下架 ${itemStack.name.string}"))
                return 1
            }
            source.sendError(Text.literal("该物品未上架"))
            return 0
        } catch (e: Exception) {
            source.sendError(Text.literal("操作失败"))
            ServerMarket.LOGGER.error("mpull命令执行失败", e)
            return 0
        }
    }

    // mlist命令处理
    private fun executeMListCommand(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val target = StringArgumentType.getString(context, "target")
        val marketRepo = ServerMarket.instance.database.marketRepository

        return try {
            val items = when (target.lowercase()) {
                "server" -> marketRepo.getSystemItems()
                else -> {
                    val sellerUuid = resolveSellerUuid(target, source.server) ?: run {
                        source.sendError(Text.literal("找不到玩家 $target"))
                        return 0
                    }
                    marketRepo.getPlayerItems(sellerUuid)
                }
            }

            if (items.isEmpty()) {
                source.sendMessage(Text.literal("$target 没有上架任何物品"))
                return 1
            }

            source.sendMessage(Text.literal("=== $target 上架物品 ===").styled { it.withBold(true) })
            items.forEach { (itemId, sellerName, price, quantity) ->
                source.sendMessage(
                    Text.literal("▸ $itemId")
                        .append(Text.literal(" 卖家: $sellerName").styled { it.withColor(0x00FF00) })
                        .append(Text.literal(" 单价: ${"%.2f".format(price)}").styled { it.withColor(0xFFA500) })
                        .append(Text.literal(" 数量: $quantity").styled { it.withColor(0xADD8E6) }))
            }
            1
        } catch (e: Exception) {
            source.sendError(Text.literal("查询失败"))
            ServerMarket.LOGGER.error("mlist命令执行失败", e)
            0
        }
    }

    private fun resolveSellerUuid(name: String, server: MinecraftServer): String? {
        return server.playerManager.getPlayer(name)?.uuid?.toString()
            ?: ServerMarket.instance.database.executeQuery(
                "SELECT uuid FROM balances WHERE uuid = ? OR EXISTS(SELECT 1 FROM player_market WHERE seller_name = ?)"
            ) { ps ->
                ps.setString(1, name)
                ps.setString(2, name)
            }
    }
}