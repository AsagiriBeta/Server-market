package asagiribeta.serverMarket.commandHandler

import asagiribeta.serverMarket.util.Language
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.CommandManager.argument
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.command.CommandSource
import net.minecraft.server.MinecraftServer
import net.minecraft.text.Text
import asagiribeta.serverMarket.ServerMarket

class MList {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            literal("mlist")
                .then(argument("target", StringArgumentType.string())
                    .suggests { context, builder ->
                        val server = context.source.server
                        val names = server.playerManager.playerNames + "server"
                        CommandSource.suggestMatching(names, builder)
                    }
                    .executes(this::execute)
                )
        )
    }

    private fun execute(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val target = StringArgumentType.getString(context, "target")
        val marketRepo = ServerMarket.instance.database.marketRepository

        return try {
            val items = when (target.lowercase()) {
                "server" -> marketRepo.getSystemItems()
                else -> {
                    val sellerUuid = resolveSellerUuid(target, source.server) ?: run {
                        source.sendError(Text.literal(Language.get("command.mlist.player_not_found", target)))
                        return 0
                    }
                    marketRepo.getPlayerItems(sellerUuid)
                }
            }

            if (items.isEmpty()) {
                source.sendMessage(Text.literal(Language.get("command.mlist.no_items", target)))
                return 1
            }

            source.sendMessage(Text.literal(Language.get("command.mlist.title", target)).styled { it.withBold(true).withColor(0xA020F0) })
            items.forEach { (itemId, sellerName, price, quantity) ->
                source.sendMessage(
                    Text.literal("▸ $itemId")
                        .append(Text.literal(Language.get("ui.seller", sellerName)).styled { it.withColor(0x00FF00) })
                        .append(Text.literal(Language.get("ui.price", "%.2f".format(price))).styled { it.withColor(0xFFA500) })
                        .append(Text.literal(Language.get("ui.quantity", quantity.toString())).styled { it.withColor(0xADD8E6) }))
            }
            1
        } catch (e: Exception) {
            source.sendError(Text.literal(Language.get("command.mlist.query_failed")))
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
