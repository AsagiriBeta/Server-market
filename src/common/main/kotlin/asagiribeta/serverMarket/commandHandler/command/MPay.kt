package asagiribeta.serverMarket.commandHandler.command

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.service.EconomyService
import asagiribeta.serverMarket.util.CommandSuggestions
import asagiribeta.serverMarket.util.Config
import asagiribeta.serverMarket.util.MoneyFormat
import asagiribeta.serverMarket.util.PermissionUtil
import asagiribeta.serverMarket.util.whenCompleteOnServerThread
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text

class MPay {
    fun buildSubCommand(): LiteralArgumentBuilder<ServerCommandSource> {
        return literal("pay")
            .requires(PermissionUtil.requirePlayer("servermarket.command.pay", 0))
            .then(
                argument("player", StringArgumentType.string())
                    .suggests(CommandSuggestions.PLAYER_NAME_SUGGESTIONS)
                    .then(
                        argument("amount", DoubleArgumentType.doubleArg())
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

        val server = context.source.server
        val onlineTarget = server.playerManager.getPlayer(targetName)

        if (onlineTarget != null) {
            doTransfer(context, sender.uuid, sender.name.string, onlineTarget.uuid, onlineTarget.name.string, amount)
            return 1
        }

        // Offline player support (XConomy-style)
        val db = ServerMarket.instance.database
        db.supplyAsync0 { db.playerLookupService.getUuidByPlayerName(targetName) }
            .whenCompleteOnServerThread(server) { uuid, ex ->
                if (ex != null || uuid == null) {
                    context.source.sendError(Text.translatable("servermarket.command.mpay.player_not_found"))
                    return@whenCompleteOnServerThread
                }
                doTransfer(context, sender.uuid, sender.name.string, uuid, targetName, amount)
            }
        return 1
    }

    private fun doTransfer(
        context: CommandContext<ServerCommandSource>,
        fromUuid: java.util.UUID,
        fromName: String,
        toUuid: java.util.UUID,
        toName: String,
        amount: Double
    ) {
        ServerMarket.instance.economyService.transfer(fromUuid, fromName, toUuid, toName, amount)
            .whenCompleteOnServerThread(context.source.server) { outcome, ex ->
                if (ex != null) {
                    context.source.sendError(Text.translatable("servermarket.command.mpay.transfer_failed"))
                    ServerMarket.LOGGER.error("/svm pay failed", ex)
                    return@whenCompleteOnServerThread
                }
                when (outcome) {
                    is EconomyService.TransferOutcome.Success -> {
                        context.source.sendMessage(
                            Text.translatable(
                                "servermarket.command.mpay.success",
                                toName,
                                MoneyFormat.format(amount, 2)
                            )
                        )
                        context.source.server.playerManager.getPlayer(toUuid)?.sendMessage(
                            Text.translatable(
                                "servermarket.command.mpay.received",
                                fromName,
                                MoneyFormat.format(amount, 2)
                            )
                        )
                    }
                    is EconomyService.TransferOutcome.InsufficientFunds -> {
                        context.source.sendError(Text.translatable("servermarket.command.mpay.insufficient_funds"))
                    }
                    is EconomyService.TransferOutcome.Error -> {
                        context.source.sendError(Text.translatable("servermarket.command.mpay.transfer_failed"))
                        ServerMarket.LOGGER.error("/svm pay error: {}", outcome.message)
                    }
                    null -> context.source.sendError(Text.translatable("servermarket.command.mpay.transfer_failed"))
                }
            }
    }
}
