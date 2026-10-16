package com.aesprt.marisuki.feature.pos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PosMoneyTest {
    @Test
    fun parsesPesosWithoutFloatingPointRounding() {
        assertEquals(12_345L, PosViewModel.parseCentavos("123.45"))
        assertEquals(1_000L, PosViewModel.parseCentavos("₱ 10.00"))
        assertNull(PosViewModel.parseCentavos("2.999"))
        assertNull(PosViewModel.parseCentavos("-1.00"))
    }

    @Test
    fun parsesFractionalQuantityToThousandths() {
        assertEquals(1_250L, PosViewModel.parseQuantityMilliUnits("1.25"))
        assertNull(PosViewModel.parseQuantityMilliUnits("0.0001"))
        assertNull(PosViewModel.parseQuantityMilliUnits("-2"))
    }

    @Test
    fun roundsLineAmountToCentavoAndFormatsQuantity() {
        assertEquals(1_234L, PosViewModel.calculateAmount(1_000L, 1_234L))
        assertEquals(1L, PosViewModel.calculateAmount(1L, 500L))
        assertEquals("1.25", PosViewModel.formatQuantity(1_250L))
    }
}
