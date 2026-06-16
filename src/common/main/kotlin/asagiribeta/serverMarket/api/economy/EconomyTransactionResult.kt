package asagiribeta.serverMarket.api.economy

/**
 * Result for economy write operations.
 */
data class EconomyTransactionResult(
    val success: Boolean,
    val error: String? = null,
    val newBalance: Double? = null
)
