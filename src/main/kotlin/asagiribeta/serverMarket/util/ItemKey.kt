package asagiribeta.serverMarket.util

import net.minecraft.item.ItemStack
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.NbtComponent
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtHelper

@Suppress("unused")
object ItemKey {
    // 提取可用于标识物品“变体”的SNBT（目前仅包含 CUSTOM_DATA，自定义数据包NBT）
    fun snbtOf(stack: ItemStack): String {
        val custom: NbtComponent? = stack.get(DataComponentTypes.CUSTOM_DATA)
        return if (custom != null) {
            val tag = try { custom.copyNbt() } catch (_: Exception) { null }
            tag?.toString() ?: ""
        } else ""
    }

    // 将 SNBT 写回到物品的 CUSTOM_DATA 组件（容错：解析失败则忽略）
    fun applySnbt(stack: ItemStack, snbt: String) {
        if (snbt.isEmpty()) return
        try {
            val el = NbtHelper.fromNbtProviderString(snbt)
            if (el is NbtCompound) {
                stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(el))
            }
        } catch (_: Exception) {
            // 忽略解析失败
        }
    }
}
