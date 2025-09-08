package asagiribeta.serverMarket.commandHandler.command

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.util.Language
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.text.Text

class Command {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            literal("money")
                .executes(this::executeMoneyCommand)
        )
        
        // mpay 命令改为独立类注册
        MPay().register(dispatcher)

        // mprice命令
        MPrice().register(dispatcher)
        
        // mpull命令
        MPull().register(dispatcher)

        // mlist命令
        MList().register(dispatcher)

        // msell命令
        MSell().register(dispatcher)

        // msearch命令
        MSearch().register(dispatcher)

        // mbuy命令
        MBuy().register(dispatcher)

        // mcash命令（兑换现金）
        MCash().register(dispatcher)

        // mexchange命令（现金换回余额）
        MExchange().register(dispatcher)
    }

    private fun executeMoneyCommand(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: run {
            context.source.sendError(Text.literal(Language.get("error.player_only")))
            return 0
        }
        val uuid = player.uuid
        val balance = ServerMarket.instance.database.getBalance(uuid)

        context.source.sendMessage(
            Text.literal(Language.get("command.money.balance", "%.2f".format(balance)))
        )
        return 1
    }
}
