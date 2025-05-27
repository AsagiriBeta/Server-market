package asagiribeta.serverMarket

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.text.Text
import java.util.*
import asagiribeta.serverMarket.ServerMarket.Companion.LOGGER

class Command {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            literal("money")
                .executes(this::executeMoneyCommand)
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
}