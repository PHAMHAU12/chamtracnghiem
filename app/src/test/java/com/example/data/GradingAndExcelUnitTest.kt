package com.example.data

import com.example.data.export.ExcelExporter
import com.example.data.model.Exam
import com.example.data.model.GradingCalculator
import com.example.data.model.QuestionStatus
import com.example.data.model.Submission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class GradingAndExcelUnitTest {

    @Test
    fun grade_computesCorrectWrongBlankAndScoreAccurately() {
        val key = listOf("A", "B", "C", "D", "A")
        val student = listOf("A", "C", "BLANK", "D", "a")
        val summary = GradingCalculator.grade(key, student, maxScore = 10.0)

        assertEquals(3, summary.correctCount)
        assertEquals(1, summary.wrongCount)
        assertEquals(1, summary.blankCount)
        assertEquals(6.0, summary.score, 0.001)
        assertEquals(QuestionStatus.CORRECT, summary.comparisons[0].status)
        assertEquals(QuestionStatus.WRONG, summary.comparisons[1].status)
        assertEquals(QuestionStatus.BLANK, summary.comparisons[2].status)
        assertEquals(QuestionStatus.CORRECT, summary.comparisons[4].status)
    }

    @Test
    fun excelExporter_producesValidMultiSheetXlsxArchive() {
        val exam = Exam(
            id = "exam_1",
            title = "Kiểm tra Toán 10",
            subject = "Toán học",
            totalQuestions = 4,
            maxScore = 10.0,
            answerKey = listOf("A", "B", "C", "D"),
            submissionCount = 1
        )
        val submissions = listOf(
            Submission(
                id = "sub_1",
                examId = "exam_1",
                studentName = "Lê Hoàng Nam & Friends",
                studentCode = "10A1",
                studentAnswers = listOf("A", "B", "D", "D"),
                correctCount = 3,
                wrongCount = 1,
                blankCount = 0,
                score = 7.5,
                notes = "Rõ nét"
            )
        )

        val bytes = ExcelExporter.buildWorkbookBytes(exam, submissions)
        assertTrue(bytes.isNotEmpty())

        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries[entry.name] = zis.readBytes().toString(Charsets.UTF_8)
                entry = zis.nextEntry
            }
        }

        assertTrue(entries.containsKey("[Content_Types].xml"))
        assertTrue(entries.containsKey("xl/workbook.xml"))
        assertTrue(entries.containsKey("xl/worksheets/sheet1.xml"))
        assertTrue(entries.containsKey("xl/worksheets/sheet2.xml"))

        val sheet1 = entries.getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet1.contains("Lê Hoàng Nam &amp; Friends"))
        assertTrue(sheet1.contains("10A1"))
        assertTrue(sheet1.contains("7.5"))

        val sheet2 = entries.getValue("xl/worksheets/sheet2.xml")
        assertTrue(sheet2.contains("ĐÁP ÁN CHUẨN"))
    }
}
