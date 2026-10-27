package com.v4ex.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v4ex.android.ui.theme.Ink

/** 黑白反色铭牌；随主题切换，保持文字和底色的对比。 */
@Composable
fun ProBadge(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(5.dp)
    Box(
        modifier.clip(shape)
            .background(Ink.Primary)
            .border(0.5.dp, Ink.OnPrimary.copy(alpha = 0.18f), shape)
            .semantics(mergeDescendants = true) { contentDescription = "PRO 会员" }
            .padding(horizontal = 7.dp, vertical = 4.dp),
    ) {
        Text(
            "PRO",
            color = Ink.OnPrimary,
            style = TextStyle(
                fontSize = 10.sp,
                lineHeight = 11.sp,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
        )
    }
}
