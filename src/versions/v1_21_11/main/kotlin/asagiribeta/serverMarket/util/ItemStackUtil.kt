package asagiribeta.serverMarket.util

import net.minecraft.item.ItemStack

/** ItemStack helpers for Minecraft 1.21.9+. */
object ItemStackUtil {
    fun decrement(stack: ItemStack, amount: Int) {
        stack.decrement(amount)
    }

    fun stacksMatch(stack: ItemStack, target: ItemStack): Boolean {
        return ItemStack.areItemsAndComponentsEqual(stack, target)
    }
}
