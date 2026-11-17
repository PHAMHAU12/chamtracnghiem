package com.example.data.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.Exam
import com.example.data.model.GradingCalculator
import com.example.data.model.Submission
import java.io.File
import java.io.OutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object PptxExporter {

    fun exportReviewSlides(
        context: Context,
        exam: Exam,
        submissions: List<Submission>
    ): Uri {
        val dir = File(context.cacheDir, "pptx_exports").apply { mkdirs() }
        val safeTitle = exam.title.replace(Regex("[^\\p{L}\\p{N}_\\- ]"), "").trim().replace(Regex("\\s+"), "_").ifEmpty { "ChuaDe" }
        val outFile = File(dir, "SlideChuaDe_${safeTitle}.pptx")
        outFile.outputStream().use { stream ->
            writePresentation(stream, exam, submissions)
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
    }

    private fun writePresentation(stream: OutputStream, exam: Exam, submissions: List<Submission>) {
        val analytics = GradingCalculator.calculateAnalytics(exam, submissions)
        val zip = ZipOutputStream(stream)

        fun addEntry(name: String, content: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        addEntry("[Content_Types].xml", pptxContentTypesXml())
        addEntry("_rels/.rels", pptxRootRelsXml())
        addEntry("ppt/presentation.xml", pptxPresentationXml())
        addEntry("ppt/_rels/presentation.xml.rels", pptxPresentationRelsXml())
        addEntry("ppt/slides/slide1.xml", buildSlide1CoverXml(exam, analytics))
        addEntry("ppt/slides/_rels/slide1.xml.rels", pptxSlideRelsXml())
        addEntry("ppt/slides/slide2.xml", buildSlide2DistributionXml(exam, analytics))
        addEntry("ppt/slides/_rels/slide2.xml.rels", pptxSlideRelsXml())
        addEntry("ppt/slides/slide3.xml", buildSlide3HardestQuestionsXml(exam, analytics))
        addEntry("ppt/slides/_rels/slide3.xml.rels", pptxSlideRelsXml())

        zip.finish()
        zip.flush()
    }

    private fun buildSlide1CoverXml(exam: Exam, analytics: com.example.data.model.ExamAnalytics): String {
        val classText = if (exam.classGroup.isNotBlank()) " | Lớp: " + escapeXml(exam.classGroup) else ""
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
       xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
    <p:cSld>
        <p:spTree>
            <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
            <p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>

            <!-- Tiêu đề lớn -->
            <p:sp>
                <p:nvSpPr><p:cNvPr id="2" name="Title"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
                <p:spPr><a:xfrm><a:off x="762000" y="1524000"/><a:ext cx="7620000" cy="1828800"/></a:xfrm></p:spPr>
                <p:txBody>
                    <a:bodyPr/><a:lstStyle/>
                    <a:p>
                        <a:pPr algn="ctr"/>
                        <a:r><a:rPr lang="vi-VN" sz="4400" b="1"><a:solidFill><a:srgbClr val="1A365D"/></a:solidFill></a:rPr>
                            <a:t>PHÂN TÍCH &amp; CHỮA ĐỀ KIỂM TRA</a:t>
                        </a:r>
                    </a:p>
                    <a:p>
                        <a:pPr algn="ctr"/>
                        <a:r><a:rPr lang="vi-VN" sz="2800"><a:solidFill><a:srgbClr val="2B579A"/></a:solidFill></a:rPr>
                            <a:t>${escapeXml(exam.title)}$classText</a:t>
                        </a:r>
                    </a:p>
                </p:txBody>
            </p:sp>

            <!-- Thẻ thống kê cơ bản -->
            <p:sp>
                <p:nvSpPr><p:cNvPr id="3" name="Subtitle"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
                <p:spPr><a:xfrm><a:off x="1000000" y="3800000"/><a:ext cx="7144000" cy="1600000"/></a:xfrm></p:spPr>
                <p:txBody>
                    <a:bodyPr/><a:lstStyle/>
                    <a:p><a:pPr algn="ctr"/>
                        <a:r><a:rPr lang="vi-VN" sz="2200" b="1"><a:solidFill><a:srgbClr val="2D3748"/></a:solidFill></a:rPr>
                            <a:t>Điểm TB: ${analytics.avgScore} | Tỉ lệ đạt: ${analytics.passRate}% | Đã chấm: ${analytics.totalSubmissions} bài</a:t>
                        </a:r>
                    </a:p>
                    <a:p><a:pPr algn="ctr"/>
                        <a:r><a:rPr lang="vi-VN" sz="1800" i="1"><a:solidFill><a:srgbClr val="718096"/></a:solidFill></a:rPr>
                            <a:t>Hệ thống tự động chấm &amp; tổng hợp bởi Chấm Thi AI</a:t>
                        </a:r>
                    </a:p>
                </p:txBody>
            </p:sp>
        </p:spTree>
    </p:cSld>
</p:sld>"""
    }

    private fun buildSlide2DistributionXml(exam: Exam, analytics: com.example.data.model.ExamAnalytics): String {
        val distText = StringBuilder()
        analytics.scoreDistribution.forEach { b ->
            distText.append("• Dải điểm ${b.rangeLabel}:  ${b.count} học sinh (${b.percentage}%)\n")
        }

        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
       xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
    <p:cSld>
        <p:spTree>
            <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
            <p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>

            <p:sp>
                <p:nvSpPr><p:cNvPr id="2" name="Title"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
                <p:spPr><a:xfrm><a:off x="762000" y="609600"/><a:ext cx="7620000" cy="914400"/></a:xfrm></p:spPr>
                <p:txBody>
                    <a:bodyPr/><a:lstStyle/>
                    <a:p>
                        <a:r><a:rPr lang="vi-VN" sz="3600" b="1"><a:solidFill><a:srgbClr val="1A365D"/></a:solidFill></a:rPr>
                            <a:t>PHỔ ĐIỂM &amp; PHÂN BỐ KẾT QUẢ LỚP HỌC</a:t>
                        </a:r>
                    </a:p>
                </p:txBody>
            </p:sp>

            <p:sp>
                <p:nvSpPr><p:cNvPr id="3" name="Content"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
                <p:spPr><a:xfrm><a:off x="762000" y="1600000"/><a:ext cx="7620000" cy="4500000"/></a:xfrm></p:spPr>
                <p:txBody>
                    <a:bodyPr/><a:lstStyle/>
                    ${buildParagraphsXml(distText.toString())}
                </p:txBody>
            </p:sp>
        </p:spTree>
    </p:cSld>
</p:sld>"""
    }

    private fun buildSlide3HardestQuestionsXml(exam: Exam, analytics: com.example.data.model.ExamAnalytics): String {
        val hardest = analytics.hardestQuestions
        val contentText = StringBuilder()
        if (hardest.isEmpty()) {
            contentText.append("Chúc mừng lớp! Toàn bộ câu hỏi đều đạt tỉ lệ đúng trên 50%.\n")
        } else {
            contentText.append("DANH SÁCH CÁC CÂU HỌC SINH SAI NHIỀU NHẤT CẦN ÔN TẬP:\n\n")
            hardest.forEach { q ->
                contentText.append("⚠️ CÂU ${q.questionNumber}: Tỉ lệ làm đúng chỉ ${q.correctPercentage}% (Đáp án đúng: ${q.correctAnswer})\n")
                contentText.append("    -> Phân bố chọn: A (${q.countA}) | B (${q.countB}) | C (${q.countC}) | D (${q.countD}) | Bỏ trống (${q.countBlank})\n\n")
            }
        }

        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
       xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
    <p:cSld>
        <p:spTree>
            <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
            <p:grpSpPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="0" cy="0"/><a:chOff x="0" y="0"/><a:chExt cx="0" cy="0"/></a:xfrm></p:grpSpPr>

            <p:sp>
                <p:nvSpPr><p:cNvPr id="2" name="Title"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
                <p:spPr><a:xfrm><a:off x="762000" y="609600"/><a:ext cx="7620000" cy="914400"/></a:xfrm></p:spPr>
                <p:txBody>
                    <a:bodyPr/><a:lstStyle/>
                    <a:p>
                        <a:r><a:rPr lang="vi-VN" sz="3600" b="1"><a:solidFill><a:srgbClr val="C53030"/></a:solidFill></a:rPr>
                            <a:t>CÁC CÂU CẦN CHỮA BÀI TRÊN LỚP (MA TRẬN ĐỘ KHÓ)</a:t>
                        </a:r>
                    </a:p>
                </p:txBody>
            </p:sp>

            <p:sp>
                <p:nvSpPr><p:cNvPr id="3" name="Content"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>
                <p:spPr><a:xfrm><a:off x="762000" y="1600000"/><a:ext cx="7620000" cy="4800000"/></a:xfrm></p:spPr>
                <p:txBody>
                    <a:bodyPr/><a:lstStyle/>
                    ${buildParagraphsXml(contentText.toString())}
                </p:txBody>
            </p:sp>
        </p:spTree>
    </p:cSld>
</p:sld>"""
    }

    private fun buildParagraphsXml(text: String): String {
        val lines = text.split("\n")
        val sb = StringBuilder()
        lines.forEach { line ->
            if (line.isNotBlank()) {
                val isWarning = line.contains("⚠️")
                val color = if (isWarning) "C53030" else "2D3748"
                val bold = if (isWarning) "1" else "0"
                sb.append("""
                    <a:p>
                        <a:r><a:rPr lang="vi-VN" sz="2000" b="$bold"><a:solidFill><a:srgbClr val="$color"/></a:solidFill></a:rPr>
                            <a:t>${escapeXml(line)}</a:t>
                        </a:r>
                    </a:p>
                """.trimIndent())
            } else {
                sb.append("<a:p><a:r><a:rPr sz=\"1200\"/><a:t></a:t></a:r></a:p>")
            }
        }
        return sb.toString()
    }

    private fun escapeXml(input: String): String {
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun pptxContentTypesXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
    <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
    <Default Extension="xml" ContentType="application/xml"/>
    <Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>
    <Override PartName="/ppt/slides/slide1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>
    <Override PartName="/ppt/slides/slide2.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>
    <Override PartName="/ppt/slides/slide3.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>
</Types>"""

    private fun pptxRootRelsXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
</Relationships>"""

    private fun pptxPresentationXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main"
                xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
    <p:sldIdLst>
        <p:sldId id="256" r:id="rId1"/>
        <p:sldId id="257" r:id="rId2"/>
        <p:sldId id="258" r:id="rId3"/>
    </p:sldIdLst>
    <p:sldSz cx="9144000" cy="6858000" type="screen4x3"/>
</p:presentation>"""

    private fun pptxPresentationRelsXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide1.xml"/>
    <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide2.xml"/>
    <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide3.xml"/>
</Relationships>"""

    private fun pptxSlideRelsXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"/>"""
}
