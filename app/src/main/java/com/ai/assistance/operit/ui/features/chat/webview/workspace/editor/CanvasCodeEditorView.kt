package com.ai.assistance.operit.ui.features.chat.webview.workspace.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.util.AttributeSet
import android.util.TypedValue
import android.view.ActionMode
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.OverScroller
import androidx.compose.ui.graphics.toArgb
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.completion.CompletionItem
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.completion.CompletionProvider
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.completion.CompletionProviderFactory
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.language.LanguageSupport
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.theme.EditorTheme
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.theme.getThemeForLanguage
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

interface EditorCompletionCallback {
    fun showCompletions(items: List<CompletionItem>, prefix: String)
    fun hideCompletions()
    fun isCompletionVisible(): Boolean
}

enum class EditorKeyCommand {
    SAVE,
    SAVE_AS,
    CLOSE,
    FIND,
    ESCAPE
}

class CanvasCodeEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {

    companion object {
        private const val MIN_SCALE = 0.5f
        private const val MAX_SCALE = 3.0f
        private const val DEFAULT_TEXT_SIZE_SP = 14f
        private const val TAB_SPACES = 4
        private const val MENU_COPY = 1
        private const val MENU_CUT = 2
        private const val MENU_PASTE = 3
        private const val MENU_SELECT_ALL = 4
    }

    private enum class DragHandle {
        NONE,
        START,
        END
    }

    private data class HandleCenters(
        val start: PointF,
        val end: PointF
    )

    private val density = resources.displayMetrics.density
    private val renderSignal = Object()
    private val scroller = OverScroller(context)
    private val viewConfig = ViewConfiguration.get(context)
    private val clipboardManager: ClipboardManager? =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    private val inputMethodManager: InputMethodManager? =
        context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager

    private val backgroundPaint = Paint().apply { style = Paint.Style.FILL }
    private val gutterPaint = Paint().apply { style = Paint.Style.FILL }
    private val gutterBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
    }
    private val indentGuidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density
    }
    private val lineNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isSubpixelText = true
        textAlign = Paint.Align.RIGHT
    }
    private val activeLineNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isSubpixelText = true
        textAlign = Paint.Align.RIGHT
    }
    private val activeGutterLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val gutterAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isSubpixelText = true
        color = Color.WHITE
    }
    private val systemGlyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isSubpixelText = true
        color = Color.WHITE
        typeface = Typeface.DEFAULT
    }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val currentLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val composingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density * 1.5f
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density * 1.4f
    }

    private val horizontalPaddingPx = density * 12f
    private val verticalPaddingPx = density * 10f
    private val gutterAccentWidthPx = max(2f, density * 2f)
    private val cursorWidthPx = max(2f, density * 2f)
    private val handleRadiusPx = density * 10f
    private val touchSlop = viewConfig.scaledTouchSlop.toFloat()
    private val minFlingVelocity = viewConfig.scaledMinimumFlingVelocity.toFloat()
    private val maxFlingVelocity = viewConfig.scaledMaximumFlingVelocity.toFloat()

    private var theme: EditorTheme = getThemeForLanguage("text")
    private var metrics = EditorMetrics(textPaint, spToPx(DEFAULT_TEXT_SIZE_SP))
    private var document = EditorDocument()
    private var highlighter = EditorSyntaxHighlighter("text") { snapshot ->
        if (snapshot.version == document.version) {
            highlightSnapshot = snapshot
            requestRender()
        }
    }
    private var highlightSnapshot = HighlightSnapshot(0, IntArray(0))
    private var completionProvider: CompletionProvider = CompletionProviderFactory.getProvider("text")
    private var completionCallback: EditorCompletionCallback? = null
    private var keyCommandListener: ((EditorKeyCommand) -> Boolean)? = null
    private var currentLanguage = "text"
    private var currentScale = 1f
    private var showLineNumbers = true
    private var completionEnabled = true
    private var readOnly = false
    @Volatile private var searchRanges: List<IntRange> = emptyList()
    @Volatile private var activeSearchMatch = -1
    private val searchPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var scrollOffsetX = 0f
    private var scrollOffsetY = 0f
    private var viewportBottomPaddingPx = 0f
    private var isReleased = false
    private var isDirty = true
    private var renderThread: RenderThread? = null
    private var completionPrefix = ""
    private var preferredColumnCells: Int? = null
    private var editorTypeface: Typeface = Typeface.MONOSPACE

    private var actionMode: ActionMode? = null
    private var activeDragHandle = DragHandle.NONE
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var hasMovedBeyondTouchSlop = false
    private var hadMultiTouch = false
    private var longPressSelectionStarted = false
    private var isScaling = false

    private var textChangedListener: ((String) -> Unit)? = null
    private var resolvedKeywordColor = Color.WHITE
    private var resolvedTypeColor = Color.WHITE
    private var resolvedStringColor = Color.WHITE
    private var resolvedCommentColor = Color.WHITE
    private var resolvedNumberColor = Color.WHITE
    private var resolvedFunctionColor = Color.WHITE
    private var resolvedVariableColor = Color.WHITE
    private var resolvedTextColor = Color.WHITE
    private var cachedGutterWidth = 0f
    private var cachedGutterDigits = -1
    private var cachedGutterTextSize = -1f

    private val scaleGestureDetector =
        ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    isScaling = true
                    if (!scroller.isFinished) {
                        scroller.forceFinished(true)
                    }
                    return true
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    applyScale(detector.scaleFactor, detector.focusX, detector.focusY)
                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    isScaling = false
                }
            }
        )

    private val gestureDetector =
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onScroll(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    distanceX: Float,
                    distanceY: Float
                ): Boolean {
                    if (isScaling || activeDragHandle != DragHandle.NONE) {
                        return false
                    }
                    applyScroll(distanceX, distanceY)
                    return true
                }

                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    if (isScaling || activeDragHandle != DragHandle.NONE) {
                        return false
                    }
                    startFling(velocityX, velocityY)
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    if (isScaling) {
                        return
                    }
                    longPressSelectionStarted = true
                    val offset = screenToOffset(e.x, e.y)
                    document.selectWordAt(offset)
                    ensureCursorVisible()
                    notifySelectionChanged()
                    showSelectionMenu()
                    requestRender()
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (isScaling) {
                        return false
                    }
                    val offset = screenToOffset(e.x, e.y)
                    document.selectWordAt(offset)
                    ensureCursorVisible()
                    notifySelectionChanged()
                    showSelectionMenu()
                    requestRender()
                    return true
                }
            }
        )

    // Steps the fling on the main thread, one Choreographer frame at a time. OverScroller is a
    // UI-thread helper: it is not thread-safe, and some OEM frameworks (e.g. HONOR) call
    // Choreographer.getInstance() inside computeScrollOffset(), which throws
    // "The current thread must have a looper!" when it runs on the render thread (#798).
    // The render thread only draws whatever scroll offsets this runnable publishes.
    private val flingRunnable = object : Runnable {
        override fun run() {
            if (!scroller.computeScrollOffset()) {
                return
            }
            setScrollOffsets(scroller.currX.toFloat(), scroller.currY.toFloat())
            postOnAnimation(this)
        }
    }

    init {
        setZOrderOnTop(false)
        setZOrderMediaOverlay(false)
        holder.setFormat(PixelFormat.OPAQUE)
        holder.addCallback(this)
        setWillNotDraw(false)
        isFocusable = true
        isFocusableInTouchMode = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        editorTypeface = loadEditorTypeface()
        refreshPaints()
        requestHighlight()
    }

    fun release() {
        if (isReleased) {
            return
        }
        isReleased = true
        removeCallbacks(flingRunnable)
        stopRenderThread()
        actionMode?.finish()
        actionMode = null
        textChangedListener = null
        completionCallback = null
        highlighter.release()
    }

    fun setEditorTheme(theme: EditorTheme) {
        this.theme = theme
        refreshPaints()
        requestRender()
    }

    fun setLanguage(language: String) {
        if (currentLanguage.equals(language, ignoreCase = true)) {
            return
        }
        currentLanguage = language
        completionProvider = CompletionProviderFactory.getProvider(language)
        highlighter.setLanguage(language)
        setEditorTheme(getThemeForLanguage(language))
        requestHighlight()
        updateCompletion()
    }

    fun setReadOnly(readOnly: Boolean) {
        this.readOnly = readOnly
        isFocusable = !readOnly
        isFocusableInTouchMode = !readOnly
        if (readOnly) {
            hideCompletions()
            hideSoftKeyboard()
        }
    }

    fun setShowLineNumbers(showLineNumbers: Boolean) {
        this.showLineNumbers = showLineNumbers
        requestRender()
    }

    fun setCompletionEnabled(enabled: Boolean) {
        completionEnabled = enabled
        if (!enabled) {
            hideCompletions()
        } else {
            updateCompletion()
        }
    }

    fun setViewportBottomPadding(bottomPaddingPx: Int) {
        val resolvedPadding = bottomPaddingPx.coerceAtLeast(0).toFloat()
        if (abs(viewportBottomPaddingPx - resolvedPadding) < 0.5f) {
            return
        }
        viewportBottomPaddingPx = resolvedPadding
        ensureCursorVisible()
        requestRender()
    }

    fun setOnTextChangedListener(listener: (String) -> Unit) {
        textChangedListener = listener
    }

    fun setCompletionCallback(callback: EditorCompletionCallback?) {
        completionCallback = callback
        if (callback == null) {
            hideCompletions()
        } else {
            updateCompletion()
        }
    }

    fun setKeyCommandListener(listener: ((EditorKeyCommand) -> Boolean)?) {
        keyCommandListener = listener
    }

    fun setTextContent(text: String) {
        document.setText(text, clearHistory = true)
        highlightSnapshot = HighlightSnapshot(document.version, IntArray(text.length))
        completionPrefix = ""
        preferredColumnCells = null
        scrollOffsetX = 0f
        scrollOffsetY = 0f
        actionMode?.finish()
        hideCompletions()
        requestHighlight()
        notifySelectionChanged()
        inputMethodManager?.restartInput(this)
        requestRender()
    }

    fun getTextContent(): String = document.textString()

    /** 行号链接按一基行号定位，等待布局完成后滚动到目标光标。 */
    fun goToLine(line: Int) {
        val targetLine = (line - 1).coerceIn(0, document.lineCount() - 1)
        document.collapseSelection(document.getLineStart(targetLine))
        preferredColumnCells = null
        notifySelectionChanged()
        post {
            ensureCursorVisible()
            requestRender()
        }
    }

    /** 搜索高亮不改变光标和选择范围，长按复制仍使用原始文本。 */
    fun setSearchMatches(matches: List<IntRange>, active: Int) {
        val valid = matches.filter { !it.isEmpty() && it.first >= 0 && it.last < document.length() }
        if (valid == searchRanges && active == activeSearchMatch) return
        searchRanges = valid
        activeSearchMatch = active
        requestRender()
    }

    /** 只有用户明确选择上一处或下一处时才改变视口。 */
    fun revealActiveSearchMatch() {
        searchRanges.getOrNull(activeSearchMatch)?.let { match ->
            val line = document.getLineForOffset(match.first)
            setScrollOffsets(
                max(0f, xForOffsetInLine(match.first) - textViewportWidth() / 3f),
                max(0f, line * metrics.lineHeight - height / 3f)
            )
            requestRender()
        }
    }

    fun undo() {
        if (document.undo()) {
            onDocumentMutated()
        }
    }

    fun redo() {
        if (document.redo()) {
            onDocumentMutated()
        }
    }

    fun replaceAllText(newText: String) {
        if (readOnly) {
            return
        }
        document.replaceAllText(newText)
        onDocumentMutated()
    }

    fun insertSymbol(symbol: String) {
        if (readOnly) {
            return
        }
        document.insertTextAtCursor(symbol)
        onDocumentMutated()
    }

    fun applyCompletion(item: CompletionItem) {
        if (readOnly) {
            return
        }
        document.applyCompletion(completionPrefix.length, item.insertText)
        completionPrefix = ""
        hideCompletions()
        onDocumentMutated()
    }

    fun getCursorScreenPosition(): Point = offsetToPoint(document.selectionEnd)

    override fun surfaceCreated(holder: SurfaceHolder) {
        startRenderThread()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        clampScrollOffsets()
        requestRender()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopRenderThread()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!isReleased && holder.surface.isValid) {
            startRenderThread()
        }
    }

    override fun onDetachedFromWindow() {
        stopRenderThread()
        actionMode?.finish()
        actionMode = null
        hideCompletions()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        clampScrollOffsets()
        ensureCursorVisible()
        requestRender()
    }

    override fun onCheckIsTextEditor(): Boolean = !readOnly

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isReleased) {
            return false
        }
        return if (handleKeyEvent(event)) {
            true
        } else {
            super.onKeyDown(keyCode, event)
        }
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo?): InputConnection {
        outAttrs?.apply {
            inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_ACTION_NONE
            initialSelStart = document.selectionStart
            initialSelEnd = document.selectionEnd
        }
        return EditorInputConnection()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)

        scaleGestureDetector.onTouchEvent(event)
        if (!isScaling) {
            gestureDetector.onTouchEvent(event)
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x
                touchDownY = event.y
                hasMovedBeyondTouchSlop = false
                hadMultiTouch = false
                longPressSelectionStarted = false
                if (!hasFocus() && !readOnly) {
                    requestFocus()
                }
                activeDragHandle = detectHandleHit(event.x, event.y)
                if (activeDragHandle != DragHandle.NONE && !scroller.isFinished) {
                    scroller.forceFinished(true)
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                hadMultiTouch = true
                hasMovedBeyondTouchSlop = true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!hasMovedBeyondTouchSlop) {
                    val dx = abs(event.x - touchDownX)
                    val dy = abs(event.y - touchDownY)
                    if (dx > touchSlop || dy > touchSlop) {
                        hasMovedBeyondTouchSlop = true
                    }
                }
                if (activeDragHandle != DragHandle.NONE) {
                    updateSelectionFromHandle(activeDragHandle, event.x, event.y)
                    notifySelectionChanged()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        actionMode?.invalidateContentRect()
                    } else {
                        actionMode?.invalidate()
                    }
                    requestRender()
                    return true
                }
            }

            MotionEvent.ACTION_UP -> {
                if (activeDragHandle != DragHandle.NONE) {
                    activeDragHandle = DragHandle.NONE
                    showSelectionMenu()
                    notifySelectionChanged()
                    requestRender()
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }

                val isTap = !hasMovedBeyondTouchSlop && !hadMultiTouch && !isScaling
                if (isTap && !longPressSelectionStarted) {
                    performClick()
                    if (document.hasSelection()) {
                        actionMode?.finish()
                    }
                    val offset = screenToOffset(event.x, event.y)
                    document.collapseSelection(offset)
                    preferredColumnCells = null
                    ensureCursorVisible()
                    notifySelectionChanged()
                    hideCompletions()
                    if (!readOnly) {
                        showSoftKeyboard()
                    }
                    requestRender()
                } else if (document.hasSelection()) {
                    showSelectionMenu()
                }
                activeDragHandle = DragHandle.NONE
                hasMovedBeyondTouchSlop = false
                hadMultiTouch = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }

            MotionEvent.ACTION_CANCEL -> {
                activeDragHandle = DragHandle.NONE
                hasMovedBeyondTouchSlop = false
                hadMultiTouch = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }

        return true
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    private fun handleKeyEvent(event: KeyEvent): Boolean {
        val ctrlPressed = event.isCtrlPressed
        if (event.keyCode == KeyEvent.KEYCODE_ESCAPE && !ctrlPressed) {
            if (keyCommandListener?.invoke(EditorKeyCommand.ESCAPE) == true) {
                return true
            }
            hideCompletions()
            actionMode?.finish()
            if (document.hasSelection()) {
                document.collapseSelection(document.selectionEnd)
                notifySelectionChanged()
                requestRender()
            }
            return true
        }
        when (event.keyCode) {
            KeyEvent.KEYCODE_DEL -> {
                if (readOnly) {
                    return true
                }
                document.deleteBackward()
                onDocumentMutated()
                return true
            }

            KeyEvent.KEYCODE_FORWARD_DEL -> {
                if (readOnly) {
                    return true
                }
                document.deleteForward()
                onDocumentMutated()
                return true
            }

            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (readOnly) {
                    return true
                }
                document.insertNewlineWithIndent()
                onDocumentMutated()
                return true
            }

            KeyEvent.KEYCODE_TAB -> {
                if (readOnly) {
                    return true
                }
                if (event.isShiftPressed) {
                    if (document.unindentSelection(TAB_SPACES)) {
                        onDocumentMutated()
                    }
                } else {
                    document.replaceSelection(" ".repeat(TAB_SPACES), recordHistory = true)
                    onDocumentMutated()
                }
                return true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                moveCursorHorizontal(-1, event.isShiftPressed)
                return true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                moveCursorHorizontal(1, event.isShiftPressed)
                return true
            }

            KeyEvent.KEYCODE_DPAD_UP -> {
                moveCursorVertical(-1, event.isShiftPressed)
                return true
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                moveCursorVertical(1, event.isShiftPressed)
                return true
            }

            KeyEvent.KEYCODE_MOVE_HOME -> {
                val line = document.getLineForOffset(document.selectionEnd)
                val target = if (ctrlPressed) 0 else document.getLineStart(line)
                if (event.isShiftPressed) {
                    document.setSelection(document.selectionStart, target)
                } else {
                    document.collapseSelection(target)
                }
                preferredColumnCells = 0
                ensureCursorVisible()
                notifySelectionChanged()
                updateCompletion()
                requestRender()
                return true
            }

            KeyEvent.KEYCODE_MOVE_END -> {
                val line = document.getLineForOffset(document.selectionEnd)
                val target = if (ctrlPressed) document.length() else document.getLineEnd(line)
                if (event.isShiftPressed) {
                    document.setSelection(document.selectionStart, target)
                } else {
                    document.collapseSelection(target)
                }
                preferredColumnCells = document.getCellColumnForOffset(target)
                ensureCursorVisible()
                notifySelectionChanged()
                updateCompletion()
                requestRender()
                return true
            }
        }

        if (ctrlPressed) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_S -> {
                    keyCommandListener?.invoke(
                        if (event.isShiftPressed) EditorKeyCommand.SAVE_AS else EditorKeyCommand.SAVE
                    )
                    return true
                }

                KeyEvent.KEYCODE_W -> {
                    keyCommandListener?.invoke(EditorKeyCommand.CLOSE)
                    return true
                }

                KeyEvent.KEYCODE_F -> {
                    keyCommandListener?.invoke(EditorKeyCommand.FIND)
                    return true
                }

                KeyEvent.KEYCODE_SLASH,
                KeyEvent.KEYCODE_NUMPAD_DIVIDE -> {
                    if (!readOnly) {
                        val syntax = CommentSyntaxRegistry.forLanguage(currentLanguage)
                        val changed = when {
                            syntax == null -> false
                            !document.hasSelection() && syntax.preferredLinePrefix != null ->
                                document.toggleLineComment(syntax.preferredLinePrefix.orEmpty())
                            syntax.hasBlockComment -> document.toggleBlockComment(
                                syntax.blockStart.orEmpty(),
                                syntax.blockEnd.orEmpty()
                            )
                            else -> commentPrefixForLanguage()?.let(document::toggleLineComment) == true
                        }
                        if (changed) {
                            onDocumentMutated()
                        }
                    }
                    return true
                }

                KeyEvent.KEYCODE_A -> {
                    document.selectAll()
                    showSelectionMenu()
                    notifySelectionChanged()
                    requestRender()
                    return true
                }

                KeyEvent.KEYCODE_C -> {
                    copySelectionToClipboard()
                    return true
                }

                KeyEvent.KEYCODE_X -> {
                    cutSelectionToClipboard()
                    return true
                }

                KeyEvent.KEYCODE_V -> {
                    pasteFromClipboard()
                    return true
                }

                KeyEvent.KEYCODE_Z -> {
                    undo()
                    return true
                }

                KeyEvent.KEYCODE_Y -> {
                    redo()
                    return true
                }
            }
        }

        if (!readOnly) {
            val unicodeChar = event.unicodeChar
            if (unicodeChar != 0 && !Character.isISOControl(unicodeChar)) {
                document.replaceSelection(unicodeChar.toChar().toString(), recordHistory = true)
                onDocumentMutated()
                return true
            }
        }

        return false
    }

    private fun commentPrefixForLanguage(): String? {
        return CommentSyntaxRegistry.forLanguage(currentLanguage)?.preferredLinePrefix
    }

    private fun moveCursorHorizontal(delta: Int, extendSelection: Boolean) {
        val currentCaret =
            if (!extendSelection && document.hasSelection()) {
                if (delta < 0) {
                    min(document.selectionStart, document.selectionEnd)
                } else {
                    max(document.selectionStart, document.selectionEnd)
                }
            } else {
                document.selectionEnd
            }
        var target = currentCaret
        repeat(abs(delta)) {
            target =
                if (delta < 0) {
                    document.previousCursorOffset(target)
                } else {
                    document.nextCursorOffset(target)
                }
        }
        if (extendSelection) {
            document.setSelection(document.selectionStart, target)
        } else {
            document.collapseSelection(target)
        }
        preferredColumnCells = null
        ensureCursorVisible()
        notifySelectionChanged()
        updateCompletion()
        requestRender()
    }

    private fun moveCursorVertical(deltaLines: Int, extendSelection: Boolean) {
        val caret = document.selectionEnd
        val currentLine = document.getLineForOffset(caret)
        val targetLine = (currentLine + deltaLines).coerceIn(0, document.lineCount() - 1)
        val targetColumn = preferredColumnCells ?: document.getCellColumnForOffset(caret)
        val targetOffset = document.getOffsetForLineAndCellColumn(targetLine, targetColumn)
        if (extendSelection) {
            document.setSelection(document.selectionStart, targetOffset)
        } else {
            document.collapseSelection(targetOffset)
        }
        preferredColumnCells = targetColumn
        ensureCursorVisible()
        notifySelectionChanged()
        updateCompletion()
        requestRender()
    }

    private fun applyScale(scaleFactor: Float, focusX: Float, focusY: Float) {
        val oldScale = currentScale
        val newScale = (currentScale * scaleFactor).coerceIn(MIN_SCALE, MAX_SCALE)
        if (abs(newScale - oldScale) < 0.001f) {
            return
        }

        val oldCharWidth = metrics.charWidth
        val oldLineHeight = metrics.lineHeight
        val oldTextLeft = textRegionLeft()
        val logicalX =
            if (oldCharWidth > 0f) {
                (scrollOffsetX + (focusX - oldTextLeft).coerceAtLeast(0f)) / oldCharWidth
            } else {
                0f
            }
        val logicalY =
            if (oldLineHeight > 0f) {
                (scrollOffsetY + (focusY - verticalPaddingPx).coerceAtLeast(0f)) / oldLineHeight
            } else {
                0f
            }

        currentScale = newScale
        refreshPaints()

        val newScrollX = logicalX * metrics.charWidth - (focusX - textRegionLeft())
        val newScrollY = logicalY * metrics.lineHeight - (focusY - verticalPaddingPx)
        setScrollOffsets(newScrollX, newScrollY)
    }

    private fun applyScroll(distanceX: Float, distanceY: Float) {
        if (!scroller.isFinished) {
            scroller.forceFinished(true)
        }
        setScrollOffsets(scrollOffsetX + distanceX, scrollOffsetY + distanceY)
    }

    private fun startFling(velocityX: Float, velocityY: Float) {
        if (hypot(velocityX.toDouble(), velocityY.toDouble()) < minFlingVelocity) {
            return
        }
        scroller.fling(
            scrollOffsetX.roundToInt(),
            scrollOffsetY.roundToInt(),
            (-velocityX).coerceIn(-maxFlingVelocity, maxFlingVelocity).roundToInt(),
            (-velocityY).coerceIn(-maxFlingVelocity, maxFlingVelocity).roundToInt(),
            0,
            maxScrollX().roundToInt(),
            0,
            maxScrollY().roundToInt()
        )
        // A MOVE that stopped the previous fling and the UP that starts this one can land in
        // the same frame, leaving the previous step still posted; drop it so only one runs.
        removeCallbacks(flingRunnable)
        postOnAnimation(flingRunnable)
    }

    private fun setScrollOffsets(x: Float, y: Float, request: Boolean = true) {
        val clampedX = x.coerceIn(0f, maxScrollX())
        val clampedY = y.coerceIn(0f, maxScrollY())
        if (abs(clampedX - scrollOffsetX) < 0.5f && abs(clampedY - scrollOffsetY) < 0.5f) {
            if (request) {
                requestRender()
            }
            return
        }
        scrollOffsetX = clampedX
        scrollOffsetY = clampedY
        if (request) {
            requestRender()
        }
    }

    private fun clampScrollOffsets() {
        setScrollOffsets(scrollOffsetX, scrollOffsetY)
    }

    private fun requestRender() {
        isDirty = true
        synchronized(renderSignal) {
            renderSignal.notifyAll()
        }
    }

    private fun startRenderThread() {
        if (renderThread?.isAlive == true) {
            requestRender()
            return
        }
        renderThread = RenderThread().also {
            it.start()
        }
        requestRender()
    }

    private fun stopRenderThread() {
        val activeThread = renderThread ?: return
        renderThread = null
        activeThread.finish()
        synchronized(renderSignal) {
            renderSignal.notifyAll()
        }
        runCatching {
            activeThread.join(500)
        }
    }

    private fun onDocumentMutated() {
        if (isReleased) {
            return
        }
        preferredColumnCells = null
        ensureCursorVisible()
        notifySelectionChanged()
        requestHighlight()
        requestRender()
        updateCompletion()
        textChangedListener?.invoke(document.textString())
    }

    private fun requestHighlight() {
        if (isReleased) {
            return
        }
        highlighter.requestHighlight(document.textString(), document.version)
    }

    private fun notifySelectionChanged() {
        inputMethodManager?.updateSelection(
            this,
            document.selectionStart,
            document.selectionEnd,
            document.composingStart,
            document.composingEnd
        )
    }

    private fun updateCompletion() {
        if (!completionEnabled || readOnly || document.hasSelection()) {
            hideCompletions()
            return
        }

        val cursor = document.selectionEnd
        val text = document.text()
        if (!completionProvider.shouldShowCompletion(text, cursor)) {
            hideCompletions()
            return
        }

        val items = completionProvider.getCompletionItems(text, cursor)
        if (items.isEmpty()) {
            hideCompletions()
            return
        }

        completionPrefix = completionProvider.getPrefix(text, cursor)
        completionCallback?.showCompletions(items, completionPrefix)
    }

    private fun hideCompletions() {
        completionPrefix = ""
        completionCallback?.hideCompletions()
    }

    private fun refreshPaints() {
        val textSizePx =
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                theme.fontSize.value.takeIf { it > 0f } ?: DEFAULT_TEXT_SIZE_SP,
                resources.displayMetrics
            ) * currentScale

        textPaint.typeface = editorTypeface
        metrics.update(textSizePx)

        val textColor = theme.textColor.toArgb()
        val lineNumberColor = theme.lineNumberColor.toArgb()
        val backgroundColor = theme.background.toArgb()
        val gutterBackgroundColor = theme.gutterBackground.toArgb()
        val gutterBorderColor = theme.gutterBorder.toArgb()
        val selectionColor = theme.selectionColor.toArgb()
        val cursorColor = theme.cursorColor.toArgb()
        val keywordColor = theme.keywordColor.toArgb()
        val typeColor = theme.typeColor.toArgb()
        val stringColor = theme.stringColor.toArgb()
        val commentColor = theme.commentColor.toArgb()
        val numberColor = theme.numberColor.toArgb()
        val functionColor = theme.codeColor.toArgb()
        val variableColor = theme.attributeColor.toArgb()

        lineNumberPaint.apply {
            textSize = textSizePx * 0.82f
            color = lineNumberColor
            typeface = editorTypeface
        }
        activeLineNumberPaint.apply {
            textSize = textSizePx * 0.82f
            color = textColor
            typeface = Typeface.create(editorTypeface, Typeface.BOLD)
        }

        textPaint.apply {
            color = textColor
            typeface = editorTypeface
        }
        systemGlyphPaint.apply {
            textSize = textSizePx
            color = textColor
            typeface = Typeface.DEFAULT
        }

        backgroundPaint.color = backgroundColor
        gutterPaint.color = gutterBackgroundColor
        gutterBorderPaint.color = gutterBorderColor
        indentGuidePaint.color = blendColors(backgroundColor, gutterBorderColor, 0.68f)
        activeGutterLinePaint.color = blendColors(gutterBackgroundColor, textColor, 0.08f)
        gutterAccentPaint.color = blendColors(gutterBackgroundColor, gutterBorderColor, 0.82f)
        selectionPaint.color = selectionColor
        cursorPaint.color = cursorColor
        currentLinePaint.color = blendColors(backgroundColor, selectionColor, 0.16f)
        composingPaint.color = cursorColor
        handlePaint.color = cursorColor
        handleStrokePaint.color = backgroundColor

        resolvedKeywordColor = keywordColor
        resolvedTypeColor = typeColor
        resolvedStringColor = stringColor
        resolvedCommentColor = commentColor
        resolvedNumberColor = numberColor
        resolvedFunctionColor = functionColor
        resolvedVariableColor = variableColor
        resolvedTextColor = textColor
        cachedGutterDigits = -1
        cachedGutterTextSize = -1f
        cachedGutterWidth = 0f
    }

    private fun blendColors(baseColor: Int, overlayColor: Int, overlayRatio: Float): Int {
        val ratio = overlayRatio.coerceIn(0f, 1f)
        val red =
            (Color.red(baseColor) + (Color.red(overlayColor) - Color.red(baseColor)) * ratio)
                .roundToInt()
                .coerceIn(0, 255)
        val green =
            (Color.green(baseColor) + (Color.green(overlayColor) - Color.green(baseColor)) * ratio)
                .roundToInt()
                .coerceIn(0, 255)
        val blue =
            (Color.blue(baseColor) + (Color.blue(overlayColor) - Color.blue(baseColor)) * ratio)
                .roundToInt()
                .coerceIn(0, 255)
        return Color.argb(255, red, green, blue)
    }

    private fun drawEditor(canvas: Canvas) {
        canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), backgroundPaint)

        val lineCount = document.lineCount()
        if (lineCount <= 0) {
            return
        }

        val gutterWidth = gutterWidth()
        val textRegionLeft = if (showLineNumbers) gutterWidth else horizontalPaddingPx
        if (showLineNumbers && gutterWidth > 0f) {
            canvas.drawRect(0f, 0f, gutterWidth, canvas.height.toFloat(), gutterPaint)
        }

        val firstVisibleLine =
            max(
                0,
                ((scrollOffsetY - verticalPaddingPx) / metrics.lineHeight).toInt()
            )
        val lastVisibleLine =
            min(
                lineCount - 1,
                ceil(
                    ((scrollOffsetY + canvas.height - verticalPaddingPx) / metrics.lineHeight)
                        .toDouble()
                ).toInt()
            )

        val cursorLine = document.getLineForOffset(document.selectionEnd)
        if (!document.hasSelection() && cursorLine in firstVisibleLine..lastVisibleLine) {
            val lineTop = verticalPaddingPx + cursorLine * metrics.lineHeight - scrollOffsetY
            if (showLineNumbers && gutterWidth > 0f) {
                canvas.drawRect(
                    0f,
                    lineTop,
                    gutterWidth,
                    lineTop + metrics.lineHeight,
                    activeGutterLinePaint
                )
            }
            canvas.drawRect(
                textRegionLeft,
                lineTop,
                canvas.width.toFloat(),
                lineTop + metrics.lineHeight,
                currentLinePaint
            )
        }

        for (line in firstVisibleLine..lastVisibleLine) {
            val lineTop = verticalPaddingPx + line * metrics.lineHeight - scrollOffsetY
            if (showLineNumbers) {
                drawLineNumber(canvas, line, lineTop, cursorLine, gutterWidth)
            }
        }

        canvas.save()
        canvas.clipRect(textRegionLeft, 0f, canvas.width.toFloat(), canvas.height.toFloat())
        for (line in firstVisibleLine..lastVisibleLine) {
            val lineTop = verticalPaddingPx + line * metrics.lineHeight - scrollOffsetY
            drawIndentGuides(canvas, line, lineTop, textRegionLeft)
            drawSearchForLine(canvas, line, lineTop, textRegionLeft)
            drawTextForLine(canvas, line, lineTop, textRegionLeft)
            drawSelectionForLine(canvas, line, lineTop, textRegionLeft)
            drawComposingUnderline(canvas, line, lineTop, textRegionLeft)
            drawCursorForLine(canvas, line, lineTop, cursorLine, textRegionLeft)
        }
        canvas.restore()

        if (document.hasSelection()) {
            drawSelectionHandles(canvas)
        }
    }

    private fun drawLineNumber(
        canvas: Canvas,
        line: Int,
        lineTop: Float,
        currentLine: Int,
        gutterWidth: Float
    ) {
        val baseline = lineTop + metrics.baseline
        if (baseline < 0f || baseline > canvas.height + metrics.lineHeight) {
            return
        }
        val label = (line + 1).toString()
        val x = gutterWidth - gutterTrailingPaddingPx()
        if (line == currentLine) {
            canvas.drawText(label, x, baseline, activeLineNumberPaint)
            return
        }
        canvas.drawText(label, x, baseline, lineNumberPaint)
    }

    private fun drawIndentGuides(canvas: Canvas, line: Int, lineTop: Float, textRegionLeft: Float) {
        val lineStart = document.getLineStart(line)
        val lineEnd = document.getLineEnd(line)
        val text = document.text()
        var cells = 0
        var indentLevels = 0
        for (offset in lineStart until lineEnd) {
            val char = text[offset]
            if (char == ' ') {
                cells += 1
            } else if (char == '\t') {
                cells += TAB_SPACES
            } else {
                indentLevels = cells / TAB_SPACES
                break
            }
        }
        if (indentLevels == 0 && cells > 0) {
            indentLevels = cells / TAB_SPACES
        }

        for (level in 1..indentLevels) {
            val x =
                textRegionLeft +
                    level * TAB_SPACES * metrics.charWidth -
                    scrollOffsetX -
                    metrics.charWidth * 0.5f
            if (x < textRegionLeft || x > width.toFloat()) {
                continue
            }
            canvas.drawLine(
                x,
                lineTop + density * 2f,
                x,
                lineTop + metrics.lineHeight - density * 2f,
                indentGuidePaint
            )
        }
    }

    private fun drawSearchForLine(canvas: Canvas, line: Int, top: Float, textLeft: Float) {
        val ranges = searchRanges
        if (ranges.isEmpty()) return
        val start = document.getLineStart(line)
        val end = document.getLineEnd(line)
        val found = ranges.binarySearchBy(start) { it.first }
        var index = if (found >= 0) found else max(0, -found - 2)
        while (index < ranges.size && ranges[index].first < end) {
            val match = ranges[index]
            val from = max(start, match.first)
            val to = min(end, match.last + 1)
            if (to > from) {
                searchPaint.color = (if (index == activeSearchMatch) theme.activeSearchMatchColor else theme.searchMatchColor).toArgb()
                canvas.drawRect(textLeft + xForOffsetInLine(from) - scrollOffsetX, top,
                    textLeft + xForOffsetInLine(to) - scrollOffsetX, top + metrics.lineHeight, searchPaint)
            }
            index++
        }
    }

    private fun drawSelectionForLine(
        canvas: Canvas,
        line: Int,
        lineTop: Float,
        textRegionLeft: Float
    ) {
        if (!document.hasSelection()) {
            return
        }

        val selectionStart = min(document.selectionStart, document.selectionEnd)
        val selectionEnd = max(document.selectionStart, document.selectionEnd)
        val lineStart = document.getLineStart(line)
        val lineEnd = document.getLineEnd(line)

        val start = max(selectionStart, lineStart)
        val end = min(selectionEnd, lineEnd)

        if (selectionEnd <= lineStart || selectionStart >= lineEnd) {
            return
        }

        if (start == end && !(selectionStart < lineStart && selectionEnd > lineStart)) {
            return
        }

        val left =
            if (selectionStart < lineStart) {
                textRegionLeft
            } else {
                textRegionLeft + xForOffsetInLine(start) - scrollOffsetX
            }
        val right =
            if (selectionEnd > lineEnd) {
                textRegionLeft + xForOffsetInLine(lineEnd) - scrollOffsetX
            } else {
                textRegionLeft + xForOffsetInLine(end) - scrollOffsetX
            }
        if (right <= left) {
            return
        }

        canvas.drawRect(
            left.coerceAtLeast(textRegionLeft),
            lineTop,
            right.coerceAtMost(width.toFloat()),
            lineTop + metrics.lineHeight,
            selectionPaint
        )
    }

    private fun drawTextForLine(
        canvas: Canvas,
        line: Int,
        lineTop: Float,
        textRegionLeft: Float
    ) {
        val lineStart = document.getLineStart(line)
        val lineEnd = document.getLineEnd(line)
        val text = document.text()
        var x = textRegionLeft - scrollOffsetX
        val baseline = lineTop + metrics.baseline
        val leftClip = textRegionLeft - metrics.charWidth * TAB_SPACES
        val rightClip = width.toFloat() + metrics.charWidth * TAB_SPACES

        var offset = lineStart
        var runStart = -1
        var runEnd = -1
        var runX = 0f
        var runColor = resolvedTextColor

        fun flushRun() {
            if (runStart < 0 || runEnd <= runStart) {
                return
            }
            textPaint.color = runColor
            canvas.drawText(text, runStart, runEnd, runX, baseline, textPaint)
            runStart = -1
            runEnd = -1
        }

        while (offset < lineEnd) {
            val nextOffset = editorNextSymbolOffset(text, offset, lineEnd)
            val cellWidth = editorCellWidth(text, offset, lineEnd)
            val advance = metrics.charWidth * cellWidth
            if (x + advance < leftClip) {
                x += advance
                offset = nextOffset
                continue
            }
            if (x > rightClip) {
                flushRun()
                break
            }

            if (text[offset] != '\t') {
                val usesSystemGlyphPaint =
                    if (nextOffset == offset + 1) {
                        shouldRenderWithSystemGlyphs(text[offset].code)
                    } else {
                        shouldRenderWithSystemGlyphs(text, offset, nextOffset)
                    }
                val color = colorForOffset(offset)
                val canBatch =
                    !usesSystemGlyphPaint &&
                        cellWidth == 1 &&
                        nextOffset == offset + 1

                if (canBatch) {
                    if (runStart >= 0 && runColor == color && runEnd == offset) {
                        runEnd = nextOffset
                    } else {
                        flushRun()
                        runStart = offset
                        runEnd = nextOffset
                        runX = x
                        runColor = color
                    }
                } else {
                    flushRun()
                    val paint = if (usesSystemGlyphPaint) systemGlyphPaint else textPaint
                    paint.color = color
                    canvas.drawText(text, offset, nextOffset, x, baseline, paint)
                }
            } else {
                flushRun()
            }
            x += advance
            offset = nextOffset
        }
        flushRun()
    }

    private fun drawComposingUnderline(
        canvas: Canvas,
        line: Int,
        lineTop: Float,
        textRegionLeft: Float
    ) {
        if (!document.hasComposingRegion()) {
            return
        }
        val composingStart = document.composingStart
        val composingEnd = document.composingEnd
        val lineStart = document.getLineStart(line)
        val lineEnd = document.getLineEnd(line)
        val start = max(composingStart, lineStart)
        val end = min(composingEnd, lineEnd)
        if (start >= end) {
            return
        }
        val left = textRegionLeft + xForOffsetInLine(start) - scrollOffsetX
        val right = textRegionLeft + xForOffsetInLine(end) - scrollOffsetX
        val y = lineTop + metrics.lineHeight - density * 3f
        canvas.drawLine(left, y, right, y, composingPaint)
    }

    private fun drawCursorForLine(
        canvas: Canvas,
        line: Int,
        lineTop: Float,
        cursorLine: Int,
        textRegionLeft: Float
    ) {
        if (document.hasSelection() || line != cursorLine) {
            return
        }
        val x = textRegionLeft + xForOffsetInLine(document.selectionEnd) - scrollOffsetX
        canvas.drawRect(
            x,
            lineTop + density,
            x + cursorWidthPx,
            lineTop + metrics.lineHeight - density,
            cursorPaint
        )
    }

    private fun drawSelectionHandles(canvas: Canvas) {
        val handles = selectionHandleCenters() ?: return
        drawHandle(canvas, handles.start)
        drawHandle(canvas, handles.end)
    }

    private fun drawHandle(canvas: Canvas, center: PointF) {
        canvas.drawLine(
            center.x,
            center.y - metrics.lineHeight * 0.45f,
            center.x,
            center.y,
            handlePaint
        )
        canvas.drawCircle(center.x, center.y, handleRadiusPx, handlePaint)
        canvas.drawCircle(center.x, center.y, handleRadiusPx, handleStrokePaint)
    }

    private fun colorForOffset(offset: Int): Int {
        val color =
            if (offset in highlightSnapshot.colors.indices) {
                highlightSnapshot.colors[offset]
            } else {
                LanguageSupport.DEFAULT_COLOR
            }
        return when (color) {
            LanguageSupport.KEYWORD_COLOR -> resolvedKeywordColor
            LanguageSupport.TYPE_COLOR -> resolvedTypeColor
            LanguageSupport.STRING_COLOR -> resolvedStringColor
            LanguageSupport.COMMENT_COLOR -> resolvedCommentColor
            LanguageSupport.NUMBER_COLOR -> resolvedNumberColor
            LanguageSupport.FUNCTION_COLOR -> resolvedFunctionColor
            LanguageSupport.VARIABLE_COLOR -> resolvedVariableColor
            LanguageSupport.OPERATOR_COLOR,
            LanguageSupport.DEFAULT_COLOR,
            0 -> resolvedTextColor
            else -> color
        }
    }

    private fun contentHeightPx(): Float {
        return verticalPaddingPx * 2f + document.lineCount() * metrics.lineHeight
    }

    private fun contentWidthPx(): Float {
        return horizontalPaddingPx + document.maxLineCells * metrics.charWidth + horizontalPaddingPx
    }

    private fun gutterWidth(): Float {
        if (!showLineNumbers) {
            return 0f
        }
        val digits = document.lineDigits()
        val textSize = lineNumberPaint.textSize
        if (digits != cachedGutterDigits || abs(textSize - cachedGutterTextSize) >= 0.5f) {
            val digitsText = "9".repeat(digits)
            val lineNumberWidth = lineNumberPaint.measureText(digitsText)
            val padding = gutterLeadingPaddingPx() + gutterTrailingPaddingPx()
            cachedGutterWidth = max(lineNumberWidth + padding, density * 24f)
            cachedGutterDigits = digits
            cachedGutterTextSize = textSize
        }
        return cachedGutterWidth
    }

    private fun textRegionLeft(): Float {
        return if (showLineNumbers) gutterWidth() else horizontalPaddingPx
    }

    private fun gutterLeadingPaddingPx(): Float {
        return when (document.lineDigits()) {
            1 -> density * 5f
            2 -> density * 6f
            3 -> density * 7f
            else -> density * 8f
        }
    }

    private fun gutterTrailingPaddingPx(): Float {
        return when (document.lineDigits()) {
            1 -> density * 5f
            2 -> density * 6f
            3 -> density * 7f
            else -> density * 8f
        }
    }

    private fun textViewportWidth(): Float {
        return max(0f, width.toFloat() - textRegionLeft() - horizontalPaddingPx)
    }

    private fun maxScrollX(): Float {
        return max(0f, contentWidthPx() - textViewportWidth())
    }

    private fun maxScrollY(): Float {
        return max(0f, contentHeightPx() - height.toFloat())
    }

    private fun xForOffsetInLine(offset: Int): Float {
        return document.getCellColumnForOffset(offset) * metrics.charWidth
    }

    private fun screenToOffset(x: Float, y: Float): Int {
        val line =
            (((y + scrollOffsetY - verticalPaddingPx) / metrics.lineHeight).toInt())
                .coerceIn(0, document.lineCount() - 1)
        val targetX = scrollOffsetX + (x - textRegionLeft()).coerceAtLeast(0f)
        val lineStart = document.getLineStart(line)
        val lineEnd = document.getLineEnd(line)
        val text = document.text()
        var currentX = 0f

        var offset = lineStart
        while (offset < lineEnd) {
            val nextOffset = editorNextSymbolOffset(text, offset, lineEnd)
            val advance = editorCellWidth(text, offset, lineEnd) * metrics.charWidth
            val midpoint = currentX + advance * 0.5f
            if (targetX < midpoint) {
                return offset
            }
            if (targetX < currentX + advance) {
                return nextOffset
            }
            currentX += advance
            offset = nextOffset
        }
        return lineEnd
    }

    private fun offsetToPoint(offset: Int): Point {
        val safeOffset = offset.coerceIn(0, document.length())
        val line = document.getLineForOffset(safeOffset)
        val x = textRegionLeft() + xForOffsetInLine(safeOffset) - scrollOffsetX
        val y = verticalPaddingPx + line * metrics.lineHeight - scrollOffsetY + metrics.lineHeight
        return Point(x.roundToInt(), y.roundToInt())
    }

    private fun ensureCursorVisible() {
        val line = document.getLineForOffset(document.selectionEnd)
        val point = offsetToPoint(document.selectionEnd)
        val lineTop = verticalPaddingPx + line * metrics.lineHeight - scrollOffsetY
        val lineBottom = lineTop + metrics.lineHeight
        val leftLimit = textRegionLeft() + metrics.charWidth * 2f
        val rightLimit = width - horizontalPaddingPx - metrics.charWidth * 2f
        val topLimit = verticalPaddingPx
        val bottomLimit =
            max(
                topLimit + metrics.lineHeight,
                height - verticalPaddingPx - viewportBottomPaddingPx
            )

        var newScrollX = scrollOffsetX
        var newScrollY = scrollOffsetY

        if (point.x < leftLimit) {
            newScrollX -= (leftLimit - point.x)
        } else if (point.x > rightLimit) {
            newScrollX += (point.x - rightLimit)
        }

        if (lineTop < topLimit) {
            newScrollY -= (topLimit - lineTop)
        } else if (lineBottom > bottomLimit) {
            newScrollY += (lineBottom - bottomLimit)
        }

        setScrollOffsets(newScrollX, newScrollY, request = false)
    }

    private fun detectHandleHit(x: Float, y: Float): DragHandle {
        val centers = selectionHandleCenters() ?: return DragHandle.NONE
        val startDistance = hypot((x - centers.start.x).toDouble(), (y - centers.start.y).toDouble())
        val endDistance = hypot((x - centers.end.x).toDouble(), (y - centers.end.y).toDouble())
        val threshold = handleRadiusPx * 2.25f
        return when {
            startDistance <= threshold && endDistance <= threshold -> {
                if (startDistance <= endDistance) DragHandle.START else DragHandle.END
            }
            startDistance <= threshold -> DragHandle.START
            endDistance <= threshold -> DragHandle.END
            else -> DragHandle.NONE
        }
    }

    private fun updateSelectionFromHandle(handle: DragHandle, x: Float, y: Float) {
        if (!document.hasSelection()) {
            return
        }
        val otherStart = min(document.selectionStart, document.selectionEnd)
        val otherEnd = max(document.selectionStart, document.selectionEnd)
        val offset = screenToOffset(x, y)
        when (handle) {
            DragHandle.START -> document.setSelection(offset, otherEnd)
            DragHandle.END -> document.setSelection(otherStart, offset)
            DragHandle.NONE -> Unit
        }
        ensureCursorVisible()
    }

    private fun selectionHandleCenters(): HandleCenters? {
        if (!document.hasSelection()) {
            return null
        }
        val start = min(document.selectionStart, document.selectionEnd)
        val end = max(document.selectionStart, document.selectionEnd)
        val startPoint = offsetToPoint(start)
        val endPoint = offsetToPoint(end)
        return HandleCenters(
            start = PointF(startPoint.x.toFloat(), startPoint.y.toFloat()),
            end = PointF(endPoint.x.toFloat(), endPoint.y.toFloat())
        )
    }

    private fun showSelectionMenu() {
        if (!document.hasSelection()) {
            actionMode?.finish()
            return
        }
        if (actionMode != null) {
            actionMode?.invalidate()
            return
        }

        val callback =
            object : ActionMode.Callback2() {
                override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                    menu.add(0, MENU_COPY, 0, "复制")
                    if (!readOnly) {
                        menu.add(0, MENU_CUT, 1, "剪切")
                        menu.add(0, MENU_PASTE, 2, "粘贴")
                    }
                    menu.add(0, MENU_SELECT_ALL, 3, "全选")
                    return true
                }

                override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

                override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                    return when (item.itemId) {
                        MENU_COPY -> {
                            copySelectionToClipboard()
                            mode.finish()
                            true
                        }

                        MENU_CUT -> {
                            cutSelectionToClipboard()
                            mode.finish()
                            true
                        }

                        MENU_PASTE -> {
                            pasteFromClipboard()
                            mode.finish()
                            true
                        }

                        MENU_SELECT_ALL -> {
                            document.selectAll()
                            notifySelectionChanged()
                            requestRender()
                            true
                        }

                        else -> false
                    }
                }

                override fun onDestroyActionMode(mode: ActionMode) {
                    actionMode = null
                    if (document.hasSelection()) {
                        document.collapseSelection(document.selectionEnd)
                        notifySelectionChanged()
                        requestRender()
                    }
                }

                override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
                    val start = min(document.selectionStart, document.selectionEnd)
                    val end = max(document.selectionStart, document.selectionEnd)
                    val startLine = document.getLineForOffset(start)
                    val endLine = document.getLineForOffset(end)
                    val left =
                        (textRegionLeft() + xForOffsetInLine(start) - scrollOffsetX)
                            .roundToInt()
                            .coerceIn(0, width)
                    val right =
                        (textRegionLeft() + xForOffsetInLine(end) - scrollOffsetX)
                            .roundToInt()
                            .coerceIn(0, width)
                    val top =
                        (verticalPaddingPx + startLine * metrics.lineHeight - scrollOffsetY)
                            .roundToInt()
                            .coerceIn(0, height)
                    val bottom =
                        (verticalPaddingPx + endLine * metrics.lineHeight - scrollOffsetY + metrics.lineHeight)
                            .roundToInt()
                            .coerceIn(0, height)
                    outRect.set(
                        min(left, right),
                        top,
                        max(left, right).coerceAtLeast(min(left, right) + 1),
                        max(bottom, top + 1)
                    )
                }
            }

        actionMode =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                startActionMode(callback, ActionMode.TYPE_FLOATING)
            } else {
                startActionMode(callback)
            }
    }

    private fun copySelectionToClipboard() {
        if (!document.hasSelection()) {
            return
        }
        val start = min(document.selectionStart, document.selectionEnd)
        val end = max(document.selectionStart, document.selectionEnd)
        val selectedText = document.text().subSequence(start, end).toString()
        clipboardManager?.setPrimaryClip(ClipData.newPlainText("Code", selectedText))
    }

    private fun cutSelectionToClipboard() {
        if (readOnly || !document.hasSelection()) {
            return
        }
        copySelectionToClipboard()
        document.replaceSelection("", recordHistory = true)
        onDocumentMutated()
    }

    private fun pasteFromClipboard() {
        if (readOnly) {
            return
        }
        val clip = clipboardManager?.primaryClip ?: return
        if (clip.itemCount <= 0) {
            return
        }
        val pasted =
            clip.getItemAt(0).coerceToText(context)?.toString()?.replace("\r\n", "\n")?.replace('\r', '\n')
                ?: return
        if (pasted.isEmpty()) {
            return
        }
        document.replaceSelection(pasted, recordHistory = true)
        onDocumentMutated()
    }

    private fun showSoftKeyboard() {
        if (readOnly) {
            return
        }
        if (!hasFocus()) {
            requestFocus()
        }
        inputMethodManager?.restartInput(this)
        inputMethodManager?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideSoftKeyboard() {
        inputMethodManager?.hideSoftInputFromWindow(windowToken, 0)
    }

    private fun spToPx(valueSp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            valueSp,
            resources.displayMetrics
        )
    }

    private fun loadEditorTypeface(): Typeface {
        return try {
            resources.getFont(com.ai.assistance.operit.terminal.R.font.jetbrains_mono_nerd_font_regular)
        } catch (_: Exception) {
            Typeface.MONOSPACE
        }
    }

    private fun shouldRenderWithSystemGlyphs(codePoint: Int): Boolean {
        return codePoint in 0x1F000..0x1FAFF ||
            codePoint in 0x2600..0x27BF ||
            codePoint in 0x1F3FB..0x1F3FF ||
            codePoint in 0x1F1E6..0x1F1FF ||
            codePoint in 0xFE00..0xFE0F ||
            codePoint == 0x200D ||
            codePoint == 0x20E3
    }

    private fun shouldRenderWithSystemGlyphs(
        text: CharSequence,
        start: Int,
        end: Int
    ): Boolean {
        var index = start.coerceAtLeast(0)
        val safeEnd = end.coerceAtMost(text.length)
        while (index < safeEnd) {
            val codePoint = Character.codePointAt(text, index)
            if (shouldRenderWithSystemGlyphs(codePoint)) {
                return true
            }
            index += Character.charCount(codePoint)
        }
        return false
    }

    private inner class EditorInputConnection : BaseInputConnection(this@CanvasCodeEditorView, true) {
        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            if (readOnly) {
                return true
            }
            val normalized = text?.toString()?.replace("\r\n", "\n")?.replace('\r', '\n') ?: ""
            document.replaceSelection(normalized, recordHistory = true)
            document.finishComposingText()
            onDocumentMutated()
            return true
        }

        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
            if (readOnly) {
                return true
            }
            val normalized = text?.toString()?.replace("\r\n", "\n")?.replace('\r', '\n') ?: ""
            document.setComposingText(normalized)
            preferredColumnCells = null
            ensureCursorVisible()
            notifySelectionChanged()
            requestRender()
            updateCompletion()
            return true
        }

        override fun finishComposingText(): Boolean {
            document.finishComposingText()
            notifySelectionChanged()
            requestRender()
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            if (readOnly) {
                return true
            }
            document.deleteSurroundingText(beforeLength, afterLength)
            onDocumentMutated()
            return true
        }

        override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
            if (readOnly) {
                return true
            }
            document.deleteSurroundingCodePoints(beforeLength, afterLength)
            onDocumentMutated()
            return true
        }

        override fun sendKeyEvent(event: KeyEvent?): Boolean {
            if (event == null || event.action != KeyEvent.ACTION_DOWN) {
                return true
            }
            if (handleKeyEvent(event)) {
                return true
            }
            return super.sendKeyEvent(event)
        }

        override fun setSelection(start: Int, end: Int): Boolean {
            document.setSelection(start, end)
            preferredColumnCells = null
            ensureCursorVisible()
            notifySelectionChanged()
            requestRender()
            updateCompletion()
            return true
        }

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence {
            val end = document.selectionEnd
            val start = (end - n).coerceAtLeast(0)
            return document.text().subSequence(start, end)
        }

        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence {
            val start = document.selectionEnd
            val end = (start + n).coerceAtMost(document.length())
            return document.text().subSequence(start, end)
        }

        override fun getSelectedText(flags: Int): CharSequence? {
            if (!document.hasSelection()) {
                return ""
            }
            val start = min(document.selectionStart, document.selectionEnd)
            val end = max(document.selectionStart, document.selectionEnd)
            return document.text().subSequence(start, end)
        }

        override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText {
            return ExtractedText().apply {
                text = document.text()
                startOffset = 0
                selectionStart = document.selectionStart
                selectionEnd = document.selectionEnd
                partialStartOffset = -1
                partialEndOffset = -1
            }
        }

        override fun performContextMenuAction(id: Int): Boolean {
            return when (id) {
                android.R.id.copy -> {
                    copySelectionToClipboard()
                    true
                }

                android.R.id.cut -> {
                    cutSelectionToClipboard()
                    true
                }

                android.R.id.paste -> {
                    pasteFromClipboard()
                    true
                }

                android.R.id.selectAll -> {
                    document.selectAll()
                    notifySelectionChanged()
                    requestRender()
                    true
                }

                else -> super.performContextMenuAction(id)
            }
        }
    }

    private inner class RenderThread : Thread("CanvasCodeEditorRenderer") {
        @Volatile
        private var running = true

        fun finish() {
            running = false
            interrupt()
        }

        override fun run() {
            while (running) {
                if (!isDirty) {
                    synchronized(renderSignal) {
                        if (!running && !isDirty) {
                            return
                        }
                        try {
                            renderSignal.wait(24L)
                        } catch (_: InterruptedException) {
                            if (!running) {
                                return
                            }
                        }
                    }
                    continue
                }

                if (!holder.surface.isValid) {
                    synchronized(renderSignal) {
                        try {
                            renderSignal.wait(24L)
                        } catch (_: InterruptedException) {
                            if (!running) {
                                return
                            }
                        }
                    }
                    continue
                }

                val canvas =
                    runCatching {
                        holder.lockCanvas()
                    }.getOrNull()

                if (canvas != null) {
                    // Clear the flag before drawing: fling steps now arrive from the main thread
                    // while a frame may be in flight, and a request that lands mid-draw must
                    // survive to trigger the next frame instead of being wiped after the draw.
                    isDirty = false
                    try {
                        drawEditor(canvas)
                    } finally {
                        runCatching {
                            holder.unlockCanvasAndPost(canvas)
                        }
                    }
                }
            }
        }
    }
}
