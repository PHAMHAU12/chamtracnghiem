package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PresentToAll
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.export.DocxExporter
import com.example.data.export.ExcelExporter
import com.example.data.export.MoodleExporter
import com.example.data.export.PptxExporter
import com.example.data.model.Exam
import com.example.data.model.GradingCalculator
import com.example.data.model.QuestionStatus
import com.example.data.model.Submission
import com.example.ui.theme.BlankAmber
import com.example.ui.theme.BlankAmberSoft
import com.example.ui.theme.CorrectGreen
import com.example.ui.theme.CorrectGreenSoft
import com.example.ui.theme.WrongRed
import com.example.ui.theme.WrongRedSoft
import com.example.ui.viewmodel.ExamDetailViewModel
import com.example.ui.viewmodel.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExamDetailScreen(
    viewModel: ExamDetailViewModel,
    onNavigateBack: () -> Unit,
    onEditExam: () -> Unit,
    onScanSheet: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val examState by viewModel.examState.collectAsStateWithLifecycle()
    val submissionsState by viewModel.submissionsState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val message by viewModel.message.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    var showExportDialog by remember { mutableStateOf(false) }
    var submissionToDelete by remember { mutableStateOf<Submission?>(null) }
    var submissionToInspect by remember { mutableStateOf<Submission?>(null) }

    val currentExam = (examState as? UiState.Success)?.data
    val currentSubmissions = (submissionsState as? UiState.Success)?.data.orEmpty()

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null && currentExam != null) {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        ExcelExporter.exportToUri(context, uri, currentExam, currentSubmissions)
                    }
                    viewModel.showMessage("Đã lưu bảng điểm Excel thành công.")
                } catch (e: Exception) {
                    viewModel.showMessage(e.localizedMessage ?: "Lưu file Excel thất bại.")
                }
            }
        }
    }

    LaunchedEffect(message) {
        val msg = message
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = currentExam?.title ?: "Chi tiết đề thi",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (currentExam != null) {
                            val classPart = if (currentExam.classGroup.isNotBlank()) " • Lớp: ${currentExam.classGroup}" else ""
                            Text(
                                text = "${currentExam.totalQuestions} câu • Thang ${formatScale(currentExam.maxScore)}$classPart",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Quay lại"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onEditExam,
                        modifier = Modifier.testTag("detail_edit_exam_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Chỉnh sửa đáp án"
                        )
                    }
                    IconButton(
                        onClick = { showExportDialog = true },
                        enabled = currentExam != null,
                        modifier = Modifier.testTag("detail_export_excel_icon")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.TableChart,
                            contentDescription = "Báo cáo & Xuất file"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (currentExam != null) {
                ExtendedFloatingActionButton(
                    onClick = onScanSheet,
                    icon = { Icon(Icons.Filled.DocumentScanner, contentDescription = null) },
                    text = { Text("Chấm phiếu AI") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("scan_sheet_fab")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxSize()
            ) {
                when (val eState = examState) {
                    is UiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is UiState.Error -> {
                        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(text = eState.message, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    is UiState.Success -> {
                        val exam = eState.data
                        if (exam == null) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Đề thi không tồn tại hoặc đã bị xóa.")
                            }
                        } else {
                            TabRow(selectedTabIndex = selectedTab) {
                                Tab(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    text = { Text("Bài nộp (${currentSubmissions.size})") }
                                )
                                Tab(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    text = { Text("Đáp án chuẩn") }
                                )
                                Tab(
                                    selected = selectedTab == 2,
                                    onClick = { selectedTab = 2 },
                                    text = { Text("Phổ điểm & Ma trận") }
                                )
                            }

                            when (selectedTab) {
                                0 -> ScoresTabContent(
                                    exam = exam,
                                    submissionsState = submissionsState,
                                    onScanSheet = onScanSheet,
                                    onExport = { showExportDialog = true },
                                    onInspectSubmission = { submissionToInspect = it },
                                    onDeleteSubmission = { submissionToDelete = it }
                                )
                                1 -> AnswerKeyPreviewContent(
                                    exam = exam,
                                    onEditExam = onEditExam
                                )
                                2 -> AnalyticsTabContent(
                                    exam = exam,
                                    submissions = currentSubmissions,
                                    onExportPptx = {
                                        scope.launch {
                                            val pptxUri = withContext(Dispatchers.IO) {
                                                PptxExporter.exportReviewSlides(context, exam, currentSubmissions)
                                            }
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                                                putExtra(Intent.EXTRA_STREAM, pptxUri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Chia sẻ slide chữa đề PowerPoint"))
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal xuất báo cáo đa định dạng (Skills: Excel, docx, pptx, moodle)
    if (showExportDialog && currentExam != null) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.TableChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Trung tâm Báo cáo & Xuất file")
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Chọn định dạng báo cáo giáo dục bạn muốn xuất:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 1. Xuất Excel (.xlsx)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showExportDialog = false
                                scope.launch {
                                    val shareUri = withContext(Dispatchers.IO) {
                                        ExcelExporter.exportToCacheAndGetShareUri(context, currentExam, currentSubmissions)
                                    }
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                        putExtra(Intent.EXTRA_STREAM, shareUri)
                                        putExtra(Intent.EXTRA_SUBJECT, "Bảng điểm: ${currentExam.title}")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Chia sẻ bảng điểm Excel"))
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.TableChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Bảng điểm Excel (.xlsx)", fontWeight = FontWeight.Bold)
                                Text("Bao gồm Bảng tổng hợp, chi tiết câu & ma trận độ khó", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    // 2. Xuất Báo cáo điểm Word (.docx)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showExportDialog = false
                                scope.launch {
                                    val docxUri = withContext(Dispatchers.IO) {
                                        DocxExporter.exportScoreReport(context, currentExam, currentSubmissions)
                                    }
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                        putExtra(Intent.EXTRA_STREAM, docxUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Chia sẻ Báo cáo điểm Word"))
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Báo cáo điểm Word (.docx)", fontWeight = FontWeight.Bold)
                                Text("Mẫu bảng điểm chính thức lưu hồ sơ chuyên môn", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    // 3. In Phiếu trả lời trắc nghiệm (.docx)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showExportDialog = false
                                val docxUri = DocxExporter.exportBlankAnswerSheet(context, currentExam)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                    putExtra(Intent.EXTRA_STREAM, docxUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "In hoặc chia sẻ phiếu trả lời"))
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.DocumentScanner, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("In Phiếu trả lời trắc nghiệm (.docx)", fontWeight = FontWeight.Bold)
                                Text("Phiếu tô A4 chuẩn ${currentExam.totalQuestions} câu phát cho học sinh", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    // 4. Tạo Slide chữa bài PowerPoint (.pptx)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showExportDialog = false
                                scope.launch {
                                    val pptxUri = withContext(Dispatchers.IO) {
                                        PptxExporter.exportReviewSlides(context, currentExam, currentSubmissions)
                                    }
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                                        putExtra(Intent.EXTRA_STREAM, pptxUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Chia sẻ Slide chữa đề PowerPoint"))
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.PresentToAll, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Slide Chữa Đề PowerPoint (.pptx)", fontWeight = FontWeight.Bold)
                                Text("Slide phân tích phổ điểm & câu sai nhiều nhất để chiếu lên lớp", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    // 5. Moodle LMS / Google Classroom CSV
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showExportDialog = false
                                val csvUri = MoodleExporter.exportMoodleCsv(context, currentExam, currentSubmissions)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/csv"
                                    putExtra(Intent.EXTRA_STREAM, csvUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Chia sẻ file Moodle CSV"))
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Đồng bộ LMS Moodle / Classroom (CSV)", fontWeight = FontWeight.Bold)
                                Text("Import trực tiếp vào Gradebook trường học", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Đóng")
                }
            }
        )
    }

    if (submissionToDelete != null) {
        val target = submissionToDelete!!
        AlertDialog(
            onDismissRequest = { submissionToDelete = null },
            title = { Text("Xóa bài chấm?") },
            text = { Text("Xóa kết quả chấm của ${target.studentName}?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSubmission(target.id)
                        submissionToDelete = null
                    }
                ) {
                    Text("Xóa")
                }
            },
            dismissButton = {
                TextButton(onClick = { submissionToDelete = null }) {
                    Text("Hủy")
                }
            }
        )
    }

    if (submissionToInspect != null && currentExam != null) {
        SubmissionInspectDialog(
            context = context,
            exam = currentExam,
            submission = submissionToInspect!!,
            onDismiss = { submissionToInspect = null },
            onSaveChanges = { newName, newCode, newExamCode, newAnswers ->
                viewModel.updateSubmissionAnswers(
                    exam = currentExam,
                    submission = submissionToInspect!!,
                    newStudentName = newName,
                    newStudentCode = newCode,
                    newExamCode = newExamCode,
                    newAnswers = newAnswers
                )
                submissionToInspect = null
            }
        )
    }
}

@Composable
private fun ScoresTabContent(
    exam: Exam,
    submissionsState: UiState<List<Submission>>,
    onScanSheet: () -> Unit,
    onExport: () -> Unit,
    onInspectSubmission: (Submission) -> Unit,
    onDeleteSubmission: (Submission) -> Unit
) {
    when (submissionsState) {
        is UiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is UiState.Error -> {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(text = submissionsState.message, color = MaterialTheme.colorScheme.error)
            }
        }
        is UiState.Success -> {
            val submissions = submissionsState.data
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    ExamStatsCard(exam = exam, submissions = submissions)
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onScanSheet,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("detail_scan_button")
                        ) {
                            Icon(Icons.Filled.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Chụp chấm bài")
                        }
                        OutlinedButton(
                            onClick = onExport,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("export_excel_button")
                        ) {
                            Icon(Icons.Filled.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Báo cáo & Xuất file")
                        }
                    }
                }

                if (submissions.isEmpty()) {
                    item {
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DocumentScanner,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Chưa có bài nào được chấm",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Nhấn \"Chấm phiếu AI\" để chụp ảnh bài thi.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(submissions, key = { it.id }) { sub ->
                        SubmissionCard(
                            exam = exam,
                            submission = sub,
                            onClick = { onInspectSubmission(sub) },
                            onDelete = { onDeleteSubmission(sub) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExamStatsCard(exam: Exam, submissions: List<Submission>) {
    val avg = if (submissions.isNotEmpty()) submissions.map { it.score }.average() else 0.0
    val max = submissions.maxOfOrNull { it.score } ?: 0.0
    val passCount = submissions.count { it.score >= exam.maxScore * 0.5 }
    val passRate = if (submissions.isNotEmpty()) (passCount * 100) / submissions.size else 0

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatCell("Điểm TB", String.format(Locale.US, "%.2f", avg))
            StatCell("Cao nhất", String.format(Locale.US, "%.2f", max))
            StatCell("Đạt (>=50%)", "$passRate%")
            StatCell("Đã chấm", "${submissions.size}")
        }
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun SubmissionCard(
    exam: Exam,
    submission: Submission,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val isPass = submission.score >= exam.maxScore * 0.5
    val scoreBg = if (isPass) CorrectGreenSoft else WrongRedSoft
    val scoreFg = if (isPass) CorrectGreen else WrongRed
    val timeText = remember(submission.createdAt) {
        submission.createdAt?.toDate()?.let {
            SimpleDateFormat("dd/MM HH:mm", Locale.forLanguageTag("vi-VN")).format(it)
        } ?: "Vừa xong"
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("submission_card_${submission.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = scoreBg,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.size(width = 68.dp, height = 56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = String.format(Locale.US, "%.2f", submission.score).trimEnd('0').trimEnd('.'),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = scoreFg
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = submission.studentName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (submission.examCode.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Mã ${submission.examCode}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                val codePart = if (submission.studentCode.isNotBlank()) "SBD: ${submission.studentCode} • " else ""
                Text(
                    text = "${codePart}Đúng ${submission.correctCount} • Sai ${submission.wrongCount} • Trống ${submission.blankCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$timeText • ${GradingCalculator.classifyRank(submission.score, exam.maxScore)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_submission_${submission.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Xóa bài chấm",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Tab 2: Phổ điểm & Phân tích ma trận đề thi (Skill: d3-visualization)
 */
@Composable
private fun AnalyticsTabContent(
    exam: Exam,
    submissions: List<Submission>,
    onExportPptx: () -> Unit
) {
    val analytics = remember(exam, submissions) {
        GradingCalculator.calculateAnalytics(exam, submissions)
    }

    if (submissions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Chưa có dữ liệu bài nộp để tạo biểu đồ phân tích phổ điểm.")
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Card 1: Biểu đồ Phổ Điểm Lớp Học
        item {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.BarChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Phổ Điểm Thi (Score Distribution)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Text("${analytics.totalSubmissions} bài", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }

                    // Biểu đồ cột phân bố điểm vẽ bằng Compose
                    val maxBucketCount = analytics.scoreDistribution.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        analytics.scoreDistribution.forEach { bucket ->
                            val heightFraction = bucket.count.toFloat() / maxBucketCount.toFloat()
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                Text(
                                    text = if (bucket.count > 0) "${bucket.count}" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .width(26.dp)
                                        .fillMaxHeight(heightFraction.coerceAtLeast(0.06f))
                                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                        .background(
                                            if (bucket.count > 0) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = bucket.rangeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // Card 2: Nút tạo Slide chữa đề PowerPoint (.pptx)
        item {
            FilledTonalButton(
                onClick = onExportPptx,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Filled.PresentToAll, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Tạo Slide chữa đề chiếu lên lớp (.pptx)")
            }
        }

        // Card 3: Ma trận câu hỏi & Cảnh báo câu khó/bẫy
        item {
            Text("Ma trận câu hỏi & Tỉ lệ làm đúng (Item Analysis)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        items(analytics.itemStats) { q ->
            val isWarning = q.isHardQuestion
            val badgeColor = if (isWarning) WrongRed else CorrectGreen
            val badgeBg = if (isWarning) WrongRedSoft else CorrectGreenSoft

            Card(
                shape = MaterialTheme.shapes.small,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = badgeBg,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(54.dp, 44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${q.correctPercentage.toInt()}%",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Câu ${q.questionNumber}", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Đ/A: ${q.correctAnswer}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            if (isWarning) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("⚠️ Khó / Bẫy", style = MaterialTheme.typography.labelSmall, color = WrongRed, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(
                            text = "A:${q.countA} | B:${q.countB} | C:${q.countC} | D:${q.countD} | Trống:${q.countBlank}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnswerKeyPreviewContent(
    exam: Exam,
    onEditExam: () -> Unit
) {
    var activeCodeTab by remember { mutableStateOf("Chuẩn") }
    val codes = exam.getAllExamCodes()
    val currentKey = exam.getAnswerKeyForCode(activeCodeTab)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (codes.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                codes.forEach { code ->
                    FilterChip(
                        selected = activeCodeTab == code,
                        onClick = { activeCodeTab = code },
                        label = { Text("Mã $code") }
                    )
                }
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            currentKey.forEachIndexed { idx, ans ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(10.dp),
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${idx + 1}.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = ans,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onEditExam,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Icon(Icons.Filled.Edit, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Chỉnh sửa đáp án chuẩn")
        }
        Spacer(modifier = Modifier.height(72.dp))
    }
}

@Composable
private fun SubmissionInspectDialog(
    context: android.content.Context,
    exam: Exam,
    submission: Submission,
    onDismiss: () -> Unit,
    onSaveChanges: (String, String, String, List<String>) -> Unit
) {
    var name by remember { mutableStateOf(submission.studentName) }
    var code by remember { mutableStateOf(submission.studentCode) }
    var examCode by remember { mutableStateOf(submission.examCode.ifBlank { "Chuẩn" }) }

    val key = exam.getAnswerKeyForCode(examCode)
    val editedAnswers = remember(examCode) {
        mutableStateListOf<String>().apply {
            for (i in key.indices) {
                add(GradingCalculator.normalizeChoice(submission.studentAnswers.getOrNull(i)))
            }
        }
    }
    val liveSummary = GradingCalculator.grade(key, editedAnswers, exam.maxScore)

    // Load ảnh nếu có
    val loadedBitmap = remember(submission.imagePath) {
        if (submission.imagePath.isNotBlank()) {
            val file = File(File(context.filesDir, "submissions"), submission.imagePath)
            if (file.exists()) {
                android.graphics.BitmapFactory.decodeFile(file.absolutePath)
            } else null
        } else null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Chi tiết bài làm", fontWeight = FontWeight.Bold)
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "${liveSummary.score} / ${formatScale(exam.maxScore)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Hiển thị ảnh chụp bài thi nếu có
                if (loadedBitmap != null) {
                    Text("Ảnh chụp phiếu bài thi:", style = MaterialTheme.typography.labelMedium)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    ) {
                        Image(
                            bitmap = loadedBitmap.asImageBitmap(),
                            contentDescription = "Ảnh bài thi",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Họ và tên") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("Số báo danh") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = examCode,
                        onValueChange = { examCode = it },
                        label = { Text("Mã đề") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (submission.feedbackNote.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "💡 Nhận xét AI: ${submission.feedbackNote}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                Text(
                    text = "Đúng: ${liveSummary.correctCount} • Sai: ${liveSummary.wrongCount} • Trống: ${liveSummary.blankCount}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                liveSummary.comparisons.forEachIndexed { idx, cmp ->
                    val statusColor = when (cmp.status) {
                        QuestionStatus.CORRECT -> CorrectGreen
                        QuestionStatus.WRONG -> WrongRed
                        QuestionStatus.BLANK -> BlankAmber
                    }
                    val statusBg = when (cmp.status) {
                        QuestionStatus.CORRECT -> CorrectGreenSoft
                        QuestionStatus.WRONG -> WrongRedSoft
                        QuestionStatus.BLANK -> BlankAmberSoft
                    }
                    Surface(
                        color = statusBg.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "C${cmp.questionNumber} (${cmp.correctAnswer})",
                                style = MaterialTheme.typography.labelMedium,
                                color = statusColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(64.dp)
                            )
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                listOf("A", "B", "C", "D", "BLANK").forEach { opt ->
                                    val selected = editedAnswers.getOrNull(idx) == opt
                                    val label = if (opt == "BLANK") "-" else opt
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (selected) statusColor
                                                else MaterialTheme.colorScheme.surface
                                            )
                                            .clickable {
                                                if (idx in editedAnswers.indices) {
                                                    editedAnswers[idx] = opt
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveChanges(name, code, examCode, editedAnswers.toList()) }
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Lưu chỉnh sửa")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng")
            }
        }
    )
}

private fun formatScale(score: Double): String {
    return if (score % 1.0 == 0.0) score.toInt().toString() else score.toString()
}
