package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Exam
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
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class ExamListViewModel(
    private val repository: ExamRepository,
    private val currentUserId: String
) : ViewModel() {

    val examsState: StateFlow<UiState<List<Exam>>> = repository.observeExams(currentUserId)
        .map<List<Exam>, UiState<List<Exam>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing exams", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "Không thể tải danh sách đề thi."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    fun deleteExam(examId: String) {
        viewModelScope.launch {
            val result = repository.deleteExam(currentUserId, examId)
            if (result.isFailure) {
                _actionMessage.value = result.exceptionOrNull()?.localizedMessage
                    ?: "Không thể xóa đề thi."
            }
        }
    }

    fun clearMessage() {
        _actionMessage.value = null
    }

    private companion object {
        const val TAG = "ExamListVM"
    }
}
