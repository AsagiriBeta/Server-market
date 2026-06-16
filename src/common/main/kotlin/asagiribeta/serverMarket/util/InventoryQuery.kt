package asagiribeta.serverMarket.util

import net.minecraft.registry.Registries
import net.minecraft.server.network.ServerPlayerEntity

/**
 * Inventory querying helpers shared by commands and GUI.
 */
object InventoryQuery {

    /**
     * Gets all stacks in the player's inventory that match the given item id and normalized SNBT.
     *
     * Note: [normalizedSnbt] must already be normalized via [ItemKey.normalizeSnbt].
     */
    fun findMatchingStacks(
        player: ServerPlayerEntity,
        itemId: String,
        normalizedSnbt: String
    ) = (0 until player.inventory.size()).map { player.inventory.getStack(it) }.filter {
        !it.isEmpty &&
            Registries.ITEM.getId(it.item).toString() == itemId &&
            ItemKey.normalizeSnbt(ItemKey.snbtOf(it)) == normalizedSnbt
    }

    /**
     * Counts total amount across all stacks.
     */
    fun countTotal(stacks: List<net.minecraft.item.ItemStack>): Int = stacks.sumOf { it.count }
}

