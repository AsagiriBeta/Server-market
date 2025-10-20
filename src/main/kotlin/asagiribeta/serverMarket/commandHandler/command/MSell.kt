package asagiribeta.serverMarket.commandHandler.command

import asagiribeta.serverMarket.util.Language
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.CommandManager.argument
import com.mojang.brigadier.context.CommandContext
import net.minecraft.registry.Registries
import asagiribeta.serverMarket.ServerMarket
import net.minecraft.text.Text
import asagiribeta.serverMarket.util.ItemKey
import asagiribeta.serverMarket.util.PermissionUtil

class MSell {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            literal("msell")
                .requires(PermissionUtil.requirePlayer("servermarket.command.msell", 0))
                .then(argument("quantity", IntegerArgumentType.integer(1))
                    .executes(this::execute)
                )
        )
    }

    private fun execute(context: CommandContext<ServerCommandSource>): Int {
        val player = context.source.player ?: run {
            context.source.sendError(Text.literal(Language.get("command.msell.player_only")))
            return 0
        }
        val quantity = IntegerArgumentType.getInteger(context, "quantity")
        val mainHandStack = player.mainHandStack
        if (mainHandStack.isEmpty) {
            context.source.sendError(Text.literal(Language.get("command.msell.hold_item")))
            return 0
        }

        // 提前获取物品名称
        val itemName = mainHandStack.name.string
        val itemId = Registries.ITEM.getId(mainHandStack.item).toString()
        val snbt = ItemKey.snbtOf(mainHandStack)

        val db = ServerMarket.instance.database
        val repo = db.marketRepository
        val uuid = player.uuid

        // 先在后台校验是否存在该上架项
        db.supplyAsync { repo.hasPlayerItem(uuid, itemId, snbt) }
            .whenComplete { exists, ex ->
                context.source.server.execute {
                    if (ex != null) {
                        context.source.sendError(Text.literal(Language.get("command.msell.operation_failed")))
                        ServerMarket.LOGGER.error("msell命令执行失败", ex)
                        return@execute
                    }
                    if (exists != true) {
                        context.source.sendError(Text.literal(Language.get("command.msell.not_listed")))
                        return@execute
                    }

                    // 物品栏访问：仅筛选同ID且同NBT的堆栈
                    val allStacks = (0 until player.inventory.size()).map { player.inventory.getStack(it) }.filter {
                        !it.isEmpty && Registries.ITEM.getId(it.item).toString() == itemId && ItemKey.snbtOf(it) == snbt
                    }

                    val totalAvailable = allStacks.sumOf { it.count }
                    if (totalAvailable < quantity) {
                        context.source.sendError(Text.literal(Language.get("command.msell.insufficient_items", quantity)))
                        return@execute
                    }

                    // 扣除指定数量（优先主手）
                    var remaining = quantity
                    for (stack in allStacks) {
                        if (remaining <= 0) break
                        val deduct = minOf(remaining, stack.count)
                        stack.decrement(deduct)
                        remaining -= deduct
                    }

                    // 后台更新库存
                    db.runAsync { repo.incrementPlayerItemQuantity(uuid, itemId, snbt, quantity) }
                        .whenComplete { _, ex2 ->
                            context.source.server.execute {
                                if (ex2 != null) {
                                    context.source.sendError(Text.literal(Language.get("command.msell.operation_failed")))
                                    ServerMarket.LOGGER.error("msell命令更新库存失败", ex2)
                                } else {
                                    context.source.sendMessage(Text.literal(Language.get("command.msell.success", quantity, itemName)))
                                }
                            }
                        }
                }
            }
        return 1
    }
}
