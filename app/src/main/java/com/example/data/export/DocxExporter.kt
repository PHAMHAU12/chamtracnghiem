package com.example.data.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.Exam
import com.example.data.model.GradingCalculator
import com.example.data.model.Submission
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DocxExporter {

    private val dateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("vi-VN"))

    /**
     * Tạo file Word (.docx) chứa Phiếu trả lời trắc nghiệm chuẩn để in ấn ra giấy A4
     */
    fun exportBlankAnswerSheet(
        context: Context,
        exam: Exam,
        targetQuestionCount: Int = exam.totalQuestions.toInt().coerceIn(10, 100)
    ): Uri {
        val dir = File(context.cacheDir, "docx_templates").apply { mkdirs() }
        val safeTitle = exam.title.replace(Regex("[^\\p{L}\\p{N}_\\- ]"), "").trim().replace(Regex("\\s+"), "_").ifEmpty { "PhieuThi" }
        val outFile = File(dir, "PhieuTo_${safeTitle}_${targetQuestionCount}Cau.docx")
        outFile.outputStream().use { stream ->
            writeBlankSheetDocx(stream, exam, targetQuestionCount)
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
    }

    /**
     * Xuất báo cáo điểm thi toàn lớp ra file Word (.docx)
     */
    fun exportScoreReport(
        context: Context,
        exam: Exam,
        submissions: List<Submission>
    ): Uri {
        val dir = File(context.cacheDir, "docx_reports").apply { mkdirs() }
        val safeTitle = exam.title.replace(Regex("[^\\p{L}\\p{N}_\\- ]"), "").trim().replace(Regex("\\s+"), "_").ifEmpty { "BaoCaoDiem" }
        val outFile = File(dir, "BaoCaoDiem_${safeTitle}.docx")
        outFile.outputStream().use { stream ->
            writeScoreReportDocx(stream, exam, submissions)
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
    }

    private fun writeBlankSheetDocx(stream: OutputStream, exam: Exam, totalQ: Int) {
        val zip = ZipOutputStream(stream)
        fun addEntry(name: String, content: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        addEntry("[Content_Types].xml", docxContentTypesXml())
        addEntry("_rels/.rels", docxRootRelsXml())
        addEntry("word/_rels/document.xml.rels", docxDocumentRelsXml())
        addEntry("word/styles.xml", docxStylesXml())
        addEntry("word/document.xml", buildBlankSheetBodyXml(exam, totalQ))

        zip.finish()
        zip.flush()
    }

    private fun writeScoreReportDocx(stream: OutputStream, exam: Exam, submissions: List<Submission>) {
        val zip = ZipOutputStream(stream)
        fun addEntry(name: String, content: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        addEntry("[Content_Types].xml", docxContentTypesXml())
        addEntry("_rels/.rels", docxRootRelsXml())
        addEntry("word/_rels/document.xml.rels", docxDocumentRelsXml())
        addEntry("word/styles.xml", docxStylesXml())
        addEntry("word/document.xml", buildScoreReportBodyXml(exam, submissions))

        zip.finish()
        zip.flush()
    }

    private fun buildBlankSheetBodyXml(exam: Exam, totalQ: Int): String {
        val questionsXml = StringBuilder()
        val columns = if (totalQ <= 30) 2 else 4
        val rowsPerCol = (totalQ + columns - 1) / columns

        questionsXml.append("<w:tbl>")
        questionsXml.append("""
            <w:tblPr>
                <w:tblW w:w="0" w:type="auto"/>
                <w:tblBorders>
                    <w:top w:val="single" w:sz="4" w:space="0" w:color="CCCCCC"/>
                    <w:bottom w:val="single" w:sz="4" w:space="0" w:color="CCCCCC"/>
                    <w:insideH w:val="single" w:sz="4" w:space="0" w:color="EEEEEE"/>
                    <w:insideV w:val="none"/>
                </w:tblBorders>
            </w:tblPr>
        """.trimIndent())

        for (r in 0 until rowsPerCol) {
            questionsXml.append("<w:tr>")
            for (c in 0 until columns) {
                val qIndex = c * rowsPerCol + r + 1
                questionsXml.append("<w:tc><w:tcPr><w:tcW w:w=\"2200\" w:type=\"dxa\"/></w:tcPr>")
                if (qIndex <= totalQ) {
                    val qNumStr = if (qIndex < 10) "0$qIndex" else "$qIndex"
                    questionsXml.append("""
                        <w:p>
                            <w:r><w:rPr><w:b/><w:sz w:val="20"/></w:rPr><w:t>$qNumStr.  </w:t></w:r>
                            <w:r><w:rPr><w:color w:val="2B579A"/><w:sz w:val="20"/></w:rPr><w:t>Ⓐ  Ⓑ  Ⓒ  Ⓓ</w:t></w:r>
                        </w:p>
                    """.trimIndent())
                } else {
                    questionsXml.append("<w:p/>")
                }
                questionsXml.append("</w:tc>")
            }
            questionsXml.append("</w:tr>")
        }
        questionsXml.append("</w:tbl>")

        val classText = if (exam.classGroup.isNotBlank()) " - Lớp: ${escapeXml(exam.classGroup)}" else ""

        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
    <w:body>
        <w:p>
            <w:pPr><w:jc w:val="center"/></w:pPr>
            <w:r>
                <w:rPr><w:b/><w:sz w:val="32"/><w:color w:val="1A365D"/></w:rPr>
                <w:t>PHIẾU TRẢ LỜI TRẮC NGHIỆM</w:t>
            </w:r>
        </w:p>
        <w:p>
            <w:pPr><w:jc w:val="center"/></w:pPr>
            <w:r>
                <w:rPr><w:i/><w:sz w:val="24"/><w:color w:val="4A5568"/></w:rPr>
                <w:t>Đề thi: ${escapeXml(exam.title)}$classText</w:t>
            </w:r>
        </w:p>
        <w:p><w:r><w:t></w:t></w:r></w:p>

        <!-- Khung điền thông tin học sinh -->
        <w:tbl>
            <w:tblPr>
                <w:tblW w:w="9000" w:type="dxa"/>
                <w:tblBorders>
                    <w:top w:val="single" w:sz="8" w:space="0" w:color="2B579A"/>
                    <w:left w:val="single" w:sz="8" w:space="0" w:color="2B579A"/>
                    <w:bottom w:val="single" w:sz="8" w:space="0" w:color="2B579A"/>
                    <w:right w:val="single" w:sz="8" w:space="0" w:color="2B579A"/>
                    <w:insideH w:val="single" w:sz="4" w:space="0" w:color="CBD5E0"/>
                    <w:insideV w:val="single" w:sz="4" w:space="0" w:color="CBD5E0"/>
                </w:tblBorders>
            </w:tblPr>
            <w:tr>
                <w:tc><w:p><w:r><w:rPr><w:b/></w:rPr><w:t>Họ và tên thí sinh:</w:t></w:r></w:p></w:tc>
                <w:tc><w:p><w:r><w:t>...........................................................................</w:t></w:r></w:p></w:tc>
                <w:tc><w:p><w:r><w:rPr><w:b/></w:rPr><w:t>MÃ ĐỀ THI</w:t></w:r></w:p></w:tc>
            </w:tr>
            <w:tr>
                <w:tc><w:p><w:r><w:rPr><w:b/></w:rPr><w:t>Số báo danh (SBD):</w:t></w:r></w:p></w:tc>
                <w:tc><w:p><w:r><w:t>[   ] [   ] [   ] [   ] [   ] [   ]</w:t></w:r></w:p></w:tc>
                <w:tc><w:p><w:r><w:rPr><w:b/><w:sz w:val="28"/><w:color w:val="C53030"/></w:rPr><w:t>[   ] [   ] [   ]</w:t></w:r></w:p></w:tc>
            </w:tr>
        </w:tbl>

        <w:p><w:pPr><w:spacing w:before="120" w:after="120"/></w:pPr>
            <w:r><w:rPr><w:i/><w:sz w:val="18"/><w:color w:val="718096"/></w:rPr>
            <w:t>Hướng dẫn: Dùng bút chì đen tô kín ô tròn tương ứng với đáp án lựa chọn. Không được gạch chéo hoặc đánh dấu X.</w:t></w:r>
        </w:p>

        <!-- Lưới câu hỏi -->
        $questionsXml

        <w:sectPr>
            <w:pgSz w:w="11906" w:h="16838"/>
            <w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134"/>
        </w:sectPr>
    </w:body>
</w:document>""".trimIndent()
    }

    private fun buildScoreReportBodyXml(exam: Exam, submissions: List<Submission>): String {
        val analytics = GradingCalculator.calculateAnalytics(exam, submissions)
        val sorted = submissions.sortedByDescending { it.score }

        val rowsXml = StringBuilder()
        sorted.forEachIndexed { index, sub ->
            val rank = GradingCalculator.classifyRank(sub.score, exam.maxScore)
            val stt = index + 1
            rowsXml.append("""
                <w:tr>
                    <w:tc><w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:t>$stt</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:rPr><w:b/></w:rPr><w:t>${escapeXml(sub.studentName)}</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:t>${escapeXml(sub.studentCode.ifBlank { "-" })}</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:t>${escapeXml(sub.examCode.ifBlank { "-" })}</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:t>${sub.correctCount}/${exam.totalQuestions}</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:rPr><w:b/><w:color w:val="2B579A"/></w:rPr><w:t>${String.format(Locale.US, "%.2f", sub.score)}</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:t>$rank</w:t></w:r></w:p></w:tc>
                </w:tr>
            """.trimIndent())
        }

        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
    <w:body>
        <w:p>
            <w:pPr><w:jc w:val="center"/></w:pPr>
            <w:r>
                <w:rPr><w:b/><w:sz w:val="36"/><w:color w:val="1A365D"/></w:rPr>
                <w:t>BÁO CÁO KẾT QUẢ KIỂM TRA</w:t>
            </w:r>
        </w:p>
        <w:p>
            <w:pPr><w:jc w:val="center"/></w:pPr>
            <w:r>
                <w:rPr><w:i/><w:sz w:val="24"/><w:color w:val="4A5568"/></w:rPr>
                <w:t>Đề: ${escapeXml(exam.title)} - Môn: ${escapeXml(exam.subject)} ${if (exam.classGroup.isNotBlank()) " - Lớp: " + escapeXml(exam.classGroup) else ""}</w:t>
            </w:r>
        </w:p>
        <w:p>
            <w:pPr><w:spacing w:after="140"/></w:pPr>
            <w:r><w:rPr><w:b/></w:rPr><w:t>Tổng số bài nộp: </w:t></w:r>
            <w:r><w:t>${analytics.totalSubmissions} | </w:t></w:r>
            <w:r><w:rPr><w:b/></w:rPr><w:t>Điểm trung bình: </w:t></w:r>
            <w:r><w:t>${analytics.avgScore} | </w:t></w:r>
            <w:r><w:rPr><w:b/></w:rPr><w:t>Tỉ lệ đạt: </w:t></w:r>
            <w:r><w:t>${analytics.passRate}% | </w:t></w:r>
            <w:r><w:rPr><w:b/></w:rPr><w:t>Cao nhất: </w:t></w:r>
            <w:r><w:t>${analytics.maxScore}</w:t></w:r>
        </w:p>

        <w:tbl>
            <w:tblPr>
                <w:tblW w:w="9200" w:type="dxa"/>
                <w:tblBorders>
                    <w:top w:val="single" w:sz="6" w:space="0" w:color="2B579A"/>
                    <w:bottom w:val="single" w:sz="6" w:space="0" w:color="2B579A"/>
                    <w:insideH w:val="single" w:sz="4" w:space="0" w:color="E2E8F0"/>
                    <w:insideV w:val="none"/>
                </w:tblBorders>
            </w:tblPr>
            <w:tr>
                <w:tc><w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:rPr><w:b/></w:rPr><w:t>STT</w:t></w:r></w:p></w:tc>
                <w:tc><w:p><w:r><w:rPr><w:b/></w:rPr><w:t>Họ và tên</w:t></w:r></w:p></w:tc>
                <w:tc><w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:rPr><w:b/></w:rPr><w:t>SBD</w:t></w:r></w:p></w:tc>
                <w:tc><w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:rPr><w:b/></w:rPr><w:t>Mã đề</w:t></w:r></w:p></w:tc>
                <w:tc><w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:rPr><w:b/></w:rPr><w:t>Đúng</w:t></w:r></w:p></w:tc>
                <w:tc><w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:rPr><w:b/></w:rPr><w:t>Điểm</w:t></w:r></w:p></w:tc>
                <w:tc><w:p><w:pPr><w:jc w:val="center"/></w:pPr><w:r><w:rPr><w:b/></w:rPr><w:t>Xếp loại</w:t></w:r></w:p></w:tc>
            </w:tr>
            $rowsXml
        </w:tbl>
        <w:sectPr>
            <w:pgSz w:w="11906" w:h="16838"/>
            <w:pgMar w:top="1134" w:right="1134" w:bottom="1134" w:left="1134"/>
        </w:sectPr>
    </w:body>
</w:document>""".trimIndent()
    }

    private fun escapeXml(input: String): String {
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun docxContentTypesXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
    <Default Extension="xml" ContentType="application/xml"/>
    <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
    <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>"""

    private fun docxRootRelsXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

    private fun docxDocumentRelsXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private fun docxStylesXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
    <w:docDefaults>
        <w:rPrDefault>
            <w:rPr>
                <w:rFonts w:ascii="Times New Roman" w:hAnsi="Times New Roman" w:cs="Times New Roman"/>
                <w:sz w:val="24"/>
                <w:szCs w:val="24"/>
                <w:lang w:val="vi-VN"/>
            </w:rPr>
        </w:rPrDefault>
    </w:docDefaults>
</w:styles>"""
}
