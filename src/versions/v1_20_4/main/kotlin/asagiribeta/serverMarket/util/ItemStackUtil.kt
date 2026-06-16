package asagiribeta.serverMarket.util

import net.minecraft.item.ItemStack

/** ItemStack helpers for Minecraft 1.20 – 1.20.4 (legacy NBT API). */
object ItemStackUtil {
    fun decrement(stack: ItemStack, amount: Int) {
        stack.count -= amount
    }

    fun stacksMatch(stack: ItemStack, target: ItemStack): Boolean {
        return ItemStack.areEqual(stack, target)
    }
}
