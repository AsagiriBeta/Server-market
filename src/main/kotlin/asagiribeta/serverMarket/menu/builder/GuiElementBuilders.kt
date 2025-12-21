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
    private const val PROFILE_CACHE_MS = 10 * 60 * 1000L

    private data class CachedProfile(val profile: GameProfile, val atMs: Long)

    private val profileCache = java.util.concurrent.ConcurrentHashMap<UUID, CachedProfile>()


    /**
     * 获取玩家的 GameProfile（用于显示玩家头像）
     */
    fun obtainPlayerGameProfile(
        player: ServerPlayerEntity,
        entry: SellerMenuEntry
    ): GameProfile? {
        if (entry.sellerId.equals("SERVER", ignoreCase = true)) return null
        val uuid = try { UUID.fromString(entry.sellerId) } catch (_: Exception) { return null }
        val server = player.entityWorld.server

        val now = System.currentTimeMillis()
        profileCache[uuid]?.let { cached ->
            if (now - cached.atMs <= PROFILE_CACHE_MS) return cached.profile
        }

        // 优先获取在线玩家的 GameProfile
        val online = server.playerManager.getPlayer(uuid)
        if (online != null) {
            profileCache[uuid] = CachedProfile(online.gameProfile, now)
            return online.gameProfile
        }

        // 其次尝试通过 GameProfileResolver 获取（可能命中磁盘缓存或在线解析）
        val resolved = try {
            server.apiServices.profileResolver().getProfileById(uuid).orElse(null)
        } catch (_: Exception) {
            null
        }
        if (resolved != null) {
            profileCache[uuid] = CachedProfile(resolved, now)
            return resolved
        }

        return GameProfile(uuid, entry.sellerName).also { profileCache[uuid] = CachedProfile(it, now) }
    }

    /**
     * 为玩家头像设置皮肤
     */
    fun GuiElementBuilder.setPlayerSkin(profile: GameProfile?): GuiElementBuilder {
        if (profile != null) {
            try {
                this.setComponent(DataComponentTypes.PROFILE, ProfileComponent.ofStatic(profile))
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

