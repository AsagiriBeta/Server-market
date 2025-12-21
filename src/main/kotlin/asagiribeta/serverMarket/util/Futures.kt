package asagiribeta.serverMarket.util

import net.minecraft.server.MinecraftServer
import java.util.concurrent.CompletableFuture

/**
 * Utility for switching back to the server thread after async DB work.
 */
fun <T> CompletableFuture<T>.whenCompleteOnServerThread(
    server: MinecraftServer,
    action: (T?, Throwable?) -> Unit
): CompletableFuture<T> {
    return this.whenComplete { value, error ->
        server.execute {
            action(value, error)
        }
    }
}
