package asagiribeta.serverMarket.commandHandler.command

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.util.ItemKey
import asagiribeta.serverMarket.util.MoneyFormat
import asagiribeta.serverMarket.util.PermissionUtil
import asagiribeta.serverMarket.util.TextFormat
import asagiribeta.serverMarket.util.whenCompleteOnServerThread
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.command.CommandSource
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text

class MList {
    // 构建 /svm list 子命令
    fun buildSubCommand(): LiteralArgumentBuilder<ServerCommandSource> {
        return literal("list")
            .requires(PermissionUtil.require("servermarket.command.list", 0))
            .then(argument("target", StringArgumentType.string())
                .suggests { context, builder ->
                    val server = context.source.server
                    val names = server.playerManager.playerNames + "server"
                    CommandSource.suggestMatching(names, builder)
                }
                .executes(this::execute)
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
        }.whenCompleteOnServerThread(source.server) { items, ex ->
            if (ex != null) {
                source.sendError(Text.translatable("servermarket.command.mlist.query_failed"))
                ServerMarket.LOGGER.error("/svm list failed", ex)
                return@whenCompleteOnServerThread
            }

            val list = items ?: emptyList()
            if (list.isEmpty()) {
                source.sendMessage(Text.translatable("servermarket.command.mlist.no_items", target))
                return@whenCompleteOnServerThread
            }

            source.sendMessage(
                Text.translatable("servermarket.command.mlist.title", target)
                    .styled { it.withBold(true).withColor(0xA020F0) }
            )

            list.forEach { item ->
                // Build a display stack so we can show localized name instead of minecraft:id + [NBT]
                val stack = ItemKey.tryBuildFullStackFromSnbt(item.nbt, 1)
                val displayName = TextFormat.displayItemName(stack ?: net.minecraft.item.ItemStack.EMPTY, item.itemId)

                source.sendMessage(
                    Text.literal("▸ $displayName")
                        .append(Text.translatable("servermarket.ui.seller", item.sellerName).styled { it.withColor(0x00FF00) })
                        .append(Text.translatable("servermarket.ui.price", MoneyFormat.format(item.price, 2)).styled { it.withColor(0xFFA500) })
                        .append(Text.translatable("servermarket.ui.quantity", item.quantity.toString()).styled { it.withColor(0xADD8E6) })
                )
            }
        }
        return 1
    }
}
