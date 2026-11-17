package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.export.DocxExporter
import com.example.data.model.Exam
import com.example.data.model.GradingCalculator
import com.example.ui.util.MediaCaptureUtils
import com.example.ui.viewmodel.ExamEditorViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamEditorScreen(
    viewModel: ExamEditorViewModel,
    isEditing: Boolean,
    onNavigateBack: () -> Unit,
    onExamSaved: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showQuickInputDialog by remember { mutableStateOf(false) }
    var showCustomCountDialog by remember { mutableStateOf(false) }
    var showAddCodeDialog by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    MediaCaptureUtils.loadBitmapFromUri(context, uri)
                }
                if (bitmap != null) {
                    viewModel.scanAnswerKeyFromPhoto(bitmap)
                } else {
                    snackbarHostState.showSnackbar("Không thể tải ảnh chụp bảng đáp án.")
                }
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    MediaCaptureUtils.loadBitmapFromUri(context, uri)
                }
                if (bitmap != null) {
                    viewModel.scanAnswerKeyFromPhoto(bitmap)
                } else {
                    snackbarHostState.showSnackbar("Không thể tải ảnh từ thư viện.")
                }
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = MediaCaptureUtils.createTempScanUri(context)
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Cần quyền máy ảnh để chụp bảng đáp án.")
            }
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        val msg = uiState.errorMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(uiState.statusMessage) {
        val msg = uiState.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessages()
        }
    }

    val currentKeys = if (uiState.activeCodeTab == "Chuẩn") {
        uiState.answerKey
    } else {
        uiState.examCodes[uiState.activeCodeTab] ?: uiState.answerKey
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditing) "Sửa đáp án đề thi" else "Tạo đề thi mới",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("editor_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Quay lại"
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = { viewModel.saveExam(onExamSaved) },
                        enabled = !uiState.isSaving && !uiState.isLoading,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .height(42.dp)
                            .testTag("save_exam_button")
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Lưu")
                        }
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
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = 680.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Card 1: Thông tin cơ bản
                    item {
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "Thông tin đề thi",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                OutlinedTextField(
                                    value = uiState.title,
                                    onValueChange = viewModel::updateTitle,
                                    label = { Text("Tên đề thi *") },
                                    placeholder = { Text("VD: Kiểm tra giữa kỳ 1 Toán 10") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("exam_title_input")
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = uiState.subject,
                                        onValueChange = viewModel::updateSubject,
                                        label = { Text("Môn học") },
                                        placeholder = { Text("VD: Toán học") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1.2f)
                                    )
                                    OutlinedTextField(
                                        value = uiState.classGroup,
                                        onValueChange = viewModel::updateClassGroup,
                                        label = { Text("Lớp") },
                                        placeholder = { Text("VD: 10A1") },
                                        singleLine = true,
                                        modifier = Modifier.weight(0.9f)
                                    )
                                    OutlinedTextField(
                                        value = uiState.maxScoreText,
                                        onValueChange = viewModel::updateMaxScoreText,
                                        label = { Text("Thang điểm") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.weight(0.9f)
                                    )
                                }

                                Text(
                                    text = "Số lượng câu hỏi: ${uiState.answerKey.size} câu",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(10, 15, 20, 25, 30, 40, 50, 60, 100).forEach { count ->
                                        FilterChip(
                                            selected = uiState.answerKey.size == count,
                                            onClick = { viewModel.setQuestionCount(count) },
                                            label = { Text("$count câu") }
                                        )
                                    }
                                    FilterChip(
                                        selected = uiState.answerKey.size !in listOf(10, 15, 20, 25, 30, 40, 50, 60, 100),
                                        onClick = { showCustomCountDialog = true },
                                        label = { Text("Tự chọn số câu...") }
                                    )
                                }
                            }
                        }
                    }

                    // Card 2: Quản lý Mã Đề (Multi-Code)
                    item {
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Mã đề thi (Đa mã đề)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    OutlinedButton(
                                        onClick = { showAddCodeDialog = true },
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Thêm mã đề")
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilterChip(
                                        selected = uiState.activeCodeTab == "Chuẩn",
                                        onClick = { viewModel.selectCodeTab("Chuẩn") },
                                        label = { Text("Đề Chuẩn (Gốc)") }
                                    )
                                    uiState.examCodes.keys.sorted().forEach { code ->
                                        FilterChip(
                                            selected = uiState.activeCodeTab == code,
                                            onClick = { viewModel.selectCodeTab(code) },
                                            label = { Text("Mã $code") },
                                            trailingIcon = {
                                                Icon(
                                                    imageVector = Icons.Filled.Close,
                                                    contentDescription = "Xóa mã đề",
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .clickable { viewModel.removeExamCode(code) }
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Card 3: Nhập nhanh & Chụp AI & In phiếu thi (.docx)
                    item {
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Nhập đáp án cho: Mã [${uiState.activeCodeTab}]",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    // Nút in phiếu trả lời Word (.docx)
                                    TextButton(
                                        onClick = {
                                            val dummyExam = Exam(
                                                title = uiState.title.ifBlank { "Kỳ thi trắc nghiệm" },
                                                subject = uiState.subject,
                                                classGroup = uiState.classGroup,
                                                totalQuestions = uiState.answerKey.size.toLong()
                                            )
                                            val docxUri = DocxExporter.exportBlankAnswerSheet(
                                                context,
                                                dummyExam,
                                                uiState.answerKey.size
                                            )
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                                putExtra(Intent.EXTRA_STREAM, docxUri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "In hoặc chia sẻ phiếu trả lời"))
                                        }
                                    ) {
                                        Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("In phiếu thi (.docx)")
                                    }
                                }

                                if (uiState.isScanningKey) {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    Text(
                                        text = "AI đang đọc bảng đáp án từ hình ảnh...",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedButton(
                                        onClick = { showQuickInputDialog = true },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                    ) {
                                        Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Dán chuỗi", maxLines = 1)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val granted = ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.CAMERA
                                            ) == PackageManager.PERMISSION_GRANTED
                                            if (granted) {
                                                val uri = MediaCaptureUtils.createTempScanUri(context)
                                                pendingCameraUri = uri
                                                takePictureLauncher.launch(uri)
                                            } else {
                                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                            }
                                        },
                                        enabled = !uiState.isScanningKey,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                    ) {
                                        Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Chụp đáp án", maxLines = 1)
                                    }
                                    IconButton(
                                        onClick = {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        enabled = !uiState.isScanningKey,
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.PhotoLibrary,
                                            contentDescription = "Chọn ảnh bảng đáp án từ thư viện"
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Bảng đáp án [${uiState.activeCodeTab}] (Chạm chọn A, B, C, D)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    itemsIndexed(currentKeys) { index, selected ->
                        AnswerKeyRow(
                            questionNumber = index + 1,
                            selectedOption = selected,
                            onSelectOption = { opt -> viewModel.setAnswer(index, opt) }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }

    if (showAddCodeDialog) {
        var newCodeText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddCodeDialog = false },
            title = { Text("Thêm mã đề mới") },
            text = {
                OutlinedTextField(
                    value = newCodeText,
                    onValueChange = { newCodeText = it },
                    label = { Text("Mã đề (VD: 101, 102...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newCodeText.isNotBlank()) {
                            viewModel.addExamCode(newCodeText.trim())
                            showAddCodeDialog = false
                        }
                    }
                ) {
                    Text("Thêm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCodeDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }

    if (showQuickInputDialog) {
        var rawText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showQuickInputDialog = false },
            title = { Text("Dán chuỗi đáp án nhanh [${uiState.activeCodeTab}]") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Nhập liên tiếp hoặc kèm số câu (VD: ABCDABCD hoặc 1A 2B 3C 4D).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = rawText,
                        onValueChange = { rawText = it },
                        placeholder = { Text("VD: A B C D A C B D...") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.applyQuickAnswerString(rawText)
                        showQuickInputDialog = false
                    }
                ) {
                    Text("Áp dụng")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickInputDialog = false }) {
                    Text("Đóng")
                }
            }
        )
    }

    if (showCustomCountDialog) {
        var countText by remember { mutableStateOf(uiState.answerKey.size.toString()) }
        AlertDialog(
            onDismissRequest = { showCustomCountDialog = false },
            title = { Text("Số câu hỏi (1 - 120)") },
            text = {
                OutlinedTextField(
                    value = countText,
                    onValueChange = { countText = it.filter { ch -> ch.isDigit() } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = countText.toIntOrNull()
                        if (parsed != null && parsed in 1..120) {
                            viewModel.setQuestionCount(parsed)
                        }
                        showCustomCountDialog = false
                    }
                ) {
                    Text("Xác nhận")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomCountDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }
}

@Composable
private fun AnswerKeyRow(
    questionNumber: Int,
    selectedOption: String,
    onSelectOption: (String) -> Unit
) {
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
            Text(
                text = "Câu ${questionNumber.toString().padStart(2, '0')}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(68.dp)
            )
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                GradingCalculator.VALID_OPTIONS.forEach { option ->
                    val isSelected = selectedOption == option
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { onSelectOption(option) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = option,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
