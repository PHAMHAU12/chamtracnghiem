package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ExamRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createExam_and_observeExams_ownerSucceeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = ExamRepository(firestore)

        val examId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createExam(
                userId = uid,
                title = "Kiểm tra 15 phút Vật Lý",
                subject = "Vật Lý 11",
                maxScore = 10.0,
                answerKey = listOf("A", "B", "C", "D")
            ).getOrThrow()
        }
        assertTrue(examId.isNotEmpty())

        val exams = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeExams(uid).first { list -> list.any { it.id == examId } }
        }
        val created = exams.first { it.id == examId }
        assertEquals("Kiểm tra 15 phút Vật Lý", created.title)
        assertEquals(4L, created.totalQuestions)
    }

    @Test
    fun createSubmission_and_observeSubmissions_ownerSucceeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = ExamRepository(firestore)

        val examId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createExam(
                userId = uid,
                title = "Đề Hóa 12",
                subject = "Hóa học",
                maxScore = 10.0,
                answerKey = listOf("A", "C", "B", "D")
            ).getOrThrow()
        }

        val subId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createSubmission(
                userId = uid,
                examId = examId,
                studentName = "Trần Bảo Ngọc",
                studentCode = "12A1-05",
                studentAnswers = listOf("A", "C", "D", "D"),
                correctCount = 3,
                wrongCount = 1,
                blankCount = 0,
                score = 7.5,
                notes = "Ảnh rõ"
            ).getOrThrow()
        }
        assertTrue(subId.isNotEmpty())

        val subs = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeSubmissions(uid, examId).first { list -> list.any { it.id == subId } }
        }
        assertEquals(1, subs.size)
        assertEquals(7.5, subs.first().score, 0.01)
    }

    @Test
    fun crossUserReadExam_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = ExamRepository(firestore)
        val examId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createExam(
                userId = aliceUid,
                title = "Đề Sinh 10",
                subject = "Sinh học",
                maxScore = 10.0,
                answerKey = listOf("A", "B")
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.getExam(aliceUid, examId)
        }
        assertTrue(result.isFailure)
        val firestoreEx = result.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun observeExams_unauthenticated_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = ExamRepository(firestore)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeExams("alice_uid_placeholder").first()
            }
            fail("Expected PERMISSION_DENIED when unauthenticated")
        } catch (e: Exception) {
            val firestoreEx = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", firestoreEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice_grader@test.com"
        const val BOB_EMAIL = "bob_grader@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
