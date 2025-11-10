package asagiribeta.serverMarket.commandHandler.adminCommand

import asagiribeta.serverMarket.ServerMarket
import asagiribeta.serverMarket.util.Language
import asagiribeta.serverMarket.util.ItemKey
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.command.CommandSource
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import asagiribeta.serverMarket.util.PermissionUtil

class ACash {
    // 构建 /svm edit cash 子命令
    fun buildSubCommand(): LiteralArgumentBuilder<ServerCommandSource> {
        return CommandManager.literal("cash")
            .requires(PermissionUtil.require("servermarket.admin.cash", 4))
            // /svm edit cash get
            .then(CommandManager.literal("get").executes(this::executeGet))
            // /svm edit cash del
            .then(CommandManager.literal("del").executes(this::executeDel))
            // /svm edit cash list [item]
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
            // /svm edit cash <value>
            .then(
                CommandManager.argument("value", DoubleArgumentType.doubleArg(0.0))
                    .executes(this::executeSetValue)
            )
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
        val (itemId, snbt) = getItemSignature(stack)
        // 使用 CurrencyService 设置面值
        ServerMarket.instance.currencyService.setCurrencyValue(itemId, snbt, value).whenComplete { success, ex ->
            source.server.execute {
                if (ex != null) {
                    ServerMarket.LOGGER.error("acash 命令设置面值失败", ex)
                    source.sendError(Text.literal(Language.get("command.acash.operation_failed")))
                } else if (success) {
                    source.sendMessage(Text.literal(Language.get("command.acash.success", stack.name.string, value)))
                } else {
                    source.sendError(Text.literal(Language.get("command.acash.operation_failed")))
                }
            }
        }
        return 1
    }

    private fun executeGet(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val stack = requireHeldItem(source) ?: return 0
        val (itemId, snbt) = getItemSignature(stack)
        // 异步读库（使用仓库异步API）
        val repo = ServerMarket.instance.database.currencyRepository
        repo.getCurrencyValueAsync(itemId, snbt).whenComplete { value, ex ->
            source.server.execute {
                if (ex != null) {
                    ServerMarket.LOGGER.error("acash get 执行失败", ex)
                    source.sendError(Text.literal(Language.get("command.acash.operation_failed")))
                } else if (value != null) {
                    source.sendMessage(Text.literal(Language.get("command.acash.get.success", value)))
                } else {
                    source.sendError(Text.literal(Language.get("command.acash.get.not_set")))
                }
            }
        }
        return 1
    }

    private fun executeDel(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val stack = requireHeldItem(source) ?: return 0
        val (itemId, snbt) = getItemSignature(stack)
        // 使用 CurrencyService 移除货币设置
        ServerMarket.instance.currencyService.removeCurrency(itemId, snbt).whenComplete { deleted, ex ->
            source.server.execute {
                if (ex != null) {
                    ServerMarket.LOGGER.error("acash del 执行失败", ex)
                    source.sendError(Text.literal(Language.get("command.acash.operation_failed")))
                } else if (deleted == true) {
                    source.sendMessage(Text.literal(Language.get("command.acash.del.success", stack.name.string)))
                } else {
                    source.sendError(Text.literal(Language.get("command.acash.del.not_set")))
                }
            }
        }
        return 1
    }

    private fun executeList(context: CommandContext<ServerCommandSource>): Int {
        val source = context.source
        val player = context.source.player // 允许控制台
        val repo = ServerMarket.instance.database.currencyRepository
        val itemArg = try { StringArgumentType.getString(context, "item") } catch (_: Exception) { null }
        // 异步查询列表（使用仓库异步API）
        val future = when {
            itemArg != null -> repo.listByItemIdAsync(itemArg, 100, 0)
            player != null && !player.mainHandStack.isEmpty -> {
                val itemId = Registries.ITEM.getId(player.mainHandStack.item).toString()
                repo.listByItemIdAsync(itemId, 100, 0)
            }
            else -> repo.listAllAsync(20, 0)
        }
        future.whenComplete { items, ex ->
            source.server.execute {
                if (ex != null) {
                    ServerMarket.LOGGER.error("acash list 执行失败", ex)
                    source.sendError(Text.literal(Language.get("command.acash.operation_failed")))
                    return@execute
                }
                val list = items ?: emptyList()
                if (list.isEmpty()) {
                    source.sendError(Text.literal(Language.get("command.acash.list.empty")))
                    return@execute
                }
                source.sendMessage(Text.literal(Language.get("command.acash.list.title", list.size)))
                list.forEach { ci ->
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
            }
        }
        return 1
    }

    private fun getItemSignature(stack: ItemStack): Pair<String, String> {
        val itemId = Registries.ITEM.getId(stack.item).toString()
        val snbt = ItemKey.snbtOf(stack)
        return itemId to snbt
    }
}
