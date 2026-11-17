package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.Exam
import com.example.data.model.GradingCalculator
import com.example.data.model.Submission
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ExamRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun examsCollection(userId: String) =
        db.collection("users").document(userId).collection("exams")

    private fun submissionsCollection(userId: String, examId: String) =
        examsCollection(userId).document(examId).collection("submissions")

    private fun requireActiveUid(expectedUserId: String? = null): String {
        val uid = Firebase.auth.currentUser?.uid
            ?: throw IllegalStateException("Người dùng chưa đăng nhập Google.")
        if (expectedUserId != null && expectedUserId != uid) {
            throw SecurityException("UID không khớp phiên đăng nhập hiện tại.")
        }
        return uid
    }

    fun observeExams(userId: String): Flow<List<Exam>> {
        val path = "users/$userId/exams"
        return examsCollection(userId)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    Exam::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                }
                throw error
            }
    }

    fun observeExam(userId: String, examId: String): Flow<Exam?> {
        val docRef = examsCollection(userId).document(examId)
        return docRef.snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        Exam::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else {
                    null
                }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null) {
                    handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                }
                throw error
            }
    }

    suspend fun getExam(userId: String, examId: String): Result<Exam?> {
        val docRef = examsCollection(userId).document(examId)
        return try {
            val snapshot = docRef.get().await()
            val exam = if (snapshot.exists()) {
                snapshot.toObject(
                    Exam::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            } else {
                null
            }
            Result.success(exam)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun createExam(
        userId: String,
        title: String,
        subject: String,
        maxScore: Double,
        answerKey: List<String>,
        classGroup: String = "",
        examCodes: Map<String, List<String>> = emptyMap()
    ): Result<String> {
        val uid = try {
            requireActiveUid(userId)
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val cleanKey = answerKey.map { choice ->
            val upper = choice.trim().uppercase()
            if (upper in GradingCalculator.VALID_OPTIONS) upper else "A"
        }
        val cleanExamCodes = examCodes.mapValues { (_, keys) ->
            keys.map { c ->
                val u = c.trim().uppercase()
                if (u in GradingCalculator.VALID_OPTIONS) u else "A"
            }
        }
        val docRef = examsCollection(uid).document()
        val payload = mutableMapOf<String, Any>(
            "userId" to uid,
            "title" to title.trim().take(150),
            "subject" to subject.trim().take(100),
            "classGroup" to classGroup.trim().take(100),
            "totalQuestions" to cleanKey.size.toLong(),
            "maxScore" to maxScore,
            "answerKey" to cleanKey,
            "examCodes" to cleanExamCodes,
            "submissionCount" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateExam(
        userId: String,
        examId: String,
        title: String,
        subject: String,
        maxScore: Double,
        answerKey: List<String>,
        classGroup: String = "",
        examCodes: Map<String, List<String>> = emptyMap()
    ): Result<Unit> {
        val uid = try {
            requireActiveUid(userId)
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val cleanKey = answerKey.map { choice ->
            val upper = choice.trim().uppercase()
            if (upper in GradingCalculator.VALID_OPTIONS) upper else "A"
        }
        val cleanExamCodes = examCodes.mapValues { (_, keys) ->
            keys.map { c ->
                val u = c.trim().uppercase()
                if (u in GradingCalculator.VALID_OPTIONS) u else "A"
            }
        }
        val docRef = examsCollection(uid).document(examId)
        val updates = mutableMapOf<String, Any>(
            "title" to title.trim().take(150),
            "subject" to subject.trim().take(100),
            "classGroup" to classGroup.trim().take(100),
            "totalQuestions" to cleanKey.size.toLong(),
            "maxScore" to maxScore,
            "answerKey" to cleanKey,
            "examCodes" to cleanExamCodes,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteExam(userId: String, examId: String): Result<Unit> {
        val uid = try {
            requireActiveUid(userId)
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val examRef = examsCollection(uid).document(examId)
        return try {
            val submissions = submissionsCollection(uid, examId).get().await()
            val batch = db.batch()
            for (doc in submissions.documents) {
                batch.delete(doc.reference)
            }
            batch.delete(examRef)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, examRef.path)
            Result.failure(e)
        }
    }

    fun observeSubmissions(userId: String, examId: String): Flow<List<Submission>> {
        val path = "users/$userId/exams/$examId/submissions"
        return submissionsCollection(userId, examId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    Submission::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun createSubmission(
        userId: String,
        examId: String,
        studentName: String,
        studentCode: String,
        studentAnswers: List<String>,
        correctCount: Int,
        wrongCount: Int,
        blankCount: Int,
        score: Double,
        notes: String,
        examCode: String = "",
        imagePath: String = "",
        feedbackNote: String = ""
    ): Result<String> {
        val uid = try {
            requireActiveUid(userId)
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val examRef = examsCollection(uid).document(examId)
        val subRef = submissionsCollection(uid, examId).document()
        val payload = mutableMapOf<String, Any>(
            "userId" to uid,
            "examId" to examId,
            "studentName" to studentName.trim().ifEmpty { "Học sinh" }.take(120),
            "studentCode" to studentCode.trim().take(40),
            "examCode" to examCode.trim().take(30),
            "imagePath" to imagePath.trim().take(500),
            "feedbackNote" to feedbackNote.trim().take(1000),
            "studentAnswers" to studentAnswers.map { GradingCalculator.normalizeChoice(it) },
            "correctCount" to correctCount.toLong(),
            "wrongCount" to wrongCount.toLong(),
            "blankCount" to blankCount.toLong(),
            "score" to score,
            "notes" to notes.trim().take(500),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            val batch = db.batch()
            batch.set(subRef, payload)
            batch.update(
                examRef,
                mapOf(
                    "submissionCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(subRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, subRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateSubmission(
        userId: String,
        examId: String,
        submissionId: String,
        studentName: String,
        studentCode: String,
        studentAnswers: List<String>,
        correctCount: Int,
        wrongCount: Int,
        blankCount: Int,
        score: Double,
        notes: String,
        examCode: String = "",
        imagePath: String = "",
        feedbackNote: String = ""
    ): Result<Unit> {
        val uid = try {
            requireActiveUid(userId)
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val subRef = submissionsCollection(uid, examId).document(submissionId)
        val updates = mutableMapOf<String, Any>(
            "studentName" to studentName.trim().ifEmpty { "Học sinh" }.take(120),
            "studentCode" to studentCode.trim().take(40),
            "examCode" to examCode.trim().take(30),
            "imagePath" to imagePath.trim().take(500),
            "feedbackNote" to feedbackNote.trim().take(1000),
            "studentAnswers" to studentAnswers.map { GradingCalculator.normalizeChoice(it) },
            "correctCount" to correctCount.toLong(),
            "wrongCount" to wrongCount.toLong(),
            "blankCount" to blankCount.toLong(),
            "score" to score,
            "notes" to notes.trim().take(500),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            subRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, subRef.path)
            Result.failure(e)
        }
    }

    fun saveSubmissionBitmap(context: Context, bitmap: android.graphics.Bitmap, submissionId: String): String {
        return try {
            val dir = java.io.File(context.filesDir, "submissions").apply { mkdirs() }
            val file = java.io.File(dir, "sub_${submissionId}.jpg")
            file.outputStream().use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
            }
            file.name
        } catch (e: Exception) {
            ""
        }
    }

    fun loadSubmissionBitmap(context: Context, imagePath: String): android.graphics.Bitmap? {
        return try {
            if (imagePath.isBlank()) return null
            val file = if (imagePath.contains(java.io.File.separator)) {
                java.io.File(imagePath)
            } else {
                java.io.File(java.io.File(context.filesDir, "submissions"), imagePath)
            }
            if (file.exists()) {
                android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun deleteSubmission(
        userId: String,
        examId: String,
        submissionId: String
    ): Result<Unit> {
        val uid = try {
            requireActiveUid(userId)
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val examRef = examsCollection(uid).document(examId)
        val subRef = submissionsCollection(uid, examId).document(submissionId)
        return try {
            val currentCount = examRef.get().await().getLong("submissionCount") ?: 0L
            val batch = db.batch()
            batch.delete(subRef)
            if (currentCount > 0L) {
                batch.update(
                    examRef,
                    mapOf(
                        "submissionCount" to FieldValue.increment(-1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, subRef.path)
            Result.failure(e)
        }
    }
}
