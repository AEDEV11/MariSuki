package com.aesprt.marisuki.feature.printing

import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductBarcodeTest {
    @Test
    fun internalProductBarcodeEncodesAsCode128() {
        val matrix = MultiFormatWriter().encode("MSKABCDEF1234", BarcodeFormat.CODE_128, 1, 1)

        assertTrue(matrix.width > 100)
        assertTrue((0 until matrix.width).any { matrix.get(it, 0) })
    }
}
