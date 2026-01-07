package asagiribeta.serverMarket.commandHandler.command

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.service.TransferService
import asagiribeta.serverMarket.util.Config
import asagiribeta.serverMarket.util.MoneyFormat
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.command.CommandSource
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text

import asagiribeta.serverMarket.util.PermissionUtil
import asagiribeta.serverMarket.util.whenCompleteOnServerThread

class MPay {
    // 构建 /svm pay 子命令
    fun buildSubCommand(): LiteralArgumentBuilder<ServerCommandSource> {
        return literal("pay")
            .requires(PermissionUtil.requirePlayer("servermarket.command.pay", 0))
            .then(argument("player", StringArgumentType.string())
                .suggests { context, builder ->
                    val names = context.source.server.playerManager.playerNames
                    CommandSource.suggestMatching(names, builder)
                }
                .then(argument("amount", DoubleArgumentType.doubleArg())
                    .executes(this::execute)
                )
            )
    }

    internal fun execute(context: CommandContext<ServerCommandSource>): Int {
        val sender = context.source.player ?: return 0
        val amount = DoubleArgumentType.getDouble(context, "amount")

        if (amount <= 0) {
            context.source.sendMessage(Text.translatable("servermarket.command.mpay.amount_must_be_positive"))
            return 0
        }

        if (amount > Config.maxTransferAmount) {
            context.source.sendError(Text.translatable("servermarket.command.mpay.amount_too_large", Config.maxTransferAmount.toString()))
            return 0
        }

        val targetName = StringArgumentType.getString(context, "player")
        if (targetName.equals(sender.name.string, ignoreCase = true)) {
            context.source.sendError(Text.translatable("servermarket.command.mpay.cannot_pay_self"))
            return 0
        }

        val targetPlayer = context.source.server.playerManager.getPlayer(targetName) ?: run {
            context.source.sendError(Text.translatable("servermarket.command.mpay.player_offline"))
            return 0
        }

        // Use TransferService instead of direct database access
        ServerMarket.instance.transferService.transfer(
            fromUuid = sender.uuid,
            fromName = sender.name.string,
            toUuid = targetPlayer.uuid,
            toName = targetPlayer.name.string,
            amount = amount
        ).whenCompleteOnServerThread(context.source.server) { result, ex ->
            if (ex != null) {
                context.source.sendError(Text.translatable("servermarket.command.mpay.transfer_failed"))
                ServerMarket.LOGGER.error("mpay命令执行失败", ex)
                return@whenCompleteOnServerThread
            }

            when (result) {
                null -> {
                    context.source.sendError(Text.translatable("servermarket.command.mpay.transfer_failed"))
                }
                is TransferService.TransferResult.Success -> {
                    context.source.sendMessage(
                        Text.translatable(
                            "servermarket.command.mpay.success",
                            targetPlayer.name,
                            MoneyFormat.format(amount, 2)
                        )
                    )
                    targetPlayer.sendMessage(
                        Text.translatable(
                            "servermarket.command.mpay.received",
                            sender.name,
                            MoneyFormat.format(amount, 2)
                        )
                    )
                }
                is TransferService.TransferResult.InsufficientFunds -> {
                    context.source.sendError(Text.translatable("servermarket.command.mpay.insufficient_funds"))
                }
                is TransferService.TransferResult.Error -> {
                    context.source.sendError(Text.translatable("servermarket.command.mpay.transfer_failed"))
                    ServerMarket.LOGGER.error("转账失败: ${result.message}")
                }
            }
        }
        return 1
    }
}
