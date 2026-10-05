package com.ai.assistance.operit.ui.features.permission.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.ai.assistance.operit.util.AppLogger
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.ui.features.permission.viewmodel.PermissionGuideViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val INTRO_PAGES_COUNT = 3
private const val WELCOME_PAGE_INDEX = INTRO_PAGES_COUNT
private const val BASIC_PERMISSIONS_PAGE_INDEX = INTRO_PAGES_COUNT + 1
private const val PERMISSION_LEVEL_PAGE_INDEX = INTRO_PAGES_COUNT + 2
private const val TOTAL_PAGES_COUNT = INTRO_PAGES_COUNT + 3

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PermissionGuideScreen(
        viewModel: PermissionGuideViewModel = viewModel(),
        onComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 状态
    val uiState by viewModel.uiState.collectAsState()
    val pagerState = rememberPagerState(pageCount = { TOTAL_PAGES_COUNT })

    // 警告对话框状态
    var showPermissionWarning by remember { mutableStateOf(false) }

    // 初始化
    LaunchedEffect(Unit) { viewModel.checkPermissions(context) }

    // 存储权限请求启动器 (适用于Android 10及以下版本)
    val storagePermissionLauncher =
            rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    val readGranted =
                            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: false
                    val writeGranted =
                            permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: false
                    if (readGranted && writeGranted) {
                        // 使用已存在的方法检查权限状态，而不是直接更新
                        viewModel.checkPermissions(context)
                    }
                }
            }

    // 位置权限请求启动器
    val locationPermissionLauncher =
            rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
                val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
                if (fineGranted || coarseGranted) {
                    viewModel.updateLocationPermission(true)
                }
            }

    // 页面切换效果
    LaunchedEffect(pagerState.currentPage) {
        when (pagerState.currentPage) {
            in 0..WELCOME_PAGE_INDEX ->
                viewModel.setCurrentStep(PermissionGuideViewModel.Step.WELCOME)
            BASIC_PERMISSIONS_PAGE_INDEX ->
                viewModel.setCurrentStep(PermissionGuideViewModel.Step.BASIC_PERMISSIONS)
            PERMISSION_LEVEL_PAGE_INDEX ->
                viewModel.setCurrentStep(PermissionGuideViewModel.Step.PERMISSION_LEVEL)
        }
    }

    // 完成设置后的回调
    LaunchedEffect(uiState.isCompleted) {
        if (uiState.isCompleted) {
            delay(500) // 短暂延迟，让用户看到完成状态
            onComplete()
        }
    }

    // 权限警告对话框
    if (showPermissionWarning) {
        AlertDialog(
                onDismissRequest = { showPermissionWarning = false },
                icon = {
                    Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                    )
                },
                title = {
                    Text(
                            text = stringResource(R.string.permission_guide_warning_title),
                            style = MaterialTheme.typography.titleMedium
                    )
                },
                text = {
                    Text(
                            text = stringResource(R.string.permission_guide_warning_message),
                            style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                            onClick = {
                                showPermissionWarning = false
                                scope.launch {
                                    pagerState.animateScrollToPage(PERMISSION_LEVEL_PAGE_INDEX)
                                }
                            }
                    ) { Text(stringResource(R.string.permission_guide_warning_continue)) }
                },
                dismissButton = {
                    TextButton(onClick = { showPermissionWarning = false }) {
                        Text(stringResource(R.string.permission_guide_warning_return))
                    }
                }
        )
    }

    Column(
            modifier =
                    Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 进度指示器
        LinearProgressIndicator(
                progress = { (pagerState.currentPage + 1).toFloat() / pagerState.pageCount },
                modifier =
                        Modifier.fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        // 主内容
        HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                userScrollEnabled = false
        ) { page ->
            when (page) {
                0 ->
                        IntroductionPage(
                                title = stringResource(R.string.permission_guide_intro_1_title),
                                description =
                                        stringResource(R.string.permission_guide_intro_1_desc),
                                pageIndex = 0
                        )
                1 ->
                        IntroductionPage(
                                title = stringResource(R.string.permission_guide_intro_2_title),
                                description =
                                        stringResource(R.string.permission_guide_intro_2_desc),
                                pageIndex = 1
                        )
                2 ->
                        IntroductionPage(
                                title = stringResource(R.string.permission_guide_intro_3_title),
                                description =
                                        stringResource(R.string.permission_guide_intro_3_desc),
                                pageIndex = 2
                        )
                WELCOME_PAGE_INDEX -> WelcomePage()
                BASIC_PERMISSIONS_PAGE_INDEX ->
                        BasicPermissionsPage(
                                hasStoragePermission = uiState.hasStoragePermission,
                                hasOverlayPermission = uiState.hasOverlayPermission,
                                hasBatteryOptimizationExemption =
                                        uiState.hasBatteryOptimizationExemption,
                                hasLocationPermission = uiState.hasLocationPermission,
                                onStoragePermissionClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        // Android 11+: 使用更精确的ALL_FILES_ACCESS权限页面
                                        try {
                                            val intent =
                                                    Intent(
                                                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                                                            )
                                                            .apply {
                                                                data =
                                                                        Uri.parse(
                                                                                "package:${context.packageName}"
                                                                        )
                                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            AppLogger.e("PermissionGuide", "无法直接打开应用存储权限页面", e)
                                            // 回退到通用设置页面
                                            try {
                                                val intent =
                                                        Intent(
                                                                Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                                                        )
                                                context.startActivity(intent)
                                            } catch (e2: Exception) {
                                                Toast.makeText(
                                                                context,
                                                                context.getString(
                                                                        R.string
                                                                                .permission_guide_storage_setting_failed
                                                                ),
                                                                Toast.LENGTH_SHORT
                                                        )
                                                        .show()
                                            }
                                        }
                                    } else {
                                        // Android 10及以下: 使用标准权限请求API
                                        storagePermissionLauncher.launch(
                                                arrayOf(
                                                        Manifest.permission.READ_EXTERNAL_STORAGE,
                                                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                                                )
                                        )
                                    }
                                },
                                onOverlayPermissionClick = {
                                    try {
                                        // 直接使用包名跳转到悬浮窗权限页面
                                        val intent =
                                                Intent(
                                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                        Uri.parse("package:" + context.packageName)
                                                )
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                                        context,
                                                        context.getString(
                                                                R.string
                                                                        .permission_guide_overlay_setting_failed
                                                        ),
                                                        Toast.LENGTH_SHORT
                                                )
                                                .show()
                                    }
                                },
                                onBatteryOptimizationClick = {
                                    try {
                                        // 直接请求忽略电池优化，无需用户搜索应用
                                        val intent =
                                                Intent(
                                                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                                        )
                                                        .apply {
                                                            data =
                                                                    Uri.parse(
                                                                            "package:" +
                                                                                    context.packageName
                                                                    )
                                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        // 如果直接请求失败，尝试打开电池优化设置页面
                                        try {
                                            val intent =
                                                    Intent(
                                                            Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                                                    )
                                            context.startActivity(intent)
                                            Toast.makeText(
                                                            context,
                                                            context.getString(
                                                                    R.string
                                                                            .permission_guide_battery_hint
                                                            ),
                                                            Toast.LENGTH_LONG
                                                    )
                                                    .show()
                                        } catch (e2: Exception) {
                                            Toast.makeText(
                                                            context,
                                                            context.getString(
                                                                    R.string
                                                                            .permission_guide_battery_setting_failed
                                                            ),
                                                            Toast.LENGTH_SHORT
                                                    )
                                                    .show()
                                        }
                                    }
                                },
                                onLocationPermissionClick = {
                                    // 直接请求位置权限
                                    locationPermissionLauncher.launch(
                                            arrayOf(
                                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                    )
                                },
                                onRefresh = { viewModel.checkPermissions(context) }
                        )
                PERMISSION_LEVEL_PAGE_INDEX ->
                        PermissionLevelPage(
                                selectedLevel = uiState.selectedPermissionLevel,
                                onLevelSelected = { level ->
                                    viewModel.selectPermissionLevel(level)
                                },
                                onConfirm = { viewModel.savePermissionLevel() }
                        )
            }
        }

        // 底部导航按钮
        Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
        ) {
            // 上一步按钮
            Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                IconButton(
                        onClick = {
                            scope.launch {
                                if (pagerState.currentPage > 0) {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            }
                        },
                        enabled = pagerState.currentPage > 0
                ) {
                    Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.permission_guide_previous),
                            tint =
                                    if (pagerState.currentPage > 0)
                                            MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            }

            // 当前步骤文本
            Text(
                    text =
                            when (pagerState.currentPage) {
                                in 0 until INTRO_PAGES_COUNT ->
                                        stringResource(
                                                R.string.permission_guide_intro_page_indicator,
                                                pagerState.currentPage + 1,
                                                INTRO_PAGES_COUNT
                                        )
                                WELCOME_PAGE_INDEX -> stringResource(R.string.permission_guide_welcome)
                                BASIC_PERMISSIONS_PAGE_INDEX ->
                                        stringResource(R.string.permission_guide_basic_permissions)
                                PERMISSION_LEVEL_PAGE_INDEX ->
                                        stringResource(R.string.permission_guide_permission_level)
                                else -> ""
                            },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
            )

            // 下一步按钮
            Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                IconButton(
                        onClick = {
                            scope.launch {
                                when {
                                    // 最后一页且已选择权限级别，完成设置
                                    pagerState.currentPage == PERMISSION_LEVEL_PAGE_INDEX &&
                                            uiState.selectedPermissionLevel != null -> {
                                        viewModel.savePermissionLevel()
                                    }
                                    // 在基础权限页但未获得所有权限时，显示警告对话框
                                    pagerState.currentPage == BASIC_PERMISSIONS_PAGE_INDEX &&
                                            !uiState.allBasicPermissionsGranted -> {
                                        showPermissionWarning = true
                                    }
                                    // 否则前进到下一页
                                    pagerState.currentPage < pagerState.pageCount - 1 -> {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                }
                            }
                        },
                        enabled =
                                when (pagerState.currentPage) {
                                    in 0..WELCOME_PAGE_INDEX -> true // 介绍页和欢迎页总是可以前进
                                    BASIC_PERMISSIONS_PAGE_INDEX ->
                                            true // 基础权限页现在始终可以前进，但会有警告弹窗
                                    PERMISSION_LEVEL_PAGE_INDEX ->
                                            uiState.selectedPermissionLevel != null // 权限级别页需要已选择级别
                                    else -> false
                                }
                ) {
                    Icon(
                            imageVector =
                                    if (pagerState.currentPage == PERMISSION_LEVEL_PAGE_INDEX)
                                            Icons.Default.Check
                                    else Icons.Default.ArrowForward,
                            contentDescription =
                                    if (pagerState.currentPage == PERMISSION_LEVEL_PAGE_INDEX)
                                            stringResource(R.string.permission_guide_complete)
                                    else stringResource(R.string.permission_guide_next),
                            tint =
                                    when {
                                        pagerState.currentPage < BASIC_PERMISSIONS_PAGE_INDEX ->
                                                MaterialTheme.colorScheme.primary
                                        pagerState.currentPage == BASIC_PERMISSIONS_PAGE_INDEX ->
                                                MaterialTheme.colorScheme.primary // 基础权限页总是显示可点击状态
                                        pagerState.currentPage ==
                                                PERMISSION_LEVEL_PAGE_INDEX &&
                                                uiState.selectedPermissionLevel != null ->
                                                MaterialTheme.colorScheme.primary
                                        else ->
                                                MaterialTheme.colorScheme.onSurface.copy(
                                                        alpha = 0.3f
                                                )
                                    }
                    )
                }
            }
        }
    }
}

@Composable
private fun IntroductionPage(title: String, description: String, pageIndex: Int) {
    Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
    ) {
        Box(
                modifier =
                        Modifier.size(80.dp)
                                .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        CircleShape
                                )
                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
        ) {
            Text(
                    text = "#${pageIndex + 1}",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun WelcomePage() {
    Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
    ) {
        Text(
                text = stringResource(R.string.permission_guide_welcome_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
                text = stringResource(R.string.permission_guide_welcome_desc),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
                text = stringResource(R.string.permission_guide_welcome_hint),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(48.dp))

        Text(
                text = stringResource(R.string.permission_guide_welcome_start),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun BasicPermissionsPage(
        hasStoragePermission: Boolean,
        hasOverlayPermission: Boolean,
        hasBatteryOptimizationExemption: Boolean,
        hasLocationPermission: Boolean,
        onStoragePermissionClick: () -> Unit,
        onOverlayPermissionClick: () -> Unit,
        onBatteryOptimizationClick: () -> Unit,
        onLocationPermissionClick: () -> Unit,
        onRefresh: () -> Unit
) {
    var refreshRotation by remember { mutableStateOf(0f) }
    val rotationAngle by
            animateFloatAsState(
                    targetValue = refreshRotation,
                    animationSpec = tween(500),
                    label = "Refresh Rotation"
            )

    Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
                text = stringResource(R.string.permission_guide_basic_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
                text = stringResource(R.string.permission_guide_basic_desc),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 权限列表卡片
        Card(
                modifier = Modifier.fillMaxWidth(),
                colors =
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 存储权限
                PermissionItem(
                        title = stringResource(R.string.permission_guide_storage_title),
                        description = stringResource(R.string.permission_guide_storage_desc),
                        isGranted = hasStoragePermission,
                        onClick = onStoragePermissionClick
                )

                HorizontalDivider()

                // 悬浮窗权限
                PermissionItem(
                        title = stringResource(R.string.permission_guide_overlay_title),
                        description = stringResource(R.string.permission_guide_overlay_desc),
                        isGranted = hasOverlayPermission,
                        onClick = onOverlayPermissionClick
                )

                HorizontalDivider()

                // 电池优化豁免
                PermissionItem(
                        title = stringResource(R.string.permission_guide_battery_title),
                        description = stringResource(R.string.permission_guide_battery_desc),
                        isGranted = hasBatteryOptimizationExemption,
                        onClick = onBatteryOptimizationClick
                )

                HorizontalDivider()

                // 位置权限
                PermissionItem(
                        title = stringResource(R.string.permission_guide_location_title),
                        description = stringResource(R.string.permission_guide_location_desc),
                        isGranted = hasLocationPermission,
                        onClick = onLocationPermissionClick
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 刷新按钮
        OutlinedButton(
                onClick = {
                    refreshRotation += 360f
                    onRefresh()
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription =
                            stringResource(R.string.permission_guide_check_permissions),
                    modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = rotationAngle }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.permission_guide_check_permissions))
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 权限状态提示
        val allGranted =
                hasStoragePermission &&
                        hasOverlayPermission &&
                        hasBatteryOptimizationExemption &&
                        hasLocationPermission

        AnimatedVisibility(visible = allGranted, enter = fadeIn(), exit = fadeOut()) {
            Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier =
                            Modifier.fillMaxWidth()
                                    .padding(8.dp)
                                    .background(
                                            color =
                                                    MaterialTheme.colorScheme.primary.copy(
                                                            alpha = 0.1f
                                                    ),
                                            shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(12.dp)
            ) {
                Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                        text = stringResource(R.string.permission_guide_all_granted),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun PermissionItem(
        title: String,
        description: String,
        isGranted: Boolean,
        onClick: () -> Unit
) {
    Row(
            modifier =
                    Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        Box(
                modifier =
                        Modifier.size(24.dp)
                                .background(
                                        color =
                                                if (isGranted)
                                                        MaterialTheme.colorScheme.primary.copy(
                                                                alpha = 0.1f
                                                        )
                                                else
                                                        MaterialTheme.colorScheme.error.copy(
                                                                alpha = 0.1f
                                                        ),
                                        shape = CircleShape
                                )
                                .border(
                                        width = 1.dp,
                                        color =
                                                if (isGranted) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.error,
                                        shape = CircleShape
                                ),
                contentAlignment = Alignment.Center
        ) {
            if (isGranted) {
                Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.permission_guide_granted),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                )
            } else {
                Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.permission_guide_not_granted),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun PermissionLevelPage(
        selectedLevel: AndroidPermissionLevel?,
        onLevelSelected: (AndroidPermissionLevel) -> Unit,
        onConfirm: () -> Unit
) {
    Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
                text = stringResource(R.string.permission_guide_level_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
                text = stringResource(R.string.permission_guide_level_desc),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 权限级别选择
        Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 标准权限
            PermissionLevelItem(
                    level = AndroidPermissionLevel.STANDARD,
                    title = stringResource(R.string.permission_guide_standard_title),
                    description = stringResource(R.string.permission_guide_standard_desc),
                    isSelected = selectedLevel == AndroidPermissionLevel.STANDARD,
                    onClick = { onLevelSelected(AndroidPermissionLevel.STANDARD) }
            )

            // 管理员模式分别管理无障碍与 Shizuku 授权。
            PermissionLevelItem(
                    level = AndroidPermissionLevel.ADMIN,
                    title = stringResource(R.string.permission_level_admin),
                    description = stringResource(R.string.permission_level_admin_desc),
                    isSelected = selectedLevel == AndroidPermissionLevel.ADMIN,
                    onClick = { onLevelSelected(AndroidPermissionLevel.ADMIN) }
            )

            // Root权限
            PermissionLevelItem(
                    level = AndroidPermissionLevel.ROOT,
                    title = stringResource(R.string.permission_guide_root_title),
                    description = stringResource(R.string.permission_guide_root_desc),
                    isSelected = selectedLevel == AndroidPermissionLevel.ROOT,
                    onClick = { onLevelSelected(AndroidPermissionLevel.ROOT) }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 确认按钮
        Button(
                onClick = onConfirm,
                enabled = selectedLevel != null,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                colors =
                        ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                disabledContainerColor =
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                disabledContentColor =
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
        ) {
            Text(
                    text = stringResource(R.string.permission_guide_confirm),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 提示文本
        Text(
                text = stringResource(R.string.permission_guide_change_anytime),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun PermissionLevelItem(
        level: AndroidPermissionLevel,
        title: String,
        description: String,
        isSelected: Boolean,
        onClick: () -> Unit
) {
    Surface(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
            shape = RoundedCornerShape(8.dp),
            color =
                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    else MaterialTheme.colorScheme.surface,
            border =
                    if (isSelected)
                            androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary
                            )
                    else null
    ) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            // 选择指示器
            Box(
                    modifier =
                            Modifier.size(20.dp)
                                    .background(
                                            color =
                                                    if (isSelected)
                                                            MaterialTheme.colorScheme.primary
                                                    else Color.Transparent,
                                            shape = CircleShape
                                    )
                                    .border(
                                            width = 1.dp,
                                            color =
                                                    if (isSelected)
                                                            MaterialTheme.colorScheme.primary
                                                    else
                                                            MaterialTheme.colorScheme.onSurface
                                                                    .copy(alpha = 0.5f),
                                            shape = CircleShape
                                    ),
                    contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.permission_guide_selected),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color =
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                )

                Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}
