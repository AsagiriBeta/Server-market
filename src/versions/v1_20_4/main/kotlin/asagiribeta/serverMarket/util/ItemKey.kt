package asagiribeta.serverMarket.util

import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtHelper
import net.minecraft.nbt.StringNbtReader
import net.minecraft.registry.Registries
import java.util.Collections
import java.util.LinkedHashMap

/**
 * ItemStack <-> SNBT utilities for Minecraft 1.20 – 1.20.4 (pre data-components).
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

    fun snbtOf(stack: ItemStack): String {
        val cacheKey = stack.hashCode()
        return snbtCache.getOrPut(cacheKey) { computeSnbtInternal(stack) }
    }

    private fun computeSnbtInternal(stack: ItemStack): String {
        val nbt = NbtCompound()
        stack.writeNbt(nbt)
        sanitizeItemCompound(nbt)
        nbt.putByte("Count", 1)
        return nbt.toString()
    }

    fun normalizeSnbt(snbt: String): String {
        if (snbt.isEmpty()) return snbt
        return normalizedSnbtCache.getOrPut(snbt) {
            try {
                val el = parseSnbt(snbt)
                if (el != null) {
                    sanitizeItemCompound(el)
                    el.putByte("Count", 1)
                    el.toString()
                } else snbt
            } catch (_: Exception) {
                snbt
            }
        }
    }

    private fun parseSnbt(snbt: String): NbtCompound? {
        return try {
            val parsed = StringNbtReader.parse(snbt)
            if (parsed is NbtCompound) parsed else null
        } catch (_: Exception) {
            try {
                for (methodName in listOf("fromNbtString", "fromNbtProviderString")) {
                    try {
                        val method = NbtHelper::class.java.getMethod(methodName, String::class.java)
                        val result = method.invoke(null, snbt)
                        if (result is NbtCompound) return result
                    } catch (_: Exception) { }
                }
                null
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun sanitizeItemCompound(tag: NbtCompound) {
        if (tag.contains("tag")) {
            val itemTag = tag.getCompound("tag")
            itemTag.remove("count")
            itemTag.remove("Count")
            if (itemTag.isEmpty) tag.remove("tag")
        }
    }

    fun applySnbt(stack: ItemStack, snbt: String) {
        if (snbt.isEmpty()) return
        val decoded = tryBuildFullStackFromSnbt(snbt, stack.count) ?: return
        val nbt = NbtCompound()
        decoded.writeNbt(nbt)
        copyStackFromNbt(stack, nbt)
    }

    fun tryBuildFullStackFromSnbt(snbt: String, desiredCount: Int): ItemStack? {
        if (snbt.isEmpty()) return null

        val norm = normalizeSnbt(snbt)
        val base = stackCache.getOrPut(norm) {
            val el = parseSnbt(norm) ?: return@getOrPut ItemStack.EMPTY
            if (!el.contains("id")) return@getOrPut ItemStack.EMPTY
            stackFromNbt(el)
        }

        if (base.isEmpty) return null
        return base.copy().also { it.count = desiredCount }
    }

    fun customDataOnly(stack: ItemStack): String {
        return try {
            val base = NbtCompound()
            base.putString("id", Registries.ITEM.getId(stack.item).toString())
            val tag = NbtCompound()
            stack.writeNbt(tag)
            if (tag.contains("tag")) {
                val itemTag = tag.getCompound("tag")
                sanitizeItemCompound(itemTag)
                if (!itemTag.isEmpty) base.put("tag", itemTag)
            }
            base.toString()
        } catch (_: Exception) {
            ""
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

    private fun copyStackFromNbt(stack: ItemStack, nbt: NbtCompound) {
        try {
            stack.javaClass.getMethod("readNbt", NbtCompound::class.java).invoke(stack, nbt)
        } catch (_: Exception) {
            stackFromNbt(nbt.copy()).takeIf { !it.isEmpty }?.let { decoded ->
                stack.count = decoded.count
            }
        }
    }
}
