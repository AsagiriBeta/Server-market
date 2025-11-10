package asagiribeta.serverMarket.commandHandler

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.commandHandler.adminCommand.ACash
import asagiribeta.serverMarket.commandHandler.adminCommand.APrice
import asagiribeta.serverMarket.commandHandler.adminCommand.APull
import asagiribeta.serverMarket.commandHandler.adminCommand.APurchase
import asagiribeta.serverMarket.commandHandler.adminCommand.MAdd
import asagiribeta.serverMarket.commandHandler.adminCommand.MLang
import asagiribeta.serverMarket.commandHandler.adminCommand.MReload
import asagiribeta.serverMarket.commandHandler.adminCommand.MSet
import asagiribeta.serverMarket.commandHandler.command.MBuy
import asagiribeta.serverMarket.commandHandler.command.MCash
import asagiribeta.serverMarket.commandHandler.command.MExchange
import asagiribeta.serverMarket.commandHandler.command.MList
import asagiribeta.serverMarket.commandHandler.command.MMenu
import asagiribeta.serverMarket.commandHandler.command.MPay
import asagiribeta.serverMarket.commandHandler.command.MPrice
import asagiribeta.serverMarket.commandHandler.command.MPull
import asagiribeta.serverMarket.commandHandler.command.MPurchase
import asagiribeta.serverMarket.commandHandler.command.MSearch
import asagiribeta.serverMarket.commandHandler.command.MSell
import asagiribeta.serverMarket.commandHandler.command.MSellToPurchase
import asagiribeta.serverMarket.util.Language
import asagiribeta.serverMarket.util.PermissionUtil
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text

class Command {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        // 新的统一命令入口：/svm
        val svmRoot = CommandManager.literal("svm")
            .requires(PermissionUtil.require("servermarket.command", 0))

        // 普通玩家命令子节点
        svmRoot
            // /svm money - 查询余额
            .then(
                CommandManager.literal("money")
                .requires(PermissionUtil.requirePlayer("servermarket.command.money", 0))
                .executes(this::executeMoneyCommand))
            // /svm pay - 转账
            .then(MPay().buildSubCommand())
            // /svm price - 设置价格
            .then(MPrice().buildSubCommand())
            // /svm pull - 下架商品
            .then(MPull().buildSubCommand())
            // /svm list - 查看列表
            .then(MList().buildSubCommand())
            // /svm sell - 上架商品
            .then(MSell().buildSubCommand())
            // /svm search - 搜索商品
            .then(MSearch().buildSubCommand())
            // /svm buy - 购买商品
            .then(MBuy().buildSubCommand())
            // /svm cash - 兑换现金
            .then(MCash().buildSubCommand())
            // /svm exchange - 现金换回余额
            .then(MExchange().buildSubCommand())
            // /svm menu - 打开GUI
            .then(MMenu().buildSubCommand())
            // /svm purchase - 设置收购订单
            .then(MPurchase().buildSubCommand())
            // /svm selltopurchase - 向收购者出售物品
            .then(MSellToPurchase().buildSubCommand())
            // 管理员命令子节点：/svm edit
            .then(
                CommandManager.literal("edit")
                .requires(PermissionUtil.require("servermarket.admin", 4))
                // /svm edit set - 设置余额
                .then(MSet().buildSubCommand())
                // /svm edit add - 增加余额
                .then(MAdd().buildSubCommand())
                // /svm edit price - 设置系统价格
                .then(APrice().buildSubCommand())
                // /svm edit pull - 下架系统商品
                .then(APull().buildSubCommand())
                // /svm edit cash - 管理现金物品
                .then(ACash().buildSubCommand())
                // /svm edit purchase - 设置系统收购
                .then(APurchase().buildSubCommand())
                // /svm edit lang - 切换语言
                .then(MLang().buildSubCommand())
                // /svm edit reload - 重载配置
                .then(MReload().buildSubCommand())
            )

        dispatcher.register(svmRoot)
    }

    private fun executeMoneyCommand(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: run {
            context.source.sendError(Text.literal(Language.get("error.player_only")))
            return 0
        }
        val uuid = player.uuid
        // 使用 TransferService 查询余额
        ServerMarket.instance.transferService.getBalance(uuid).whenComplete { balance, _ ->
            context.source.server.execute {
                context.source.sendMessage(
                    Text.literal(Language.get("command.money.balance", "%.2f".format(balance)))
                )
            }
        }
        return 1
    }
}