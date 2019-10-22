package audio.processing.spectrogram.tflite

import android.app.Activity

class AcousticEventDetector(
    activity: Activity?,
    device: Device?,
    numThreads: Int,
    val featureWidth: Int,
    val melFilterbankSize: Int
) :
    Classifier(activity, device, numThreads, featureWidth, melFilterbankSize) {

    companion object {
        const val CLASS_LABELS_FILE = "labels.txt"
        const val MODEL_FILE = "aed.tflite"  // TFLite model for Acoustic Event Detection
        const val NUM_BYTES_PER_CHANNEL = 4  // 32bit float
    }

    private var labelProbArray: Array<FloatArray>? = null

    override fun getModelPath(): String {

        return MODEL_FILE
    }

    override fun getLabelPath(): String {
        return CLASS_LABELS_FILE
    }

    override fun getNumBytesPerChannel(): Int {
        return NUM_BYTES_PER_CHANNEL
    }

    override fun addPixelValue(pixelValue: Float) {
        imgData.putFloat(pixelValue)
    }

    override fun getProbability(labelIndex: Int): Float {
        return labelProbArray!![0][labelIndex]
    }

    override fun setProbability(
        labelIndex: Int,
        value: Number
    ) {
        labelProbArray!![0][labelIndex] = value.toFloat()
    }

    override fun getNormalizedProbability(labelIndex: Int): Float {
        return labelProbArray!![0][labelIndex]
    }

    override fun runInference() {
        tflite.run(imgData, labelProbArray)
    }

    /**
     * Initializes a `ClassifierFloatMobileNet`.
     *
     * @param activity
     */

    init {
        labelProbArray = Array(1) { FloatArray(numLabels) }
    }
}