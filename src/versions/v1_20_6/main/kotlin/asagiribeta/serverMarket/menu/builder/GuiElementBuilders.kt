package asagiribeta.serverMarket.menu.builder

import asagiribeta.serverMarket.repository.SellerMenuEntry
import asagiribeta.serverMarket.util.marketServer
import com.mojang.authlib.GameProfile
import eu.pb4.sgui.api.elements.GuiElementBuilder
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtHelper
import net.minecraft.server.network.ServerPlayerEntity
import java.util.UUID

/**
 * GUI element builders for Minecraft 1.20.5 - 1.21.8 (legacy-compatible skull rendering).
 */
object GuiElementBuilders {
    private const val PROFILE_CACHE_MS = 10 * 60 * 1000L

    private data class CachedProfile(val profile: GameProfile, val atMs: Long)

    private val profileCache = java.util.concurrent.ConcurrentHashMap<UUID, CachedProfile>()

    fun obtainProfileByUuid(
        viewer: ServerPlayerEntity,
        uuid: UUID,
        name: String
    ): GameProfile? {
        val now = System.currentTimeMillis()
        profileCache[uuid]?.let { cached ->
            if (now - cached.atMs <= PROFILE_CACHE_MS) return cached.profile
        }

        val server = viewer.marketServer()
        server.playerManager.getPlayer(uuid)?.let { online ->
            profileCache[uuid] = CachedProfile(online.gameProfile, now)
            return online.gameProfile
        }

        return GameProfile(uuid, name).also { profileCache[uuid] = CachedProfile(it, now) }
    }

    fun obtainPlayerGameProfile(
        player: ServerPlayerEntity,
        entry: SellerMenuEntry
    ): GameProfile? {
        if (entry.sellerId.equals("SERVER", ignoreCase = true)) return null
        val uuid = try { UUID.fromString(entry.sellerId) } catch (_: Exception) { return null }
        return obtainProfileByUuid(player, uuid, entry.sellerName)
    }

    fun GuiElementBuilder.setPlayerSkin(profile: GameProfile?): GuiElementBuilder {
        if (profile == null) return this
        try {
            val stack = ItemStack(Items.PLAYER_HEAD)
            val tag = NbtCompound()
            val owner = NbtCompound()
            putUuid(owner, "Id", profile.id)
            if (!profile.name.isNullOrBlank()) owner.putString("Name", profile.name)
            tag.put("SkullOwner", owner)
            applyItemNbt(stack, tag)
            setGuiStack(this, stack)
        } catch (_: Exception) { }
        return this
    }

    fun createSellerIcon(entry: SellerMenuEntry): net.minecraft.item.Item {
        return if (entry.sellerId == "SERVER") Items.NETHER_STAR else Items.PLAYER_HEAD
    }

    private fun putUuid(tag: NbtCompound, key: String, uuid: UUID) {
        try {
            tag.javaClass.getMethod("putUuid", String::class.java, UUID::class.java)
                .invoke(tag, key, uuid)
            return
        } catch (_: Exception) { }
        try {
            val element = NbtHelper::class.java.getMethod("fromUuid", UUID::class.java)
                .invoke(null, uuid)
            if (element != null) {
                tag.put(key, element as net.minecraft.nbt.NbtElement)
                return
            }
        } catch (_: Exception) { }
        val msb = uuid.mostSignificantBits
        val lsb = uuid.leastSignificantBits
        tag.putIntArray(
            key,
            intArrayOf(
                (msb shr 32).toInt(),
                msb.toInt(),
                (lsb shr 32).toInt(),
                lsb.toInt()
            )
        )
    }

    private fun applyItemNbt(stack: ItemStack, tag: NbtCompound) {
        try {
            stack.javaClass.getMethod("setNbt", NbtCompound::class.java).invoke(stack, tag)
            return
        } catch (_: Exception) { }
        try {
            val componentType = Class.forName("net.minecraft.component.DataComponentTypes")
            val customData = componentType.getField("CUSTOM_DATA").get(null)
            val nbtComponent = Class.forName("net.minecraft.component.type.NbtComponent")
            val ofMethod = nbtComponent.getMethod("of", NbtCompound::class.java)
            val component = ofMethod.invoke(null, tag)
            stack.javaClass.getMethod("set", Class::class.java, Any::class.java)
                .invoke(stack, customData, component)
        } catch (_: Exception) { }
    }

    private fun setGuiStack(builder: GuiElementBuilder, stack: ItemStack) {
        val candidates = listOf("stack", "item", "setStack", "setItem")
        for (name in candidates) {
            try {
                val method = builder.javaClass.methods.firstOrNull {
                    it.name == name && it.parameterCount == 1 && it.parameterTypes[0].isAssignableFrom(ItemStack::class.java)
                }
                if (method != null) {
                    method.invoke(builder, stack)
                    return
                }
            } catch (_: Exception) { }
        }
    }
}
