package asagiribeta.serverMarket.util

import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries

object TextFormat {
    /**
     * Prefer a clean, localized in-game item name.
     * Falls back to registry id (without namespace) when stack is empty.
     */
    fun displayItemName(stack: ItemStack, fallbackItemId: String? = null): String {
        if (!stack.isEmpty) {
            // This is already localized by the client language.
            val name = stack.name.string
            if (name.isNotBlank()) return name

            val id = Registries.ITEM.getId(stack.item)
            return id.path
        }

        if (fallbackItemId != null) {
            val idx = fallbackItemId.indexOf(':')
            return if (idx >= 0 && idx + 1 < fallbackItemId.length) fallbackItemId.substring(idx + 1) else fallbackItemId
        }

        return ""
    }
}

