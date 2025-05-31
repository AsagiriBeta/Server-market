package asagiribeta.serverMarket

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import com.mojang.brigadier.context.CommandContext
import net.minecraft.command.CommandSource
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.CommandManager.argument

class AdminCommand {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            literal("mset")
                .requires { it.hasPermissionLevel(4) }
                .then(argument("player", StringArgumentType.string())
                    .suggests { context, builder ->  // 玩家名称自动补全
                        val server = context.source.server
                        val names = server.playerManager.playerNames
                        CommandSource.suggestMatching(names, builder)
                    }
                    .then(argument("amount", DoubleArgumentType.doubleArg(0.0))
                        .executes(this::executeMSetCommand)
                    )
                )
        )

        dispatcher.register(
            literal("aprice")
                .requires { it.hasPermissionLevel(4) }
                .then(argument("price", DoubleArgumentType.doubleArg(0.0))
                    .executes(this::executeAPriceCommand)
                )
        )

        dispatcher.register(
            literal("apull")
                .requires { it.hasPermissionLevel(4) }
                .executes(this::executeAPullCommand)
        )

    }

    private fun executeMSetCommand(context: CommandContext<ServerCommandSource>): Int {
        val targetName = StringArgumentType.getString(context, "player")
        val amount = DoubleArgumentType.getDouble(context, "amount")
        val server = context.source.server
        val targetPlayer = server.playerManager.getPlayer(targetName) ?: run {
            context.source.sendError(Text.literal("目标玩家不在线"))
            return 0
        }

        if (amount < 0) {
            context.source.sendError(Text.literal("金额不能为负数"))
            return 0
        }

        try {
            ServerMarket.instance.database.setBalance(targetPlayer.uuid, amount)
            context.source.sendMessage(
                Text.literal("成功设置玩家 ${targetPlayer.name.string} 的余额为 ${"%.2f".format(amount)}")
            )
            return 1
        } catch (e: Exception) {
            context.source.sendError(Text.literal("设置余额失败"))
            ServerMarket.LOGGER.error("mset命令执行失败", e)
            return 0
        }
    }

    private fun executeAPriceCommand(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val player = source.player ?: run {
            source.sendError(Text.literal("只有玩家可以执行此命令"))
            return 0
        }

        val price = DoubleArgumentType.getDouble(context, "price")
        val itemStack = player.mainHandStack
        if (itemStack.isEmpty) {
            source.sendError(Text.literal("请手持要设置价格的物品"))
            return 0
        }

        try {
            val itemId = itemStack.item.translationKey
            val marketRepo = ServerMarket.instance.database.marketRepository
            
            if (!marketRepo.hasSystemItem(itemId)) {
                marketRepo.addSystemItem(itemId, price)
                source.sendMessage(Text.literal("成功上架 ${itemStack.name.string} 价格为 $price"))
            } else {
                marketRepo.addSystemItem(itemId, price)
                source.sendMessage(Text.literal("成功更新 ${itemStack.name.string} 价格为 $price"))
            }
            return 1
        } catch (e: Exception) {
            source.sendError(Text.literal("操作失败"))
            ServerMarket.LOGGER.error("aprice命令执行失败", e)
            return 0
        }
    }

    private fun executeAPullCommand(context: CommandContext<ServerCommandSource>): Int {
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
            
            if (marketRepo.hasSystemItem(itemId)) {
                marketRepo.removeSystemItem(itemId)
                source.sendMessage(Text.literal("成功下架 ${itemStack.name.string}"))
                return 1
            }
            source.sendError(Text.literal("该物品未上架"))
            return 0
        } catch (e: Exception) {
            source.sendError(Text.literal("操作失败"))
            ServerMarket.LOGGER.error("apull命令执行失败", e)
            return 0
        }
    }
}