package asagiribeta.serverMarket.api.economy

/**
 * Registry for the active [EconomyProvider], similar to Vault's ServicesManager.
 */
object EconomyProviderRegistry {
    @Volatile
    private var provider: EconomyProvider? = null

    /** Returns the registered economy provider, or null if none is available. */
    fun get(): EconomyProvider? = provider

    /** Returns the registered provider or throws if ServerMarket has not initialized yet. */
    fun require(): EconomyProvider = provider
        ?: error("No EconomyProvider registered — is ServerMarket loaded?")

    internal fun register(instance: EconomyProvider) {
        provider = instance
    }
}
