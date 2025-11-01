package asagiribeta.serverMarket.util

import asagiribeta.serverMarket.ServerMarket
import net.minecraft.item.ItemStack
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.NbtComponent
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtHelper
import net.minecraft.nbt.NbtOps
import net.minecraft.registry.RegistryOps
import net.minecraft.registry.Registries
import net.minecraft.nbt.NbtElement
import net.minecraft.component.type.ItemEnchantmentsComponent
import com.mojang.serialization.Codec
import net.minecraft.nbt.NbtList
import java.lang.reflect.Modifier
import java.util.*

@Suppress("unused")
object ItemKey {
    private val itemStackCodec: Codec<ItemStack>? by lazy {
        try { ItemStack.CODEC } catch (t: Throwable) {
            ServerMarket.LOGGER.debug("ItemStack.CODEC 不可用: ${t::class.java.simpleName}")
            null
        }
    }
    private val enchantmentsCodec: Codec<ItemEnchantmentsComponent>? by lazy {
        try { ItemEnchantmentsComponent.CODEC } catch (t: Throwable) {
            ServerMarket.LOGGER.debug("ItemEnchantmentsComponent.CODEC 不可用: ${t::class.java.simpleName}")
            null
        }
    }

    // NBT 缓存：LRU 缓存，最多 200 条记录
    private val snbtCache = Collections.synchronizedMap(
        object : LinkedHashMap<Int, String>(200, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, String>?) = size > 200
        }
    )

    fun snbtOf(stack: ItemStack): String {
        // 使用 components hashCode 作为缓存键
        val cacheKey = stack.components.hashCode()
        return snbtCache.getOrPut(cacheKey) {
            computeSnbtInternal(stack)
        }
    }

    private fun computeSnbtInternal(stack: ItemStack): String {
        try {
            val viaCodec = NbtCompound()
            if (encodeViaCodec(stack, viaCodec)) {
                sanitizeItemCompound(viaCodec)
                if (viaCodec.contains("Count")) viaCodec.putByte("Count", 1)
                if (viaCodec.contains("count")) viaCodec.putByte("count", 1)
                return viaCodec.toString()
            }
        } catch (_: Exception) { }
        return try {
            val full = NbtCompound()
            if (!tryWriteFull(stack, full)) {
                return customDataOnly(stack)
            }
            sanitizeItemCompound(full)
            if (full.contains("Count")) full.putByte("Count", 1)
            if (full.contains("count")) full.putByte("count", 1)
            full.toString()
        } catch (_: Exception) {
            customDataOnly(stack)
        }
    }

    // 对外暴露：将任意物品 SNBT 进行标准化清洗（移除无意义 custom_data 等）
    fun normalizeSnbt(snbt: String): String {
        if (snbt.isEmpty()) return snbt
        return try {
            val el = NbtHelper.fromNbtProviderString(snbt)
            if (el is NbtCompound) {
                sanitizeItemCompound(el)
                el.toString()
            } else snbt
        } catch (_: Exception) { snbt }
    }

    private fun customDataOnly(stack: ItemStack): String {
        return try {
            val base = NbtCompound()
            val id = Registries.ITEM.getId(stack.item).toString()
            base.putString("id", id)
            val custom: NbtComponent? = stack.get(DataComponentTypes.CUSTOM_DATA)
            if (custom != null) {
                val tag = try { custom.copyNbt() } catch (_: Exception) { null }
                if (tag != null) {
                    sanitizeCustomData(tag, id)
                    if (!tag.isEmpty) base.put("_customData", tag)
                }
            }
            try {
                val ench: ItemEnchantmentsComponent? = stack.get(DataComponentTypes.ENCHANTMENTS)
                if (ench != null && !ench.isEmpty) {
                    val enc = encodeEnchantmentsComponent(ench)
                    if (enc != null) base.put("_enchantments", enc)
                }
            } catch (_: Exception) { }
            base.toString()
        } catch (_: Exception) { "" }
    }

    // 兼容不同版本 NBT API 的辅助方法
    private fun getElement(tag: NbtCompound, key: String): NbtElement? {
        try {
            // 尝试调用 get(String)
            val m = tag.javaClass.methods.firstOrNull { it.name == "get" && it.parameterCount == 1 && it.parameterTypes[0] == String::class.java }
            if (m != null) {
                val ret = m.invoke(tag, key)
                // 可能返回 Optional 或 NbtElement
                if (ret is java.util.Optional<*>) return ret.orElse(null) as? NbtElement
                return ret as? NbtElement
            }
        } catch (_: Exception) { }
        try {
            // 尝试 getCompound(String)
            val m2 = tag.javaClass.methods.firstOrNull { it.name.equals("getCompound", true) && it.parameterCount == 1 && it.parameterTypes[0] == String::class.java }
            if (m2 != null) {
                val ret = m2.invoke(tag, key)
                return when (ret) {
                    is NbtCompound -> ret
                    is java.util.Optional<*> -> ret.orElse(null) as? NbtCompound
                    else -> null
                }
            }
        } catch (_: Exception) { }
        return null
    }

    @Suppress("SameParameterValue")
    private fun getStringCompat(tag: NbtCompound, key: String): String? {
        try {
            val m = tag.javaClass.methods.firstOrNull { it.name == "getString" && it.parameterCount == 1 && it.parameterTypes[0] == String::class.java }
            if (m != null) {
                val ret = m.invoke(tag, key)
                return when (ret) {
                    is String -> ret
                    is java.util.Optional<*> -> ret.orElse(null) as? String
                    else -> ret?.toString()
                }
            }
        } catch (_: Exception) { }
        try {
            val el = getElement(tag, key) ?: return null
            val asStr = el.javaClass.methods.firstOrNull { it.name == "asString" && it.parameterCount == 0 }
            if (asStr != null) return asStr.invoke(el) as? String
        } catch (_: Exception) { }
        return null
    }

    private fun removeKey(tag: NbtCompound, key: String) {
        try {
            val m = tag.javaClass.methods.firstOrNull { it.name == "remove" && it.parameterCount == 1 && it.parameterTypes[0] == String::class.java }
            if (m != null) {
                m.invoke(tag, key)
                return
            }
        } catch (_: Exception) { }
        // 尝试放置一个空值覆盖（通常不可行），忽略失败
        try { tag.put(key, null) } catch (_: Exception) { }
    }

    // 移除无意义的自定义数据（仅包含 id/count/空 palette 等）
    private fun sanitizeItemCompound(tag: NbtCompound) {
        try {
            // 仅检查键是否存在，忽略类型参数以兼容新版 API
            if (tag.contains("components")) {
                val comps = getElement(tag, "components") as? NbtCompound
                if (comps != null) {
                    val cd = getElement(comps, "minecraft:custom_data") as? NbtCompound
                    val itemId = getStringCompat(tag, "id")
                    if (cd != null) {
                        sanitizeCustomData(cd, itemId)
                        if (cd.isEmpty) {
                            removeKey(comps, "minecraft:custom_data")
                        }
                    }
                    if (comps.isEmpty) removeKey(tag, "components")
                }
            }
        } catch (_: Exception) { }
    }

    private fun sanitizeCustomData(cd: NbtCompound, itemId: String?) {
        try {
            // 移除空 palette
            val pel = getElement(cd, "palette")
            if (pel is NbtList && pel.isEmpty()) removeKey(cd, "palette")
            // 移除计数字段
            if (cd.contains("count")) removeKey(cd, "count")
            if (cd.contains("Count")) removeKey(cd, "Count")
            // 如果 id 与物品 id 一致则移除
            if (itemId != null) {
                val selfId = getStringCompat(cd, "id")
                if (selfId != null && selfId == itemId) removeKey(cd, "id")
            }
        } catch (_: Exception) { }
    }

    private fun tryWriteFull(stack: ItemStack, into: NbtCompound): Boolean {
        try {
            val ret = try { stack::class.java.getMethod("writeNbt", NbtCompound::class.java).invoke(stack, into) } catch (_: NoSuchMethodException) {
                try {
                    val m = stack.javaClass.declaredMethods.firstOrNull { it.parameterCount == 1 && it.parameterTypes[0] == NbtCompound::class.java && it.returnType == NbtCompound::class.java }
                    if (m != null) m.invoke(stack, into) else null
                } catch (_: Exception) { null }
            }
            if (ret is NbtCompound && ret !== into) ret.keys.forEach { k -> into.put(k, ret.get(k)) }
            if (into.contains("id")) return true
        } catch (_: Exception) { }
        try {
            val m = stack.javaClass.getMethod("writeNbt", NbtCompound::class.java)
            m.invoke(stack, into)
            if (into.contains("id")) return true
        } catch (_: Exception) { }
        try {
            val lookup = obtainRegistryLookup()
            if (lookup != null) {
                val methods = stack.javaClass.methods.filter { it.name.equals("writeNbt", true) || it.name.equals("save", true) || it.name.equals("toNbt", true) }
                for (m in methods) if (m.parameterCount == 2) {
                    val p0 = m.parameterTypes[0]; val p1 = m.parameterTypes[1]
                    val hasCompoundFirst = NbtCompound::class.java.isAssignableFrom(p0)
                    val hasCompoundSecond = NbtCompound::class.java.isAssignableFrom(p1)
                    if (hasCompoundFirst xor hasCompoundSecond) {
                        try {
                            if (hasCompoundFirst && p1.isInstance(lookup)) {
                                val ret = m.invoke(stack, into, lookup)
                                if (ret is NbtCompound && ret !== into) ret.keys.forEach { k -> into.put(k, ret.get(k)) }
                                if (into.contains("id")) return true
                            } else if (hasCompoundSecond && p0.isInstance(lookup)) {
                                val ret = m.invoke(stack, lookup, into)
                                if (ret is NbtCompound && ret !== into) ret.keys.forEach { k -> into.put(k, ret.get(k)) }
                                if (into.contains("id")) return true
                            }
                        } catch (_: Exception) { }
                    }
                }
            }
        } catch (_: Exception) { }
        try {
            val candidate = stack.javaClass.methods.firstOrNull { m ->
                (m.name.equals("toNbt", true) || m.name.equals("save", true) || m.name.equals("saveNbt", true)) && m.parameterTypes.size == 1
            }
            if (candidate != null) {
                val lookup = obtainRegistryLookup()
                if (lookup != null) {
                    val result = candidate.invoke(stack, lookup)
                    if (result is NbtCompound) {
                        result.keys.forEach { k -> into.put(k, result.get(k)) }
                        if (into.contains("id")) return true
                    }
                }
            }
        } catch (_: Exception) { }
        try {
            val zero = stack.javaClass.methods.firstOrNull { m ->
                (m.name.equals("toNbt", true) || m.name.equals("save", true) || m.name.equals("saveNbt", true)) && m.parameterTypes.isEmpty()
            }
            if (zero != null) {
                val result = zero.invoke(stack)
                if (result is NbtCompound) {
                    result.keys.forEach { k -> into.put(k, result.get(k)) }
                    if (into.contains("id")) return true
                }
            }
        } catch (_: Exception) { }
        if (encodeViaCodec(stack, into)) return true
        return false
    }

    private fun buildRegistryOps(): RegistryOps<NbtElement>? {
        val server = ServerMarket.instance.server ?: return null
        return try { RegistryOps.of(NbtOps.INSTANCE, server.registryManager) } catch (_: Exception) { null }
    }

    private fun encodeViaCodec(stack: ItemStack, into: NbtCompound): Boolean {
        val codec = itemStackCodec ?: return false
        val opsList = buildList {
            buildRegistryOps()?.let { add(it) }
            add(NbtOps.INSTANCE)
        }
        for (ops in opsList) try {
            val dr = codec.encodeStart(ops, stack)
            val opt = dr.result()
            if (opt.isPresent) {
                val el = opt.get()
                if (el is NbtCompound) {
                    el.keys.forEach { k -> into.put(k, el.get(k)) }
                    return true
                }
            }
        } catch (_: Exception) { }
        return false
    }

    private fun obtainRegistryLookup(): Any? {
        return try {
            ServerMarket.instance.server?.let { srv ->
                try {
                    val rm = try { srv.javaClass.getField("registryManager").get(srv) } catch (_: Exception) {
                        try { srv.javaClass.getMethod("getRegistryManager").invoke(srv) } catch (_: Exception) { null }
                    }
                    if (rm != null) return rm
                } catch (_: Exception) { }
            }
            null
        } catch (_: Exception) { null }
    }

    fun applySnbt(stack: ItemStack, snbt: String) {
        if (snbt.isEmpty()) return
        try {
            val el = NbtHelper.fromNbtProviderString(snbt)
            if (el is NbtCompound) {
                if (el.contains("_customData")) {
                    val sub = el.get("_customData")
                    if (sub is NbtCompound) stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(sub.copy()))
                }
                if (el.contains("_enchantments")) try {
                    val enchTag = el.get("_enchantments")
                    if (enchTag != null) {
                        val dec = decodeEnchantments(enchTag)
                        if (dec != null) stack.set(DataComponentTypes.ENCHANTMENTS, dec)
                    }
                } catch (_: Exception) { }
            }
        } catch (_: Exception) { }
    }

    fun tryBuildFullStackFromSnbt(snbt: String, desiredCount: Int): ItemStack? {
        if (snbt.isEmpty()) return null
        val el = try { NbtHelper.fromNbtProviderString(snbt) } catch (_: Exception) { return null }
        if (el !is NbtCompound || !el.contains("id")) return null
        parseViaCodec(el.copy(), desiredCount)?.let { return it }
        // 静态 fromNbt 反射兜底
        try {
            val methods = ItemStack::class.java.methods.filter { m ->
                m.name.equals("fromNbt", true) && Modifier.isStatic(m.modifiers)
            }
            val lookup = obtainRegistryLookup()
            for (m in methods) {
                try {
                    when (m.parameterCount) {
                        1 -> {
                            if (m.parameterTypes[0] == NbtCompound::class.java) {
                                val ret = m.invoke(null, el.copy())
                                val stack = unwrapFromOptional(ret)
                                if (stack != null) { stack.count = desiredCount; return stack }
                            }
                        }
                        2 -> {
                            val p0 = m.parameterTypes[0]
                            val p1 = m.parameterTypes[1]
                            val hasCompound0 = p0 == NbtCompound::class.java
                            val hasCompound1 = p1 == NbtCompound::class.java
                            if (hasCompound0 xor hasCompound1 && lookup != null) {
                                if (hasCompound0) {
                                    if (p1.isInstance(lookup)) {
                                        val ret = m.invoke(null, el.copy(), lookup)
                                        val stack = unwrapFromOptional(ret)
                                        if (stack != null) { stack.count = desiredCount; return stack }
                                    }
                                } else if (hasCompound1) {
                                    if (p0.isInstance(lookup)) {
                                        val ret = m.invoke(null, lookup, el.copy())
                                        val stack = unwrapFromOptional(ret)
                                        if (stack != null) { stack.count = desiredCount; return stack }
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) { }
            }
        } catch (_: Exception) { }
        return null
    }

    private fun unwrapFromOptional(ret: Any?): ItemStack? = when (ret) {
        is ItemStack -> ret
        is java.util.Optional<*> -> ret.orElse(null) as? ItemStack
        else -> null
    }

    private fun parseViaCodec(nbt: NbtCompound, desiredCount: Int): ItemStack? {
        val codec = itemStackCodec ?: return null
        val opsList = buildList {
            buildRegistryOps()?.let { add(it) }
            add(NbtOps.INSTANCE)
        }
        for (ops in opsList) try {
            val dr = codec.parse(ops, nbt)
            val opt = dr.result()
            if (opt.isPresent) {
                val stack = opt.get()
                stack.count = desiredCount
                return stack
            }
        } catch (_: Exception) { }
        return null
    }

    private fun encodeEnchantmentsComponent(component: ItemEnchantmentsComponent): NbtElement? {
        val codec = enchantmentsCodec ?: return null
        return try { codec.encodeStart(NbtOps.INSTANCE, component).result().orElse(null) } catch (_: Exception) { null }
    }

    private fun decodeEnchantments(tag: NbtElement): ItemEnchantmentsComponent? {
        val codec = enchantmentsCodec ?: return null
        return try { codec.parse(NbtOps.INSTANCE, tag).result().orElse(null) } catch (_: Exception) { null }
    }
}
