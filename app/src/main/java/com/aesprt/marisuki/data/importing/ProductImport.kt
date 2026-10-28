package com.aesprt.marisuki.data.importing

import com.aesprt.marisuki.domain.model.NewProduct
import com.aesprt.marisuki.domain.model.ProductUnitInput
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

/** A template row is one new product; existing products are never updated. */
enum class ImportProblem { REQUIRED, NUMBER, FRACTION, DUPLICATE_BARCODE, UNIT, BOOLEAN, HEADERS, EMPTY, LIMIT, FORMAT, FORMULA, PERMISSION, FAILED }
data class ImportIssue(val row: Int, val column: String, val problem: ImportProblem)
data class ImportRow(val row: Int, val name: String, val input: NewProduct?, val issues: List<ImportIssue>)
data class ImportPreview(val rows: List<ImportRow>) {
    val validCount get() = rows.count { it.input != null && it.issues.isEmpty() }
    val issues get() = rows.flatMap { it.issues }
    val canImport get() = rows.isNotEmpty() && validCount == rows.size
}
class ImportFileException(val problem: ImportProblem) : Exception(problem.name)
data class ImportResult(val count: Int, val alreadyImported: Boolean)

object ProductImport {
    const val MAX_ROWS = 1000
    const val MAX_BYTES = 8 * 1024 * 1024
    val headers = listOf("Product name", "Barcode", "Category", "Stock unit", "Selling price", "Purchase cost", "Starting stock", "Low stock alert", "Allow fractions", "Selling unit 1", "Stock units per selling unit 1", "Selling price 1", "Selling unit 2", "Stock units per selling unit 2", "Selling price 2")
    fun csvTemplate() = "\uFEFF" + headers.joinToString(",") + "\r\n"

    fun parse(bytes: ByteArray, excel: Boolean): ImportPreview {
        if (bytes.size > MAX_BYTES) throw ImportFileException(ImportProblem.LIMIT)
        val table = if (excel) XlsxRows.read(bytes) else {
            val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            val text = try { decoder.decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF") } catch (_: Exception) { throw ImportFileException(ImportProblem.FORMAT) }
            csvRows(text).mapIndexed { index, row -> index + 1 to row }
        }
        val nonempty = table.filter { (_, values) -> values.any(String::isNotBlank) }
        if (nonempty.isEmpty()) throw ImportFileException(ImportProblem.EMPTY)
        val heading = nonempty.first().second.map { it.trim().lowercase(Locale.ROOT) }
        if (heading.distinct().size != heading.size || heading.toSet() != headers.map { it.lowercase(Locale.ROOT) }.toSet()) throw ImportFileException(ImportProblem.HEADERS)
        val indices = headers.map { heading.indexOf(it.lowercase(Locale.ROOT)) }
        if (nonempty.size - 1 > MAX_ROWS) throw ImportFileException(ImportProblem.LIMIT)
        val rows = nonempty.drop(1).map { (line, raw) ->
            val values = indices.map { raw.getOrElse(it) { "" }.trim() }
            val issues = mutableListOf<ImportIssue>()
            fun issue(i: Int, code: ImportProblem) { issues += ImportIssue(line, headers[i], code) }
            fun number(i: Int, scale: Int, required: Boolean = false): Long {
                val text = values[i]
                if (text.isEmpty()) { if (required) issue(i, ImportProblem.REQUIRED); return 0 }
                return try {
                    // Plain decimal values only: no currency signs, separators, exponents, or rounding.
                    require(text.matches(Regex("[0-9]{1,9}(\\.[0-9]+)?")))
                    BigDecimal(text).setScale(scale, RoundingMode.UNNECESSARY).movePointRight(scale).longValueExact()
                } catch (_: Exception) { issue(i, ImportProblem.NUMBER); 0 }
            }
            values.forEachIndexed { i, value ->
                if (value == XlsxRows.FORMULA) issue(i, ImportProblem.FORMULA)
                if (value.length > 240) issue(i, ImportProblem.LIMIT)
            }
            if (raw.size > heading.size && raw.drop(heading.size).any(String::isNotBlank)) issue(0, ImportProblem.HEADERS)
            if (values[0].isBlank()) issue(0, ImportProblem.REQUIRED)
            if (values[3].isBlank()) issue(3, ImportProblem.REQUIRED)
            val price = number(4, 2, true)
            val cost = number(5, 2)
            val stock = number(6, 3)
            val low = number(7, 3)
            val fractional = when (values[8].lowercase(Locale.ROOT)) {
                "", "no", "false", "0", "hindi" -> false
                "yes", "true", "1", "oo" -> true
                else -> { issue(8, ImportProblem.BOOLEAN); false }
            }
            if (!fractional && stock % 1000L != 0L) issue(6, ImportProblem.FRACTION)
            val extras = mutableListOf<ProductUnitInput>()
            for (i in listOf(9, 12)) if ((i..i + 2).any { values[it].isNotBlank() }) {
                if (values[i].isBlank()) issue(i, ImportProblem.REQUIRED)
                val count = number(i + 1, 3, true)
                val extraPrice = number(i + 2, 2, true)
                if (count <= 0L) issue(i + 1, ImportProblem.UNIT)
                if (!fractional && count % 1000L != 0L) issue(i + 1, ImportProblem.FRACTION)
                extras += ProductUnitInput(values[i], count, extraPrice)
            }
            val labels = (listOf(values[3]) + extras.map { it.label }).map { it.lowercase(Locale.ROOT) }
            if (labels.distinct().size != labels.size) issue(9, ImportProblem.UNIT)
            val product = NewProduct(values[0], values[1].ifBlank { null }, values[2].ifBlank { null }, values[3], price, cost, stock, low, !fractional, extras)
            ImportRow(line, values[0], product.takeIf { issues.isEmpty() }, issues.distinct())
        }
        if (rows.isEmpty()) throw ImportFileException(ImportProblem.EMPTY)
        val duplicateCodes = rows.mapNotNull { it.input?.barcode }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        return ImportPreview(rows.map { row -> if (row.input?.barcode in duplicateCodes) row.copy(issues = row.issues + ImportIssue(row.row, headers[1], ImportProblem.DUPLICATE_BARCODE)) else row })
    }

    /** RFC 4180 quoting, embedded commas/newlines, CRLF and UTF-8 BOM. */
    internal fun csvRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val value = StringBuilder()
        var quoted = false
        var closed = false
        var i = 0
        fun cell() { row += value.toString(); value.clear(); closed = false; if (row.size > 64) throw ImportFileException(ImportProblem.HEADERS) }
        fun endRow() { cell(); rows += row; row = mutableListOf(); if (rows.size > MAX_ROWS + 2) throw ImportFileException(ImportProblem.LIMIT) }
        while (i < text.length) {
            val c = text[i++]
            if (quoted) {
                if (c == '"') { if (i < text.length && text[i] == '"') { value.append('"'); i++ } else { quoted = false; closed = true } } else value.append(c)
            } else when (c) {
                '"' -> { if (value.isNotEmpty() || closed) throw ImportFileException(ImportProblem.FORMAT); quoted = true }
                ',' -> cell()
                '\r', '\n' -> { if (c == '\r' && i < text.length && text[i] == '\n') i++; endRow() }
                else -> { if (closed) throw ImportFileException(ImportProblem.FORMAT); value.append(c) }
            }
            if (value.length > 4096) throw ImportFileException(ImportProblem.LIMIT)
        }
        if (quoted) throw ImportFileException(ImportProblem.FORMAT)
        if (value.isNotEmpty() || row.isNotEmpty() || closed) endRow()
        return rows
    }
}

/** Bounded, non-evaluating OOXML reader. External entities and formulas are never evaluated. */
internal object XlsxRows {
    const val FORMULA = "\u0000FORMULA"
    fun read(bytes: ByteArray): List<Pair<Int, List<String>>> {
        try {
            val parts = mutableMapOf<String, ByteArray>()
            var total = 0L
            var entries = 0
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (++entries > 512) throw ImportFileException(ImportProblem.LIMIT)
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = zip.read(buffer); if (n < 0) break
                        total += n; if (total > 32L * 1024 * 1024) throw ImportFileException(ImportProblem.LIMIT)
                        out.write(buffer, 0, n)
                    }
                    if (entry.name.startsWith("xl/") && entry.name.endsWith(".xml") || entry.name == "xl/_rels/workbook.xml.rels") {
                        if (parts.put(entry.name, out.toByteArray()) != null) throw ImportFileException(ImportProblem.FORMAT)
                    }
                }
            }
            fun xml(name: String, handler: DefaultHandler) {
                val data = parts[name] ?: throw ImportFileException(ImportProblem.FORMAT)
                val factory = SAXParserFactory.newInstance().apply {
                    isNamespaceAware = true
                    setFeature("http://xml.org/sax/features/external-general-entities", false)
                    setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                }
                val parser = factory.newSAXParser().xmlReader
                parser.entityResolver = org.xml.sax.EntityResolver { _, _ -> throw ImportFileException(ImportProblem.FORMAT) }
                // Android's parser does not expose every JAXP feature; reject a DTD before parsing too.
                if (data.toString(Charsets.UTF_8).replace("\u0000", "").contains("<!DOCTYPE", true)) throw ImportFileException(ImportProblem.FORMAT)
                parser.contentHandler = handler
                parser.parse(org.xml.sax.InputSource(ByteArrayInputStream(data)))
            }
            val sheets = mutableListOf<Pair<String, String>>()
            xml("xl/workbook.xml", object : DefaultHandler() {
                override fun startElement(uri: String?, local: String, qName: String?, a: Attributes) {
                    if (local == "sheet") sheets += a.getValue("name") to (a.getValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id") ?: a.getValue("r:id"))
                }
            })
            val selected = sheets.firstOrNull { it.first.equals("Products", true) } ?: sheets.firstOrNull() ?: throw ImportFileException(ImportProblem.EMPTY)
            var target: String? = null
            xml("xl/_rels/workbook.xml.rels", object : DefaultHandler() {
                override fun startElement(uri: String?, local: String, qName: String?, a: Attributes) {
                    if (local == "Relationship" && a.getValue("Id") == selected.second) {
                        if (a.getValue("TargetMode") == "External") throw ImportFileException(ImportProblem.FORMAT)
                        target = a.getValue("Target")
                    }
                }
            })
            val path = target?.let { if (it.startsWith("/")) it.removePrefix("/") else "xl/$it" } ?: throw ImportFileException(ImportProblem.FORMAT)
            if (path.contains("..") || !path.startsWith("xl/worksheets/")) throw ImportFileException(ImportProblem.FORMAT)
            val shared = mutableListOf<String>()
            if ("xl/sharedStrings.xml" in parts) xml("xl/sharedStrings.xml", object : DefaultHandler() {
                var inText = false; val value = StringBuilder()
                override fun startElement(uri: String?, local: String, qName: String?, a: Attributes) { if (local == "si") value.clear(); if (local == "t") inText = true }
                override fun characters(ch: CharArray, start: Int, length: Int) { if (inText) value.append(ch, start, length) }
                override fun endElement(uri: String?, local: String, qName: String?) { if (local == "t") inText = false; if (local == "si") shared += value.toString() }
            })
            val rows = mutableListOf<Pair<Int, List<String>>>()
            xml(path, object : DefaultHandler() {
                var rowNumber = 0; var column = -1; var type = ""; var reading = false; var formula = false
                var row = mutableListOf<String>(); val value = StringBuilder()
                override fun startElement(uri: String?, local: String, qName: String?, a: Attributes) {
                    when (local) {
                        "row" -> { rowNumber = a.getValue("r")?.toIntOrNull() ?: rowNumber + 1; row = mutableListOf(); column = -1 }
                        "c" -> {
                            val letters = a.getValue("r")?.takeWhile(Char::isLetter)
                            column = if (letters.isNullOrEmpty()) column + 1 else letters.fold(0) { n, c -> n * 26 + c.uppercaseChar().code - 64 } - 1
                            if (column !in 0..63) throw ImportFileException(ImportProblem.HEADERS)
                            type = a.getValue("t").orEmpty(); value.clear(); formula = false
                        }
                        "f" -> formula = true
                        "v", "t" -> reading = true
                    }
                }
                override fun characters(ch: CharArray, start: Int, length: Int) { if (reading) { value.append(ch, start, length); if (value.length > 4096) throw ImportFileException(ImportProblem.LIMIT) } }
                override fun endElement(uri: String?, local: String, qName: String?) {
                    when (local) {
                        "v", "t" -> reading = false
                        "c" -> {
                            while (row.size <= column) row += ""
                            row[column] = when { formula -> FORMULA; type == "s" -> shared.getOrNull(value.toString().toIntOrNull() ?: -1) ?: throw ImportFileException(ImportProblem.FORMAT); type == "e" -> FORMULA; else -> value.toString() }
                        }
                        "row" -> if (row.any(String::isNotBlank)) { rows += rowNumber to row; if (rows.size > ProductImport.MAX_ROWS + 1) throw ImportFileException(ImportProblem.LIMIT) }
                    }
                }
            })
            return rows
        } catch (e: ImportFileException) { throw e } catch (_: Exception) { throw ImportFileException(ImportProblem.FORMAT) }
    }
}
