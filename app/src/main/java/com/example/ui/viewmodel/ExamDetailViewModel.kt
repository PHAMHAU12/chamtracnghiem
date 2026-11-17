package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Exam
import com.example.data.model.GradingCalculator
import com.example.data.model.Submission
import com.example.data.repository.ExamRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<out T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class ExamDetailViewModel(
    private val repository: ExamRepository,
    private val currentUserId: String,
    private val examId: String
) : ViewModel() {

    val examState: StateFlow<UiState<Exam?>> = repository.observeExam(currentUserId, examId)
        .map<Exam?, UiState<Exam?>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing exam", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "Không thể tải thông tin đề thi."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val submissionsState: StateFlow<UiState<List<Submission>>> =
        repository.observeSubmissions(currentUserId, examId)
            .map<List<Submission>, UiState<List<Submission>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing submissions", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "Không thể tải bảng điểm."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun deleteSubmission(submissionId: String) {
        viewModelScope.launch {
            val result = repository.deleteSubmission(currentUserId, examId, submissionId)
            if (result.isFailure) {
                _message.value = result.exceptionOrNull()?.localizedMessage ?: "Không thể xóa bài chấm."
            }
        }
    }

    fun updateSubmissionAnswers(
        exam: Exam,
        submission: Submission,
        newStudentName: String,
        newStudentCode: String,
        newExamCode: String,
        newAnswers: List<String>
    ) {
        val key = exam.getAnswerKeyForCode(newExamCode)
        val summary = GradingCalculator.grade(
            answerKey = key,
            studentAnswers = newAnswers,
            maxScore = exam.maxScore
        )
        viewModelScope.launch {
            val result = repository.updateSubmission(
                userId = currentUserId,
                examId = examId,
                submissionId = submission.id,
                studentName = newStudentName,
                studentCode = newStudentCode,
                studentAnswers = newAnswers,
                correctCount = summary.correctCount,
                wrongCount = summary.wrongCount,
                blankCount = summary.blankCount,
                score = summary.score,
                notes = submission.notes,
                examCode = newExamCode,
                imagePath = submission.imagePath,
                feedbackNote = submission.feedbackNote
            )
            if (result.isSuccess) {
                _message.value = "Đã cập nhật bài làm của $newStudentName."
            } else {
                _message.value = result.exceptionOrNull()?.localizedMessage ?: "Cập nhật bài chấm thất bại."
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun showMessage(msg: String) {
        _message.value = msg
    }

    private companion object {
        const val TAG = "ExamDetailViewModel"
    }
}
