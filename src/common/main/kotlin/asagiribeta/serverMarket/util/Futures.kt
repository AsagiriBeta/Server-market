package asagiribeta.serverMarket.util

import asagiribeta.serverMarket.ServerMarket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import java.util.concurrent.CompletableFuture

/**
 * Utility for switching back to the server thread after async DB work.
 */
fun <T> CompletableFuture<T>.whenCompleteOnServerThread(
    server: MinecraftServer?,
    action: (T?, Throwable?) -> Unit
): CompletableFuture<T> {
    val target = server ?: ServerMarket.instance.server
    return this.whenComplete { value, error ->
        if (target != null) {
            target.execute { action(value, error) }
        } else {
            action(value, error)
        }
    }
}

fun ServerPlayerEntity.marketServer(): MinecraftServer {
    try {
        javaClass.getMethod("getServer").invoke(this)?.let { return it as MinecraftServer }
    } catch (_: Exception) { }

    try {
        val world = javaClass.getMethod("getEntityWorld").invoke(this)
            ?: javaClass.getMethod("getWorld").invoke(this)
        world?.javaClass?.getMethod("getServer")?.invoke(world)?.let { return it as MinecraftServer }
    } catch (_: Exception) { }

    return ServerMarket.instance.server ?: error("Server unavailable")
}
