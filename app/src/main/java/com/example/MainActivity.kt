package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.ai.AiAnswerSheetGrader
import com.example.data.firebase.configureAppCheck
import com.example.data.repository.ExamRepository
import com.example.ui.auth.authStateFlow
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ExamDetailScreen
import com.example.ui.screens.ExamEditorScreen
import com.example.ui.screens.ExamListScreen
import com.example.ui.screens.ScanSheetScreen
import com.example.ui.theme.ChamThiTheme
import com.example.ui.viewmodel.ExamDetailViewModel
import com.example.ui.viewmodel.ExamEditorViewModel
import com.example.ui.viewmodel.ExamListViewModel
import com.example.ui.viewmodel.ScanSheetViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

object AppRoutes {
    const val EXAM_LIST = "exam_list"
    const val EXAM_EDITOR = "exam_editor"
    const val EXAM_DETAIL = "exam_detail"
    const val SCAN_SHEET = "scan_sheet"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Step 1: Configure Firebase App Check with launch intent before any UI or ViewModels.
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            ChamThiTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = { /* AuthStateListener updates state automatically */ })
    } else {
        key(user.uid) {
            AuthenticatedNavHost(
                currentUserId = user.uid,
                userDisplayName = user.displayName ?: user.email
            )
        }
    }
}

@Composable
private fun AuthenticatedNavHost(
    currentUserId: String,
    userDisplayName: String?
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = AppRoutes.EXAM_LIST
    ) {
        composable(AppRoutes.EXAM_LIST) {
            val listViewModel: ExamListViewModel = viewModel(
                key = "list_$currentUserId",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val db = FirebaseFirestore.getInstance(
                            app.getString(R.string.firestore_database_id)
                        )
                        ExamListViewModel(ExamRepository(db), currentUserId)
                    }
                }
            )
            ExamListScreen(
                viewModel = listViewModel,
                userDisplayName = userDisplayName,
                onCreateExam = { navController.navigate(AppRoutes.EXAM_EDITOR) },
                onEditExam = { examId -> navController.navigate("${AppRoutes.EXAM_EDITOR}?examId=$examId") },
                onOpenExamDetail = { examId -> navController.navigate("${AppRoutes.EXAM_DETAIL}/$examId") },
                onQuickScan = { examId -> navController.navigate("${AppRoutes.SCAN_SHEET}/$examId") }
            )
        }

        composable(
            route = "${AppRoutes.EXAM_EDITOR}?examId={examId}",
            arguments = listOf(
                navArgument("examId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val examId = backStackEntry.arguments?.getString("examId")
            val editorViewModel: ExamEditorViewModel = viewModel(
                key = "editor_${currentUserId}_${examId ?: "new"}",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val db = FirebaseFirestore.getInstance(
                            app.getString(R.string.firestore_database_id)
                        )
                        ExamEditorViewModel(
                            repository = ExamRepository(db),
                            aiGrader = AiAnswerSheetGrader(app),
                            currentUserId = currentUserId,
                            examId = examId
                        )
                    }
                }
            )
            ExamEditorScreen(
                viewModel = editorViewModel,
                isEditing = !examId.isNullOrBlank(),
                onNavigateBack = { navController.popBackStack() },
                onExamSaved = { savedExamId ->
                    if (examId.isNullOrBlank()) {
                        navController.navigate("${AppRoutes.EXAM_DETAIL}/$savedExamId") {
                            popUpTo(AppRoutes.EXAM_LIST)
                        }
                    } else {
                        navController.popBackStack()
                    }
                }
            )
        }

        composable(
            route = "${AppRoutes.EXAM_DETAIL}/{examId}",
            arguments = listOf(
                navArgument("examId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val examId = backStackEntry.arguments?.getString("examId").orEmpty()
            val detailViewModel: ExamDetailViewModel = viewModel(
                key = "detail_${currentUserId}_$examId",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val db = FirebaseFirestore.getInstance(
                            app.getString(R.string.firestore_database_id)
                        )
                        ExamDetailViewModel(ExamRepository(db), currentUserId, examId)
                    }
                }
            )
            ExamDetailScreen(
                viewModel = detailViewModel,
                onNavigateBack = { navController.popBackStack() },
                onEditExam = { navController.navigate("${AppRoutes.EXAM_EDITOR}?examId=$examId") },
                onScanSheet = { navController.navigate("${AppRoutes.SCAN_SHEET}/$examId") }
            )
        }

        composable(
            route = "${AppRoutes.SCAN_SHEET}/{examId}",
            arguments = listOf(
                navArgument("examId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val examId = backStackEntry.arguments?.getString("examId").orEmpty()
            val scanViewModel: ScanSheetViewModel = viewModel(
                key = "scan_${currentUserId}_$examId",
                factory = viewModelFactory {
                    initializer {
                        val app = checkNotNull(this[APPLICATION_KEY])
                        val db = FirebaseFirestore.getInstance(
                            app.getString(R.string.firestore_database_id)
                        )
                        ScanSheetViewModel(
                            repository = ExamRepository(db),
                            aiGrader = AiAnswerSheetGrader(app),
                            currentUserId = currentUserId,
                            examId = examId
                        )
                    }
                }
            )
            ScanSheetScreen(
                viewModel = scanViewModel,
                onNavigateBack = { navController.popBackStack() },
                onSavedDone = { navController.popBackStack() }
            )
        }
    }
}
