package com.v4ex.android.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.clickable
import com.mikepenz.markdown.model.ImageTransformer
import com.mikepenz.markdown.model.ImageData
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.coil2.Coil2ImageTransformerImpl
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownPadding
import com.v4ex.android.ui.theme.Ink

/** Explicit renderer choice keeps the old implementation available for comparison. */
enum class MarkdownRenderer { Modern, Legacy }

internal fun normalizeMarkdownLineEndings(text: String): String =
    text.replace("\r\n", "\n").replace('\r', '\n')

/** Reusable body renderer; callers can choose an engine without depending on either library. */
@Composable
fun MarkdownContent(
    text: String,
    modifier: Modifier = Modifier,
    renderer: MarkdownRenderer = MarkdownRenderer.Modern,
) {
    var preview by remember(text) { mutableStateOf<String?>(null) }
    val images = remember(text) {
        object : ImageTransformer by Coil2ImageTransformerImpl {
            @Composable
            override fun transform(url: String): ImageData? {
                val data = Coil2ImageTransformerImpl.transform(url) ?: return null
                return data.copy(modifier = data.modifier.clickable { preview = url })
            }
        }
    }
    preview?.let { ImagePreview(it) { preview = null } }
    when (renderer) {
        MarkdownRenderer.Legacy -> RichContent(text, modifier, paragraphSpacing = 18.dp)
        MarkdownRenderer.Modern -> {
            val body = TextStyle(fontSize = 15.sp, lineHeight = 25.sp)
            Markdown(
                content = remember(text) { normalizeMarkdownLineEndings(text) },
                modifier = modifier.fillMaxWidth(),
                imageTransformer = images,
                colors = markdownColor(
                    text = Ink.Primary, linkText = Ink.Blue,
                    codeBackground = Ink.ChipBg, dividerColor = Ink.Line,
                ),
                typography = markdownTypography(
                    h1 = body.copy(fontSize = 21.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
                    h2 = body.copy(fontSize = 19.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
                    h3 = body.copy(fontSize = 17.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
                    h4 = body.copy(fontWeight = FontWeight.SemiBold),
                    h5 = body.copy(fontWeight = FontWeight.SemiBold),
                    h6 = body.copy(fontWeight = FontWeight.SemiBold),
                    text = body, paragraph = body, ordered = body,
                    bullet = body, list = body, quote = body, link = body,
                ),
                padding = markdownPadding(block = 8.dp),
            )
        }
    }
}

/** Topic bodies use the modern renderer. */
@Composable
fun TopicBody(text: String, modifier: Modifier = Modifier) {
    MarkdownContent(text = text, modifier = modifier)
}
