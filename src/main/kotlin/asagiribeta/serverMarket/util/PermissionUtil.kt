package asagiribeta.serverMarket.util

import asagiribeta.serverMarket.ServerMarket
import net.fabricmc.loader.api.FabricLoader
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
    @Volatile private var loggedNoApi: Boolean = false
    @Volatile private var luckPermsLoaded: Boolean = false

    private fun resolve() {
        if (resolved) return
        synchronized(this) {
            if (resolved) return
            try {
                val clazz = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions")

                // Scan methods to support minor signature/boxing changes across versions
                for (m in clazz.methods) {
                    if (m.name != "check") continue
                    val params = m.parameterTypes
                    if (params.size != 3) continue
                    val p0 = params[0]
                    val p1 = params[1]
                    val p2 = params[2]
                    val firstAcceptsSrc = p0.isAssignableFrom(ServerCommandSource::class.java)
                    if (!firstAcceptsSrc) continue
                    if (p1 != String::class.java) continue

                    when (p2) {
                        java.lang.Boolean.TYPE, java.lang.Boolean::class.java -> {
                            checkMethodBool = m
                        }
                        Integer.TYPE -> {
                            checkMethodInt = m
                        }
                    }
                }

                // Detect LuckPerms presence (to switch default for level 0)
                try {
                    luckPermsLoaded = FabricLoader.getInstance().isModLoaded("luckperms")
                    if (luckPermsLoaded) {
                        ServerMarket.LOGGER.info("PermissionUtil: LuckPerms detected; level 0 permissions default to DENY unless explicitly granted")
                    } else {
                        ServerMarket.LOGGER.info("PermissionUtil: LuckPerms not detected; level 0 permissions default to ALLOW when no provider is present")
                    }
                } catch (_: Throwable) {
                    luckPermsLoaded = false
                }

                if (checkMethodBool != null) {
                    ServerMarket.LOGGER.info("PermissionUtil: using fabric-permissions-api boolean overload (provider-aware defaults)")
                } else if (checkMethodInt != null) {
                    ServerMarket.LOGGER.warn("PermissionUtil: boolean overload not found, falling back to int overload (level 0 will allow everyone unless provider explicitly denies)")
                } else {
                    ServerMarket.LOGGER.warn("PermissionUtil: no compatible fabric-permissions-api 'check' overload found; falling back to vanilla op-level checks")
                }
            } catch (_: Throwable) {
                // API not present
                checkMethodInt = null
                checkMethodBool = null
                luckPermsLoaded = false
                if (!loggedNoApi) {
                    loggedNoApi = true
                    try {
                        ServerMarket.LOGGER.warn("fabric-permissions-api not found. Falling back to vanilla op-level checks; commands with default level 0 are allowed for everyone. Install fabric-permissions-api to integrate with LuckPerms.")
                    } catch (_: Throwable) { /* ignore logging issues */ }
                }
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
            // Prefer boolean default overload so we can control defaults based on LuckPerms presence
            checkMethodBool?.let { m ->
                // - defaultOpLevel > 0: keep vanilla op-level semantics
                // - defaultOpLevel <= 0: if LP present -> default DENY; else -> default ALLOW
                val def = if (defaultOpLevel > 0) {
                    source.hasPermissionLevel(defaultOpLevel)
                } else {
                    !luckPermsLoaded
                }
                val res = m.invoke(null, source, node, java.lang.Boolean.valueOf(def)) as Boolean
                return res
            }
            // Fallback: use int overload if boolean one is not available
            checkMethodInt?.let { m ->
                val res = m.invoke(null, source, node, defaultOpLevel) as Boolean
                return res
            }
        } catch (t: Throwable) {
            try {
                ServerMarket.LOGGER.debug("PermissionUtil: permission check via API failed, falling back to vanilla: {}", t.toString())
            } catch (_: Throwable) {}
            // fall through to vanilla
        }
        // Fallback: vanilla op level check (no permissions provider installed)
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
