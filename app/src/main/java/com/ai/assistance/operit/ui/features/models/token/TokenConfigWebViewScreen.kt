package com.ai.assistance.operit.ui.features.models.token

import com.ai.assistance.operit.util.logging.AppLogger
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import com.ai.assistance.operit.ui.theme.script.text.ThemeText as Text
import com.ai.assistance.operit.ui.components.CustomScaffold
import com.ai.assistance.operit.ui.main.LocalTopBarActions
import com.ai.assistance.operit.ui.main.components.LocalAppBarContentColor
import com.ai.assistance.operit.ui.main.components.LocalIsCurrentScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ai.assistance.operit.R
import com.ai.assistance.operit.ui.features.models.token.components.UrlConfigDialog
import com.ai.assistance.operit.ui.features.models.token.model.NavDestination
import com.ai.assistance.operit.ui.features.models.token.model.getIconForIndex
import com.ai.assistance.operit.ui.features.models.token.preferences.UrlConfigManager
import com.ai.assistance.operit.ui.features.models.token.webview.WebViewConfig
import kotlinx.coroutines.launch

/** Token配置屏幕 */
@Composable
fun TokenConfigWebViewScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val urlConfigManager = remember { UrlConfigManager(context) }
    
    // 获取URL配置
    val urlConfig by urlConfigManager.urlConfigFlow.collectAsState(initial = com.ai.assistance.operit.ui.features.models.token.model.UrlConfig())

    // State
    var isLoading by remember { mutableStateOf(true) }
    var selectedTabIndex by remember { mutableStateOf(0) }
    var showConfigDialog by remember { mutableStateOf(false) }

    // 创建WebView实例
    val webView = remember { WebViewConfig.createWebView(context) }

    // 根据配置创建导航目标
    val navDestinations = remember(urlConfig) {
        urlConfig.tabs.take(4).mapIndexed { index, tabConfig ->
            NavDestination(
                title = tabConfig.title,
                url = tabConfig.url,
                icon = getIconForIndex(index)
            )
        }
    }

    // 导航到指定URL
    fun navigateTo(url: String, index: Int) {
        isLoading = true
        webView.loadUrl(url)
        selectedTabIndex = index
    }

    // 简化的WebViewClient
    val webViewClient = remember {
        object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
            ): Boolean {
                request?.url?.let { uri ->
                    val url = uri.toString()
                    
                    // 只拦截明确需要外部应用处理的协议
                    if (url.startsWith("alipays:") || 
                        url.startsWith("alipay:") || 
                        url.startsWith("weixin:") ||
                        url.startsWith("weixins:")) {
                        
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                            return true
                        } catch (e: Exception) {
                            AppLogger.e("TokenConfigWebView", "无法打开外部应用: ${e.message}")
                            // 如果打开失败，返回false让WebView尝试处理
                            return false
                        }
                    }
                    
                    // 对于http/https链接，让WebView正常加载
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        return false
                    }
                    
                    // 对于其他协议（如javascript:, about:等），也让WebView处理
                    // 不要尝试用外部应用打开
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                isLoading = false
                
                // 更新选中的标签
                url?.let { finishedUrl ->
                        navDestinations.forEachIndexed { index, destination ->
                        if (finishedUrl.contains(destination.url) || 
                            destination.url.contains(finishedUrl)) {
                                selectedTabIndex = index
                        }
                    }
                }
            }
        }
    }

    // 设置WebView
    DisposableEffect(webView) {
        webView.webViewClient = webViewClient
        
        // 加载初始URL
        if (urlConfig.signInUrl.isNotEmpty()) {
            webView.loadUrl(urlConfig.signInUrl)
        }

        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    // 从CompositionLocal获取设置TopBar Actions的函数
    val setTopBarActions = LocalTopBarActions.current
    val appBarContentColor = LocalAppBarContentColor.current
    val isCurrentScreen = LocalIsCurrentScreen.current

    // 使用DisposableEffect来设置和清理TopBar按钮，避免竞争条件
    LaunchedEffect(isCurrentScreen, appBarContentColor) {
        if (isCurrentScreen) {
            setTopBarActions {
                // 设置按钮
                IconButton(
                    onClick = { showConfigDialog = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.settings_config),
                        tint = appBarContentColor
                    )
                }
            }
        }
    }

    // 配置对话框
    if (showConfigDialog) {
        UrlConfigDialog(
            currentConfig = urlConfig,
            onSave = { newConfig ->
                scope.launch {
                    urlConfigManager.saveUrlConfig(newConfig)
                    showConfigDialog = false
                }
            },
            onDismiss = { showConfigDialog = false }
        )
    }

    // UI布局
    CustomScaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.White,
                shadowElevation = 2.dp
                ) {
                    Column {
                        HorizontalDivider(
                                color = Color.LightGray.copy(alpha = 0.3f),
                                thickness = 0.5.dp
                        )

                    // 导航栏
                        Row(
                        modifier = Modifier
                            .fillMaxWidth()
                                                .height(60.dp)
                                                .background(Color.White),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            navDestinations.forEachIndexed { index, destination ->
                                val isSelected = selectedTabIndex == index
                                Column(
                                modifier = Modifier
                                    .weight(1f)
                                                        .fillMaxSize()
                                                        .clickable {
                                                            navigateTo(destination.url, index)
                                                        }
                                                        .padding(4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                            imageVector = destination.icon,
                                            contentDescription = destination.title,
                                            modifier = Modifier.size(24.dp),
                                    tint = if (isSelected) 
                                                            MaterialTheme.colorScheme.primary
                                                    else Color.Gray
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                            text = destination.title,
                                            fontSize = 12.sp,
                                    fontWeight = if (isSelected) 
                                        FontWeight.Medium 
                                                    else FontWeight.Normal,
                                    color = if (isSelected)
                                                            MaterialTheme.colorScheme.primary
                                                    else Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                    factory = { webView },
                    modifier = Modifier.fillMaxSize()
            )
            
            // 加载指示器
            if (isLoading) {
                LinearProgressIndicator(
                        modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent
                )
            }
        }
    }
}
