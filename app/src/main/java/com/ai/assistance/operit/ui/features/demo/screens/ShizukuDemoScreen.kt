package com.ai.assistance.operit.ui.features.demo.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.ai.assistance.operit.util.AppLogger
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ai.assistance.operit.R
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.ai.assistance.operit.ui.main.screens.Screen
import com.ai.assistance.operit.ui.main.screens.ScreenNavigationHandler
import com.ai.assistance.operit.core.tools.system.AccessibilityProviderInstaller
import com.ai.assistance.operit.core.tools.system.ShizukuAuthorizer
import com.ai.assistance.operit.core.tools.system.ShizukuInstaller
import com.ai.assistance.operit.data.repository.UIHierarchyManager
import com.ai.assistance.operit.ui.features.demo.components.*
import com.ai.assistance.operit.ui.features.demo.permissions.SystemPermissionSettings
import com.ai.assistance.operit.ui.features.demo.viewmodel.ShizukuDemoViewModel
import com.ai.assistance.operit.ui.features.demo.wizards.AccessibilityWizardCard
import com.ai.assistance.operit.ui.features.demo.wizards.OperitTerminalWizardCard
import com.ai.assistance.operit.ui.features.demo.wizards.RootWizardCard
import com.ai.assistance.operit.ui.features.demo.wizards.ShizukuWizardCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShizukuDemoScreen(
        viewModel: ShizukuDemoViewModel =
                viewModel(
                        factory =
                                ShizukuDemoViewModel.Factory(
                                        LocalContext.current.applicationContext as
                                                android.app.Application
                                )
                ),
        navigateTo: ScreenNavigationHandler? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Collect UI state from ViewModel
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // 跟踪当前显示的权限级别
    var currentDisplayedPermissionLevel by remember {
        mutableStateOf(AndroidPermissionLevel.STANDARD)
    }

    // Location permission launcher
    val locationPermissionLauncher =
            rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                val fineLocationGranted =
                        permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
                val coarseLocationGranted =
                        permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
                if (fineLocationGranted || coarseLocationGranted) {
                    scope.launch(Dispatchers.IO) { viewModel.refreshStatus(context) }
                }
            }

    // Register state change listeners
    DisposableEffect(Unit) {
        val shizukuListener: () -> Unit = {
            scope.launch(Dispatchers.IO) { viewModel.refreshStatus(context) }
        }

        ShizukuAuthorizer.addStateChangeListener(shizukuListener)

        onDispose { ShizukuAuthorizer.removeStateChangeListener(shizukuListener) }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshSystemPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 预先加载一个空的UI状态，避免初始化时的卡顿
    var isInitialized by remember { mutableStateOf(false) }

    // Initialize ViewModel
    LaunchedEffect(Unit) {
        // 显示加载指示器
        viewModel.setLoading(true)

        // 在IO线程上执行所有初始化
        withContext(Dispatchers.IO) {
            // 将初始化任务拆分成多个小任务，避免长时间阻塞
            viewModel.initializeAsync(context)
        }

        // 标记初始化完成
        isInitialized = true
    }

    Column(
            modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 加载指示器
        if (uiState.isLoading.value) {
            Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(context.getString(R.string.loading_app_state), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        // 检查无障碍服务版本状态
        val (accessibilityInstalledVersion, accessibilityBundledVersion, isAccessibilityUpdateNeeded) =
                remember(uiState.isRefreshing.value) {
                    val installed = AccessibilityProviderInstaller.getInstalledVersion(context)
                    val bundled = AccessibilityProviderInstaller.getBundledVersion(context)
                    val needsUpdate = AccessibilityProviderInstaller.isUpdateNeeded(context)
                    Triple(installed, bundled, needsUpdate)
                }

        // 权限管理卡片
        PermissionLevelCard(
                hasStoragePermission = uiState.hasStoragePermission.value,
                hasOverlayPermission = uiState.hasOverlayPermission.value,
                hasBatteryOptimizationExemption = uiState.hasBatteryOptimizationExemption.value,
                systemPermissions = uiState.systemPermissions.value,
                hasAccessibilityServiceEnabled = uiState.hasAccessibilityServiceEnabled.value,
                hasLocationPermission = uiState.hasLocationPermission.value,
                isShizukuInstalled = uiState.isShizukuInstalled.value,
                isShizukuRunning = uiState.isShizukuRunning.value,
                hasShizukuPermission = uiState.hasShizukuPermission.value,
                isOperitTerminalInstalled = uiState.isOperitTerminalInstalled.value,
                isDeviceRooted = uiState.isDeviceRooted.value,
                hasRootAccess = uiState.hasRootAccess.value,
                isAccessibilityProviderInstalled = uiState.isAccessibilityProviderInstalled.value,
                isAccessibilityUpdateNeeded = isAccessibilityUpdateNeeded,
                isRefreshing = uiState.isRefreshing.value,
                onRefresh = {
                    scope.launch(Dispatchers.IO) {
                        // 手动刷新时，清除版本缓存以获取最新状态
                        AccessibilityProviderInstaller.clearCache()
                        ShizukuInstaller.clearCache()
                        AppLogger.d("ShizukuDemoScreen", "手动刷新：已清除无障碍和Shizuku版本缓存")
                        viewModel.refreshStatus(context)
                    }
                },
                onStoragePermissionClick = {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            // Android 11+: Go to manage all files permission page
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            context.startActivity(intent)
                        } else {
                            // Android 10-: Go to app settings page
                            val intent =
                                    Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.parse("package:" + context.packageName)
                                    )
                            context.startActivity(intent)
                        }
                    } catch (e: Exception) {
                        // Fall back to app settings
                        try {
                            val intent =
                                    Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.parse("package:" + context.packageName)
                                    )
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, context.getString(R.string.cannot_open_permission_settings), Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onOverlayPermissionClick = {
                    try {
                        val intent =
                                Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:" + context.packageName)
                                )
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, context.getString(R.string.cannot_open_overlay_settings), Toast.LENGTH_SHORT).show()
                    }
                },
                onBatteryOptimizationClick = {
                    try {
                        val intent =
                                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:" + context.packageName)
                                }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                                                    Toast.makeText(context, context.getString(R.string.cannot_open_battery_settings), Toast.LENGTH_SHORT).show()
                    }
                },
                onSystemPermissionClick = { permission ->
                    if (!SystemPermissionSettings.open(context, permission)) {
                        Toast.makeText(context, context.getString(R.string.shizuku_demo_permission_failed), Toast.LENGTH_SHORT).show()
                    }
                },
                onAccessibilityClick = {
                    try {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, context.getString(R.string.cannot_open_accessibility_settings), Toast.LENGTH_SHORT).show()
                    }
                },
                onInstallAccessibilityProviderClick = {
                    scope.launch(Dispatchers.IO) {
                        if (!UIHierarchyManager.isProviderAppInstalled(context)) {
                            UIHierarchyManager.launchProviderInstall(context)
                        } else {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, context.getString(R.string.accessibility_provider_installed), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                onLocationPermissionClick = {
                    // 请求位置权限
                    locationPermissionLauncher.launch(
                            arrayOf(
                                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                    )
                },
                onShizukuClick = {
                    // 如果Shizuku未完全设置，则显示向导
                    if (!uiState.isShizukuInstalled.value ||
                                    !uiState.isShizukuRunning.value ||
                                    !uiState.hasShizukuPermission.value
                    ) {
                        viewModel.toggleShizukuWizard()
                    }
                },
                onOperitTerminalClick = {
                    // 点击时总是打开向导
                    viewModel.toggleOperitTerminalWizard()
                },
                onRootClick = {
                    // 处理Root权限
                    if (currentDisplayedPermissionLevel == AndroidPermissionLevel.ROOT) {
                        // 如果当前正在浏览ROOT权限级别，则显示或隐藏Root向导
                        viewModel.toggleRootWizard()
                    }
                },
                onPermissionLevelChange = { level -> currentDisplayedPermissionLevel = level },
                onPermissionLevelSet = { _ ->
                    // 当设置了新的权限级别时，刷新工具
                    scope.launch { viewModel.refreshTools(context) }
                }
        )

        // 组合向导卡片到一个专门的设置区域 - 现在检查NodeJS和Python环境
        val needOperitTerminalSetupGuide = !viewModel.isNodejsPythonEnvironmentReady.value

        // 检查Shizuku版本状态 - 使用remember缓存结果，避免每次重组时重复调用
        val (installedVersion, bundledVersion, isUpdateNeeded) =
                remember(uiState.isRefreshing.value) {
                    val installed = ShizukuInstaller.getInstalledShizukuVersion(context)
                    val bundled = ShizukuInstaller.getBundledShizukuVersion(context)
                    val needsUpdate = ShizukuInstaller.isShizukuUpdateNeeded(context)
                    AppLogger.d(
                            "ShizukuDemo",
                            "缓存Shizuku版本状态 - 已安装: $installed, 内置: $bundled, 需要更新: $needsUpdate"
                    )
                    Triple(installed, bundled, needsUpdate)
                }

        val needShizukuSetupGuide =
                currentDisplayedPermissionLevel == AndroidPermissionLevel.ADMIN &&
                        ((!uiState.isShizukuInstalled.value ||
                                !uiState.isShizukuRunning.value ||
                                !uiState.hasShizukuPermission.value) ||
                                // 如果Shizuku已完全设置但有更新可用，也显示向导
                                (uiState.isShizukuInstalled.value &&
                                        uiState.isShizukuRunning.value &&
                                        uiState.hasShizukuPermission.value &&
                                        isUpdateNeeded))

        val needRootSetupGuide =
                currentDisplayedPermissionLevel == AndroidPermissionLevel.ROOT &&
                        (!uiState.hasRootAccess.value)
    
        val needAccessibilitySetupGuide =
            currentDisplayedPermissionLevel == AndroidPermissionLevel.ADMIN &&
                    (!uiState.isAccessibilityProviderInstalled.value ||
                            !uiState.hasAccessibilityServiceEnabled.value ||
                            isAccessibilityUpdateNeeded)


        val needSetupGuide = needOperitTerminalSetupGuide || needShizukuSetupGuide || needRootSetupGuide || needAccessibilitySetupGuide

        if (needSetupGuide) {
            Spacer(modifier = Modifier.height(16.dp))

            // 修改为左对齐带图标的标题样式
            Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = context.getString(R.string.setup_wizard_icon_desc),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                        text = context.getString(R.string.setup_wizard),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                )
            }

            // 添加分割线
            HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )

            // Accessibility向导卡片
            if (needAccessibilitySetupGuide) {
                AccessibilityWizardCard(
                    isProviderInstalled = uiState.isAccessibilityProviderInstalled.value,
                    isServiceEnabled = uiState.hasAccessibilityServiceEnabled.value,
                    showWizard = uiState.showAccessibilityWizard.value,
                    onToggleWizard = { viewModel.toggleAccessibilityWizard() },
                    onInstallProvider = {
                        scope.launch(Dispatchers.IO) {
                            UIHierarchyManager.launchProviderInstall(context)
                        }
                    },
                    onOpenAccessibilitySettings = {
                        try {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, context.getString(R.string.cannot_open_accessibility_settings), Toast.LENGTH_SHORT).show()
                        }
                    },
                    updateNeeded = isAccessibilityUpdateNeeded,
                    installedVersion = accessibilityInstalledVersion,
                    bundledVersion = accessibilityBundledVersion,
                    onUpdateProvider = {
                        scope.launch(Dispatchers.IO) {
                            UIHierarchyManager.launchProviderInstall(context)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }


            // Root向导卡片 - 如果当前浏览的是ROOT权限级别且Root未获取
            if (needRootSetupGuide) {
                RootWizardCard(
                        isDeviceRooted = uiState.isDeviceRooted.value,
                        hasRootAccess = uiState.hasRootAccess.value,
                        showWizard = uiState.showRootWizard.value,
                        onToggleWizard = { viewModel.toggleRootWizard() },
                        onRequestRoot = {
                            scope.launch(Dispatchers.IO) {
                                viewModel.requestRootPermission(context)
                            }
                        },
                        onWatchTutorial = {
                            try {
                                val videoUrl = "https://magiskmanager.com/"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, context.getString(R.string.cannot_open_root_tutorial), Toast.LENGTH_SHORT).show()
                            }
                        }
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Shizuku向导卡片 - 在管理员模式下展示独立的 Shizuku 授权向导
            if (needShizukuSetupGuide) {
                ShizukuWizardCard(
                        isShizukuInstalled = uiState.isShizukuInstalled.value,
                        isShizukuRunning = uiState.isShizukuRunning.value,
                        hasShizukuPermission = uiState.hasShizukuPermission.value,
                        showWizard = uiState.showShizukuWizard.value,
                        onToggleWizard = { viewModel.toggleShizukuWizard() },
                        onInstallFromStore = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW)
                                intent.data =
                                        Uri.parse("https://shizuku.rikka.app/zh-hans/download/")
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(
                                                context,
                                                context.getString(
                                                        R.string.toast_download_link_failed
                                                ),
                                                Toast.LENGTH_SHORT
                                        )
                                        .show()
                            }
                        },
                        onInstallBundled = {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    AppLogger.d("ShizukuDemo", "开始安装内置Shizuku")
                                    // 提取APK并安装，无论是否已安装
                                    val apkFile = ShizukuInstaller.extractApkFromAssets(context)
                                    if (apkFile == null) {
                                        AppLogger.e("ShizukuDemo", "提取APK失败")
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(
                                                            context,
                                                            context.getString(
                                                                    R.string
                                                                            .toast_apk_extract_failed
                                                            ),
                                                            Toast.LENGTH_SHORT
                                                    )
                                                    .show()
                                        }
                                        return@launch
                                    }

                                    AppLogger.d(
                                            "ShizukuDemo",
                                            "APK提取成功: ${apkFile.absolutePath}, 大小: ${apkFile.length()} 字节"
                                    )

                                    // 生成APK的URI
                                    val apkUri =
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                                FileProvider.getUriForFile(
                                                        context,
                                                        "${context.packageName}.fileprovider",
                                                        apkFile
                                                )
                                            } else {
                                                Uri.fromFile(apkFile)
                                            }

                                    AppLogger.d("ShizukuDemo", "生成APK URI: $apkUri")

                                    // 创建安装意图
                                    val installIntent =
                                            Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(
                                                        apkUri,
                                                        "application/vnd.android.package-archive"
                                                )
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                                                ) {
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                            }

                                    AppLogger.d("ShizukuDemo", "启动安装界面")

                                    // 启动安装界面
                                    withContext(Dispatchers.Main) {
                                        context.startActivity(installIntent)
                                        Toast.makeText(
                                                        context,
                                                        context.getString(
                                                                R.string.shizuku_demo_install_notify
                                                        ),
                                                        Toast.LENGTH_LONG
                                                )
                                                .show()
                                    }
                                } catch (e: Exception) {
                                    AppLogger.e("ShizukuDemo", "安装内置Shizuku时出错", e)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                                        context,
                                                        context.getString(
                                                                R.string.toast_operation_failed,
                                                                e.message ?: ""
                                                        ),
                                                        Toast.LENGTH_SHORT
                                                )
                                                .show()
                                    }
                                }
                            }
                        },
                        onOpenShizuku = {
                            try {
                                val intent =
                                        context.packageManager.getLaunchIntentForPackage(
                                                "moe.shizuku.privileged.api"
                                        )
                                if (intent != null) {
                                    AppLogger.d("ShizukuDemo", "打开Shizuku应用")
                                    context.startActivity(intent)
                                } else {
                                    AppLogger.e("ShizukuDemo", "无法找到Shizuku应用")
                                    Toast.makeText(context, context.getString(R.string.cannot_find_shizuku_app), Toast.LENGTH_SHORT)
                                            .show()
                                }
                            } catch (e: Exception) {
                                AppLogger.e("ShizukuDemo", "无法启动Shizuku应用", e)
                                Toast.makeText(context, context.getString(R.string.cannot_start_shizuku_app), Toast.LENGTH_SHORT).show()
                            }
                        },
                        onWatchTutorial = {
                            try {
                                val videoUrl = "https://shizuku.rikka.app/zh-hans/guide/setup/"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, context.getString(R.string.cannot_open_doc_link), Toast.LENGTH_SHORT).show()
                            }
                        },
                        onRequestPermission = {
                            scope.launch {
                                AppLogger.d("ShizukuDemo", "请求Shizuku权限")
                                ShizukuAuthorizer.requestShizukuPermission { granted ->
                                    AppLogger.d("ShizukuDemo", "Shizuku权限请求结果: $granted")
                                    scope.launch(Dispatchers.Main) {
                                        if (granted) {
                                            Toast.makeText(
                                                            context,
                                                            context.getString(
                                                                    R.string
                                                                            .shizuku_demo_shizuku_permission_granted
                                                            ),
                                                            Toast.LENGTH_SHORT
                                                    )
                                                    .show()
                                        } else {
                                            Toast.makeText(
                                                            context,
                                                            context.getString(
                                                                    R.string
                                                                            .shizuku_demo_shizuku_permission_denied
                                                            ),
                                                            Toast.LENGTH_SHORT
                                                    )
                                                    .show()
                                        }
                                    }

                                    scope.launch(Dispatchers.IO) {
                                        viewModel.refreshStatus(context)
                                    }
                                }
                            }
                        },
                        updateNeeded = isUpdateNeeded,
                        onUpdateShizuku = {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    AppLogger.d("ShizukuDemo", "开始更新Shizuku")
                                    // 提取APK并安装，无论是否已安装
                                    val apkFile = ShizukuInstaller.extractApkFromAssets(context)
                                    if (apkFile == null) {
                                        AppLogger.e("ShizukuDemo", "提取APK失败")
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(
                                                            context,
                                                            context.getString(
                                                                    R.string
                                                                            .toast_apk_extract_failed
                                                            ),
                                                            Toast.LENGTH_SHORT
                                                    )
                                                    .show()
                                        }
                                        return@launch
                                    }

                                    AppLogger.d(
                                            "ShizukuDemo",
                                            "APK提取成功: ${apkFile.absolutePath}, 大小: ${apkFile.length()} 字节"
                                    )

                                    // 生成APK的URI
                                    val apkUri =
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                                FileProvider.getUriForFile(
                                                        context,
                                                        "${context.packageName}.fileprovider",
                                                        apkFile
                                                )
                                            } else {
                                                Uri.fromFile(apkFile)
                                            }

                                    AppLogger.d("ShizukuDemo", "生成APK URI: $apkUri")

                                    // 创建安装意图
                                    val installIntent =
                                            Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(
                                                        apkUri,
                                                        "application/vnd.android.package-archive"
                                                )
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                                                ) {
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                            }

                                    AppLogger.d("ShizukuDemo", "启动更新界面")

                                    // 启动安装界面
                                    withContext(Dispatchers.Main) {
                                        context.startActivity(installIntent)
                                        Toast.makeText(
                                                        context,
                                                        context.getString(
                                                                R.string.shizuku_demo_update_notify
                                                        ),
                                                        Toast.LENGTH_LONG
                                                )
                                                .show()
                                    }
                                } catch (e: Exception) {
                                    AppLogger.e("ShizukuDemo", "更新Shizuku时出错", e)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(
                                                        context,
                                                        context.getString(
                                                                R.string.toast_operation_failed,
                                                                e.message ?: ""
                                                        ),
                                                        Toast.LENGTH_SHORT
                                                )
                                                .show()
                                    }
                                }
                            }
                        },
                        // 传递已缓存的版本信息，避免重复调用API
                        installedVersion = installedVersion,
                        bundledVersion = bundledVersion
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            // NodeJS和Python环境配置向导卡片
            if (needOperitTerminalSetupGuide) {
                OperitTerminalWizardCard(
                    isPnpmInstalled = viewModel.isPnpmInstalled.value,
                    isPipInstalled = viewModel.isPythonInstalled.value,
                    isEnvironmentReady = viewModel.isNodejsPythonEnvironmentReady.value,
                    showWizard = uiState.showOperitTerminalWizard.value,
                    onToggleWizard = { viewModel.toggleOperitTerminalWizard() },
                    onOpenTerminalScreen = { 
                        // 跳转到TerminalSetup，直接显示配置界面
                        navigateTo?.invoke(Screen.TerminalSetup)
                    }
                )
            }
        }
    }
}
