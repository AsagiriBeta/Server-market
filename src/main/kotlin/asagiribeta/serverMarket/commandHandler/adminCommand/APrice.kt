package asagiribeta.serverMarket.commandHandler.adminCommand

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.util.Language
import asagiribeta.serverMarket.util.ItemKey
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.registry.Registries
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import asagiribeta.serverMarket.util.PermissionUtil

class APrice {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            CommandManager.literal("aprice")
                .requires(PermissionUtil.require("servermarket.admin.aprice", 4))
                .then(
                    CommandManager.argument("price", DoubleArgumentType.doubleArg(0.0))
                        .executes(this::execute)
                        .then(
                            CommandManager.argument("limitPerDay", IntegerArgumentType.integer(-1))
                                .executes(this::executeWithLimit)
                        )
                )
        )
    }

    // 提取公共准备逻辑：校验玩家、手持物品，解析价格，生成 itemId 与 nbt
    private data class Prepared(
        val source: ServerCommandSource,
        val itemName: String,
        val itemId: String,
        val nbt: String,
        val price: Double
    )

    private fun prepare(context: CommandContext<ServerCommandSource>): Prepared? {
        val source = context.source
        val player = source.player ?: run {
            source.sendError(Text.literal(Language.get("command.aprice.player_only")))
            return null
        }

        val itemStack = player.mainHandStack
        if (itemStack.isEmpty) {
            source.sendError(Text.literal(Language.get("command.aprice.hold_item")))
            return null
        }

        val price = DoubleArgumentType.getDouble(context, "price")
        val itemId = Registries.ITEM.getId(itemStack.item).toString()
        val nbt = ItemKey.snbtOf(itemStack)
        val itemName = itemStack.name.string
        return Prepared(source, itemName, itemId, nbt, price)
    }

    // 提取公共完成回调，统一在主线程反馈结果
    private fun handleCompletion(prepared: Prepared, ex: Throwable?) {
        prepared.source.server.execute {
            if (ex != null) {
                prepared.source.sendError(Text.literal(Language.get("command.aprice.operation_failed")))
                ServerMarket.LOGGER.error("aprice命令执行失败", ex)
            } else {
                prepared.source.sendMessage(
                    Text.literal(
                        Language.get("command.aprice.update_success", prepared.itemName, prepared.price)
                    )
                )
            }
        }
    }

    private fun execute(context: CommandContext<ServerCommandSource>): Int {
        val prepared = prepare(context) ?: return 0

        val repo = ServerMarket.instance.database.marketRepository
        // 直接 UPSERT，避免多一次 has 查询
        ServerMarket.instance.database.runAsync {
            repo.addSystemItem(prepared.itemId, prepared.nbt, prepared.price, -1)
        }.whenComplete { _, ex ->
            handleCompletion(prepared, ex)
        }
        return 1
    }

    private fun executeWithLimit(context: CommandContext<ServerCommandSource>): Int {
        val prepared = prepare(context) ?: return 0

        val limitPerDay = IntegerArgumentType.getInteger(context, "limitPerDay")
        val repo = ServerMarket.instance.database.marketRepository
        ServerMarket.instance.database.runAsync {
            repo.addSystemItem(prepared.itemId, prepared.nbt, prepared.price, limitPerDay)
        }.whenComplete { _, ex ->
            handleCompletion(prepared, ex)
        }
        return 1
    }
}
