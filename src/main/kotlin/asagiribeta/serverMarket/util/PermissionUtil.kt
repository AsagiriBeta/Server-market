package asagiribeta.serverMarket.util

import net.minecraft.server.command.ServerCommandSource
import java.lang.reflect.Method
import java.util.function.Predicate

/**
 * Permission helper that integrates with LuckPerms via fabric-permissions-api if present.
 * Falls back to vanilla op-level checks when the API/mod is not installed.
 */
object PermissionUtil {
    @Volatile private var resolved: Boolean = false
    @Volatile private var checkMethodInt: Method? = null
    @Volatile private var checkMethodBool: Method? = null

    private fun resolve() {
        if (resolved) return
        synchronized(this) {
            if (resolved) return
            try {
                val clazz = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions")
                // Prefer (ServerCommandSource, String, int)
                checkMethodInt = try {
                    clazz.getMethod(
                        "check",
                        ServerCommandSource::class.java,
                        String::class.java,
                        Int::class.javaPrimitiveType
                    )
                } catch (_: NoSuchMethodException) { null }
                // Fallback to (ServerCommandSource, String, boolean)
                checkMethodBool = try {
                    clazz.getMethod(
                        "check",
                        ServerCommandSource::class.java,
                        String::class.java,
                        Boolean::class.javaPrimitiveType
                    )
                } catch (_: NoSuchMethodException) { null }
            } catch (_: Throwable) {
                // API not present
                checkMethodInt = null
                checkMethodBool = null
            } finally {
                resolved = true
            }
        }
    }

    /**
     * Check a permission node for the given source.
     * @param defaultOpLevel vanilla fallback required op level (0 = everyone)
     */
    @JvmStatic
    fun check(source: ServerCommandSource, node: String, defaultOpLevel: Int = 0): Boolean {
        resolve()
        try {
            checkMethodInt?.let { m ->
                val res = m.invoke(null, source, node, defaultOpLevel) as Boolean
                return res
            }
            checkMethodBool?.let { m ->
                val def = source.hasPermissionLevel(defaultOpLevel)
                val res = m.invoke(null, source, node, def) as Boolean
                return res
            }
        } catch (_: Throwable) {
            // fall through to vanilla
        }
        // Fallback: vanilla op level check
        return source.hasPermissionLevel(defaultOpLevel)
    }

    /**
     * Brigadier requires predicate for a permission node.
     */
    @JvmStatic
    fun require(node: String, defaultOpLevel: Int = 0): Predicate<ServerCommandSource> =
        Predicate { src -> check(src, node, defaultOpLevel) }

    /**
     * Brigadier requires predicate for a player-only command with a permission node.
     */
    @JvmStatic
    fun requirePlayer(node: String, defaultOpLevel: Int = 0): Predicate<ServerCommandSource> =
        Predicate { src -> src.player != null && check(src, node, defaultOpLevel) }
}

