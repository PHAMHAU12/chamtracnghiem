package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlin.math.roundToInt

@IgnoreExtraProperties
data class Exam(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val subject: String = "",
    val classGroup: String = "",
    val totalQuestions: Long = 10L,
    val maxScore: Double = 10.0,
    val answerKey: List<String> = emptyList(),
    val examCodes: Map<String, List<String>> = emptyMap(),
    val submissionCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun getAnswerKeyForCode(code: String?): List<String> {
        val trimmed = code?.trim().orEmpty()
        if (trimmed.isNotEmpty() && examCodes.containsKey(trimmed)) {
            val list = examCodes[trimmed].orEmpty()
            if (list.isNotEmpty()) return list
        }
        return answerKey
    }

    fun getAllExamCodes(): List<String> {
        val keys = examCodes.keys.toList()
        return if (keys.isEmpty()) listOf("Chuẩn") else keys.sorted()
    }
}

@IgnoreExtraProperties
data class Submission(
    @DocumentId val id: String = "",
    val userId: String = "",
    val examId: String = "",
    val studentName: String = "",
    val studentCode: String = "",
    val examCode: String = "",
    val imagePath: String = "",
    val feedbackNote: String = "",
    val studentAnswers: List<String> = emptyList(),
    val correctCount: Long = 0L,
    val wrongCount: Long = 0L,
    val blankCount: Long = 0L,
    val score: Double = 0.0,
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class QuestionStatus {
    CORRECT,
    WRONG,
    BLANK
}

data class QuestionComparison(
    val questionNumber: Int,
    val correctAnswer: String,
    val studentAnswer: String,
    val status: QuestionStatus
)

data class GradeSummary(
    val correctCount: Int,
    val wrongCount: Int,
    val blankCount: Int,
    val score: Double,
    val comparisons: List<QuestionComparison>
)

data class ScoreBucket(
    val rangeLabel: String,
    val count: Int,
    val percentage: Double
)

data class QuestionItemStat(
    val questionNumber: Int,
    val countA: Int = 0,
    val countB: Int = 0,
    val countC: Int = 0,
    val countD: Int = 0,
    val countBlank: Int = 0,
    val countMultiple: Int = 0,
    val correctAnswer: String = "",
    val correctPercentage: Double = 0.0,
    val isHardQuestion: Boolean = false // < 50% đúng
)

data class ExamAnalytics(
    val totalSubmissions: Int,
    val avgScore: Double,
    val maxScore: Double,
    val minScore: Double,
    val passRate: Double,
    val scoreDistribution: List<ScoreBucket>,
    val itemStats: List<QuestionItemStat>,
    val hardestQuestions: List<QuestionItemStat>
)

object GradingCalculator {
    val VALID_OPTIONS = listOf("A", "B", "C", "D")

    fun normalizeChoice(raw: String?): String {
        val upper = raw?.trim()?.uppercase().orEmpty()
        return when (upper) {
            "A", "B", "C", "D", "MULTIPLE" -> upper
            else -> "BLANK"
        }
    }

    fun grade(
        answerKey: List<String>,
        studentAnswers: List<String>,
        maxScore: Double
    ): GradeSummary {
        var correct = 0
        var wrong = 0
        var blank = 0
        val comparisons = answerKey.mapIndexed { index, rawKey ->
            val key = normalizeChoice(rawKey)
            val student = normalizeChoice(studentAnswers.getOrNull(index))
            val status = when {
                student == "BLANK" -> {
                    blank++
                    QuestionStatus.BLANK
                }
                student == key -> {
                    correct++
                    QuestionStatus.CORRECT
                }
                else -> {
                    wrong++
                    QuestionStatus.WRONG
                }
            }
            QuestionComparison(
                questionNumber = index + 1,
                correctAnswer = key,
                studentAnswer = student,
                status = status
            )
        }
        val total = answerKey.size.coerceAtLeast(1)
        val rawScore = (correct.toDouble() / total.toDouble()) * maxScore
        val roundedScore = (rawScore * 100.0).roundToInt() / 100.0
        return GradeSummary(
            correctCount = correct,
            wrongCount = wrong,
            blankCount = blank,
            score = roundedScore,
            comparisons = comparisons
        )
    }

    fun classifyRank(score: Double, maxScore: Double): String {
        if (maxScore <= 0.0) return "Trung bình"
        val ratio = score / maxScore
        return when {
            ratio >= 0.9 -> "Xuất sắc"
            ratio >= 0.8 -> "Giỏi"
            ratio >= 0.65 -> "Khá"
            ratio >= 0.5 -> "Trung bình"
            else -> "Yếu"
        }
    }

    fun calculateAnalytics(exam: Exam, submissions: List<Submission>): ExamAnalytics {
        val total = submissions.size
        if (total == 0) {
            return ExamAnalytics(
                totalSubmissions = 0,
                avgScore = 0.0,
                maxScore = 0.0,
                minScore = 0.0,
                passRate = 0.0,
                scoreDistribution = defaultDistribution(),
                itemStats = emptyList(),
                hardestQuestions = emptyList()
            )
        }

        val scores = submissions.map { it.score }
        val avg = (scores.average() * 100.0).roundToInt() / 100.0
        val max = scores.maxOrNull() ?: 0.0
        val min = scores.minOrNull() ?: 0.0
        val passThreshold = exam.maxScore * 0.5
        val passCount = submissions.count { it.score >= passThreshold }
        val passRate = (passCount.toDouble() / total.toDouble() * 100.0).roundToInt().toDouble()

        // Phổ điểm chuẩn theo thang điểm 10 (hoặc quy đổi tỉ lệ)
        val buckets = listOf(
            "0 - <2" to { s: Double -> s < exam.maxScore * 0.2 },
            "2 - <4" to { s: Double -> s >= exam.maxScore * 0.2 && s < exam.maxScore * 0.4 },
            "4 - <5" to { s: Double -> s >= exam.maxScore * 0.4 && s < exam.maxScore * 0.5 },
            "5 - <6.5" to { s: Double -> s >= exam.maxScore * 0.5 && s < exam.maxScore * 0.65 },
            "6.5 - <8" to { s: Double -> s >= exam.maxScore * 0.65 && s < exam.maxScore * 0.8 },
            "8 - <9" to { s: Double -> s >= exam.maxScore * 0.8 && s < exam.maxScore * 0.9 },
            "9 - 10" to { s: Double -> s >= exam.maxScore * 0.9 }
        )

        val distribution = buckets.map { (label, predicate) ->
            val count = submissions.count { predicate(it.score) }
            val percent = (count.toDouble() / total.toDouble() * 100.0).roundToInt().toDouble()
            ScoreBucket(rangeLabel = label, count = count, percentage = percent)
        }

        // Thống kê từng câu hỏi (Item Analysis)
        val qCount = exam.totalQuestions.toInt()
        val itemStats = (0 until qCount).map { qIdx ->
            var a = 0
            var b = 0
            var c = 0
            var d = 0
            var blank = 0
            var multiple = 0
            var correctCount = 0

            submissions.forEach { sub ->
                val ans = sub.studentAnswers.getOrNull(qIdx)?.trim()?.uppercase().orEmpty()
                when (ans) {
                    "A" -> a++
                    "B" -> b++
                    "C" -> c++
                    "D" -> d++
                    "MULTIPLE" -> multiple++
                    else -> blank++
                }

                // Kiểm tra đúng với mã đề của học sinh
                val key = exam.getAnswerKeyForCode(sub.examCode).getOrNull(qIdx)
                if (key != null && ans == key) {
                    correctCount++
                }
            }

            val correctPct = if (total > 0) {
                ((correctCount.toDouble() / total.toDouble()) * 100.0).roundToInt().toDouble()
            } else 0.0

            val defaultKey = exam.answerKey.getOrNull(qIdx) ?: "A"
            QuestionItemStat(
                questionNumber = qIdx + 1,
                countA = a,
                countB = b,
                countC = c,
                countD = d,
                countBlank = blank,
                countMultiple = multiple,
                correctAnswer = defaultKey,
                correctPercentage = correctPct,
                isHardQuestion = correctPct < 50.0
            )
        }

        val hardest = itemStats.filter { it.isHardQuestion }
            .sortedBy { it.correctPercentage }
            .take(5)

        return ExamAnalytics(
            totalSubmissions = total,
            avgScore = avg,
            maxScore = max,
            minScore = min,
            passRate = passRate,
            scoreDistribution = distribution,
            itemStats = itemStats,
            hardestQuestions = hardest
        )
    }

    private fun defaultDistribution() = listOf(
        ScoreBucket("0 - <2", 0, 0.0),
        ScoreBucket("2 - <4", 0, 0.0),
        ScoreBucket("4 - <5", 0, 0.0),
        ScoreBucket("5 - <6.5", 0, 0.0),
        ScoreBucket("6.5 - <8", 0, 0.0),
        ScoreBucket("8 - <9", 0, 0.0),
        ScoreBucket("9 - 10", 0, 0.0)
    )
}
