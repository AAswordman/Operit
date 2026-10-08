package com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * 编辑器主题定义
 */
data class EditorTheme(
    // 基础颜色
    val background: Color,
    val textColor: Color,
    val cursorColor: Color,
    val selectionColor: Color,
    // 搜索颜色由编辑器主题提供，画布中不再固定颜色。
    val searchMatchColor: Color,
    val activeSearchMatchColor: Color,

    // 语法高亮颜色
    val keywordColor: Color,
    val typeColor: Color,
    val stringColor: Color,
    val commentColor: Color,
    val numberColor: Color,
    val attributeColor: Color,
    val selectorColor: Color,
    val processingColor: Color,
    
    // Markdown特定颜色
    val headingColor: Color,
    val quoteColor: Color,
    val listItemColor: Color,
    val codeColor: Color,
    
    // 行号
    val gutterBackground: Color,
    val gutterBorder: Color,
    val lineNumberColor: Color,
    
    // 字体大小
    val fontSize: TextUnit
)

/**
 * 暗色主题
 */
val DarkTheme = EditorTheme(
    background = Color(0xFF1E1E1E),
    textColor = Color(0xFFD4D4D4),
    cursorColor = Color(0xFFAEAFAD),
    selectionColor = Color(0x80264F78),
    searchMatchColor = Color(0x66FFC107),
    activeSearchMatchColor = Color(0x99FF8A65),

    keywordColor = Color(0xFF6CB6FF),
    typeColor = Color(0xFF4EC9B0),
    stringColor = Color(0xFFCE9178),
    commentColor = Color(0xFF9E9E9E),
    numberColor = Color(0xFFB5CEA8),
    attributeColor = Color(0xFF9CDCFE),
    selectorColor = Color(0xFFD7BA7D),
    processingColor = Color(0xFFC586C0),
    
    headingColor = Color(0xFF569CD6),
    quoteColor = Color(0xFF608B4E),
    listItemColor = Color(0xFFD7BA7D),
    codeColor = Color(0xFFDCDCAA),
    
    gutterBackground = Color(0xFF2A2D2E),
    gutterBorder = Color(0xFF3A3D41),
    lineNumberColor = Color(0xFF858585),
    
    fontSize = 14.sp
)

/**
 * 亮色主题
 */
val LightTheme = EditorTheme(
    background = Color(0xFFFFFFFF),
    textColor = Color(0xFF1F2328),
    cursorColor = Color(0xFF1F2328),
    selectionColor = Color(0x66ADD6FF),
    searchMatchColor = Color(0x66FFD54F),
    activeSearchMatchColor = Color(0x88FFAB91),

    keywordColor = Color(0xFF0000FF),
    typeColor = Color(0xFF267F99),
    stringColor = Color(0xFFA31515),
    commentColor = Color(0xFF9E9E9E),
    numberColor = Color(0xFF098658),
    attributeColor = Color(0xFF001080),
    selectorColor = Color(0xFF800000),
    processingColor = Color(0xFFAF00DB),
    
    headingColor = Color(0xFF0000FF),
    quoteColor = Color(0xFF008000),
    listItemColor = Color(0xFF800000),
    codeColor = Color(0xFF795E26),
    
    gutterBackground = Color(0xFFF8F8F8),
    gutterBorder = Color(0xFFE5E5E5),
    lineNumberColor = Color(0xFF8A8F98),
    
    fontSize = 14.sp
)

/**
 * 根据语言获取适合的主题
 */
fun getThemeForLanguage(_language: String): EditorTheme {
    return DarkTheme
}
