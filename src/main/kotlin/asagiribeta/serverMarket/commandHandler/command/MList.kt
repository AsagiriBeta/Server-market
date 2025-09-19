package asagiribeta.serverMarket.commandHandler.command

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.util.Language
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.command.CommandSource
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text

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
        val db = ServerMarket.instance.database
        val repo = db.marketRepository

        db.supplyAsync {
            if (target.equals("server", ignoreCase = true)) {
                repo.getSystemItems()
            } else {
                val sellerUuid = repo.findSellerUuidByName(target)
                    ?: return@supplyAsync emptyList()
                repo.getPlayerItems(sellerUuid)
            }
        }.whenComplete { items, ex ->
            source.server.execute {
                if (ex != null) {
                    source.sendError(Text.literal(Language.get("command.mlist.query_failed")))
                    ServerMarket.LOGGER.error("mlist命令执行失败", ex)
                    return@execute
                }
                val list = items ?: emptyList()
                if (list.isEmpty()) {
                    source.sendMessage(Text.literal(Language.get("command.mlist.no_items", target)))
                    return@execute
                }
                source.sendMessage(Text.literal(Language.get("command.mlist.title", target)).styled { it.withBold(true).withColor(0xA020F0) })
                list.forEach { item ->
                    val nbtSuffix = if (item.nbt.isNotEmpty()) " [NBT]" else ""
                    source.sendMessage(
                        Text.literal("▸ ${item.itemId}$nbtSuffix")
                            .append(Text.literal(Language.get("ui.seller", item.sellerName)).styled { it.withColor(0x00FF00) })
                            .append(Text.literal(Language.get("ui.price", "%.2f".format(item.price))).styled { it.withColor(0xFFA500) })
                            .append(Text.literal(Language.get("ui.quantity", item.quantity.toString())).styled { it.withColor(0xADD8E6) })
                    )
                }
            }
        }
        return 1
    }
}
