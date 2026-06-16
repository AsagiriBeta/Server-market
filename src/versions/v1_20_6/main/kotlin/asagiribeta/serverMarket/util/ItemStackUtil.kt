package asagiribeta.serverMarket.util

import net.minecraft.item.ItemStack

/** ItemStack helpers for Minecraft 1.20.5 – 1.21.8. */
object ItemStackUtil {
    fun decrement(stack: ItemStack, amount: Int) {
        try {
            stack.decrement(amount)
        } catch (_: Throwable) {
            stack.count -= amount
        }
    }

    fun stacksMatch(stack: ItemStack, target: ItemStack): Boolean {
        return try {
            ItemStack.areItemsAndComponentsEqual(stack, target)
        } catch (_: Throwable) {
            ItemStack.areEqual(stack, target)
        }
    }
}
