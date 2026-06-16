package asagiribeta.serverMarket.util

import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.util.Identifier

/**
 * Small helpers for building ItemStacks from (itemId, SNBT).
 *
 * GUI code frequently needs a safe stack for display. If the item id is invalid
 * or SNBT can't be parsed, we fall back to a configurable item.
 */
object ItemStackFactory {

    /**
     * Build a display stack for UI from an item id and SNBT.
     *
     * - Tries to reconstruct the full item stack from SNBT.
     * - Falls back to [fallbackItem] if the item id is invalid.
     */
    fun forDisplay(
        itemId: String,
        snbt: String,
        count: Int = 1,
        fallbackItem: Item = Items.STONE
    ): ItemStack {
        ItemKey.tryBuildFullStackFromSnbt(snbt, count)?.let { return it }

        val id = Identifier.tryParse(itemId)
        val itemType = if (id != null && Registries.ITEM.containsId(id)) {
            Registries.ITEM.get(id)
        } else {
            fallbackItem
        }
        return ItemStack(itemType, count)
    }
}

