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

@Suppress("unused")
object ItemKey {
    fun snbtOf(stack: ItemStack): String {
        // 优先：反射 CODEC (RegistryOps -> NbtOps) 完整序列化
        try {
            val viaCodec = NbtCompound()
            if (encodeViaCodec(stack, viaCodec)) {
                if (viaCodec.contains("Count")) viaCodec.putByte("Count", 1)
                if (viaCodec.contains("count")) viaCodec.putByte("count", 1)
                return viaCodec.toString()
            }
        } catch (_: Exception) { }
        // 次级：旧反射 writeNbt / toNbt
        return try {
            val full = NbtCompound()
            if (!tryWriteFull(stack, full)) {
                return customDataOnly(stack)
            }
            if (full.contains("Count")) full.putByte("Count", 1.toByte())
            if (full.contains("count")) full.putByte("count", 1.toByte())
            full.toString()
        } catch (_: Exception) {
            customDataOnly(stack)
        }
    }

    private fun customDataOnly(stack: ItemStack): String {
        return try {
            val base = NbtCompound()
            val id = Registries.ITEM.getId(stack.item).toString()
            base.putString("id", id)
            val custom: NbtComponent? = stack.get(DataComponentTypes.CUSTOM_DATA)
            if (custom != null) {
                val tag = try { custom.copyNbt() } catch (_: Exception) { null }
                if (tag != null && !tag.isEmpty) {
                    base.put("_customData", tag)
                }
            }
            // 新增：保存附魔组件（1.21+ DataComponent）
            try {
                val ench: ItemEnchantmentsComponent? = stack.get(DataComponentTypes.ENCHANTMENTS)
                if (ench != null && !ench.isEmpty) {
                    val enc = encodeComponent(ench)
                    if (enc != null) base.put("_enchantments", enc)
                }
            } catch (_: Exception) { }
            base.toString()
        } catch (_: Exception) { "" }
    }

    private fun tryWriteFull(stack: ItemStack, into: NbtCompound): Boolean {
        try {
            val ret = try { stack::class.java.getMethod("writeNbt", NbtCompound::class.java).invoke(stack, into) } catch (_: NoSuchMethodException) {
                try {
                    val m = stack.javaClass.declaredMethods.firstOrNull { it.parameterCount == 1 && it.parameterTypes[0] == NbtCompound::class.java && it.returnType == NbtCompound::class.java }
                    if (m != null) m.invoke(stack, into) else null
                } catch (_: Exception) { null }
            }
            if (ret is NbtCompound && ret !== into) {
                ret.keys.forEach { k -> into.put(k, ret.get(k)) }
            }
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
                for (m in methods) {
                    if (m.parameterCount == 2) {
                        val p0 = m.parameterTypes[0]
                        val p1 = m.parameterTypes[1]
                        val hasCompoundFirst = NbtCompound::class.java.isAssignableFrom(p0)
                        val hasCompoundSecond = NbtCompound::class.java.isAssignableFrom(p1)
                        if (hasCompoundFirst xor hasCompoundSecond) {
                            try {
                                if (hasCompoundFirst) {
                                    if (p1.isInstance(lookup)) {
                                        val ret = m.invoke(stack, into, lookup)
                                        if (ret is NbtCompound && ret !== into) ret.keys.forEach { k -> into.put(k, ret.get(k)) }
                                        if (into.contains("id")) return true
                                    }
                                } else if (hasCompoundSecond) {
                                    if (p0.isInstance(lookup)) {
                                        val ret = m.invoke(stack, lookup, into)
                                        if (ret is NbtCompound && ret !== into) ret.keys.forEach { k -> into.put(k, ret.get(k)) }
                                        if (into.contains("id")) return true
                                    }
                                }
                            } catch (_: Exception) { }
                        }
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

    private fun buildRegistryOps(): Any? {
        val lookup = obtainRegistryLookup() ?: return null
        return try {
            val registryOpsClass = Class.forName("net.minecraft.registry.RegistryOps")
            val ofMethod = registryOpsClass.methods.firstOrNull { m ->
                m.name == "of" && m.parameterTypes.size == 2 && m.parameterTypes[0].isAssignableFrom(NbtOps::class.java)
            } ?: return null
            ofMethod.invoke(null, NbtOps.INSTANCE, lookup)
        } catch (_: Exception) { null }
    }

    private fun encodeViaCodec(stack: ItemStack, into: NbtCompound): Boolean {
        val opsCandidates = mutableListOf<Any>()
        buildRegistryOps()?.let { opsCandidates.add(it) }
        opsCandidates.add(NbtOps.INSTANCE)
        for (ops in opsCandidates) {
            try {
                val codec = locateItemStackCodec() ?: continue
                val encodeStart = codec.javaClass.methods.firstOrNull { it.name == "encodeStart" && it.parameterTypes.size == 2 }
                    ?: continue
                val dataResult = encodeStart.invoke(codec, ops, stack)
                val resultMethod = dataResult.javaClass.methods.firstOrNull { it.name == "result" && it.parameterTypes.isEmpty() } ?: continue
                val opt = resultMethod.invoke(dataResult) as? java.util.Optional<*>
                val el = opt?.orElse(null)
                if (el is NbtCompound) {
                    el.keys.forEach { k -> into.put(k, el.get(k)) }
                    return true
                }
            } catch (_: Exception) { }
        }
        return false
    }

    private fun locateItemStackCodec(): Any? {
        val possibleNames = listOf("CODEC", "ITEM_STACK_CODEC", "STACK_CODEC", "FIELD_CODEC")
        for (name in possibleNames) {
            try {
                val f = ItemStack::class.java.getField(name)
                f.isAccessible = true
                val v = f.get(null)
                if (v != null && f.type.name.contains("Codec", true)) return v
            } catch (_: Exception) { }
        }
        try {
            val m = ItemStack::class.java.methods.firstOrNull { it.parameterCount == 0 && it.returnType.name.contains("Codec", true) }
            if (m != null) return m.invoke(null)
        } catch (_: Exception) { }
        return null
    }

    private fun obtainRegistryLookup(): Any? {
        // 修复：原逻辑只尝试获取不存在的大写字段 INSTANCE；实际 companion 里是小写 instance
        // 兼容：仍保留反射路径，失败则直接访问 ServerMarket.instance.server
        return try {
            // 优先直接访问（避免不必要反射）
            ServerMarket.instance.server?.let { srv ->
                try {
                    // 直接属性或 getter
                    val rm = try { srv.javaClass.getField("registryManager").get(srv) } catch (_: Exception) {
                        try { srv.javaClass.getMethod("getRegistryManager").invoke(srv) } catch (_: Exception) { null }
                    }
                    if (rm != null) return rm
                } catch (_: Exception) { }
            }
            // 回退反射（旧写法纠正）
            val serverMarketClass = Class.forName("asagiribeta.serverMarket.ServerMarket")
            val instanceField = try { serverMarketClass.getDeclaredField("INSTANCE") } catch (_: Exception) {
                try { serverMarketClass.getDeclaredField("instance") } catch (_: Exception) { null }
            }
            val instance = instanceField?.let {
                it.isAccessible = true
                it.get(null)
            }
            if (instance != null) {
                val serverField = try { serverMarketClass.getDeclaredField("server") } catch (_: Exception) { null }
                val server = serverField?.let { f ->
                    f.isAccessible = true
                    f.get(instance)
                }
                if (server != null) {
                    val clazz = server.javaClass
                    val rm = try { clazz.getField("registryManager").get(server) } catch (_: Exception) {
                        try { clazz.getMethod("getRegistryManager").invoke(server) } catch (_: Exception) { null }
                    }
                    rm
                } else null
            } else null
        } catch (_: Exception) { null }
    }

    fun applySnbt(stack: ItemStack, snbt: String) {
        if (snbt.isEmpty()) return
        try {
            val el = NbtHelper.fromNbtProviderString(snbt)
            if (el is NbtCompound) {
                // 如果是完整物品（含 id 和标准结构），交由 tryBuildFullStackFromSnbt 处理，此处仅处理“部分补丁”情况
                // 旧逻辑：含 id 直接 return 导致无法应用自定义/附魔补丁；现调整：尝试解析我们自定义的前缀键
                if (el.contains("_customData")) {
                    val sub = el.get("_customData")
                    if (sub is NbtCompound) {
                        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(sub.copy()))
                    }
                }
                if (el.contains("_enchantments")) {
                    try {
                        val enchTag = el.get("_enchantments") as? NbtElement
                        if (enchTag != null) {
                            val dec = decodeEnchantments(enchTag)
                            if (dec != null) stack.set(DataComponentTypes.ENCHANTMENTS, dec)
                        }
                    } catch (_: Exception) { }
                }
            }
        } catch (_: Exception) { }
    }

    fun tryBuildFullStackFromSnbt(snbt: String, desiredCount: Int): ItemStack? {
        if (snbt.isEmpty()) return null
        val el = try { NbtHelper.fromNbtProviderString(snbt) } catch (_: Exception) { return null }
        if (el !is NbtCompound || !el.contains("id")) return null
        // a) 反射 CODEC
        parseViaCodec(el.copy(), desiredCount)?.let { return it }
        // b) fromNbt(NbtCompound)
        try {
            val mOld = ItemStack::class.java.getMethod("fromNbt", NbtCompound::class.java)
            val stack = mOld.invoke(null, el.copy()) as? ItemStack
            if (stack != null) {
                stack.count = desiredCount
                return stack
            }
        } catch (_: Exception) { }
        // c) fromNbt(WrapperLookup, NbtCompound)
        try {
            val wrapperLookupClass = Class.forName("net.minecraft.registry.RegistryWrapper\$WrapperLookup")
            val mNew = ItemStack::class.java.getMethod("fromNbt", wrapperLookupClass, NbtCompound::class.java)
            val lookup = obtainRegistryLookup()
            if (lookup != null && wrapperLookupClass.isInstance(lookup)) {
                val opt = mNew.invoke(null, lookup, el.copy())
                val itemStack = when (opt) {
                    is java.util.Optional<*> -> opt.orElse(null) as? ItemStack
                    else -> null
                }
                if (itemStack != null) {
                    itemStack.count = desiredCount
                    return itemStack
                }
            }
        } catch (_: Exception) { }
        return null
    }

    private fun parseViaCodec(nbt: NbtCompound, desiredCount: Int): ItemStack? {
        val opsCandidates = mutableListOf<Any>()
        buildRegistryOps()?.let { opsCandidates.add(it) }
        opsCandidates.add(NbtOps.INSTANCE)
        for (ops in opsCandidates) {
            try {
                val codec = locateItemStackCodec() ?: continue
                val parseMethod = codec.javaClass.methods.firstOrNull { it.name == "parse" && it.parameterTypes.size == 2 }
                    ?: continue
                val dataResult = parseMethod.invoke(codec, ops, nbt)
                val resultMethod = dataResult.javaClass.methods.firstOrNull { it.name == "result" && it.parameterTypes.isEmpty() } ?: continue
                val opt = resultMethod.invoke(dataResult) as? java.util.Optional<*>
                val stack = opt?.orElse(null) as? ItemStack ?: continue
                stack.count = desiredCount
                return stack
            } catch (_: Exception) { }
        }
        return null
    }

    private fun encodeComponent(component: Any): NbtElement? {
        return try {
            val clazz = component::class.java
            val codecField = try { clazz.getField("CODEC") } catch (_: Exception) {
                try { clazz.getDeclaredField("CODEC") } catch (_: Exception) { null }
            } ?: return null
            codecField.isAccessible = true
            val codec = codecField.get(null)
            val encodeStart = codec.javaClass.methods.firstOrNull { it.name == "encodeStart" && it.parameterTypes.size == 2 } ?: return null
            val dataResult = encodeStart.invoke(codec, NbtOps.INSTANCE, component)
            val resultMethod = dataResult.javaClass.methods.firstOrNull { it.name == "result" && it.parameterTypes.isEmpty() } ?: return null
            val opt = resultMethod.invoke(dataResult) as? java.util.Optional<*>
            opt?.orElse(null) as? NbtElement
        } catch (_: Exception) { null }
    }

    private fun decodeEnchantments(tag: NbtElement): ItemEnchantmentsComponent? {
        return try {
            val clazz = ItemEnchantmentsComponent::class.java
            val codecField = try { clazz.getField("CODEC") } catch (_: Exception) {
                try { clazz.getDeclaredField("CODEC") } catch (_: Exception) { null }
            } ?: return null
            codecField.isAccessible = true
            val codec = codecField.get(null)
            val parseMethod = codec.javaClass.methods.firstOrNull { it.name == "parse" && it.parameterTypes.size == 2 } ?: return null
            val dataResult = parseMethod.invoke(codec, NbtOps.INSTANCE, tag)
            val resultMethod = dataResult.javaClass.methods.firstOrNull { it.name == "result" && it.parameterTypes.isEmpty() } ?: return null
            val opt = resultMethod.invoke(dataResult) as? java.util.Optional<*>
            opt?.orElse(null) as? ItemEnchantmentsComponent
        } catch (_: Exception) { null }
    }
}
