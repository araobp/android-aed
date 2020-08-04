package audio.processing.spectrogram.dsp

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.experimental.and


/**
 * WAVE file header for 16bit PCM mono audio
 */
fun waveHeader(dataLen: Int, samplingRate: Int): ByteArray {
    val totalDataLen = 36 + dataLen
    val byteRate = samplingRate * 2

    return byteArrayOf(
        'R'.toByte(),
        'I'.toByte(),
        'F'.toByte(),
        'F'.toByte(),

        (totalDataLen and 0xff).toByte(),
        ((totalDataLen ushr 8) and 0xff).toByte(),
        ((totalDataLen ushr 16) and 0xff).toByte(),
        ((totalDataLen ushr 24) and 0xff).toByte(),

        'W'.toByte(),
        'A'.toByte(),
        'V'.toByte(),
        'E'.toByte(),

        'f'.toByte(),
        'm'.toByte(),
        't'.toByte(),
        ' '.toByte(),

        16, // sub chunk size
        0,
        0,
        0,

        1,  // PCM
        0,

        1,  // the number of channel
        0,

        (samplingRate and 0xff).toByte(),
        ((samplingRate ushr 8) and 0xff).toByte(),
        ((samplingRate ushr 16) and 0xff).toByte(),
        ((samplingRate ushr 24) and 0xff).toByte(),

        (byteRate and 0xff).toByte(),
        ((byteRate ushr 8) and 0xff).toByte(),
        ((byteRate ushr 16) and 0xff).toByte(),
        ((byteRate ushr 24) and 0xff).toByte(),

        2,  // Block align
        0,

        16,  // bits per sample
        0,

        'd'.toByte(),
        'a'.toByte(),
        't'.toByte(),
        'a'.toByte(),

        (dataLen and 0xff).toByte(),
        ((dataLen ushr 8) and 0xff).toByte(),
        ((dataLen ushr 16) and 0xff).toByte(),
        ((dataLen ushr 24) and 0xff).toByte()
    )
}

/**
 * Save raw PCM data in a WAVE file
 */
fun savePCM(context: Context, rawPcmData: ShortArray, waveFile: File, samplingRate: Int) {

    val rawData = ByteArray(rawPcmData.size * 2)
    for (i in rawPcmData.indices) {
        val sample = rawPcmData[i]
        rawData[2*i] = (sample and 0xff).toByte()
        rawData[2*i+1] = (sample.toUInt() shr 8).toByte()
    }

    var output: DataOutputStream? = null
    val header = waveHeader(rawData.size, samplingRate)
    try {
        output = DataOutputStream(FileOutputStream(waveFile))
        output.write(header)
        output.write(rawData)

        /*
        // Notify Android's media manager of the creation of new file
        val contentUri: Uri = Uri.fromFile(waveFile)
        val mediaScanIntent = Intent(
            Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
            contentUri
        )
        context.sendBroadcast(mediaScanIntent)
         */
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        output?.close()
    }
}