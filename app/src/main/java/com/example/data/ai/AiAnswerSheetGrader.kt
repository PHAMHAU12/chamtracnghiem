package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.data.model.ApiKeyPreferences
import com.example.data.model.GradingCalculator
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max

data class RecognizedSheetResult(
    val studentName: String,
    val studentCode: String,
    val examCode: String,
    val answers: List<String>,
    val confidenceNote: String,
    val feedbackNote: String
)

data class RecognizedKeyResult(
    val examCode: String,
    val answers: List<String>,
    val note: String
)

class AiAnswerSheetGrader(private val appContext: Context? = null) {

    private val sheetSchema = Schema.obj(
        properties = mapOf(
            "studentName" to Schema.string(
                description = "Họ và tên học sinh ghi trên phiếu trả lời (nếu không thấy để chuỗi rỗng)"
            ),
            "studentCode" to Schema.string(
                description = "Số báo danh hoặc mã học sinh trên phiếu (nếu không thấy để chuỗi rỗng)"
            ),
            "examCode" to Schema.string(
                description = "Mã đề thi học sinh tô hoặc ghi trên phiếu (ví dụ: 101, 102, A, B...). Nếu không có để chuỗi rỗng"
            ),
            "confidenceNote" to Schema.string(
                description = "Ghi chú ngắn tiếng Việt về chất lượng ảnh hoặc các câu có dấu tô mờ/tô 2 đáp án"
            ),
            "feedbackNote" to Schema.string(
                description = "Nhận xét sư phạm ngắn gọn bằng tiếng Việt về bài làm của học sinh"
            ),
            "answers" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "questionNumber" to Schema.integer(
                            description = "Số thứ tự câu hỏi (1-based)"
                        ),
                        "selectedOption" to Schema.enumeration(
                            values = listOf("A", "B", "C", "D", "BLANK", "MULTIPLE"),
                            description = "Đáp án được tô hoặc khoanh của học sinh"
                        )
                    )
                ),
                description = "Danh sách đáp án nhận diện được theo thứ tự từng câu"
            )
        ),
        optionalProperties = listOf("studentName", "studentCode", "examCode", "confidenceNote", "feedbackNote")
    )

    private val keySchema = Schema.obj(
        properties = mapOf(
            "examCode" to Schema.string(
                description = "Mã đề thi nếu có ghi trên bảng đáp án (ví dụ 101, 102...)"
            ),
            "note" to Schema.string(
                description = "Ghi chú ngắn tiếng Việt về bảng đáp án vừa đọc"
            ),
            "answers" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "questionNumber" to Schema.integer(description = "Số thứ tự câu hỏi (1-based)"),
                        "selectedOption" to Schema.enumeration(
                            values = listOf("A", "B", "C", "D"),
                            description = "Đáp án đúng của câu hỏi"
                        )
                    )
                ),
                description = "Danh sách đáp án đúng theo số thứ tự câu"
            )
        ),
        optionalProperties = listOf("examCode", "note")
    )

    private val omrGrader = com.example.data.omr.OmrAnswerSheetGrader(appContext)

    suspend fun recognizeStudentSheet(
        bitmap: Bitmap,
        totalQuestions: Int,
        knownExamCodes: List<String> = emptyList()
    ): Result<RecognizedSheetResult> {
        // Ưu tiên chạy OMR xử lý ảnh thuần túy bằng Computer Vision (Không dùng AI, 100% Offline, siêu tốc)
        return omrGrader.recognizeStudentSheet(
            bitmap = bitmap,
            totalQuestions = totalQuestions,
            knownExamCodes = knownExamCodes
        )
    }

    suspend fun recognizeAnswerKeyPhoto(
        bitmap: Bitmap,
        totalQuestions: Int
    ): Result<RecognizedKeyResult> {
        val omrResult = omrGrader.recognizeAnswerKeyPhoto(
            bitmap = bitmap,
            totalQuestions = totalQuestions
        )
        if (omrResult.isSuccess) {
            val keyRes = omrResult.getOrNull()
            if (keyRes != null && keyRes.answers.isNotEmpty() && keyRes.answers.any { it != "A" }) {
                return omrResult
            }
        }

        val scaledBitmap = scaleDownIfNeeded(bitmap, 1200)
        val promptText = buildString {
            appendLine("Hãy đọc bảng đáp án chuẩn của đề thi trắc nghiệm từ hình ảnh này.")
            appendLine("Đề thi có $totalQuestions câu hỏi (từ câu 1 đến câu $totalQuestions).")
            appendLine("Trích xuất examCode (nếu có ghi trên đề/bảng) và danh sách đáp án A, B, C, D cho từng câu từ 1 đến $totalQuestions.")
            appendLine("Nếu thiếu câu nào hoặc bảng mờ, hãy suy luận đáp án hợp lý nhất và giải thích trong note.")
        }

        val modelsToTry = getFallbackModels()
        var lastError: Exception? = null

        for (modelName in modelsToTry) {
            try {
                Log.d(TAG, "Attempting answer key recognition with model: $modelName")
                val jsonString = callGeminiVision(scaledBitmap, promptText, modelName, isStudentSheet = false)
                val parsed = parseAnswerKeyJson(jsonString, totalQuestions)
                return Result.success(parsed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Model $modelName failed for answer key: ${e.message}")
                lastError = e
            }
        }

        return Result.failure(lastError ?: Exception("Không thể đọc bảng đáp án từ ảnh."))
    }

    private suspend fun callGeminiVision(
        bitmap: Bitmap,
        promptText: String,
        modelName: String,
        isStudentSheet: Boolean
    ): String = withContext(Dispatchers.IO) {
        val customKey = appContext?.let { ApiKeyPreferences.getCustomApiKey(it) }?.trim().orEmpty()

        if (customKey.isNotEmpty()) {
            // Sử dụng Google Gemini REST API trực tiếp bằng API Key của người dùng
            callGeminiRestApi(bitmap, promptText, modelName, customKey)
        } else {
            // Sử dụng Firebase AI SDK với App Check & Google Cloud Project tích hợp
            callFirebaseAiSdk(bitmap, promptText, modelName, isStudentSheet)
        }
    }

    private suspend fun callFirebaseAiSdk(
        bitmap: Bitmap,
        promptText: String,
        modelName: String,
        isStudentSheet: Boolean
    ): String {
        val schema = if (isStudentSheet) sheetSchema else keySchema
        val model = Firebase.ai.generativeModel(
            modelName = modelName,
            generationConfig = generationConfig {
                temperature = 0.1f
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )

        val response = withTimeout(60_000L) {
            model.generateContent(
                content {
                    image(bitmap)
                    text(promptText)
                }
            )
        }
        return response.text ?: throw IllegalStateException("AI không trả về nội dung dữ liệu.")
    }

    private fun callGeminiRestApi(
        bitmap: Bitmap,
        promptText: String,
        modelName: String,
        apiKey: String
    ): String {
        val base64Image = bitmapToBase64(bitmap)
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connectTimeout = 45_000
            readTimeout = 60_000
            doOutput = true
        }

        val requestBody = JSONObject().apply {
            val parts = JSONArray().apply {
                put(JSONObject().apply {
                    put("inline_data", JSONObject().apply {
                        put("mime_type", "image/jpeg")
                        put("data", base64Image)
                    })
                })
                put(JSONObject().apply {
                    put("text", promptText + "\n\nQuan trọng: Hãy trả về phản hồi dưới dạng JSON thuần túy (không bọc trong markdown codeblock ```json).")
                })
            }
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", parts)
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.1)
                put("response_mime_type", "application/json")
            })
        }

        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(requestBody.toString())
            writer.flush()
        }

        val responseCode = conn.responseCode
        if (responseCode !in 200..299) {
            val errorStream = conn.errorStream ?: conn.inputStream
            val errorText = BufferedReader(InputStreamReader(errorStream, Charsets.UTF_8)).use { it.readText() }
            throw Exception("Lỗi API Gemini ($responseCode): $errorText")
        }

        val responseText = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
        val rootJson = JSONObject(responseText)
        val candidates = rootJson.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            throw Exception("Gemini không trả về kết quả.")
        }
        val content = candidates.getJSONObject(0).optJSONObject("content")
        val parts = content?.optJSONArray("parts")
        if (parts == null || parts.length() == 0) {
            throw Exception("Không tìm thấy dữ liệu phản hồi.")
        }
        return parts.getJSONObject(0).optString("text", "")
    }

    private fun parseStudentSheetJson(rawText: String, totalQuestions: Int): RecognizedSheetResult {
        val cleanJson = cleanJsonString(rawText)
        val json = JSONObject(cleanJson)
        val studentName = json.optString("studentName", "").trim()
        val studentCode = json.optString("studentCode", "").trim()
        val examCode = json.optString("examCode", "").trim()
        val confidenceNote = json.optString("confidenceNote", "").trim()
        val feedbackNote = json.optString("feedbackNote", "").trim()

        val mapped = MutableList(totalQuestions) { "BLANK" }
        val answersArray = json.optJSONArray("answers")
        if (answersArray != null) {
            for (i in 0 until answersArray.length()) {
                val item = answersArray.optJSONObject(i) ?: continue
                val qNum = item.optInt("questionNumber", i + 1)
                val idx = qNum - 1
                if (idx in 0 until totalQuestions) {
                    mapped[idx] = GradingCalculator.normalizeChoice(
                        item.optString("selectedOption", "BLANK")
                    )
                }
            }
        }

        return RecognizedSheetResult(
            studentName = studentName,
            studentCode = studentCode,
            examCode = examCode,
            answers = mapped,
            confidenceNote = confidenceNote,
            feedbackNote = feedbackNote
        )
    }

    private fun parseAnswerKeyJson(rawText: String, totalQuestions: Int): RecognizedKeyResult {
        val cleanJson = cleanJsonString(rawText)
        val json = JSONObject(cleanJson)
        val examCode = json.optString("examCode", "").trim()
        val note = json.optString("note", "").trim()
        val mapped = MutableList(totalQuestions) { "A" }
        val answersArray = json.optJSONArray("answers")
        if (answersArray != null) {
            for (i in 0 until answersArray.length()) {
                val item = answersArray.optJSONObject(i) ?: continue
                val qNum = item.optInt("questionNumber", i + 1)
                val idx = qNum - 1
                val opt = item.optString("selectedOption", "A").trim().uppercase()
                if (idx in 0 until totalQuestions && opt in GradingCalculator.VALID_OPTIONS) {
                    mapped[idx] = opt
                }
            }
        }
        return RecognizedKeyResult(examCode = examCode, answers = mapped, note = note)
    }

    private fun cleanJsonString(raw: String): String {
        var str = raw.trim()
        if (str.startsWith("```json")) {
            str = str.removePrefix("```json")
        }
        if (str.startsWith("```")) {
            str = str.removePrefix("```")
        }
        if (str.endsWith("```")) {
            str = str.removeSuffix("```")
        }
        str = str.trim()
        val firstBrace = str.indexOf('{')
        val lastBrace = str.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            str = str.substring(firstBrace, lastBrace + 1)
        }
        return str.trim()
    }

    private fun getFallbackModels(): List<String> {
        return if (appContext != null) {
            ApiKeyPreferences.getModelFallbackChain(appContext)
        } else {
            listOf("gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-flash", "gemini-3.8-flash", "gemini-2.5-pro")
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        val bytes = stream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val longest = max(width, height)
        if (longest <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / longest.toFloat()
        val newWidth = (width * scale).toInt().coerceAtLeast(1)
        val newHeight = (height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private companion object {
        const val TAG = "AiAnswerSheetGrader"
    }
}
