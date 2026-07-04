package com.contentreg.app.feature4_retention.shortcut

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Task 3 — center-crops a chosen photo to a square, scales it to a launcher-icon size, and persists
 * it in app-internal storage so the pinned shortcut can be re-created later.
 *
 * The geometry ([centerCropSquare]) and path ([iconFile]) helpers are pure and unit-tested; the
 * Bitmap decode/scale/compress calls are Android-only and device-verified.
 */
object IconImageStore {

    /** Target icon edge in px — a sane size for a launcher shortcut icon. */
    const val TARGET_SIZE_PX = 256

    private const val ICON_FILE_NAME = "custom_shortcut_icon.png"

    /** Center of the geometry: the largest centered square crop for a source of the given size. */
    fun centerCropSquare(srcWidth: Int, srcHeight: Int): CropRect {
        val size = minOf(srcWidth, srcHeight)
        val left = (srcWidth - size) / 2
        val top = (srcHeight - size) / 2
        return CropRect(left, top, size)
    }

    /** The internal-storage file backing the persisted icon. Pure — no Android types. */
    fun iconFile(filesDir: File): File = File(filesDir, ICON_FILE_NAME)

    data class CropRect(val left: Int, val top: Int, val size: Int)

    /**
     * Center-crops [source] to a square, scales to [TARGET_SIZE_PX], persists it as PNG, and returns
     * the scaled bitmap (ready to hand straight to the pin request).
     */
    fun save(context: Context, source: Bitmap): Bitmap {
        val crop = centerCropSquare(source.width, source.height)
        val cropped = Bitmap.createBitmap(source, crop.left, crop.top, crop.size, crop.size)
        val scaled = Bitmap.createScaledBitmap(cropped, TARGET_SIZE_PX, TARGET_SIZE_PX, true)
        iconFile(context.filesDir).outputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return scaled
    }

    /** Loads the persisted icon for a re-pin, or null if nothing has been saved yet. */
    fun load(context: Context): Bitmap? {
        val file = iconFile(context.filesDir)
        return if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }

    fun hasSaved(context: Context): Boolean = iconFile(context.filesDir).exists()
}
