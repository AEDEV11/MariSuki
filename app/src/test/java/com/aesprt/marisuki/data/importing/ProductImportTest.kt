package com.aesprt.marisuki.data.importing

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

class ProductImportTest {
    private fun values() = mutableListOf("Rice", "0012345678901", "Groceries", "Kilo", "55.50", "42", "25.125", "5", "Yes", "Sack", "25", "1300", "", "", "")
    private fun csv(rows: List<List<String>>, headers: List<String> = ProductImport.headers): ByteArray =
        (listOf(headers) + rows).joinToString("\r\n") { row -> row.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" } }.toByteArray()
    @Test fun parsesQuotedCsvExactlyAndPreservesBarcodeAndUnits() {
        val row = values().apply { this[0] = "Rice, \"Premium\"\nLocal" }
        val preview = ProductImport.parse(csv(listOf(row)), false)
        assertTrue(preview.canImport)
        val p = preview.rows.single().input!!
        assertEquals(row[0], p.name); assertEquals("0012345678901", p.barcode)
        assertEquals(5550L, p.baseUnitPriceCentavos); assertEquals(25125L, p.initialStockMilliUnits)
        assertEquals(25000L, p.extraUnits.single().baseQuantityMilliUnits); assertEquals(130000L, p.extraUnits.single().priceCentavos)
    }
    @Test fun rejectsDuplicateBarcodesInEveryAffectedRow() {
        val preview = ProductImport.parse(csv(listOf(values(), values())), false)
        assertFalse(preview.canImport); assertEquals(2, preview.issues.count { it.problem == ImportProblem.DUPLICATE_BARCODE })
    }
    @Test fun reportsInvalidNumbersRequiredFieldsAndFractionalCounts() {
        val row = values().apply { this[0] = ""; this[4] = "10.001"; this[8] = "No" }
        val issues = ProductImport.parse(csv(listOf(row)), false).issues.map { it.problem }
        assertTrue(ImportProblem.REQUIRED in issues); assertTrue(ImportProblem.NUMBER in issues); assertTrue(ImportProblem.FRACTION in issues)
    }
    @Test fun rejectsPartialUnitsDuplicateLabelsAndInvalidBoolean() {
        val row = values().apply { this[9] = "Kilo"; this[10] = ""; this[8] = "maybe" }
        val preview = ProductImport.parse(csv(listOf(row)), false)
        assertFalse(preview.canImport); assertTrue(preview.issues.any { it.problem == ImportProblem.UNIT }); assertTrue(preview.issues.any { it.problem == ImportProblem.BOOLEAN })
    }
    @Test fun acceptsBomTrailingEmptyCellsAndReorderedHeaders() {
        val source = csv(listOf(values().reversed()), ProductImport.headers.reversed())
        assertTrue(ProductImport.parse(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + source, false).canImport)
        val minimal = values().apply { for (i in 5..14) this[i] = "" }
        val p = ProductImport.parse(csv(listOf(minimal)), false).rows.single().input!!
        assertEquals(0L, p.initialStockMilliUnits); assertTrue(p.wholeBaseUnitsOnly); assertTrue(p.extraUnits.isEmpty())
    }
    @Test fun rejectsMalformedCsvHeadersAndOversizedFiles() {
        assertProblem(ImportProblem.FORMAT) { ProductImport.parse("\"unterminated".toByteArray(), false) }
        assertProblem(ImportProblem.HEADERS) { ProductImport.parse("name,price\na,10".toByteArray(), false) }
        assertProblem(ImportProblem.LIMIT) { ProductImport.parse(ByteArray(ProductImport.MAX_BYTES + 1), false) }
        assertProblem(ImportProblem.EMPTY) { ProductImport.parse(ProductImport.csvTemplate().toByteArray(), false) }
    }
    @Test fun generatedExcelTemplateContainsNoImportableExamples() {
        assertProblem(ImportProblem.EMPTY) { ProductImport.parse(File("src/main/assets/templates/products.xlsx").readBytes(), true) }
    }
    @Test fun readsSharedStringsInlineStringsAndSparseExcelCells() {
        val data = xlsx("""<row r="4"><c r="A4" t="s"><v>0</v></c><c r="B4" t="inlineStr"><is><t>00123</t></is></c><c r="D4" t="inlineStr"><is><t>Piece</t></is></c><c r="E4"><v>10.50</v></c></row>""")
        val result = ProductImport.parse(data, true)
        assertTrue(result.canImport); assertEquals(4, result.rows.single().row)
        assertEquals("Snack", result.rows.single().input!!.name); assertEquals("00123", result.rows.single().input!!.barcode)
        assertEquals(1050L, result.rows.single().input!!.baseUnitPriceCentavos)
    }
    @Test fun rejectsExcelFormulasAndDoctypes() {
        val data = xlsx("""<row r="2"><c r="A2" t="s"><v>0</v></c><c r="D2" t="inlineStr"><is><t>Piece</t></is></c><c r="E2"><f>5+5</f><v>10</v></c></row>""")
        assertFalse(ProductImport.parse(data, true).canImport)
        assertTrue(ProductImport.parse(data, true).issues.any { it.problem == ImportProblem.FORMULA })
        assertProblem(ImportProblem.FORMAT) { ProductImport.parse(xlsx("", true), true) }
    }
    private fun assertProblem(expected: ImportProblem, action: () -> Unit) {
        try { action(); fail("Expected $expected") } catch (e: ImportFileException) { assertEquals(expected, e.problem) }
    }
    private fun xlsx(row: String, dtd: Boolean = false): ByteArray {
        val ns = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
        val headers = ProductImport.headers.mapIndexed { i, label -> "<c r=\"${('A'.code + i).toChar()}1\" t=\"inlineStr\"><is><t>$label</t></is></c>" }.joinToString("")
        val parts = mapOf(
            "xl/workbook.xml" to """${if (dtd) "<!DOCTYPE workbook [<!ENTITY x 'bad'>]>" else ""}<workbook xmlns="$ns" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Products" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Target="worksheets/sheet1.xml"/></Relationships>""",
            "xl/sharedStrings.xml" to """<sst xmlns="$ns"><si><t>Snack</t></si></sst>""",
            "xl/worksheets/sheet1.xml" to """<worksheet xmlns="$ns"><sheetData><row r="1">$headers</row>$row</sheetData></worksheet>""",
        )
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip -> parts.forEach { (name, xml) -> zip.putNextEntry(ZipEntry(name)); zip.write(xml.toByteArray()); zip.closeEntry() } }
        return output.toByteArray()
    }
}
