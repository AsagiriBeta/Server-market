package asagiribeta.serverMarket.api

/**
 * Static access point for other mods.
 *
 * Kotlin:
 *   val api = ServerMarketApiProvider.get() ?: return
 * Java:
 *   ServerMarketApi api = ServerMarketApiProvider.get();
 */
object ServerMarketApiProvider {
    @Volatile
    private var api: ServerMarketApi? = null

    @JvmStatic
    fun get(): ServerMarketApi? = api

    @JvmStatic
    fun require(): ServerMarketApi = api
        ?: error("ServerMarket API not available (mod not initialized)")

    internal fun set(instance: ServerMarketApi) {
        api = instance
    }
}
