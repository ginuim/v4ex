package com.v4ex.android.ui

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.AnnotatedString
import com.mikepenz.markdown.model.MarkdownAnnotator
import kotlin.math.roundToInt
import org.intellij.markdown.ast.ASTNode

private const val POSTER_BLOCK_START = "poster_block_start"

// Compose concatenates selected text nodes without separators. Mark the starts
// of Markdown blocks so the exported selection can restore paragraph breaks.
internal val posterBlockAnnotator = object : MarkdownAnnotator {
    override val annotate: AnnotatedString.Builder.(String, ASTNode) -> Boolean =
        { _, _ ->
            if (length == 0) markPosterBlockStart()
            false
        }
}

internal fun AnnotatedString.Builder.markPosterBlockStart() {
    addStringAnnotation(POSTER_BLOCK_START, "", 0, 0)
}

private fun AnnotatedString.posterText(): String {
    val breaks = getStringAnnotations(POSTER_BLOCK_START, 0, length)
        .map { it.start }
        .filter { it > 0 && it < length }
        .distinct()
        .sorted()
    return buildString {
        var start = 0
        for (end in breaks) {
            append(text, start, end)
            append("\n\n")
            start = end
        }
        append(text, start, text.length)
    }.replace("\uFFFC", "").trim()
}

@Composable
internal fun TopicTextSelection(
    onPosterRequested: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val clipboard = LocalClipboardManager.current
    val onPoster = rememberUpdatedState(onPosterRequested)
    val toolbar = remember(view, clipboard) {
        TopicSelectionToolbar(view, clipboard) { onPoster.value(it) }
    }
    DisposableEffect(toolbar) { onDispose { toolbar.hide() } }
    CompositionLocalProvider(
        LocalTextToolbar provides toolbar,
        LocalClipboardManager provides toolbar.clipboard,
    ) {
        SelectionContainer(content = content)
    }
}

private class TopicSelectionToolbar(
    private val view: View,
    systemClipboard: ClipboardManager,
    private val onPosterRequested: (String) -> Unit,
) : TextToolbar {
    private var mode: ActionMode? = null
    private var bounds = Rect.Zero
    private var copy: (() -> Unit)? = null
    private var selectAll: (() -> Unit)? = null
    private var readingSelection = false
    private var selectedText: String? = null

    val clipboard = object : ClipboardManager by systemClipboard {
        override fun setText(annotatedString: AnnotatedString) {
            if (readingSelection) {
                selectedText = annotatedString.posterText()
            } else {
                systemClipboard.setText(annotatedString)
            }
        }
    }

    override val status: TextToolbarStatus
        get() = if (mode == null) TextToolbarStatus.Hidden else TextToolbarStatus.Shown

    private val callback = object : ActionMode.Callback2() {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            updateMenu(menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            val copyVisible = copy != null
            val selectAllVisible = selectAll != null
            if ((menu.findItem(android.R.id.copy) != null) == copyVisible &&
                (menu.findItem(android.R.id.selectAll) != null) == selectAllVisible
            ) return false
            updateMenu(menu)
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            when (item.itemId) {
                android.R.id.copy -> copy?.invoke()
                android.R.id.selectAll -> selectAll?.invoke()
                POSTER_ACTION -> requestPoster()
                else -> return false
            }
            return true
        }

        override fun onGetContentRect(mode: ActionMode, view: View, outRect: android.graphics.Rect) {
            outRect.set(
                bounds.left.roundToInt(), bounds.top.roundToInt(),
                bounds.right.roundToInt(), bounds.bottom.roundToInt(),
            )
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            if (this@TopicSelectionToolbar.mode == mode) this@TopicSelectionToolbar.mode = null
        }
    }

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        bounds = rect
        copy = onCopyRequested
        selectAll = onSelectAllRequested
        val current = mode
        if (current == null) {
            mode = view.startActionMode(callback, ActionMode.TYPE_FLOATING)
        } else {
            current.invalidate()
            current.invalidateContentRect()
        }
    }

    override fun hide() {
        mode?.finish()
        mode = null
        copy = null
        selectAll = null
    }

    private fun updateMenu(menu: Menu) {
        menu.clear()
        if (copy != null) {
            menu.add(Menu.NONE, android.R.id.copy, 0, android.R.string.copy)
            menu.add(Menu.NONE, POSTER_ACTION, 1, "生成海报")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
        if (selectAll != null) {
            menu.add(Menu.NONE, android.R.id.selectAll, 2, android.R.string.selectAll)
        }
    }

    private fun requestPoster() {
        val copySelection = copy ?: return
        // SelectionContainer exposes the range through its synchronous copy action.
        // Capture that action's result locally while normal Copy still uses the system clipboard.
        readingSelection = true
        try {
            copySelection()
        } finally {
            readingSelection = false
        }
        val text = selectedText.orEmpty()
        selectedText = null
        if (text.isNotBlank()) onPosterRequested(text)
    }

    private companion object {
        const val POSTER_ACTION = 1
    }
}
