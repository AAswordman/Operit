package com.ai.assistance.operit.ui.theme

import android.annotation.SuppressLint
import android.app.Activity
import android.net.Uri
import android.os.Build
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.R
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.ai.assistance.operit.data.model.ActivePrompt
import com.ai.assistance.operit.data.preferences.ActivePromptManager
import com.ai.assistance.operit.data.preferences.CharacterCardManager
import com.ai.assistance.operit.data.preferences.NativeThemePreferenceSchemaV1
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.StyledPlayerView
import java.io.File
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import io.github.fletchmckee.liquid.liquefiable
import io.github.fletchmckee.liquid.rememberLiquidState

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun OperitTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activePromptManager = remember { ActivePromptManager.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()
    val activePrompt by activePromptManager.activePromptFlow.collectAsState(
        initial = ActivePrompt.CharacterCard(CharacterCardManager.DEFAULT_CHARACTER_CARD_ID),
    )
    val themeSnapshot = rememberActiveThemePreferenceSnapshot()

    fun disableBackgroundForTarget(target: ActivePrompt) {
        coroutineScope.launch {
            activePromptManager.mutateActiveThemeForPrompt(target) { values ->
                values.withBoolean(NativeThemePreferenceSchemaV1.useBackgroundImage, false)
            }
        }
    }

    val systemDarkTheme = isSystemInDarkTheme()
    val resolvedTheme =
        resolveNativeThemeV1(
            snapshot = themeSnapshot,
            environment =
                NativeThemeEnvironment(
                    hostSurface = NativeThemeHostSurface.MAIN,
                    systemDarkTheme = systemDarkTheme,
                ),
            baseColorScheme = { darkTheme ->
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                        if (darkTheme) dynamicDarkColorScheme(context)
                        else dynamicLightColorScheme(context)
                    }
                    darkTheme -> NativeThemeV1DarkColorScheme
                    else -> NativeThemeV1LightColorScheme
                }
            },
        )
    val backgroundSpec = resolvedTheme.background
    val typographySpec = resolvedTheme.typography
    val systemChromeSpec = resolvedTheme.systemChrome
    val darkTheme = resolvedTheme.darkTheme
    val colorScheme = resolvedTheme.colorScheme
    val useBackgroundImage = backgroundSpec.enabled
    val backgroundImageUri = backgroundSpec.uri
    val backgroundImageOpacity = backgroundSpec.opacity
    val backgroundMediaType = backgroundSpec.mediaType
    val videoBackgroundMuted = backgroundSpec.videoMuted
    val videoBackgroundLoop = backgroundSpec.videoLoop
    val useCustomStatusBarColor = systemChromeSpec.useCustomStatusBarColor
    val customStatusBarColorValue = systemChromeSpec.customStatusBarColor
    val statusBarTransparent = systemChromeSpec.statusBarTransparent
    val statusBarHidden = systemChromeSpec.statusBarHidden
    val useBackgroundBlur = backgroundSpec.blurEnabled
    val backgroundBlurRadius = backgroundSpec.blurRadius

    // 创建自定义 Typography
    val customTypography = remember(typographySpec) {
        createCustomTypography(
            context = context,
            useCustomFont = typographySpec.useCustomFont,
            fontType = typographySpec.fontType,
            systemFontName = typographySpec.systemFontName,
            customFontPath = typographySpec.customFontPath,
            fontScale = typographySpec.fontScale,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = window.decorView.let { decorView ->
                androidx.core.view.WindowCompat.getInsetsController(window, decorView)
            }
            
            // 始终保持沉浸式模式，让Compose处理状态栏背景
            WindowCompat.setDecorFitsSystemWindows(window, false)

            // 隐藏或显示状态栏
            if (statusBarHidden) {
                // 隐藏状态栏
                insetsController?.hide(WindowInsetsCompat.Type.statusBars())
                insetsController?.systemBarsBehavior = 
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                // 显示状态栏
                insetsController?.show(WindowInsetsCompat.Type.statusBars())
                
                // 状态栏颜色和图标颜色控制
                val statusBarColor = when {
                    statusBarTransparent -> Color.Transparent.toArgb()
                    useBackgroundImage && backgroundImageUri != null -> Color.Transparent.toArgb()  // 有背景时透明
                    useCustomStatusBarColor && customStatusBarColorValue != null -> customStatusBarColorValue!!.toInt()
                    else -> colorScheme.primary.toArgb()
                }
                window.statusBarColor = statusBarColor

                // 根据状态栏背景色动态设置状态栏图标颜色
                // isAppearanceLightStatusBars = true 表示图标为深色（适用于浅色背景）
                // isAppearanceLightStatusBars = false 表示图标为浅色（适用于深色背景）
                insetsController?.isAppearanceLightStatusBars = isColorLight(Color(statusBarColor))
            }
            
            // 设置导航栏颜色（底部小白条所在的区域）
            // 在有背景图片时，让导航栏透明
            if (useBackgroundImage && backgroundImageUri != null) {
                // 关键：禁用导航栏对比度强制模式（Android 10+）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                // 设置为完全透明
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                // 根据主题设置导航栏图标颜色
                insetsController?.isAppearanceLightNavigationBars = !darkTheme
            } else {
                // 没有背景时使用软件背景色作为导航栏背景色
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = true
                }
                window.navigationBarColor = colorScheme.background.toArgb()
                // 根据导航栏背景色动态设置导航栏图标颜色
                // isAppearanceLightNavigationBars = true 表示图标为深色（适用于浅色背景）
                // isAppearanceLightNavigationBars = false 表示图标为浅色（适用于深色背景）
                insetsController?.isAppearanceLightNavigationBars = !isColorLight(colorScheme.background)
            }
        }
    }

    // 视频播放器状态
    val lifecycleOwner = LocalLifecycleOwner.current
    val exoPlayer =
            remember(
                    useBackgroundImage,
                    backgroundImageUri,
                    backgroundMediaType,
                    videoBackgroundLoop,
                    videoBackgroundMuted
            ) {
                if (useBackgroundImage &&
                                backgroundImageUri != null &&
                                backgroundMediaType == UserPreferencesManager.MEDIA_TYPE_VIDEO
                ) {
                    ExoPlayer.Builder(context)
                            // Add memory optimizations
                            .setLoadControl(
                                    DefaultLoadControl.Builder()
                                            .setBufferDurationsMs(
                                                    5000,  // 最小缓冲时间，减少到5秒
                                                    10000, // 最大缓冲时间，减少到10秒
                                                    500,   // 回放所需的最小缓冲
                                                    1000   // 重新缓冲后回放所需的最小缓冲
                                            )
                                            .setTargetBufferBytes(5 * 1024 * 1024) // 将缓冲限制为5MB
                                            .setPrioritizeTimeOverSizeThresholds(true)
                                            .build()
                            )
                            .build()
                            .apply {
                                // 设置循环播放
                                repeatMode =
                                        if (videoBackgroundLoop) Player.REPEAT_MODE_ALL
                                        else Player.REPEAT_MODE_OFF
                                // 设置静音
                                volume = if (videoBackgroundMuted) 0f else 1f
                                 playWhenReady =
                                         lifecycleOwner.lifecycle.currentState.isAtLeast(
                                                 Lifecycle.State.RESUMED
                                         )

                                // 加载视频
                                try {
                                    val mediaItem = MediaItem.Builder()
                                        .setUri(Uri.parse(backgroundImageUri))
                                        .build()
                                    setMediaItem(mediaItem)
                                    prepare()
                                } catch (e: Exception) {
                                    AppLogger.e(
                                            "OperitTheme",
                                            "Error loading video background: ${e.message}",
                                            e
                                    )
                                    disableBackgroundForTarget(activePrompt)
                                }
                            }
                } else {
                    null
                }
            }

    // 释放ExoPlayer资源
    DisposableEffect(exoPlayer) {
        onDispose {
            try {
                exoPlayer?.stop()
                exoPlayer?.clearMediaItems()
                exoPlayer?.release()
            } catch (e: Exception) {
                AppLogger.e("OperitTheme", "ExoPlayer释放错误", e)
            }
        }
    }

    // 监听应用生命周期，控制视频播放
    if (exoPlayer != null) {
        DisposableEffect(lifecycleOwner, exoPlayer) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> {
                        exoPlayer.playWhenReady = false
                        exoPlayer.pause()
                    }
                    Lifecycle.Event.ON_RESUME -> {
                        exoPlayer.playWhenReady = true
                        exoPlayer.play()
                    }
                    else -> {}
                }
            }

            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    // 应用主题和自定义背景
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val liquidGlassBackdrop = rememberLayerBackdrop()
        val waterGlassState = if (isWaterGlassSupported()) rememberLiquidState() else null

        CompositionLocalProvider(
            LocalThemePreferenceSnapshot provides themeSnapshot,
            LocalResolvedNativeThemeV1 provides resolvedTheme,
            LocalLiquidGlassBackdrop provides liquidGlassBackdrop,
            LocalWaterGlassState provides waterGlassState,
        ) {
            Box(
                modifier = Modifier.fillMaxSize().layerBackdrop(liquidGlassBackdrop)
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(if (darkTheme) Color.Black else Color.White)
                            .then(
                                if (waterGlassState != null) {
                                    Modifier.liquefiable(waterGlassState)
                                } else {
                                    Modifier
                                },
                            )
                )

                if (useBackgroundImage && backgroundImageUri != null) {
                    val uri = Uri.parse(backgroundImageUri)

                    if (backgroundMediaType == UserPreferencesManager.MEDIA_TYPE_IMAGE) {
                        val painter =
                            rememberAsyncImagePainter(
                                model = uri,
                                error =
                                    rememberAsyncImagePainter(
                                        if (darkTheme) Color.Black else Color.White
                                    ),
                            )

                        LaunchedEffect(painter) {
                            if (painter.state is AsyncImagePainter.State.Error) {
                                AppLogger.e(
                                    "OperitTheme",
                                    "Error loading background image from URI: $backgroundImageUri",
                                )

                                if (uri.scheme == "file") {
                                    val file = uri.path?.let { File(it) }
                                    if (file == null || !file.exists()) {
                                        AppLogger.e(
                                            "OperitTheme",
                                            "Internal file doesn't exist: ${file?.absolutePath}",
                                        )
                                    } else {
                                        AppLogger.e(
                                            "OperitTheme",
                                            "File exists but couldn't be loaded: ${file.absolutePath}, size: ${file.length()}",
                                        )
                                    }
                                }

                                disableBackgroundForTarget(activePrompt)
                            }
                        }

                        Image(
                            painter = painter,
                            contentDescription = "Background Image",
                            modifier =
                                Modifier.fillMaxSize()
                                    .alpha(backgroundImageOpacity)
                                    .then(
                                        if (useBackgroundBlur) {
                                            Modifier.blur(radius = backgroundBlurRadius.dp)
                                        } else {
                                            Modifier
                                        },
                                    ).then(
                                        if (waterGlassState != null) {
                                            Modifier.liquefiable(waterGlassState)
                                        } else {
                                            Modifier
                                        },
                                    ),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        exoPlayer?.let { player ->
                            val videoBackgroundColor =
                                if (darkTheme) {
                                    android.graphics.Color.BLACK
                                } else {
                                    android.graphics.Color.WHITE
                                }
                            AndroidView(
                                factory = { ctx ->
                                    (LayoutInflater.from(ctx).inflate(
                                        R.layout.view_background_texture_player,
                                        null,
                                        false,
                                    ) as StyledPlayerView).apply {
                                        this.player = player
                                        useController = false
                                        layoutParams =
                                            ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        setBackgroundColor(videoBackgroundColor)
                                        setShutterBackgroundColor(videoBackgroundColor)
                                        setKeepContentOnPlayerReset(true)
                                        foreground =
                                            android.graphics.drawable.ColorDrawable(
                                                android.graphics.Color.argb(
                                                    ((1f - backgroundImageOpacity) * 255).toInt(),
                                                    if (darkTheme) 0 else 255,
                                                    if (darkTheme) 0 else 255,
                                                    if (darkTheme) 0 else 255,
                                                )
                                            )
                                    }
                                },
                                update = { view ->
                                    if (view.player != player) {
                                        view.player = player
                                    }

                                    view.setBackgroundColor(videoBackgroundColor)
                                    view.setShutterBackgroundColor(videoBackgroundColor)
                                    view.setKeepContentOnPlayerReset(true)
                                    view.foreground =
                                        android.graphics.drawable.ColorDrawable(
                                            android.graphics.Color.argb(
                                                ((1f - backgroundImageOpacity) * 255).toInt(),
                                                if (darkTheme) 0 else 255,
                                                if (darkTheme) 0 else 255,
                                                if (darkTheme) 0 else 255,
                                            )
                                        )
                                },
                                modifier =
                                    Modifier.fillMaxSize().then(
                                        if (waterGlassState != null) {
                                            Modifier.liquefiable(waterGlassState)
                                        } else {
                                            Modifier
                                        },
                                    ),
                            )
                        }
                    }
                }
            }

            MaterialTheme(
                colorScheme = resolvedTheme.contentColorScheme,
                typography = customTypography,
                content = content,
            )
        }
    }
}

/** 判断颜色是否较浅 */
private fun isColorLight(color: Color): Boolean {
    // 计算颜色亮度 (0.0-1.0)
    val luminance = 0.299 * color.red + 0.587 * color.green + 0.114 * color.blue
    return luminance > 0.5
}
