package asagiribeta.serverMarket.util

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import kotlin.math.roundToLong

/**
 * Converts between ServerMarket's decimal balances and Common Economy API v2 minor units.
 *
 * ServerMarket stores balances as [Double] with 2 decimal places; Common Economy API 2.x
 * uses [BigInteger] raw values. We use a fixed scale of 100 (cents).
 */
object MoneyUnits {
    private const val SCALE = 2
    private val MULTIPLIER = BigInteger.valueOf(100)

    fun toMinorUnits(amount: Double): BigInteger {
        if (!amount.isFinite()) return BigInteger.ZERO
        return BigDecimal.valueOf(amount)
            .movePointRight(SCALE)
            .setScale(0, RoundingMode.HALF_UP)
            .toBigInteger()
    }

    fun fromMinorUnits(units: BigInteger): Double {
        return BigDecimal(units)
            .movePointLeft(SCALE)
            .setScale(SCALE, RoundingMode.HALF_UP)
            .toDouble()
    }

    fun toMinorUnits(amount: Long): BigInteger = BigInteger.valueOf(amount)

    fun fromMinorUnits(units: Long): Double = fromMinorUnits(BigInteger.valueOf(units))

    fun roundToMoney(amount: Double): Double {
        if (!amount.isFinite()) return 0.0
        return (amount * 100.0).roundToLong() / 100.0
    }
}
