package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.GradingCalculator
import com.example.data.model.QuestionComparison
import com.example.data.model.QuestionStatus
import com.example.ui.theme.BlankAmber
import com.example.ui.theme.BlankAmberSoft
import com.example.ui.theme.CorrectGreen
import com.example.ui.theme.CorrectGreenSoft
import com.example.ui.theme.WrongRed
import com.example.ui.theme.WrongRedSoft
import com.example.ui.util.MediaCaptureUtils
import com.example.ui.viewmodel.ScanAiState
import com.example.ui.viewmodel.ScanSheetViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanSheetScreen(
    viewModel: ScanSheetViewModel,
    onNavigateBack: () -> Unit,
    onSavedDone: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingCameraUriString by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingCameraUri = pendingCameraUriString?.let { Uri.parse(it) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            scope.launch {
                val bitmap = MediaCaptureUtils.decodeBitmapFromUri(context, uri)
                if (bitmap != null) {
                    viewModel.onPhotoSelected(bitmap, autoStartAi = true)
                } else {
                    snackbarHostState.showSnackbar("Không thể đọc ảnh vừa chụp.")
                }
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = MediaCaptureUtils.createTempScanUri(context)
            pendingCameraUriString = uri.toString()
            takePictureLauncher.launch(uri)
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Cần quyền Camera để chụp phiếu trả lời.")
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bitmap = MediaCaptureUtils.decodeBitmapFromUri(context, uri)
                if (bitmap != null) {
                    viewModel.onPhotoSelected(bitmap, autoStartAi = true)
                } else {
                    snackbarHostState.showSnackbar("Không thể đọc ảnh đã chọn.")
                }
            }
        }
    }

    LaunchedEffect(uiState.statusToast, uiState.saveError) {
        val msg = uiState.saveError ?: uiState.statusToast
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Chấm phiếu bằng OMR (OpenCV)",
                            style = MaterialTheme.typography.titleLarge
                        )
                        uiState.exam?.let { exam ->
                            Text(
                                text = "${exam.title} (${exam.totalQuestions} câu)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("scan_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Quay lại"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            when {
                uiState.isLoadingExam -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.examError != null -> {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(text = uiState.examError!!, color = MaterialTheme.colorScheme.error)
                    }
                }
                uiState.exam != null -> {
                    val exam = uiState.exam!!
                    LazyColumn(
                        modifier = Modifier
                            .widthIn(max = 680.dp)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Card(
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "1. Chụp hoặc chọn ảnh phiếu trả lời",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Button(
                                            onClick = {
                                                val granted = ContextCompat.checkSelfPermission(
                                                    context,
                                                    Manifest.permission.CAMERA
                                                ) == PackageManager.PERMISSION_GRANTED
                                                if (granted) {
                                                    val uri = MediaCaptureUtils.createTempScanUri(context)
                                                    pendingCameraUriString = uri.toString()
                                                    takePictureLauncher.launch(uri)
                                                } else {
                                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                                }
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("capture_sheet_camera_button")
                                        ) {
                                            Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Chụp ảnh")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                photoPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("pick_sheet_gallery_button")
                                        ) {
                                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Thư viện")
                                        }
                                    }

                                    val bitmap = uiState.selectedBitmap
                                    if (bitmap != null) {
                                        Surface(
                                            shape = MaterialTheme.shapes.small,
                                            tonalElevation = 1.dp,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(210.dp)
                                        ) {
                                            Image(
                                                bitmap = bitmap.asImageBitmap(),
                                                contentDescription = "Ảnh phiếu trả lời đã chọn",
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    } else {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                            shape = MaterialTheme.shapes.small,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(110.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.fillMaxSize().padding(12.dp),
                                                verticalArrangement = Arrangement.Center,
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.AutoAwesome,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.secondary
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = "Chụp thẳng góc, đủ sáng để thuật toán OMR nhận diện A, B, C, D chính xác nhất.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        when (val ai = uiState.aiState) {
                            is ScanAiState.Idle -> {
                                if (uiState.selectedBitmap != null) {
                                    item {
                                        Button(
                                            onClick = viewModel::runAiGrading,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(52.dp)
                                                .testTag("run_ai_grading_button")
                                        ) {
                                            Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Nhận diện & Chấm bằng OMR (OpenCV)")
                                        }
                                    }
                                }
                            }
                            is ScanAiState.Scanning -> {
                                item {
                                    Card(
                                        shape = MaterialTheme.shapes.medium,
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(20.dp),
                                                    strokeWidth = 2.5.dp
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = "Đang quét dấu tô OMR và chấm điểm...",
                                                    style = MaterialTheme.typography.titleMedium
                                                )
                                            }
                                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                        }
                                    }
                                }
                            }
                            is ScanAiState.Error -> {
                                item {
                                    Card(
                                        shape = MaterialTheme.shapes.medium,
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(
                                                text = ai.message,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            Button(
                                                onClick = viewModel::runAiGrading,
                                                modifier = Modifier.height(48.dp)
                                            ) {
                                                Icon(Icons.Filled.Refresh, contentDescription = null)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Thử nhận diện lại")
                                            }
                                        }
                                    }
                                }
                            }
                            is ScanAiState.Graded -> {
                                item {
                                    Card(
                                        shape = MaterialTheme.shapes.medium,
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(
                                                        text = "Kết quả chấm OMR - Mã đề: ${ai.examCode}",
                                                        style = MaterialTheme.typography.labelLarge,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                    Text(
                                                        text = "${ai.summary.score} / ${formatScale(exam.maxScore)}",
                                                        style = MaterialTheme.typography.displaySmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.testTag("graded_score_text")
                                                    )
                                                }
                                                Surface(
                                                    color = MaterialTheme.colorScheme.surface,
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Text(
                                                        text = GradingCalculator.classifyRank(ai.summary.score, exam.maxScore),
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                                    )
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                CountBadge("Đúng: ${ai.summary.correctCount}", CorrectGreenSoft, CorrectGreen, Modifier.weight(1f))
                                                CountBadge("Sai: ${ai.summary.wrongCount}", WrongRedSoft, WrongRed, Modifier.weight(1f))
                                                CountBadge("Trống: ${ai.summary.blankCount}", BlankAmberSoft, BlankAmber, Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }

                                item {
                                    Card(
                                        shape = MaterialTheme.shapes.medium,
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(
                                                text = "2. Thông tin học sinh & Mã đề",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            OutlinedTextField(
                                                value = ai.studentName,
                                                onValueChange = viewModel::updateStudentName,
                                                label = { Text("Họ và tên học sinh") },
                                                placeholder = { Text("Nhập hoặc chỉnh sửa họ tên") },
                                                singleLine = true,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("student_name_input")
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                OutlinedTextField(
                                                    value = ai.studentCode,
                                                    onValueChange = viewModel::updateStudentCode,
                                                    label = { Text("Số báo danh (SBD)") },
                                                    placeholder = { Text("VD: 1024") },
                                                    singleLine = true,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                OutlinedTextField(
                                                    value = ai.examCode,
                                                    onValueChange = viewModel::updateExamCode,
                                                    label = { Text("Mã đề") },
                                                    placeholder = { Text("VD: 101") },
                                                    singleLine = true,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }

                                            if (exam.examCodes.isNotEmpty()) {
                                                Text(
                                                    text = "Chọn nhanh mã đề:",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    exam.getAllExamCodes().forEach { code ->
                                                        androidx.compose.material3.FilterChip(
                                                            selected = ai.examCode == code,
                                                            onClick = { viewModel.selectPreferredExamCode(code) },
                                                            label = { Text(code) }
                                                        )
                                                    }
                                                }
                                            }

                                            if (ai.confidenceNote.isNotBlank()) {
                                                Surface(
                                                    color = BlankAmberSoft,
                                                    shape = MaterialTheme.shapes.small,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(10.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Filled.Info,
                                                            contentDescription = null,
                                                            tint = BlankAmber,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = ai.confidenceNote,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = BlankAmber
                                                        )
                                                    }
                                                }
                                            }

                                            if (ai.feedbackNote.isNotBlank()) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                                    shape = MaterialTheme.shapes.small,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        text = "💡 Nhận xét AI: ${ai.feedbackNote}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.padding(10.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                item {
                                    Text(
                                        text = "3. Đối chiếu từng câu (chạm để sửa nếu cần)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                itemsIndexed(ai.summary.comparisons) { idx, comparison ->
                                    ComparisonRow(
                                        comparison = comparison,
                                        onOverride = { opt -> viewModel.overrideStudentAnswer(idx, opt) }
                                    )
                                }

                                item {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                viewModel.saveSubmission(
                                                    context = context,
                                                    keepScanning = true,
                                                    onDone = {}
                                                )
                                                // Tự động mở camera chụp bài kế tiếp nếu có quyền
                                                val hasPermission = ContextCompat.checkSelfPermission(
                                                    context,
                                                    Manifest.permission.CAMERA
                                                ) == PackageManager.PERMISSION_GRANTED
                                                if (hasPermission) {
                                                    val uri = MediaCaptureUtils.createTempScanUri(context)
                                                    pendingCameraUriString = uri.toString()
                                                    takePictureLauncher.launch(uri)
                                                } else {
                                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                                }
                                            },
                                            enabled = !uiState.isSaving,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(52.dp)
                                                .testTag("save_and_next_button")
                                        ) {
                                            Text("Lưu & Chụp bài tiếp")
                                        }
                                        Button(
                                            onClick = {
                                                viewModel.saveSubmission(
                                                    context = context,
                                                    keepScanning = false,
                                                    onDone = onSavedDone
                                                )
                                            },
                                            enabled = !uiState.isSaving,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(52.dp)
                                                .testTag("save_submission_button")
                                        ) {
                                            if (uiState.isSaving) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(20.dp),
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Lưu bảng điểm")
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CountBadge(
    text: String,
    bgColor: androidx.compose.ui.graphics.Color,
    fgColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = fgColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ComparisonRow(
    comparison: QuestionComparison,
    onOverride: (String) -> Unit
) {
    val statusColor = when (comparison.status) {
        QuestionStatus.CORRECT -> CorrectGreen
        QuestionStatus.WRONG -> WrongRed
        QuestionStatus.BLANK -> BlankAmber
    }
    val statusIcon = when (comparison.status) {
        QuestionStatus.CORRECT -> Icons.Filled.CheckCircle
        QuestionStatus.WRONG -> Icons.Filled.Close
        QuestionStatus.BLANK -> Icons.Filled.RemoveCircleOutline
    }
    val statusDesc = when (comparison.status) {
        QuestionStatus.CORRECT -> "Đúng"
        QuestionStatus.WRONG -> "Sai"
        QuestionStatus.BLANK -> "Bỏ trống"
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = statusIcon,
                contentDescription = statusDesc,
                tint = statusColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.width(66.dp)) {
                Text(
                    text = "Câu ${comparison.questionNumber}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "ĐÁ: ${comparison.correctAnswer}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf("A", "B", "C", "D", "BLANK").forEach { opt ->
                    val isSelected = comparison.studentAnswer == opt
                    val label = if (opt == "BLANK") "-" else opt
                    val chipBg = when {
                        isSelected && comparison.status == QuestionStatus.CORRECT -> CorrectGreen
                        isSelected && comparison.status == QuestionStatus.WRONG -> WrongRed
                        isSelected -> BlankAmber
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val chipFg = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant

                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(chipBg)
                            .clickable { onOverride(opt) }
                            .testTag("scan_q${comparison.questionNumber}_$opt"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = chipFg
                        )
                    }
                }
            }
        }
    }
}

private fun formatScale(score: Double): String {
    return if (score % 1.0 == 0.0) score.toInt().toString() else score.toString()
}
