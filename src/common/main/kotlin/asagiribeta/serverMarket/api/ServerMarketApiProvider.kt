package asagiribeta.serverMarket.api

/**
 * Static access point for market-specific inter-mod features.
 *
 * Balance integration: use Common Economy API (`server-market` provider).
 *
 * Kotlin:
 *   val api = ServerMarketApiProvider.get() ?: return
 *   api.getHistory(uuid)
 *   api.openMenu(player)
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
