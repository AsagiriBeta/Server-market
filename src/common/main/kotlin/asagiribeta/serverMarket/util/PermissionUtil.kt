package asagiribeta.serverMarket.util

import me.lucko.fabric.api.permissions.v0.Permissions
import net.minecraft.server.command.ServerCommandSource
import java.util.function.Predicate

/**
 * Permission helper that integrates with LuckPerms via fabric-permissions-api.
 * Falls back to vanilla op-level checks when no permission provider is installed.
 */
object PermissionUtil {
  @JvmStatic
  @Suppress("unused", "DEPRECATION")
  fun check(source: ServerCommandSource, node: String, defaultOpLevel: Int = 0): Boolean {
    return Permissions.check(source, node, defaultOpLevel)
  }

  @JvmStatic
  @Suppress("DEPRECATION")
  fun require(node: String, defaultOpLevel: Int = 0): Predicate<ServerCommandSource> {
    return Permissions.require(node, defaultOpLevel)
  }

  @JvmStatic
  fun requirePlayer(node: String, defaultOpLevel: Int = 0): Predicate<ServerCommandSource> {
    return Predicate { src -> src.player != null && check(src, node, defaultOpLevel) }
  }
}
