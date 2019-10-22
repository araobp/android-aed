package audio.processing.spectrogram

import android.graphics.Bitmap
import android.graphics.Matrix

fun rotate(src: Bitmap, angle: Int, flip: Boolean = false): Bitmap {

    if (angle == 0 && !flip) {
        return src
    }

    val matrix = Matrix()
    val centerX = src.width.toFloat() / 2
    val centerY = src.height.toFloat() / 2
    matrix.setRotate(angle.toFloat(), centerX, centerY)
    if (flip) matrix.postScale(-1F, 1F, centerX, centerY)
    return Bitmap.createBitmap(
        src, 0, 0,
        src.width, src.height, matrix, true
    )
}
