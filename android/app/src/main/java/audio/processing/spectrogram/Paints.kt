package audio.processing.spectrogram

import android.graphics.Color
import android.graphics.Paint

val mPaintDarkGrayStroke = Paint().apply {
    style = Paint.Style.STROKE
    color = Color.DKGRAY
    strokeWidth = 1F
}

val mPaintCyanStroke = Paint().apply {
    style = Paint.Style.STROKE
    color = Color.CYAN
    strokeWidth = 2F
}

val mPaintGreenStroke = Paint().apply {
    style = Paint.Style.STROKE
    color = Color.GREEN
    strokeWidth = 5F
}

val mPaintRedStroke = Paint().apply {
    style = Paint.Style.STROKE
    color = Color.RED
    strokeWidth = 5F
}

val mPaintYellowFill = Paint().apply {
    style = Paint.Style.FILL
    color = Color.YELLOW
    strokeWidth = 0F
}
