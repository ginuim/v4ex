package com.v4ex.android.ui.theme

import com.v4ex.android.ui.MdBlock
import com.v4ex.android.ui.parseMarkdown
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeTest {
    @Test fun unknownStoredModeFallsBackToSystem() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored("old-value"))
        ThemeMode.entries.forEach { assertEquals(it, ThemeMode.fromStored(it.name)) }
    }

    @Test fun markdownSpansUseTheSuppliedPalette() {
        for (palette in listOf(LightTokens, DarkTokens)) {
            val block = parseMarkdown("`code` [link](https://example.com)", palette.Blue, palette.ChipBg)
                .single() as MdBlock.Para
            assertEquals(palette.ChipBg, block.text.spanStyles.first().item.background)
            assertEquals(palette.Blue, block.text.spanStyles.last().item.color)
        }
    }
}
