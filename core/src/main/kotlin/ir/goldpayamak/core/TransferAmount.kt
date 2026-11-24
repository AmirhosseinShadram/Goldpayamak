package ir.goldpayamak.core

sealed interface TransferAmount {
    fun format(): String

    data class Toman(val value: Long) : TransferAmount {
        override fun format() = Price.formatToman(value)
    }

    @ConsistentCopyVisibility
    data class GoldGrams private constructor(val value: String) : TransferAmount {
        override fun format() = Digits.toPersian(value.replace('.', '٫')) + " گرم طلا"

        companion object {
            private val decimal = Regex("""(?:[0-9]+(?:\.[0-9]*)?|\.[0-9]+)""")

            fun parse(input: String): GoldGrams? {
                val normalized = Digits.toLatin(input.trim()).replace('٫', '.')
                if (!decimal.matches(normalized)) return null
                val grams = normalized.toBigDecimalOrNull()?.stripTrailingZeros() ?: return null
                return GoldGrams(grams.toPlainString()).takeIf { grams.signum() > 0 }
            }
        }
    }
}
