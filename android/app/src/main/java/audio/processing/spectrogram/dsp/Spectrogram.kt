package audio.processing.spectrogram.dsp

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import audio.processing.spectrogram.rotate
import edu.emory.mathcs.jtransforms.fft.FloatFFT_1D
import kotlin.math.*

/**
 * Note: In general, MFCCs are used as features for machine learning. But, in my experience,
 * MFSCs with CNN shows better accuracy rate. Therefore, this class does not implement
 * MFCCs.
 */
class Spectrogram(
    private val fs: Int,
    bufferSizeInShort: Int,
    numBlocks: Int,
    private val fftSize: Int = 512,
    private val melFilterbankSize: Int = 40
) {

    companion object {
        const val TAG = "Spec"
        private const val ALPHA = 0.97F  // 0.95 ~ 0.97
        val PRE_EMPHASIS_MPULSE_RESPONSE = floatArrayOf(-ALPHA, 1.0F)
    }

    // TODO: currently, this class supports quantized feature only (uint8_t, 0 - 255 range).
    data class AudioFeature(
        val fs: Int,
        val fftSize: Int,
        val melFilterbankSize: Int,
        val FeatureSize: Int,
        val mfsc: IntArray
    )

    // FFT size
    private val N = fftSize
    private val N_HALF = N / 2

    // Mel-filterbank size
    private val NUM_FILTERS = melFilterbankSize  // The number of filters in the filterbank

    // Spectrogram length corresponding to the size of audio recording
    private var M: Int

    // FIR (Finite Impulse Response)
    private var mPreEmphasisFir = FIR(PRE_EMPHASIS_MPULSE_RESPONSE)

    // FFT
    private var mHannWindow = FloatArray(N)  // Hann windows
    private val mFloatfft1d = FloatFFT_1D(N)  // Instance of FloatFFT_1D
    private var mDelayedLine = ShortArray(bufferSizeInShort + N)  // Raw audio buffer
    private var mDelayedLineSize = 0
    private var mBufFloat = FloatArray(N)  // Raw audio buffer for FFT

    // Spectrogram
    private var mSpectrogram: Array<FloatArray>
    private var mPosSpectrogram: Int = 0
    private var mFloatArraySpec: FloatArray
    private var mIntArraySpec: IntArray
    private var mIntArraySpecImg: IntArray

    // Mel-frequency spectral coefficients
    private var mMfsc: Array<FloatArray>
    private var mPosMfsc: Int = 0
    private var mFloatArrayMfsc: FloatArray
    private var mIntArrayMfsc: IntArray
    private var mIntArrayMfscImg: IntArray

    // Mel-frequency filterbank
    private var mMelFilterbank = MelFilterbank(fs.toFloat(), N, NUM_FILTERS)

    // Temporary buffer for PSD
    private var mBufPsd = FloatArray(0)

    // Bitmap
    private val mBitmapSpectrogram: Bitmap
    private val mBitmapMFSCs: Bitmap

    init {
        // Generate Hann window
        val scale = 2.0 * PI / N
        for (n in 0 until N) {
            mHannWindow[n] = 0.5F - 0.5F * cos(n * scale).toFloat()
        }

        // The size of mSpectrogram
        M = bufferSizeInShort * numBlocks / N * 2
        Log.d(TAG, "The size of mSpectrogram: $bufferSizeInShort, $numBlocks, $M")

        // Spectrogram
        mSpectrogram = Array(M) { FloatArray(N_HALF) }
        mFloatArraySpec = FloatArray(M * N_HALF)
        mIntArraySpec = IntArray(M * N_HALF)
        mIntArraySpecImg = IntArray(M * N_HALF)

        // Mel-frequency spectral coefficients
        mMfsc = Array(M) { FloatArray(NUM_FILTERS) }
        mFloatArrayMfsc = FloatArray(M * NUM_FILTERS)
        mIntArrayMfsc = IntArray(M * NUM_FILTERS)
        mIntArrayMfscImg = IntArray(M * NUM_FILTERS)

        // Bitmaps
        mBitmapSpectrogram = Bitmap.createBitmap(N_HALF, M, Bitmap.Config.ARGB_8888)
        mBitmapMFSCs = Bitmap.createBitmap(NUM_FILTERS, M, Bitmap.Config.ARGB_8888)
    }

    fun update(buf: ShortArray, enablePreEmphasis: Boolean = true) {
        for (i in buf.indices) {
            mDelayedLine[mDelayedLineSize + i] = buf[i]
        }
        mDelayedLineSize += buf.size
        var offset = 0
        do {
            for (j in 0 until N) {
                mBufFloat[j] = mDelayedLine[offset + j].toFloat()
            }
            sFft(mBufFloat, enablePreEmphasis)
            offset += N_HALF  // 50% overlap
        } while (mDelayedLineSize - offset >= N)
        for (i in offset until mDelayedLineSize) {
            mDelayedLine[i - offset] = mDelayedLine[i]
        }
        mDelayedLineSize -= offset
    }

    fun getPsd(): IntArray {
        val pos = if (mPosSpectrogram == 0) mPosSpectrogram else mPosSpectrogram - 1
        return mSpectrogram[pos].map { it.toInt() }.toIntArray()
    }

    private fun getSpectrogram(normalize: Boolean = true): IntArray {
        var idx = 0
        for (m in mPosSpectrogram until M) {
            val spec = mSpectrogram[m]
            for (n in 0 until N_HALF) {
                mFloatArraySpec[idx++] = spec[n]
            }
        }
        for (m in 0 until mPosSpectrogram) {
            val spec = mSpectrogram[m]
            for (n in 0 until N_HALF) {
                mFloatArraySpec[idx++] = spec[n]
            }
        }
        if (normalize) {
            normalize(mFloatArraySpec, mIntArraySpec)
        } else {
            mFloatArraySpec.forEachIndexed { i, _ ->
                val s = mFloatArraySpec[i].toInt()
                mIntArraySpec[i] = if (s < 0) 0 else (s and 0xff)
            }
        }
        return mIntArraySpec
    }

    fun getSpectrogramBitmap(rotationDegrees: Int, normalize: Boolean = false): Bitmap {
        applyColorMap(getSpectrogram(normalize), mIntArraySpecImg)
        mBitmapSpectrogram.setPixels(mIntArraySpecImg, 0, N_HALF, 0, 0, N_HALF, M)
        return rotate(mBitmapSpectrogram, rotationDegrees)
    }

    fun getAudioFeature(): AudioFeature {
        return AudioFeature(
            fs = fs,
            fftSize = fftSize,
            melFilterbankSize = melFilterbankSize,
            FeatureSize = M,
            mfsc = mIntArrayMfsc
        )
    }

    private fun getMfsc(normalize: Boolean = true): IntArray {
        var idx = 0
        for (m in mPosMfsc until M) {
            val coeffs = mMfsc[m]
            for (n in 0 until NUM_FILTERS) {
                mFloatArrayMfsc[idx++] = coeffs[n]
            }
        }
        for (m in 0 until mPosMfsc) {
            val coeffs = mMfsc[m]
            for (n in 0 until NUM_FILTERS) {
                mFloatArrayMfsc[idx++] = coeffs[n]
            }
        }
        if (normalize) {
            normalize(mFloatArrayMfsc, mIntArrayMfsc)
        } else {
            mFloatArrayMfsc.forEachIndexed { i, v ->
                val m = v.toInt()
                mIntArrayMfsc[i] = if (m < 0) 0 else (m and 0xff)
            }
        }
        return mIntArrayMfsc
    }

    fun getMfscBitmap(rotationDegrees: Int, normalize: Boolean = false): Bitmap {
        applyColorMap(getMfsc(normalize), mIntArrayMfscImg)
        mBitmapMFSCs.setPixels(mIntArrayMfscImg, 0, NUM_FILTERS, 0, 0, NUM_FILTERS, M)
        return rotate(mBitmapMFSCs, rotationDegrees)
    }

    fun getFilterbankDetails(): MelFilterbank.MelFilterbankDetails {
        return mMelFilterbank.melFilterbankDetails
    }

    /**
     * Min-max normalize, 0 - 255 range
     */
    private fun normalize(floatArray: FloatArray, intArray: IntArray) {
        check(floatArray.size == intArray.size)
        val size = intArray.size
        var max = 0F
        var min = 255F
        for (i in 0 until size) {
            if (floatArray[i] > max) max = floatArray[i]
            if (floatArray[i] < min) min = floatArray[i]
        }
        val maxMin = max - min
        for (i in 0 until size) {
            intArray[i] = ((floatArray[i] - min) * 255F / maxMin).toInt()
        }
    }

    /**
     * Color map "Coral reef sea"
     */
    private fun applyColorMap(src: IntArray, dst: IntArray) {
        for (i in src.indices) {
            val mag = src[i]
            dst[i] = Color.argb(0xff, 128 - mag / 2, mag, 128 + mag / 2)
        }
    }

    /**
     * Note: pre-emphasis is required for emphasizing feature values in high frequencies.
     */
    private fun applyPreEmphasis(buf: FloatArray) {
        mPreEmphasisFir.transform(buf)
    }

    /**
     * PCM audio output on Android OS is AC-coupled, so this function is unnecessary.
    private fun applyAcCoupling(buf: FloatArray) {
    }
     */

    private fun applyHann(buf: FloatArray) {
        check(buf.size == N)
        mHannWindow.forEachIndexed { i, v -> buf[i] = v * buf[i] }
    }

    private fun applyFft(buf: FloatArray) {
        mFloatfft1d.realForward(buf)
    }

    /**
     * Note: Since the input is an output from Real FFT, half of the array is real and
     * the other half is imaginary.
     *
     * Reference: http://wendykierp.github.io/JTransforms/apidocs/
     *
     * @param buf
     */
    private fun applyPsd(buf: FloatArray) {
        val n = N.toFloat()
        for (k in 2 until N step 2) {
            // sqrt(re * re + im * im)
            buf[k / 2] = sqrt(buf[k].pow(2) + buf[k + 1].pow(2)) / n
        }
    }

    /**
     * Note: Since the input is an output from PSD from Real FFT, half of the array is effective.
     *
     * @param src
     */
    private fun applyLogscale(src: FloatArray, dst: FloatArray) {
        for (i in 0 until N_HALF) {
            var v = src[i]
            if (v == 0F) v = Float.MAX_VALUE  // to avoid "log10(0) = -Infinity"
            dst[i] = 20.0F * log10(v)
        }
    }

    /**
     * Short-time Fourier Transform
     *
     * @param buf
     */
    private fun sFft(buf: FloatArray, enablePreEmphasis: Boolean = true) {
        if (mBufPsd.size != buf.size / 2) {
            mBufPsd = FloatArray(buf.size / 2)
        }

        if (enablePreEmphasis) applyPreEmphasis(buf)

        applyHann(buf)
        applyFft(buf)
        applyPsd(buf)
        applyLogscale(buf, mBufPsd)

        // Update getSpectrogramBitmap
        val spec = mSpectrogram[mPosSpectrogram]
        for (i in 0 until N_HALF) {
            spec[i] = mBufPsd[i]
        }
        if (++mPosSpectrogram >= M) mPosSpectrogram = 0

        // Update MFSCs
        mMelFilterbank.applyFilterbank(buf)
        applyLogscale(buf, buf)
        val coefs = mMfsc[mPosMfsc]
        for (i in 0 until NUM_FILTERS) {
            coefs[i] = buf[i]
        }
        if (++mPosMfsc >= M) mPosMfsc = 0
    }

}