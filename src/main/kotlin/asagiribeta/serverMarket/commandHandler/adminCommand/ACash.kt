package asagiribeta.serverMarket.commandHandler.adminCommand

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.util.Language
import asagiribeta.serverMarket.util.ItemKey
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.command.CommandSource
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text

class ACash {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        val root = CommandManager.literal("acash")
            .requires { it.hasPermissionLevel(4) }
            // /acash get
            .then(CommandManager.literal("get").executes(this::executeGet))
            // /acash del
            .then(CommandManager.literal("del").executes(this::executeDel))
            // /acash list [item]
            .then(
                CommandManager.literal("list")
                    .executes(this::executeList)
                    .then(
                        CommandManager.argument("item", StringArgumentType.string())
                            .suggests { _, builder ->
                                val ids = Registries.ITEM.ids.map { it.toString() }
                                CommandSource.suggestMatching(ids, builder)
                            }
                            .executes(this::executeList)
                    )
            )
            // 兼容：/acash <value>
            .then(
                CommandManager.argument("value", DoubleArgumentType.doubleArg(0.0))
                    .executes(this::executeSetValue)
            )
        dispatcher.register(root)
    }

    // 提取的公用方法：要求执行者为玩家且主手持有物品，否则提示并返回 null
    private fun requireHeldItem(source: ServerCommandSource): ItemStack? {
        val player = source.player ?: run {
            source.sendError(Text.literal(Language.get("command.acash.player_only")))
            return null
        }
        val stack = player.mainHandStack
        if (stack.isEmpty) {
            source.sendError(Text.literal(Language.get("command.acash.hold_item")))
            return null
        }
        return stack
    }

    private fun executeSetValue(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val value = DoubleArgumentType.getDouble(context, "value")
        if (value <= 0.0) {
            source.sendError(Text.literal(Language.get("command.acash.non_positive_value")))
            return 0
        }
        val stack = requireHeldItem(source) ?: return 0
        return try {
            val (itemId, snbt) = getItemSignature(stack)
            ServerMarket.instance.database.currencyRepository.upsertCurrency(itemId, snbt, value)
            source.sendMessage(Text.literal(Language.get("command.acash.success", stack.name.string, value)))
            1
        } catch (e: Exception) {
            ServerMarket.LOGGER.error("acash 命令设置面值失败", e)
            source.sendError(Text.literal(Language.get("command.acash.operation_failed")))
            0
        }
    }

    private fun executeGet(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val stack = requireHeldItem(source) ?: return 0
        return try {
            val (itemId, snbt) = getItemSignature(stack)
            val value = ServerMarket.instance.database.currencyRepository.getCurrencyValue(itemId, snbt)
            if (value != null) {
                source.sendMessage(Text.literal(Language.get("command.acash.get.success", value)))
                1
            } else {
                source.sendError(Text.literal(Language.get("command.acash.get.not_set")))
                0
            }
        } catch (e: Exception) {
            ServerMarket.LOGGER.error("acash get 执行失败", e)
            source.sendError(Text.literal(Language.get("command.acash.operation_failed")))
            0
        }
    }

    private fun executeDel(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val stack = requireHeldItem(source) ?: return 0
        return try {
            val (itemId, snbt) = getItemSignature(stack)
            val deleted = ServerMarket.instance.database.currencyRepository.deleteCurrency(itemId, snbt)
            if (deleted) {
                source.sendMessage(Text.literal(Language.get("command.acash.del.success", stack.name.string)))
                1
            } else {
                source.sendError(Text.literal(Language.get("command.acash.del.not_set")))
                0
            }
        } catch (e: Exception) {
            ServerMarket.LOGGER.error("acash del 执行失败", e)
            source.sendError(Text.literal(Language.get("command.acash.operation_failed")))
            0
        }
    }

    private fun executeList(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val player = context.source.player // 允许控制台
        val db = ServerMarket.instance.database
        val repo = db.currencyRepository
        return try {
            val itemArg = try { StringArgumentType.getString(context, "item") } catch (_: Exception) { null }
            val items = when {
                itemArg != null -> repo.listByItemId(itemArg, 100, 0)
                player != null && !player.mainHandStack.isEmpty -> {
                    val itemId = Registries.ITEM.getId(player.mainHandStack.item).toString()
                    repo.listByItemId(itemId, 100, 0)
                }
                else -> repo.listAll(20, 0)
            }
            if (items.isEmpty()) {
                source.sendError(Text.literal(Language.get("command.acash.list.empty")))
                return 0
            }
            source.sendMessage(Text.literal(Language.get("command.acash.list.title", items.size)))
            items.forEach { ci ->
                source.sendMessage(
                    Text.literal(
                        Language.get(
                            "command.acash.list.entry",
                            ci.itemId,
                            ci.nbt.ifEmpty { "<none>" },
                            ci.value
                        )
                    )
                )
            }
            1
        } catch (e: Exception) {
            ServerMarket.LOGGER.error("acash list 执行失败", e)
            source.sendError(Text.literal(Language.get("command.acash.operation_failed")))
            0
        }
    }

    private fun getItemSignature(stack: ItemStack): Pair<String, String> {
        val itemId = Registries.ITEM.getId(stack.item).toString()
        val snbt = ItemKey.snbtOf(stack)
        return itemId to snbt
    }
}
