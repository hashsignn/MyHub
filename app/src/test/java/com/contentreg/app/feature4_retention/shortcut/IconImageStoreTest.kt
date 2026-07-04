package com.contentreg.app.feature4_retention.shortcut

import org.junit.Assert.assertEquals
import java.io.File
import org.junit.Test

/** Task 3 — unit tests for the pure geometry/path helpers in [IconImageStore]. */
class IconImageStoreTest {

    @Test
    fun `centerCropSquare on a landscape image crops horizontally`() {
        assertEquals(
            IconImageStore.CropRect(left = 50, top = 0, size = 100),
            IconImageStore.centerCropSquare(srcWidth = 200, srcHeight = 100),
        )
    }

    @Test
    fun `centerCropSquare on a portrait image crops vertically`() {
        assertEquals(
            IconImageStore.CropRect(left = 0, top = 50, size = 100),
            IconImageStore.centerCropSquare(srcWidth = 100, srcHeight = 200),
        )
    }

    @Test
    fun `centerCropSquare on a square image is a no-op crop`() {
        assertEquals(
            IconImageStore.CropRect(left = 0, top = 0, size = 100),
            IconImageStore.centerCropSquare(srcWidth = 100, srcHeight = 100),
        )
    }

    @Test
    fun `centerCropSquare uses the shorter edge as the size`() {
        assertEquals(64, IconImageStore.centerCropSquare(300, 64).size)
        assertEquals(64, IconImageStore.centerCropSquare(64, 300).size)
    }

    @Test
    fun `iconFile uses the fixed name under the given dir`() {
        val dir = File("some/dir")
        val f = IconImageStore.iconFile(dir)
        assertEquals("custom_shortcut_icon.png", f.name)
        assertEquals(dir, f.parentFile)
    }
}
