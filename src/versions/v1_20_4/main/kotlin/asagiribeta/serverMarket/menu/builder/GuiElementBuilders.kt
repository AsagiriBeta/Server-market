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

/** GUI element builders for Minecraft 1.20 – 1.20.4. */
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

        viewer.marketServer().playerManager.getPlayer(uuid)?.let { online ->
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
            val owner = NbtCompound()
            putUuid(owner, "Id", profile.id)
            if (!profile.name.isNullOrBlank()) owner.putString("Name", profile.name)
            val tag = NbtCompound()
            tag.put("SkullOwner", owner)
            val nbt = NbtCompound()
            val stack = ItemStack(Items.PLAYER_HEAD)
            stack.writeNbt(nbt)
            nbt.put("tag", tag)
            val head = stackFromNbt(nbt).takeIf { !it.isEmpty } ?: stack
            setGuiStack(this, head)
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
        } catch (_: Exception) {
            tag.putIntArray(
                key,
                intArrayOf(
                    (uuid.mostSignificantBits shr 32).toInt(),
                    uuid.mostSignificantBits.toInt(),
                    (uuid.leastSignificantBits shr 32).toInt(),
                    uuid.leastSignificantBits.toInt()
                )
            )
        }
    }

    private fun stackFromNbt(nbt: NbtCompound): ItemStack {
        return try {
            val method = ItemStack::class.java.getMethod("fromNbt", NbtCompound::class.java)
            when (val result = method.invoke(null, nbt)) {
                is ItemStack -> result
                is java.util.Optional<*> -> (result.orElse(null) as? ItemStack) ?: ItemStack.EMPTY
                else -> ItemStack.EMPTY
            }
        } catch (_: Exception) {
            ItemStack.EMPTY
        }
    }

    private fun setGuiStack(builder: GuiElementBuilder, stack: ItemStack) {
        for (name in listOf("stack", "item", "setStack", "setItem")) {
            try {
                val method = builder.javaClass.methods.firstOrNull {
                    it.name == name && it.parameterCount == 1 &&
                        it.parameterTypes[0].isAssignableFrom(ItemStack::class.java)
                }
                if (method != null) {
                    method.invoke(builder, stack)
                    return
                }
            } catch (_: Exception) { }
        }
    }
}
