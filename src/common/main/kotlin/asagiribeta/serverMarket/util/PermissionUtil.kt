package asagiribeta.serverMarket.util

import me.lucko.fabric.api.permissions.v0.Permissions
import net.minecraft.server.command.ServerCommandSource
import java.util.function.Predicate

/**
 * Permission helper that integrates with LuckPerms via fabric-permissions-api.
 * Falls back to vanilla op-level checks when no permission provider is installed.
 *
 * fabric-permissions-api is included in this mod, so it is always available.
 * When LuckPerms is installed, permissions are checked through it.
 * When LuckPerms is not installed, falls back to vanilla op-level checks.
 */
object PermissionUtil {
    /**
     * Check a permission node for the given source.
     * @param node the permission node (e.g., "servermarket.command.mmenu")
     * @param defaultOpLevel vanilla fallback required op level (0 = everyone, 4 = ops)
     * @return true if the source has permission
     */
    @JvmStatic
    @Suppress("unused") // Public API for runtime permission checks
    fun check(source: ServerCommandSource, node: String, defaultOpLevel: Int = 0): Boolean {
        return Permissions.check(source, node, defaultOpLevel)
    }

    /**
     * Brigadier requires predicate for a permission node.
     * @param node the permission node
     * @param defaultOpLevel vanilla fallback required op level
     * @return predicate for Brigadier command requirements
     */
    @JvmStatic
    fun require(node: String, defaultOpLevel: Int = 0): Predicate<ServerCommandSource> {
        return Permissions.require(node, defaultOpLevel)
    }

    /**
     * Brigadier requires predicate for a player-only command with a permission node.
     * @param node the permission node
     * @param defaultOpLevel vanilla fallback required op level
     * @return predicate that also checks if source is a player
     */
    @JvmStatic
    fun requirePlayer(node: String, defaultOpLevel: Int = 0): Predicate<ServerCommandSource> {
        return Predicate { src -> src.player != null && Permissions.check(src, node, defaultOpLevel) }
    }
}
