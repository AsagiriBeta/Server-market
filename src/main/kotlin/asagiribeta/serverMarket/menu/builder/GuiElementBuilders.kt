package asagiribeta.serverMarket.menu.builder

import asagiribeta.serverMarket.repository.SellerMenuEntry
import com.mojang.authlib.GameProfile
import eu.pb4.sgui.api.elements.GuiElementBuilder
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.ProfileComponent
import net.minecraft.item.Items
import net.minecraft.server.network.ServerPlayerEntity
import java.util.*

/**
 * GUI 元素构建器
 * 提供创建各种 GUI 元素的工具方法
 */
object GuiElementBuilders {

    /**
     * 获取玩家的 GameProfile（用于显示玩家头像）
     */
    fun obtainPlayerGameProfile(
        player: ServerPlayerEntity,
        entry: SellerMenuEntry
    ): GameProfile? {
        if (entry.sellerId.equals("SERVER", ignoreCase = true)) return null
        val uuid = try { UUID.fromString(entry.sellerId) } catch (_: Exception) { return null }
        val server = player.server ?: return null

        // 优先获取在线玩家的 GameProfile
        server.playerManager.getPlayer(uuid)?.let { return it.gameProfile }

        // 其次尝试从缓存获取
        val cache = server.userCache
        val cached = try { cache?.getByUuid(uuid)?.orElse(null) } catch (_: Exception) { null }
        if (cached != null) return cached

        return GameProfile(uuid, entry.sellerName)
    }

    /**
     * 为玩家头像设置皮肤
     */
    fun GuiElementBuilder.setPlayerSkin(profile: GameProfile?): GuiElementBuilder {
        if (profile != null) {
            try {
                this.setComponent(DataComponentTypes.PROFILE, ProfileComponent(profile))
            } catch (_: Exception) { }
        }
        return this
    }

    /**
     * 创建卖家头像元素（但不设置回调）
     */
    fun createSellerIcon(entry: SellerMenuEntry): net.minecraft.item.Item {
        return if (entry.sellerId == "SERVER") Items.NETHER_STAR else Items.PLAYER_HEAD
    }
}

