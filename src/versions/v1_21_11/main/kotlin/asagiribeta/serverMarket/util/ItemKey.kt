package asagiribeta.serverMarket.util

import asagiribeta.serverMarket.ServerMarket
import com.mojang.serialization.DynamicOps
import net.minecraft.component.DataComponentTypes
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtHelper
import net.minecraft.nbt.NbtList
import net.minecraft.nbt.NbtOps
import net.minecraft.registry.RegistryOps
import java.util.Collections
import java.util.LinkedHashMap

/**
 * ItemStack <-> SNBT utilities for Minecraft 1.21.9+.
 *
 * We store a normalized ItemStack SNBT (count forced to 1) as the DB key.
 */
@Suppress("unused")
object ItemKey {
    private val snbtCache = Collections.synchronizedMap(
        object : LinkedHashMap<Int, String>(200, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, String>?) = size > 200
        }
    )

    private val normalizedSnbtCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(200, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 200
        }
    )

    private val stackCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, ItemStack>(200, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ItemStack>?) = size > 200
        }
    )

    private fun ops(): DynamicOps<NbtElement> {
        val server = ServerMarket.instance.server
        return if (server != null) RegistryOps.of(NbtOps.INSTANCE, server.registryManager) else NbtOps.INSTANCE
    }

    fun snbtOf(stack: ItemStack): String {
        val cacheKey = stack.components.hashCode()
        return snbtCache.getOrPut(cacheKey) { computeSnbtInternal(stack) }
    }

    private fun computeSnbtInternal(stack: ItemStack): String {
        val normalized = stack.copyWithCount(1)
        val el = ItemStack.CODEC.encodeStart(ops(), normalized).result().orElse(null) ?: return ""
        if (el !is NbtCompound) return ""

        sanitizeItemCompound(el)
        if (el.contains("count")) el.putByte("count", 1)
        if (el.contains("Count")) el.putByte("Count", 1)
        return el.toString()
    }

    fun normalizeSnbt(snbt: String): String {
        if (snbt.isEmpty()) return snbt
        return normalizedSnbtCache.getOrPut(snbt) {
            try {
                val el = NbtHelper.fromNbtProviderString(snbt)
                if (el is NbtCompound) {
                    sanitizeItemCompound(el)
                    if (el.contains("count")) el.putByte("count", 1)
                    if (el.contains("Count")) el.putByte("Count", 1)
                    el.toString()
                } else snbt
            } catch (_: Exception) {
                snbt
            }
        }
    }

    private fun sanitizeItemCompound(tag: NbtCompound) {
        if (!tag.contains("components")) return
        val comps = tag.getCompoundOrEmpty("components")

        if (comps.contains("minecraft:custom_data")) {
            val cd = comps.getCompoundOrEmpty("minecraft:custom_data")
            val itemId = tag.getString("id").orElse(null)
            sanitizeCustomData(cd, itemId)
            if (cd.isEmpty) comps.remove("minecraft:custom_data")
        }

        if (comps.isEmpty) tag.remove("components")
    }

    private fun sanitizeCustomData(cd: NbtCompound, itemId: String?) {
        if (cd.contains("palette")) {
            val pel = cd.get("palette")
            if (pel is NbtList && pel.isEmpty()) cd.remove("palette")
        }
        cd.remove("count")
        cd.remove("Count")
        if (itemId != null && cd.contains("id") && cd.getString("id").orElse("") == itemId) {
            cd.remove("id")
        }
    }

    fun applySnbt(stack: ItemStack, snbt: String) {
        if (snbt.isEmpty()) return
        val decoded = tryBuildFullStackFromSnbt(snbt, stack.count) ?: return
        decoded.get(DataComponentTypes.CUSTOM_DATA)?.let { stack.set(DataComponentTypes.CUSTOM_DATA, it) }
        decoded.get(DataComponentTypes.ENCHANTMENTS)?.let { stack.set(DataComponentTypes.ENCHANTMENTS, it) }
    }

    fun tryBuildFullStackFromSnbt(snbt: String, desiredCount: Int): ItemStack? {
        if (snbt.isEmpty()) return null

        val norm = normalizeSnbt(snbt)
        val base = stackCache.getOrPut(norm) {
            val el = try { NbtHelper.fromNbtProviderString(norm) } catch (_: Exception) { return@getOrPut ItemStack.EMPTY }
            if (el !is NbtCompound || !el.contains("id")) return@getOrPut ItemStack.EMPTY
            ItemStack.CODEC.parse(ops(), el).result().orElse(null) ?: ItemStack.EMPTY
        }

        if (base.isEmpty) return null
        return base.copyWithCount(desiredCount)
    }

    fun customDataOnly(stack: ItemStack): String {
        return try {
            val base = NbtCompound()
            base.putString("id", net.minecraft.registry.Registries.ITEM.getId(stack.item).toString())

            stack.get(DataComponentTypes.CUSTOM_DATA)?.copyNbt()?.let { cd ->
                sanitizeCustomData(cd, base.getString("id", ""))
                if (!cd.isEmpty) base.put("_customData", cd)
            }
            base.toString()
        } catch (_: Exception) {
            ""
        }
    }
}
