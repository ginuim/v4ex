package com.v4ex.android.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** 纯函数测试：正文图片「只缩不放」。 */
class ImageSizingTest {

    @Test
    fun smallerThanContainerKeepsOriginalPixels() {
        // 表情包场景：240px 的图放进去还是 240px，不再被拉到容器宽
        assertEquals(240 to 180, imageDisplaySizePx(240, 180, 890))
    }

    @Test
    fun widerThanContainerFitsWidthKeepingAspect() {
        // 1080x2400 的手机截图放进 890px 宽的容器：宽缩到 890，高同比
        assertEquals(890 to 1978, imageDisplaySizePx(1080, 2400, 890))
    }

    @Test
    fun exactlyContainerWidthIsUnchanged() {
        assertEquals(890 to 500, imageDisplaySizePx(890, 500, 890))
    }

    @Test
    fun invalidInputCollapsesToZero() {
        assertEquals(0 to 0, imageDisplaySizePx(0, 100, 890))
        assertEquals(0 to 0, imageDisplaySizePx(100, 0, 890))
        assertEquals(0 to 0, imageDisplaySizePx(100, 100, 0))
    }
}
