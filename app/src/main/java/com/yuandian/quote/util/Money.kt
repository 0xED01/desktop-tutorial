package com.yuandian.quote.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * 金额工具。所有金额在程序内部统一以「分」(Long) 存储与计算，避免浮点误差。
 */
object Money {

    private val symbols = DecimalFormatSymbols(Locale.CHINA)

    /** 把用户输入的“元”文本转换为分；非法输入返回 null。 */
    fun parseYuanToCents(text: String): Long? {
        val t = text.trim()
        if (t.isEmpty()) return null
        return try {
            val v = BigDecimal(t)
            if (v.signum() < 0) return null
            v.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
        } catch (e: Exception) {
            null
        }
    }

    /** 解析数量，必须大于 0。 */
    fun parseQuantity(text: String): BigDecimal? {
        val t = text.trim()
        if (t.isEmpty()) return null
        return try {
            BigDecimal(t).takeIf { it.signum() > 0 }
        } catch (e: Exception) {
            null
        }
    }

    /** 行金额 = 数量 × 单价（分），四舍五入到分。 */
    fun lineAmount(quantity: BigDecimal, unitPriceCents: Long): Long =
        quantity.multiply(BigDecimal.valueOf(unitPriceCents))
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()

    /** 1234567 分 -> "12,345.67" */
    fun format(cents: Long): String {
        val df = DecimalFormat("#,##0.00", symbols)
        return df.format(BigDecimal.valueOf(cents, 2))
    }

    /** 1234567 分 -> "¥12,345.67" */
    fun formatWithSymbol(cents: Long): String = "¥" + format(cents)

    /** 分 -> 编辑框里显示的元文本，如 "128.5" */
    fun centsToInput(cents: Long): String =
        BigDecimal.valueOf(cents, 2).stripTrailingZeros().toPlainString()

    /** 数量标准化显示："2.500" -> "2.5" */
    fun formatQuantity(q: BigDecimal): String = q.stripTrailingZeros().toPlainString()

    fun formatQuantity(text: String): String =
        parseQuantity(text)?.let { formatQuantity(it) } ?: text

    /**
     * 输入过滤：只允许数字和一个小数点，并限制小数位数。
     */
    fun filterDecimalInput(input: String, maxDecimals: Int, maxIntDigits: Int = 9): String {
        val sb = StringBuilder()
        var dotSeen = false
        var decimals = 0
        var intDigits = 0
        for (c in input) {
            when {
                c == '.' || c == '。' -> if (!dotSeen && maxDecimals > 0) {
                    if (sb.isEmpty()) sb.append('0')
                    sb.append('.'); dotSeen = true
                }
                c.isDigit() -> {
                    if (dotSeen) {
                        if (decimals < maxDecimals) { sb.append(c); decimals++ }
                    } else if (intDigits < maxIntDigits) {
                        sb.append(c); intDigits++
                    }
                }
            }
        }
        return sb.toString()
    }

    private val DIGITS = charArrayOf('零', '壹', '贰', '叁', '肆', '伍', '陆', '柒', '捌', '玖')
    private val POS_UNITS = arrayOf("", "拾", "佰", "仟")
    private val GROUP_UNITS = arrayOf("", "万", "亿", "万亿")

    /**
     * 人民币金额大写，例如 1234567.89 元 -> 壹佰贰拾叁万肆仟伍佰陆拾柒元捌角玖分
     */
    fun toChineseUppercase(cents: Long): String {
        if (cents == 0L) return "零元整"
        if (cents < 0) return "负" + toChineseUppercase(-cents)
        val yuan = cents / 100
        val jiao = ((cents / 10) % 10).toInt()
        val fen = (cents % 10).toInt()
        val sb = StringBuilder()
        if (yuan > 0) sb.append(integerToChinese(yuan)).append('元')
        if (jiao == 0 && fen == 0) {
            sb.append('整')
        } else {
            if (jiao > 0) {
                sb.append(DIGITS[jiao]).append('角')
            } else if (yuan > 0) {
                sb.append('零')
            }
            if (fen > 0) sb.append(DIGITS[fen]).append('分') else sb.append('整')
        }
        return sb.toString()
    }

    private fun integerToChinese(n: Long): String {
        val groups = ArrayList<Int>()
        var v = n
        while (v > 0) { groups.add((v % 10000).toInt()); v /= 10000 }
        val sb = StringBuilder()
        var pendingZero = false
        for (gi in groups.indices.reversed()) {
            val g = groups[gi]
            if (g == 0) {
                if (sb.isNotEmpty()) pendingZero = true
                continue
            }
            if (sb.isNotEmpty() && (pendingZero || g < 1000)) sb.append('零')
            sb.append(groupToChinese(g)).append(GROUP_UNITS[gi.coerceAtMost(GROUP_UNITS.size - 1)])
            pendingZero = false
        }
        return sb.toString()
    }

    private fun groupToChinese(g: Int): String {
        val sb = StringBuilder()
        var zero = false
        for (pos in 3 downTo 0) {
            val d = (g / pow10(pos)) % 10
            if (d == 0) {
                if (sb.isNotEmpty()) zero = true
            } else {
                if (zero) { sb.append('零'); zero = false }
                sb.append(DIGITS[d]).append(POS_UNITS[pos])
            }
        }
        return sb.toString()
    }

    private fun pow10(p: Int): Int = when (p) { 0 -> 1; 1 -> 10; 2 -> 100; else -> 1000 }
}
