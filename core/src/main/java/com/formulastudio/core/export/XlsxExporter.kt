package com.formulastudio.core.export

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 纯 Kotlin 的 xlsx 写出器（零第三方依赖）。
 *
 * 只依赖 OOXML 规范里最小的一部分：inlineStr 单元格，
 * 因此不需要 sharedStrings.xml，任何 Excel / WPS / LibreOffice 都能打开。
 */
object XlsxExporter {

    fun build(rows: List<List<String>>, sheetName: String = "Sheet1"): ByteArray {
        val sheet = StringBuilder()
        sheet.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
            .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
            .append("<sheetData>")
        for ((ri, row) in rows.withIndex()) {
            sheet.append("<row r=\"").append(ri + 1).append("\">")
            for ((ci, cell) in row.withIndex()) {
                if (cell.isEmpty()) continue
                val ref = colName(ci) + (ri + 1)
                sheet.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                    .append(esc(cell)).append("</t></is></c>")
            }
            sheet.append("</row>")
        }
        sheet.append("</sheetData></worksheet>")

        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            fun e(name: String, data: String) {
                z.putNextEntry(ZipEntry(name)); z.write(data.toByteArray(Charsets.UTF_8)); z.closeEntry()
            }
            e("[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                "</Types>")
            e("_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
                "</Relationships>")
            e("xl/workbook.xml", StringBuilder()
                .append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n")
                .append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" ")
                .append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">")
                .append("<sheets><sheet name=\"").append(esc(sheetName))
                .append("\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>")
                .toString())
            e("xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
                "</Relationships>")
            e("xl/worksheets/sheet1.xml", sheet.toString())
        }
        return bos.toByteArray()
    }

    private fun colName(i: Int): String {
        var n = i
        val sb = StringBuilder()
        while (n >= 0) {
            sb.insert(0, ('A' + (n % 26)))
            n = n / 26 - 1
        }
        return sb.toString()
    }

    private fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
