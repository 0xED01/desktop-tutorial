package com.yuandian.quote.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class MoneyTest {

    @Test fun parseYuan() {
        assertEquals(12850L, Money.parseYuanToCents("128.5"))
        assertEquals(100L, Money.parseYuanToCents("1"))
        assertEquals(1L, Money.parseYuanToCents("0.005"))
        assertNull(Money.parseYuanToCents(""))
        assertNull(Money.parseYuanToCents("abc"))
        assertNull(Money.parseYuanToCents("-1"))
    }

    @Test fun lineAmountRoundsToCent() {
        assertEquals(142800L, Money.lineAmount(BigDecimal("25.5"), 5600L))
        assertEquals(333L, Money.lineAmount(BigDecimal("0.333"), 1000L))
        assertEquals(1L, Money.lineAmount(BigDecimal("0.005"), 100L)) // 0.5 分 -> 四舍五入为 1 分
    }

    @Test fun format() {
        assertEquals("1,234,567.89", Money.format(123456789L))
        assertEquals("0.05", Money.format(5L))
        assertEquals("¥100.00", Money.formatWithSymbol(10000L))
        assertEquals("128.5", Money.centsToInput(12850L))
        assertEquals("2.5", Money.formatQuantity("2.500"))
    }

    @Test fun inputFilter() {
        assertEquals("12.34", Money.filterDecimalInput("12.345", 2))
        assertEquals("0.5", Money.filterDecimalInput(".5", 2))
        assertEquals("125", Money.filterDecimalInput("1a2b5", 2))
        assertEquals("1.23", Money.filterDecimalInput("1.2.3", 2)) // 多余的小数点被忽略
        assertEquals("3", Money.filterDecimalInput("3.", 0))
    }

    @Test fun chineseUppercase() {
        assertEquals("零元整", Money.toChineseUppercase(0))
        assertEquals("壹佰贰拾叁万肆仟伍佰陆拾柒元捌角玖分", Money.toChineseUppercase(123456789L))
        assertEquals("壹拾万元整", Money.toChineseUppercase(10000000L))
        assertEquals("壹拾元零伍分", Money.toChineseUppercase(1005L))
        assertEquals("壹亿零壹佰元整", Money.toChineseUppercase(10000010000L))
        assertEquals("壹亿零壹万元整", Money.toChineseUppercase(10001000000L))
        assertEquals("壹仟零伍元伍角整", Money.toChineseUppercase(100550L))
        assertEquals("伍角", Money.toChineseUppercase(50L).removeSuffix("整"))
        assertEquals("叁分", Money.toChineseUppercase(3L))
        assertEquals("壹万零贰拾元整", Money.toChineseUppercase(1002000L))
        assertEquals("贰万叁仟元整", Money.toChineseUppercase(2300000L))
    }
}
