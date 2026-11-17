package com.example.data.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.Exam
import com.example.data.model.Submission
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MoodleExporter {

    /**
     * Xuất file CSV định dạng Moodle Gradebook / Google Classroom import
     */
    fun exportMoodleCsv(
        context: Context,
        exam: Exam,
        submissions: List<Submission>
    ): Uri {
        val dir = File(context.cacheDir, "moodle_exports").apply { mkdirs() }
        val safeTitle = exam.title.replace(Regex("[^\\p{L}\\p{N}_\\- ]"), "").trim().replace(Regex("\\s+"), "_").ifEmpty { "MoodleGrade" }
        val outFile = File(dir, "Moodle_Grades_${safeTitle}.csv")

        val sb = StringBuilder()
        // Header chuẩn của Moodle Grade Import
        sb.append("Identifier,Student Name,ID Number,Grade,Feedback,Exam Code\n")

        submissions.forEach { sub ->
            val name = sub.studentName.replace("\"", "\"\"")
            val idNum = sub.studentCode.replace("\"", "\"\"")
            val grade = String.format(Locale.US, "%.2f", sub.score)
            val feedback = (if (sub.feedbackNote.isNotBlank()) sub.feedbackNote else "Đúng ${sub.correctCount}/${exam.totalQuestions} câu").replace("\"", "\"\"")
            val examCode = sub.examCode.replace("\"", "\"\"")

            sb.append("\"${sub.id}\",\"$name\",\"$idNum\",$grade,\"$feedback\",\"$examCode\"\n")
        }

        outFile.writeText(sb.toString(), Charsets.UTF_8)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
    }

    /**
     * Xuất file XML định dạng Moodle Gradebook XML Import chuẩn
     */
    fun exportMoodleXml(
        context: Context,
        exam: Exam,
        submissions: List<Submission>
    ): Uri {
        val dir = File(context.cacheDir, "moodle_exports").apply { mkdirs() }
        val safeTitle = exam.title.replace(Regex("[^\\p{L}\\p{N}_\\- ]"), "").trim().replace(Regex("\\s+"), "_").ifEmpty { "MoodleGrade" }
        val outFile = File(dir, "Moodle_Grades_${safeTitle}.xml")

        val timeStamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>
<results exported="$timeStamp">
    <gradeitem name="${escapeXml(exam.title)}" maxgrade="${exam.maxScore}">
""".trimIndent())

        submissions.forEach { sub ->
            sb.append("""
        <result>
            <student>${escapeXml(sub.studentName)}</student>
            <idnumber>${escapeXml(sub.studentCode)}</idnumber>
            <score>${String.format(Locale.US, "%.2f", sub.score)}</score>
            <feedback>${escapeXml(sub.feedbackNote.ifBlank { "Chấm điểm tự động qua Chấm Thi AI" })}</feedback>
        </result>
""".trimIndent())
        }

        sb.append("""
    </gradeitem>
</results>""".trimIndent())

        outFile.writeText(sb.toString(), Charsets.UTF_8)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
    }

    private fun escapeXml(input: String): String {
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
