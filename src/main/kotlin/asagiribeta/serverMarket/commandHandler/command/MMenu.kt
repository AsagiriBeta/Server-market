package asagiribeta.serverMarket.commandHandler.command

import asagiribeta.serverMarket.menu.MarketMenuScreenHandler
import asagiribeta.serverMarket.util.Language
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import net.minecraft.screen.ScreenHandlerContext
import net.minecraft.screen.SimpleNamedScreenHandlerFactory
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text

class MMenu {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            literal("mmenu")
                .executes(this::openMenu)
        )
    }

    private fun openMenu(ctx: CommandContext<ServerCommandSource>): Int {
        val player = ctx.source.player as? ServerPlayerEntity ?: run {
            ctx.source.sendError(Text.literal(Language.get("error.player_only")))
            return 0
        }
        player.openHandledScreen(SimpleNamedScreenHandlerFactory({ syncId, inv, _ ->
            MarketMenuScreenHandler(syncId, inv, ScreenHandlerContext.EMPTY, emptyList())
        }, Text.literal(Language.get("menu.title"))))
        return 1
    }
}
