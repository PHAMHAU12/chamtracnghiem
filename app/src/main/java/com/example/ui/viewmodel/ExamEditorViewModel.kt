package com.example.ui.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiAnswerSheetGrader
import com.example.data.model.GradingCalculator
import com.example.data.repository.ExamRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExamEditorUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isScanningKey: Boolean = false,
    val title: String = "",
    val subject: String = "",
    val classGroup: String = "",
    val maxScoreText: String = "10",
    val answerKey: List<String> = List(20) { "A" },
    val examCodes: Map<String, List<String>> = emptyMap(),
    val activeCodeTab: String = "Chuẩn",
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class ExamEditorViewModel(
    private val repository: ExamRepository,
    private val aiGrader: AiAnswerSheetGrader,
    private val currentUserId: String,
    private val examId: String?
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExamEditorUiState(isLoading = examId != null))
    val uiState: StateFlow<ExamEditorUiState> = _uiState.asStateFlow()

    init {
        if (!examId.isNullOrBlank()) {
            viewModelScope.launch {
                val result = repository.getExam(currentUserId, examId)
                result.onSuccess { exam ->
                    if (exam != null) {
                        val maxScoreStr = if (exam.maxScore % 1.0 == 0.0) {
                            exam.maxScore.toInt().toString()
                        } else {
                            exam.maxScore.toString()
                        }
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                title = exam.title,
                                subject = exam.subject,
                                classGroup = exam.classGroup,
                                maxScoreText = maxScoreStr,
                                answerKey = exam.answerKey.ifEmpty { List(10) { "A" } },
                                examCodes = exam.examCodes
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(isLoading = false, errorMessage = "Không tìm thấy đề thi.")
                        }
                    }
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.localizedMessage ?: "Lỗi tải thông tin đề thi."
                        )
                    }
                }
            }
        }
    }

    fun updateTitle(value: String) {
        _uiState.update { it.copy(title = value, errorMessage = null) }
    }

    fun updateSubject(value: String) {
        _uiState.update { it.copy(subject = value) }
    }

    fun updateClassGroup(value: String) {
        _uiState.update { it.copy(classGroup = value) }
    }

    fun updateMaxScoreText(value: String) {
        _uiState.update { it.copy(maxScoreText = value, errorMessage = null) }
    }

    fun selectCodeTab(code: String) {
        _uiState.update { it.copy(activeCodeTab = code) }
    }

    fun addExamCode(code: String) {
        val cleanCode = code.trim()
        if (cleanCode.isEmpty()) return
        _uiState.update { state ->
            if (state.examCodes.containsKey(cleanCode)) {
                state.copy(activeCodeTab = cleanCode)
            } else {
                val currentBase = state.answerKey
                val newMap = state.examCodes.toMutableMap()
                newMap[cleanCode] = currentBase
                state.copy(examCodes = newMap, activeCodeTab = cleanCode, statusMessage = "Đã thêm mã đề $cleanCode")
            }
        }
    }

    fun removeExamCode(code: String) {
        _uiState.update { state ->
            val newMap = state.examCodes.toMutableMap()
            newMap.remove(code)
            val nextTab = if (state.activeCodeTab == code) "Chuẩn" else state.activeCodeTab
            state.copy(examCodes = newMap, activeCodeTab = nextTab)
        }
    }

    fun setQuestionCount(newCount: Int) {
        val clamped = newCount.coerceIn(1, 120)
        _uiState.update { state ->
            val updatedBase = resizeKeyList(state.answerKey, clamped)
            val updatedCodes = state.examCodes.mapValues { (_, keys) ->
                resizeKeyList(keys, clamped)
            }
            state.copy(answerKey = updatedBase, examCodes = updatedCodes)
        }
    }

    private fun resizeKeyList(current: List<String>, targetSize: Int): List<String> {
        return when {
            targetSize == current.size -> current
            targetSize < current.size -> current.take(targetSize)
            else -> current + List(targetSize - current.size) { "A" }
        }
    }

    fun setAnswer(index: Int, option: String) {
        if (option !in GradingCalculator.VALID_OPTIONS) return
        _uiState.update { state ->
            val activeTab = state.activeCodeTab
            if (activeTab == "Chuẩn") {
                if (index !in state.answerKey.indices) return@update state
                val mutable = state.answerKey.toMutableList()
                mutable[index] = option
                state.copy(answerKey = mutable)
            } else {
                val currentCodeKeys = state.examCodes[activeTab] ?: state.answerKey
                if (index !in currentCodeKeys.indices) return@update state
                val mutable = currentCodeKeys.toMutableList()
                mutable[index] = option
                val newMap = state.examCodes.toMutableMap()
                newMap[activeTab] = mutable
                state.copy(examCodes = newMap)
            }
        }
    }

    fun applyQuickAnswerString(rawInput: String): Int {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return 0

        // 1. Dò theo cấu trúc câu hỏi có đánh số: "1.A 2.B" hoặc "Câu 1: A" hoặc "1 - C"
        val qNumRegex = Regex("""(?:câu|c|q)?\s*(\d{1,3})[\s.:\-)\t/]+\s*([A-D])\b""", RegexOption.IGNORE_CASE)
        val numMatches = qNumRegex.findAll(trimmed).toList()
        val numAnswerMap = mutableMapOf<Int, String>()

        for (m in numMatches) {
            val qNum = m.groupValues[1].toIntOrNull() ?: continue
            val ans = m.groupValues[2].uppercase()
            if (qNum in 1..120) {
                numAnswerMap[qNum] = ans
            }
        }

        // Dò dạng viết liền: "1A 2B 3C"
        if (numAnswerMap.isEmpty()) {
            val joinedRegex = Regex("""\b(\d{1,3})([A-D])\b""", RegexOption.IGNORE_CASE)
            for (m in joinedRegex.findAll(trimmed)) {
                val qNum = m.groupValues[1].toIntOrNull() ?: continue
                val ans = m.groupValues[2].uppercase()
                if (qNum in 1..120) {
                    numAnswerMap[qNum] = ans
                }
            }
        }

        val parsed: List<String> = if (numAnswerMap.isNotEmpty()) {
            val maxQ = (numAnswerMap.keys.maxOrNull() ?: 1).coerceAtMost(120)
            (1..maxQ).map { q -> numAnswerMap[q] ?: "A" }
        } else {
            // 2. Không có số câu: Loại bỏ các từ khóa tiếng Việt chứa chữ A, B, C, D để tránh chèn đáp án sai
            val clean = trimmed.replace(
                Regex("""(?:bảng\s*đáp\s*án|đáp\s*án|mã\s*đề\s*thi|mã\s*đề|mã|đề\s*thi|đề|câu\s*hỏi|câu|phần|bài|toán|vật\s*lý|hóa\s*học|sinh\s*học|tiếng\s*anh|lịch\s*sử|địa\s*lý|gdcd|tin\s*học)""", RegexOption.IGNORE_CASE),
                " "
            )
            val letters = clean.uppercase().filter { it in listOf('A', 'B', 'C', 'D') }
            if (letters.isEmpty()) return 0
            letters.take(120).map { it.toString() }
        }

        _uiState.update { state ->
            val activeTab = state.activeCodeTab
            if (activeTab == "Chuẩn") {
                state.copy(
                    answerKey = parsed,
                    statusMessage = "Đã nạp chính xác ${parsed.size} đáp án cho mã Chuẩn."
                )
            } else {
                val newMap = state.examCodes.toMutableMap()
                newMap[activeTab] = parsed
                state.copy(
                    examCodes = newMap,
                    statusMessage = "Đã nạp chính xác ${parsed.size} đáp án cho mã đề $activeTab."
                )
            }
        }
        return parsed.size
    }

    fun scanAnswerKeyFromPhoto(bitmap: Bitmap) {
        val expectedCount = _uiState.value.answerKey.size
        viewModelScope.launch {
            _uiState.update { it.copy(isScanningKey = true, errorMessage = null, statusMessage = null) }
            val result = aiGrader.recognizeAnswerKeyPhoto(bitmap, expectedCount)
            result.onSuccess { recognized ->
                _uiState.update { state ->
                    val activeTab = state.activeCodeTab
                    val detectedCode = recognized.examCode.ifBlank { activeTab }
                    if (activeTab == "Chuẩn" && recognized.examCode.isBlank()) {
                        state.copy(
                            isScanningKey = false,
                            answerKey = recognized.answers,
                            statusMessage = recognized.note.ifBlank {
                                "AI đã điền ${recognized.answers.size} đáp án từ ảnh."
                            }
                        )
                    } else {
                        val targetTab = if (detectedCode != "Chuẩn") detectedCode else activeTab
                        val newMap = state.examCodes.toMutableMap()
                        newMap[targetTab] = recognized.answers
                        state.copy(
                            isScanningKey = false,
                            examCodes = newMap,
                            activeCodeTab = targetTab,
                            statusMessage = "AI đã điền ${recognized.answers.size} đáp án cho mã đề $targetTab."
                        )
                    }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isScanningKey = false,
                        errorMessage = err.localizedMessage ?: "Không thể nhận diện bảng đáp án."
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(statusMessage = null, errorMessage = null) }
    }

    fun saveExam(onSaved: (String) -> Unit) {
        val current = _uiState.value
        val title = current.title.trim()
        if (title.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Vui lòng nhập tên đề thi.") }
            return
        }
        val maxScore = current.maxScoreText.replace(',', '.').toDoubleOrNull()
        if (maxScore == null || maxScore <= 0.0 || maxScore > 1000.0) {
            _uiState.update { it.copy(errorMessage = "Thang điểm phải lớn hơn 0 và tối đa 1000.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            if (examId.isNullOrBlank()) {
                val result = repository.createExam(
                    userId = currentUserId,
                    title = title,
                    subject = current.subject.trim(),
                    maxScore = maxScore,
                    answerKey = current.answerKey,
                    classGroup = current.classGroup.trim(),
                    examCodes = current.examCodes
                )
                result.onSuccess { newId ->
                    _uiState.update { it.copy(isSaving = false) }
                    onSaved(newId)
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = err.localizedMessage ?: "Lưu đề thi thất bại."
                        )
                    }
                }
            } else {
                val result = repository.updateExam(
                    userId = currentUserId,
                    examId = examId,
                    title = title,
                    subject = current.subject.trim(),
                    maxScore = maxScore,
                    answerKey = current.answerKey,
                    classGroup = current.classGroup.trim(),
                    examCodes = current.examCodes
                )
                result.onSuccess {
                    _uiState.update { it.copy(isSaving = false) }
                    onSaved(examId)
                }.onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = err.localizedMessage ?: "Cập nhật đề thi thất bại."
                        )
                    }
                }
            }
        }
    }
}
