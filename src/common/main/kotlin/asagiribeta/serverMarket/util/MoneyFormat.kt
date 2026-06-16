package asagiribeta.serverMarket.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

/**
 * Centralized number formatting for money-like values.
 *
 * Goals:
 * - Provide thousand separators (123,000.00)
 * - Provide compact "short" format (1.4k, 2.5m, 3.27b) with rounding
 *
 * Notes:
 * - Formatting is locale-neutral (uses Locale.ROOT). Player language is handled by translation keys.
 * - This is intentionally conservative and won't throw for NaN/Infinity.
 */
object MoneyFormat {

    /**
     * Use a stable locale to avoid coupling to any deprecated server-side language selector.
     * We intentionally keep `,` as thousand separator and `.` as decimal separator.
     */
    private fun symbols(): DecimalFormatSymbols = DecimalFormatSymbols.getInstance(Locale.ROOT)

    /** Format with thousand separators and fixed decimals. */
    fun format(value: Double, decimals: Int = 2): String {
        if (!value.isFinite()) return value.toString()
        val symbols = symbols()
        val pattern = buildString {
            append("#,##0")
            if (decimals > 0) {
                append('.')
                repeat(decimals) { append('0') }
            }
        }
        val df = DecimalFormat(pattern, symbols)
        df.isGroupingUsed = true
        df.maximumFractionDigits = decimals
        df.minimumFractionDigits = decimals
        return df.format(value)
    }

    /**
     * Compact format like 1.4k / 2.5m / 3.27b.
     *
     * Rules:
     * - k (thousand), m (million), b (billion)
     * - round to 3 decimals
     * - show up to 2 decimals; only show the second when necessary
     */
    fun formatShort(value: Double): String {
        if (!value.isFinite()) return value.toString()
        val absV = abs(value)
        val (div, suffix) = when {
            absV >= 1_000_000_000.0 -> 1_000_000_000.0 to "b"
            absV >= 1_000_000.0 -> 1_000_000.0 to "m"
            absV >= 1_000.0 -> 1_000.0 to "k"
            else -> 1.0 to ""
        }

        val v = value / div
        // round to 3 decimals
        val rounded = kotlin.math.round(v * 1000.0) / 1000.0

        // decide displayed decimals: up to 2, only when necessary
        val as2 = kotlin.math.round(rounded * 100.0) / 100.0
        val out = when {
            (as2 % 1.0) == 0.0 -> as2.toLong().toString()
            (as2 * 10 % 1.0) == 0.0 -> format(as2, 1)
            else -> format(as2, 2)
        }

        return out + suffix
    }
}
