package audio.processing.spectrogram.dsp

/**
 * Finite Impulse Response
 *
 * @property impulseResponse
 */
class FIR(private val impulseResponse: FloatArray) {
    private var size: Int = impulseResponse.size
    private var delayLine: FloatArray
    private var cnt = 0

    init {
        delayLine = FloatArray(size)
    }

    private fun fir(sample: Float): Float {
        delayLine[cnt] = sample
        var result = 0.0F
        var idx = cnt
        for (i in 0 until size) {
            result += impulseResponse[i] * delayLine[idx--]
            if(idx<0) idx = size - 1
        }
        if (++cnt >= size) cnt=0
        return result
    }

    fun transform(sample: FloatArray) {
        sample.forEachIndexed { i, v ->
            sample[i] = fir(v)
        }
    }

}
