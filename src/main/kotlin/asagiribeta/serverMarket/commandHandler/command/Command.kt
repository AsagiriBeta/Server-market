package asagiribeta.serverMarket.commandHandler.command

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.util.Language
import asagiribeta.serverMarket.util.Config
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.CommandManager.argument
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import net.minecraft.command.CommandSource
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
        MPrice().register(dispatcher)
        
        // mpull命令
        MPull().register(dispatcher)  // 修改：使用独立类注册

        // mlist命令
        MList().register(dispatcher)

        // msell命令
        MSell().register(dispatcher)

        // msearch命令
        MSearch().register(dispatcher)

        // mbuy命令
        MBuy().register(dispatcher)
    }

    private fun executeMoneyCommand(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: run {
            context.source.sendError(Text.literal(Language.get("error.player_only")))
            return 0
        }
        val uuid = player.uuid
        val balance = ServerMarket.instance.database.getBalance(uuid)  // 修复：使用单例

        context.source.sendMessage(
            Text.literal(Language.get("command.money.balance", "%.2f".format(balance)))
        )
        return 1
    }

    private fun executeMPayCommand(context: CommandContext<ServerCommandSource>): Int {
        val sender = context.source.player ?: return 0
        val amount = DoubleArgumentType.getDouble(context, "amount")
        
        if (amount <= 0) {
            context.source.sendMessage(Text.literal(Language.get("command.mpay.amount_must_be_positive")))
            return 0
        }

        // 使用配置系统中的最大转账金额
        if (amount > Config.maxTransferAmount) {
            context.source.sendError(Text.literal(Language.get("command.mpay.amount_too_large", Config.maxTransferAmount.toString())))
            return 0
        }

        val targetName = StringArgumentType.getString(context, "player")

        // 防止自己给自己转账
        if (targetName.equals(sender.name.string, ignoreCase = true)) {
            context.source.sendError(Text.literal(Language.get("command.mpay.cannot_pay_self")))
            return 0
        }

        val server = context.source.server
        val targetPlayer = server.playerManager.getPlayer(targetName) ?: run {
            context.source.sendError(Text.literal(Language.get("command.mpay.player_offline")))
            return 0
        }

        val database = ServerMarket.instance.database
        val fromUuid = sender.uuid
        val toUuid = targetPlayer.uuid

        try {
            database.transfer(fromUuid, toUuid, amount)

            context.source.sendMessage(
                Text.literal(Language.get("command.mpay.success", targetPlayer.name.string, "%.2f".format(amount)))
            )
            
            // 接收者提示（转账成功）
            targetPlayer.sendMessage(
                Text.literal(Language.get("command.mpay.received", sender.name.string, "%.2f".format(amount)))
            )
            
            return 1
        } catch (e: Exception) {
            context.source.sendError(Text.literal(Language.get("command.mpay.transfer_failed")))
            ServerMarket.LOGGER.error("MPay命令执行失败", e)
            return 0
        }
    }
}
