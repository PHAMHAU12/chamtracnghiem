package com.example.data.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.Exam
import com.example.data.model.GradingCalculator
import com.example.data.model.Submission
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ExcelExporter {

    private val dateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi-VN"))

    fun suggestedFileName(exam: Exam): String {
        val safeTitle = exam.title
            .replace(Regex("[^\\p{L}\\p{N}_\\- ]"), "")
            .trim()
            .replace(Regex("\\s+"), "_")
            .ifEmpty { "BangDiem" }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        return "BangDiem_${safeTitle}_$stamp.xlsx"
    }

    fun exportToCacheAndGetShareUri(
        context: Context,
        exam: Exam,
        submissions: List<Submission>
    ): Uri {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val outFile = File(dir, suggestedFileName(exam))
        outFile.outputStream().use { stream ->
            writeWorkbook(stream, exam, submissions)
        }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outFile
        )
    }

    fun exportToUri(
        context: Context,
        uri: Uri,
        exam: Exam,
        submissions: List<Submission>
    ) {
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            writeWorkbook(stream, exam, submissions)
        } ?: throw IllegalStateException("Không thể mở tệp đích để ghi bảng điểm Excel.")
    }

    fun buildWorkbookBytes(exam: Exam, submissions: List<Submission>): ByteArray {
        val baos = ByteArrayOutputStream()
        writeWorkbook(baos, exam, submissions)
        return baos.toByteArray()
    }

    private fun writeWorkbook(
        outputStream: OutputStream,
        exam: Exam,
        submissions: List<Submission>
    ) {
        val zip = ZipOutputStream(outputStream)

        fun addEntry(name: String, content: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        addEntry("[Content_Types].xml", contentTypesXml())
        addEntry("_rels/.rels", rootRelsXml())
        addEntry("xl/workbook.xml", workbookXml())
        addEntry("xl/_rels/workbook.xml.rels", workbookRelsXml())
        addEntry("xl/styles.xml", stylesXml())
        addEntry("xl/worksheets/sheet1.xml", buildSummarySheetXml(exam, submissions))
        addEntry("xl/worksheets/sheet2.xml", buildDetailSheetXml(exam, submissions))
        addEntry("xl/worksheets/sheet3.xml", buildItemAnalysisSheetXml(exam, submissions))

        zip.finish()
        zip.flush()
    }

    private fun buildSummarySheetXml(exam: Exam, submissions: List<Submission>): String {
        val sorted = submissions.sortedWith(
            compareBy<Submission> { it.studentCode.ifBlank { "ZZZ" } }
                .thenBy { it.studentName }
        )
        val avgScore = if (sorted.isNotEmpty()) {
            sorted.map { it.score }.average()
        } else 0.0
        val highestScore = sorted.maxOfOrNull { it.score } ?: 0.0
        val lowestScore = sorted.minOfOrNull { it.score } ?: 0.0

        val rows = StringBuilder()
        var rowIdx = 1

        fun addRow(cells: List<CellValue>) {
            rows.append("<row r=\"$rowIdx\">")
            cells.forEachIndexed { colIdx, cell ->
                val ref = "${colLetter(colIdx)}$rowIdx"
                when (cell) {
                    is CellValue.Text -> {
                        rows.append("<c r=\"$ref\" s=\"${cell.style}\" t=\"inlineStr\"><is><t>${escapeXml(cell.value)}</t></is></c>")
                    }
                    is CellValue.Number -> {
                        rows.append("<c r=\"$ref\" s=\"${cell.style}\"><v>${cell.value}</v></c>")
                    }
                }
            }
            rows.append("</row>")
            rowIdx++
        }

        addRow(listOf(CellValue.Text("BẢNG ĐIỂM KIỂM TRA TRẮC NGHIỆM (CHẤM THI AI)", STYLE_TITLE)))
        addRow(
            listOf(
                CellValue.Text("Đề thi:", STYLE_META_LABEL),
                CellValue.Text(exam.title, STYLE_META_VALUE),
                CellValue.Text("Môn học:", STYLE_META_LABEL),
                CellValue.Text(exam.subject.ifBlank { "-" }, STYLE_META_VALUE),
                CellValue.Text("Lớp:", STYLE_META_LABEL),
                CellValue.Text(exam.classGroup.ifBlank { "-" }, STYLE_META_VALUE)
            )
        )
        addRow(
            listOf(
                CellValue.Text("Số câu:", STYLE_META_LABEL),
                CellValue.Number(exam.totalQuestions.toDouble(), STYLE_INT),
                CellValue.Text("Thang điểm:", STYLE_META_LABEL),
                CellValue.Number(exam.maxScore, STYLE_DECIMAL),
                CellValue.Text("Số bài đã chấm:", STYLE_META_LABEL),
                CellValue.Number(sorted.size.toDouble(), STYLE_INT)
            )
        )
        addRow(
            listOf(
                CellValue.Text("Điểm trung bình:", STYLE_META_LABEL),
                CellValue.Number(String.format(Locale.US, "%.2f", avgScore).toDouble(), STYLE_DECIMAL),
                CellValue.Text("Cao nhất:", STYLE_META_LABEL),
                CellValue.Number(highestScore, STYLE_DECIMAL),
                CellValue.Text("Thấp nhất:", STYLE_META_LABEL),
                CellValue.Number(lowestScore, STYLE_DECIMAL)
            )
        )
        addRow(emptyList())

        // Header bảng
        addRow(
            listOf(
                CellValue.Text("STT", STYLE_HEADER),
                CellValue.Text("Họ và tên", STYLE_HEADER),
                CellValue.Text("Số báo danh", STYLE_HEADER),
                CellValue.Text("Mã đề", STYLE_HEADER),
                CellValue.Text("Số câu đúng", STYLE_HEADER),
                CellValue.Text("Số câu sai", STYLE_HEADER),
                CellValue.Text("Bỏ trống", STYLE_HEADER),
                CellValue.Text("Điểm số", STYLE_HEADER),
                CellValue.Text("Xếp loại", STYLE_HEADER),
                CellValue.Text("Thời gian chấm", STYLE_HEADER),
                CellValue.Text("Ghi chú / Nhận xét AI", STYLE_HEADER)
            )
        )

        val passThreshold = exam.maxScore * 0.5
        sorted.forEachIndexed { index, sub ->
            val dateStr = sub.createdAt?.toDate()?.let { dateFormatter.format(it) } ?: "-"
            val rank = GradingCalculator.classifyRank(sub.score, exam.maxScore)
            val scoreStyle = if (sub.score >= passThreshold) STYLE_PASS else STYLE_FAIL
            val combinedNote = listOf(sub.notes, sub.feedbackNote).filter { it.isNotBlank() }.joinToString(" | ")

            addRow(
                listOf(
                    CellValue.Number((index + 1).toDouble(), STYLE_INT),
                    CellValue.Text(sub.studentName, STYLE_BODY),
                    CellValue.Text(sub.studentCode.ifBlank { "-" }, STYLE_BODY),
                    CellValue.Text(sub.examCode.ifBlank { "-" }, STYLE_BODY),
                    CellValue.Number(sub.correctCount.toDouble(), STYLE_INT),
                    CellValue.Number(sub.wrongCount.toDouble(), STYLE_INT),
                    CellValue.Number(sub.blankCount.toDouble(), STYLE_INT),
                    CellValue.Number(sub.score, scoreStyle),
                    CellValue.Text(rank, STYLE_BODY),
                    CellValue.Text(dateStr, STYLE_BODY),
                    CellValue.Text(combinedNote, STYLE_BODY)
                )
            )
        }

        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <cols>
    <col min="1" max="1" width="6" customWidth="1"/>
    <col min="2" max="2" width="26" customWidth="1"/>
    <col min="3" max="3" width="16" customWidth="1"/>
    <col min="4" max="4" width="10" customWidth="1"/>
    <col min="5" max="7" width="12" customWidth="1"/>
    <col min="8" max="8" width="12" customWidth="1"/>
    <col min="9" max="9" width="14" customWidth="1"/>
    <col min="10" max="10" width="20" customWidth="1"/>
    <col min="11" max="11" width="35" customWidth="1"/>
  </cols>
  <sheetData>$rows</sheetData>
</worksheet>"""
    }

    private fun buildDetailSheetXml(exam: Exam, submissions: List<Submission>): String {
        val sorted = submissions.sortedWith(
            compareBy<Submission> { it.studentCode.ifBlank { "ZZZ" } }
                .thenBy { it.studentName }
        )
        val totalQ = exam.totalQuestions.toInt()

        val rows = StringBuilder()
        var rowIdx = 1

        fun addRow(cells: List<CellValue>) {
            rows.append("<row r=\"$rowIdx\">")
            cells.forEachIndexed { colIdx, cell ->
                val ref = "${colLetter(colIdx)}$rowIdx"
                when (cell) {
                    is CellValue.Text -> {
                        rows.append("<c r=\"$ref\" s=\"${cell.style}\" t=\"inlineStr\"><is><t>${escapeXml(cell.value)}</t></is></c>")
                    }
                    is CellValue.Number -> {
                        rows.append("<c r=\"$ref\" s=\"${cell.style}\"><v>${cell.value}</v></c>")
                    }
                }
            }
            rows.append("</row>")
            rowIdx++
        }

        addRow(listOf(CellValue.Text("BẢNG ĐÁP ÁN CHI TIẾT TỪNG CÂU", STYLE_TITLE)))
        addRow(emptyList())

        // Header câu hỏi
        val headerCells = mutableListOf<CellValue>()
        headerCells.add(CellValue.Text("STT", STYLE_HEADER))
        headerCells.add(CellValue.Text("Họ và tên", STYLE_HEADER))
        headerCells.add(CellValue.Text("SBD", STYLE_HEADER))
        headerCells.add(CellValue.Text("Mã đề", STYLE_HEADER))
        headerCells.add(CellValue.Text("Điểm", STYLE_HEADER))
        for (q in 1..totalQ) {
            headerCells.add(CellValue.Text("C$q", STYLE_HEADER))
        }
        addRow(headerCells)

        // Đáp án chuẩn mặc định
        val keyRow = mutableListOf<CellValue>()
        keyRow.add(CellValue.Text("-", STYLE_META_VALUE))
        keyRow.add(CellValue.Text("ĐÁP ÁN CHUẨN", STYLE_META_LABEL))
        keyRow.add(CellValue.Text("-", STYLE_META_VALUE))
        keyRow.add(CellValue.Text("Chuẩn", STYLE_META_VALUE))
        keyRow.add(CellValue.Number(exam.maxScore, STYLE_DECIMAL))
        for (q in 0 until totalQ) {
            val key = exam.answerKey.getOrNull(q) ?: "-"
            keyRow.add(CellValue.Text(key, STYLE_META_LABEL))
        }
        addRow(keyRow)

        // Từng học sinh
        sorted.forEachIndexed { index, sub ->
            val subKey = exam.getAnswerKeyForCode(sub.examCode)
            val cells = mutableListOf<CellValue>()
            cells.add(CellValue.Number((index + 1).toDouble(), STYLE_INT))
            cells.add(CellValue.Text(sub.studentName, STYLE_BODY))
            cells.add(CellValue.Text(sub.studentCode.ifBlank { "-" }, STYLE_BODY))
            cells.add(CellValue.Text(sub.examCode.ifBlank { "-" }, STYLE_BODY))
            cells.add(CellValue.Number(sub.score, STYLE_DECIMAL))
            for (q in 0 until totalQ) {
                val ans = sub.studentAnswers.getOrNull(q) ?: "-"
                val expected = subKey.getOrNull(q)
                val style = if (expected != null && ans == expected) STYLE_PASS else STYLE_BODY
                cells.add(CellValue.Text(ans, style))
            }
            addRow(cells)
        }

        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <cols>
    <col min="1" max="1" width="6" customWidth="1"/>
    <col min="2" max="2" width="24" customWidth="1"/>
    <col min="3" max="3" width="14" customWidth="1"/>
    <col min="4" max="4" width="10" customWidth="1"/>
    <col min="5" max="5" width="10" customWidth="1"/>
    <col min="6" max="${5 + totalQ}" width="6" customWidth="1"/>
  </cols>
  <sheetData>$rows</sheetData>
</worksheet>"""
    }

    private fun buildItemAnalysisSheetXml(exam: Exam, submissions: List<Submission>): String {
        val analytics = GradingCalculator.calculateAnalytics(exam, submissions)
        val rows = StringBuilder()
        var rowIdx = 1

        fun addRow(cells: List<CellValue>) {
            rows.append("<row r=\"$rowIdx\">")
            cells.forEachIndexed { colIdx, cell ->
                val ref = "${colLetter(colIdx)}$rowIdx"
                when (cell) {
                    is CellValue.Text -> {
                        rows.append("<c r=\"$ref\" s=\"${cell.style}\" t=\"inlineStr\"><is><t>${escapeXml(cell.value)}</t></is></c>")
                    }
                    is CellValue.Number -> {
                        rows.append("<c r=\"$ref\" s=\"${cell.style}\"><v>${cell.value}</v></c>")
                    }
                }
            }
            rows.append("</row>")
            rowIdx++
        }

        addRow(listOf(CellValue.Text("PHÂN TÍCH ĐỘ KHÓ & MA TRẬN CÂU HỎI (ITEM ANALYSIS)", STYLE_TITLE)))
        addRow(
            listOf(
                CellValue.Text("Đề thi: ${exam.title} | Tổng số bài chấm: ${submissions.size}", STYLE_META_VALUE)
            )
        )
        addRow(emptyList())

        addRow(
            listOf(
                CellValue.Text("Câu số", STYLE_HEADER),
                CellValue.Text("Đáp án chuẩn", STYLE_HEADER),
                CellValue.Text("Tỉ lệ đúng (%)", STYLE_HEADER),
                CellValue.Text("Chọn A", STYLE_HEADER),
                CellValue.Text("Chọn B", STYLE_HEADER),
                CellValue.Text("Chọn C", STYLE_HEADER),
                CellValue.Text("Chọn D", STYLE_HEADER),
                CellValue.Text("Bỏ trống", STYLE_HEADER),
                CellValue.Text("Đánh giá độ khó", STYLE_HEADER)
            )
        )

        analytics.itemStats.forEach { q ->
            val assessment = when {
                q.correctPercentage >= 80.0 -> "Dễ (Tốt)"
                q.correctPercentage >= 50.0 -> "Trung bình"
                else -> "⚠️ Khó / Cảnh báo bẫy"
            }
            val rateStyle = if (q.isHardQuestion) STYLE_FAIL else STYLE_PASS

            addRow(
                listOf(
                    CellValue.Number(q.questionNumber.toDouble(), STYLE_INT),
                    CellValue.Text(q.correctAnswer, STYLE_BODY),
                    CellValue.Number(q.correctPercentage, rateStyle),
                    CellValue.Number(q.countA.toDouble(), STYLE_INT),
                    CellValue.Number(q.countB.toDouble(), STYLE_INT),
                    CellValue.Number(q.countC.toDouble(), STYLE_INT),
                    CellValue.Number(q.countD.toDouble(), STYLE_INT),
                    CellValue.Number(q.countBlank.toDouble(), STYLE_INT),
                    CellValue.Text(assessment, STYLE_BODY)
                )
            )
        }

        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <cols>
    <col min="1" max="1" width="8" customWidth="1"/>
    <col min="2" max="2" width="14" customWidth="1"/>
    <col min="3" max="3" width="16" customWidth="1"/>
    <col min="4" max="8" width="10" customWidth="1"/>
    <col min="9" max="9" width="25" customWidth="1"/>
  </cols>
  <sheetData>$rows</sheetData>
</worksheet>"""
    }

    private fun colLetter(colZeroBased: Int): String {
        var index = colZeroBased + 1
        val sb = StringBuilder()
        while (index > 0) {
            val rem = (index - 1) % 26
            sb.append(('A'.code + rem).toChar())
            index = (index - 1) / 26
        }
        return sb.reverse().toString()
    }

    private fun escapeXml(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> if (ch.code >= 0x20 || ch == '\t' || ch == '\n' || ch == '\r') sb.append(ch)
            }
        }
        return sb.toString()
    }

    private sealed class CellValue(val style: Int) {
        class Text(val value: String, style: Int) : CellValue(style)
        class Number(val value: Double, style: Int) : CellValue(style)
    }

    private const val STYLE_BODY = 0
    private const val STYLE_TITLE = 1
    private const val STYLE_HEADER = 2
    private const val STYLE_META_LABEL = 3
    private const val STYLE_META_VALUE = 4
    private const val STYLE_INT = 5
    private const val STYLE_DECIMAL = 6
    private const val STYLE_PASS = 7
    private const val STYLE_FAIL = 8

    private fun contentTypesXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet3.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

    private fun rootRelsXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private fun workbookXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Bảng điểm" sheetId="1" r:id="rId1"/>
    <sheet name="Chi tiết từng câu" sheetId="2" r:id="rId2"/>
    <sheet name="Phân tích độ khó" sheetId="3" r:id="rId3"/>
  </sheets>
</workbook>"""

    private fun workbookRelsXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet3.xml"/>
  <Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private fun stylesXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <numFmts count="2">
    <numFmt numFmtId="164" formatCode="0"/>
    <numFmt numFmtId="165" formatCode="0.00"/>
  </numFmts>
  <fonts count="5">
    <font><sz val="11"/><name val="Calibri"/></font>
    <font><b/><sz val="14"/><color rgb="FF1A3967"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FF1E293B"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FF065F46"/><name val="Calibri"/></font>
  </fonts>
  <fills count="5">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF1A3967"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFE2E8F0"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFD1FAE5"/></patternFill></fill>
  </fills>
  <borders count="2">
    <border/>
    <border>
      <left style="thin"><color rgb="FFCBD5E1"/></left>
      <right style="thin"><color rgb="FFCBD5E1"/></right>
      <top style="thin"><color rgb="FFCBD5E1"/></top>
      <bottom style="thin"><color rgb="FFCBD5E1"/></bottom>
    </border>
  </borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="9">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" applyBorder="1"/>
    <xf numFmtId="0" fontId="1" fillId="0" borderId="0"/>
    <xf numFmtId="0" fontId="2" fillId="2" borderId="1" applyFill="1" applyBorder="1"><alignment horizontal="center" vertical="center"/></xf>
    <xf numFmtId="0" fontId="3" fillId="3" borderId="1" applyFill="1" applyBorder="1"/>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" applyBorder="1"/>
    <xf numFmtId="164" fontId="0" fillId="0" borderId="1" applyNumberFormat="1" applyBorder="1"><alignment horizontal="center"/></xf>
    <xf numFmtId="165" fontId="0" fillId="0" borderId="1" applyNumberFormat="1" applyBorder="1"><alignment horizontal="center"/></xf>
    <xf numFmtId="165" fontId="4" fillId="4" borderId="1" applyFont="1" applyFill="1" applyNumberFormat="1" applyBorder="1"><alignment horizontal="center"/></xf>
    <xf numFmtId="165" fontId="3" fillId="3" borderId="1" applyFont="1" applyFill="1" applyNumberFormat="1" applyBorder="1"><alignment horizontal="center"/></xf>
  </cellXfs>
</styleSheet>"""
}
