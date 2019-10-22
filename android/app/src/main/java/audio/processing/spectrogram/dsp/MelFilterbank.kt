package audio.processing.spectrogram.dsp

import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * Mel Filterbank
 *
 * @property fs
 * @property N
 * @property numFilters
 */
class MelFilterbank(val fs: Float, val N: Int = 512, val numFilters: Int = 40) {

    val nyquistFs = fs/2

    var filterbank = Array(numFilters+2){FloatArray(FILTER_LENGTH)}
    data class KRange(var left: Int, var len: Int)

    var kRangeArray = Array(numFilters + 2){
        KRange(
            0,
            0
        )
    }

    data class MelFilterbankDetails(val kRangeArray: Array<KRange>, val filterbank: Array<FloatArray>)
    val melFilterbankDetails: MelFilterbankDetails

    var signalBuf = FloatArray(N/2)

    companion object {
        const val FILTER_LENGTH = 64
    }

    init {
        val melMin = 0F
        val melMax = freqToMel(nyquistFs)

        var melPoints = FloatArray(numFilters+2)
        var hzPoints = FloatArray(numFilters+2)

        var f = FloatArray(numFilters+2)

        var deltaMel = (melMax - melMin) / (numFilters + 2)

        for (m in 0 until numFilters + 2) {
            melPoints[m] = deltaMel * m
            hzPoints[m] = melToFreq(melPoints[m])
            f[m] = floor((N+1) * hzPoints[m] / fs)
        }

        for (m in 1 until numFilters + 1) {
                val fMinus = f[m-1]
                val fCenter = f[m]
                val fPlus = f[m+1]
                val fMinusInt = fMinus.toInt()
                val fCenterInt = fCenter.toInt()
                val fPlusInt = fPlus.toInt()

                for (k in fMinusInt until fCenterInt) {
                    filterbank[m][k - fMinusInt] = (k - fMinus) / (fCenter - fMinus)
                }
                for (k in fCenterInt until fPlusInt) {
                    filterbank[m][k - fMinusInt] = (fPlus - k) / (fPlus - fCenter)
                }

                kRangeArray[m].left = fMinusInt
                kRangeArray[m].len = fPlusInt - fMinusInt + 1
        }
        melFilterbankDetails = MelFilterbankDetails(kRangeArray, filterbank)
    }

    private fun freqToMel(hz: Float): Float {
        return 2595F * log10(hz / 700F + 1F)
    }

    private fun melToFreq(mel: Float): Float {
        return 700F * (10F.pow(mel/2595F) - 1F)
    }


    fun applyFilterbank(buf: FloatArray) {
        for (i in 0 until signalBuf.size) {
            signalBuf[i] = 0F
        }

        for (m in 1 until numFilters + 1) {
            val leftK = kRangeArray[m].left
            val len = kRangeArray[m].len

            var sum = 0F
            val filter = filterbank[m]

            for (i in 0 until len) {
                sum += buf[leftK+i] * filter[i]
            }
            signalBuf[m-1] = sum
        }

        for (i in 0 until numFilters) {
            buf[i] = signalBuf[i]
        }
    }

}