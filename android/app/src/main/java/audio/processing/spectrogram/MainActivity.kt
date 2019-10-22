package audio.processing.spectrogram

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.media.*
import android.media.audiofx.AutomaticGainControl
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.Surface
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import audio.processing.spectrogram.dsp.Spectrogram
import audio.processing.spectrogram.dsp.savePCM
import audio.processing.spectrogram.tflite.AcousticEventDetector
import audio.processing.spectrogram.tflite.Classifier
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.concurrent.thread


class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "Main"

        const val SCALEDOWN = 0.08F
        const val SCALEUP = 1.7F

        const val INFERENCE_INTERVAL = 30

        val SAMPLING_FREQS = listOf(8000, 16000, 22050, 44100, 48000)
        val FFT_SIZES = listOf(128, 256, 512)
        val MEL_FILTERBANK_SIZES = listOf(20, 30, 40, 64)
        val SPEC_LENGTHS = listOf(1, 2, 3, 4, 5)
        val FEATURE_WIDTHS = listOf(40, 64, 96, 128)

        const val PREFS_NAME = "mSpectrogram"

        val DIR =
            Environment.getExternalStorageDirectory().toString() + "/Android/media/audio.processing.spectrogram"
    }

    // Paint objects
    private lateinit var mPaintDarkGrayStroke: Paint
    private lateinit var mPaintCyanStroke: Paint
    private lateinit var mPaintGreenStroke: Paint
    private lateinit var mPaintRedStroke: Paint
    private lateinit var mPaintYellowFill: Paint

    // Screen rotation
    private var mCurrentRotaionDegrees = 0

    // Audio-related objects
    private lateinit var mSpectrogram: Spectrogram
    private var mAudioRecord: AudioRecord? = null
    private var mAudioTrack: AudioTrack? = null

    // Recording status
    private var mRecording = false
    private var mDestroy = false

    // Audio buffers
    private var mBufferSizeInShort = 0
    private lateinit var mBufPlay: ShortArray
    private lateinit var mBufRecord: ShortArray

    // Class labels
    private lateinit var mClassLabels: MutableList<String>

    // Feature parameters
    private var mSamplingFreq: Int = 0
    private var mSamplingFreqWaiting: Int? = null
    private var mFftSize: Int = 0
    private var mFftSizeWaiting: Int? = null
    private var mMelFilterbankSize: Int = 0
    private var mMelFilterbankSizeWating: Int? = null
    private var mSpecLength: Int = 0
    private var mSpecLengthWaiting: Int? = null

    // Recorded audio in PCM format
    private var mRecordedAudio = ShortArray(0)

    // Feature dimension
    private var mFeatureCenter: Int = 0
    private var mFeatureWidth: Int = 0
    private var mRatio: Float = 0F  // feature size to canvas size

    // Audio and feature filess
    private var mLastAudioFileName = "X"

    private fun regexAudio() = Regex("[a-zA-Z0-9_\\-]+.wav")
    private fun regexFeature() = Regex("${classLabel()}-[a-zA-Z0-9_\\-]+.json")
    private fun regexFeaturePerLastAudio() = Regex("${mLastAudioFileName}-[0-9]+.json")

    // Acoustic Event Detector
    private lateinit var mAcousticEventDetector: AcousticEventDetector

    // Reset spectrogram
    private fun resetSpectrogram() {
        try {
            val numBlocks = initAudio()
            mSpectrogram =
                Spectrogram(
                    mSamplingFreq,
                    mBufferSizeInShort,
                    numBlocks,
                    mFftSize,
                    mMelFilterbankSize
                )
            saveParameters()
            progressBar.max = numBlocks
            progressBar.progress = 0
        } catch (e: Exception) {  // Initialization failure
            saveParameters(fallback = true)  // Fallback to default
            finish()
        }
    }

    private fun fileCnt() {
        var cntAudio = 0
        try {
            File(DIR).list()
                .forEach { filename -> if (regexAudio().matches(filename)) ++cntAudio }

            var cntFeature = 0
            File(DIR).list()
                .forEach { filename -> if (regexFeature().matches(filename)) ++cntFeature }

            var cntFeaturePerLastAudio = 0
            File(DIR).list()
                .forEach { filename -> if (regexFeaturePerLastAudio().matches(filename)) ++cntFeaturePerLastAudio }

            runOnUiThread {
                textViewFileCount.text = "$cntAudio-$cntFeaturePerLastAudio($cntFeature)"
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveAudioAsWaveFile() {
        val current = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
        val formatted = current.format(formatter)
        mLastAudioFileName = "${classLabel()}-${formatted}"
        val file = File(
            DIR,
            "${mLastAudioFileName}.wav"
        )
        savePCM(this, mRecordedAudio, file, mSamplingFreq)
        fileCnt()
    }

    private fun showSettingDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.setting)

        val spinnerSamplingFreq = dialog.findViewById<Spinner>(R.id.spinnerSamplingFreq)
        val spinnerFftSize = dialog.findViewById<Spinner>(R.id.spinnerFftSize)
        val spinnerMelFilterbank = dialog.findViewById<Spinner>(R.id.spinnerMelFilterbank)
        val spinnerSpecLength = dialog.findViewById<Spinner>(R.id.spinnerSpecLength)
        val spinnerFeatureWidth = dialog.findViewById<Spinner>(R.id.spinnerFeatureWidth)

        // Sampling frequencies
        val adapterSamplingFreqs = ArrayAdapter(
            this, android.R.layout.simple_spinner_item,
            SAMPLING_FREQS
        )
        adapterSamplingFreqs.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSamplingFreq.adapter = adapterSamplingFreqs
        spinnerSamplingFreq.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                    if (started()) mSamplingFreqWaiting =
                        spinnerSamplingFreq.getItemAtPosition(p2) as Int
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                }
            }
        spinnerSamplingFreq.setSelection(adapterSamplingFreqs.getPosition(mSamplingFreq))

        // FFT sizes
        val adapterFftSizes = ArrayAdapter(
            this, android.R.layout.simple_spinner_item,
            FFT_SIZES
        )
        adapterFftSizes.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFftSize.adapter = adapterFftSizes
        spinnerFftSize.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                if (started()) mFftSizeWaiting = spinnerFftSize.getItemAtPosition(p2) as Int
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {
            }
        }
        spinnerFftSize.setSelection(adapterFftSizes.getPosition(mFftSize))

        // Mel-filterbank sizes
        val adapterMelFiterbankSizes = ArrayAdapter(
            this, android.R.layout.simple_spinner_item,
            MEL_FILTERBANK_SIZES
        )
        adapterMelFiterbankSizes.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerMelFilterbank.adapter = adapterMelFiterbankSizes
        spinnerMelFilterbank.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                    if (started()) mMelFilterbankSizeWating =
                        spinnerMelFilterbank.getItemAtPosition(p2) as Int
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                }
            }
        spinnerMelFilterbank.setSelection(
            adapterMelFiterbankSizes.getPosition(
                mMelFilterbankSize
            )
        )

        // Spec length
        val adapterSpecLength = ArrayAdapter(
            this, android.R.layout.simple_spinner_item,
            SPEC_LENGTHS
        )
        adapterSpecLength.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSpecLength.adapter = adapterSpecLength
        spinnerSpecLength.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                if (started()) mSpecLengthWaiting =
                    spinnerSpecLength.getItemAtPosition(p2) as Int
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {
            }
        }
        spinnerSpecLength.setSelection(adapterSpecLength.getPosition(mSpecLength))

        // Feature length
        val adapterFeatureWidth = ArrayAdapter(
            this, android.R.layout.simple_spinner_item,
            FEATURE_WIDTHS
        )
        adapterFeatureWidth.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFeatureWidth.adapter = adapterFeatureWidth
        spinnerFeatureWidth.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                    mFeatureWidth = spinnerFeatureWidth.getItemAtPosition(p2) as Int
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                }
            }
        spinnerFeatureWidth.setSelection(adapterFeatureWidth.getPosition(mFeatureWidth))

        dialog.show()
    }

    private fun started(): Boolean {
        return lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }

    override fun onPause() {
        saveParameters()
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        // Directory check
        val dir = File(DIR)
        if (!dir.isDirectory && !dir.exists()) {
            dir.mkdirs()
        }

        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")

        setContentView(R.layout.activity_main)

        // Load parameters from local preferences
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        mClassLabels =
            prefs.getString("classLabels", "<Please register!>").split(",").toMutableList()
        mSamplingFreq = prefs.getInt("fs", SAMPLING_FREQS[1])
        mFftSize = prefs.getInt("fftSize", FFT_SIZES[2])
        mMelFilterbankSize = prefs.getInt("melFilterbankSize", MEL_FILTERBANK_SIZES[2])
        mSpecLength = prefs.getInt("specLength", SPEC_LENGTHS[1])
        mFeatureWidth = prefs.getInt("featureWidth", FEATURE_WIDTHS[1])
        mFeatureCenter = prefs.getInt("featureCenter", 64)

        val adapterClasses =
            ArrayAdapter(this, android.R.layout.simple_spinner_item, mClassLabels)
        adapterClasses.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerClasses.adapter = adapterClasses

        mPaintDarkGrayStroke = Paint()
        mPaintDarkGrayStroke.style = Paint.Style.STROKE
        mPaintDarkGrayStroke.color = Color.DKGRAY
        mPaintDarkGrayStroke.strokeWidth = 1F

        mPaintCyanStroke = Paint()
        mPaintCyanStroke.style = Paint.Style.STROKE
        mPaintCyanStroke.color = Color.CYAN
        mPaintCyanStroke.strokeWidth = 2F

        mPaintGreenStroke = Paint()
        mPaintGreenStroke.style = Paint.Style.STROKE
        mPaintGreenStroke.color = Color.GREEN
        mPaintGreenStroke.strokeWidth = 5F

        mPaintRedStroke = Paint()
        mPaintRedStroke.style = Paint.Style.STROKE
        mPaintRedStroke.color = Color.RED
        mPaintRedStroke.strokeWidth = 5F

        mPaintYellowFill = Paint()
        mPaintYellowFill.style = Paint.Style.FILL
        mPaintYellowFill.color = Color.YELLOW
        mPaintYellowFill.strokeWidth = 0F

        buttonSetting.setOnClickListener {
            showSettingDialog()
        }

        toggleButtonRecord.setOnClickListener {
            if (!mRecording) {
                mRecording = true
                startRecording()
            } else {
                mRecording = false
            }
        }

        radioButtonWave.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!mRecording) drawWave()
            }
        }

        radioButtonPSD.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!mRecording) drawPsd()
            }
        }

        radioButtonSpectrogram.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!mRecording) drawSpectrogram()
            }
        }

        radioButtonMFSCs.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!mRecording) drawMFSCs()
            }
        }

        radioButtonFilterbank.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!mRecording) drawMelFilterbank()
            }
        }

        buttonClass.setOnClickListener {
            val dialog = Dialog(this)
            dialog.setContentView(R.layout.class_edit)
            val listViewLabels = dialog.findViewById<ListView>(R.id.listViewLabels)
            val buttonAdd = dialog.findViewById<Button>(R.id.buttonAdd)
            val buttonDelete = dialog.findViewById<Button>(R.id.buttonDelete)
            val editTextClassLabel = dialog.findViewById<EditText>(R.id.editTextClassLabel)

            var adapterClasses = ArrayAdapter(
                this,
                android.R.layout.simple_list_item_1,
                mClassLabels.toList()
            )
            listViewLabels.adapter = adapterClasses

            var posListViewLabels: Int? = null

            listViewLabels.setOnItemClickListener { _, _, position, _ ->
                posListViewLabels = position
            }

            buttonDelete.setOnClickListener {
                posListViewLabels?.let {
                    mClassLabels.removeAt(it)
                    adapterClasses = ArrayAdapter(
                        this,
                        android.R.layout.simple_list_item_1,
                        mClassLabels.toList()
                    )
                    listViewLabels.adapter = adapterClasses
                    saveParameters()
                }
            }

            buttonAdd.setOnClickListener {
                var classLabel = editTextClassLabel.text.toString()
                if (classLabel.matches(Regex("[A-Za-z0-9_]+"))) {
                    classLabel = classLabel.toCharArray()
                        .filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' }
                        .joinToString(separator = "") // Remove non-alphanumeric chars
                    if (!mClassLabels.contains(classLabel)) {
                        mClassLabels.add(classLabel)
                        adapterClasses = ArrayAdapter(
                            this,
                            android.R.layout.simple_list_item_1,
                            mClassLabels.toList()
                        )
                        listViewLabels.adapter = adapterClasses
                        saveParameters()
                    }
                }
            }

            dialog.setOnDismissListener {
                spinnerClasses.adapter = adapterClasses
            }

            dialog.show()
        }

        buttonPlay.setOnClickListener {
            mAudioTrack?.let { track ->
                if (!mRecording) {
                    track.playbackRate = mSamplingFreq
                    track.play()
                    track.write(mRecordedAudio, 0, mRecordedAudio.size)
                    track.stop()
                    track.flush()
                }
            }
        }

        spinnerClasses.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                fileCnt()
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {
            }
        }

        buttonFeature.setOnClickListener {
            if (!mRecording and switchSave.isChecked) {
                val feature = mSpectrogram.getAudioFeature()
                val jsonObject = JSONObject()

                jsonObject.put("fs", feature.fs)
                jsonObject.put("fftSize", feature.fftSize)
                jsonObject.put("melFilterbankSize", feature.melFilterbankSize)
                jsonObject.put("featureCenter", mFeatureCenter)
                jsonObject.put("featureWidth", mFeatureWidth)
                jsonObject.put("mfsc", JSONArray(feature.mfsc))

                var cntFeaturePerLastAudio = 0
                File(DIR).list()
                    .forEach { filename -> if (regexFeaturePerLastAudio().matches(filename)) ++cntFeaturePerLastAudio }
                ++cntFeaturePerLastAudio

                val filename = "${mLastAudioFileName}-${cntFeaturePerLastAudio}.json"
                val file = File(DIR, filename)
                file.writeText(jsonObject.toString())

                fileCnt()

                // Notify Android's media manager of the creation of new file
                val contentUri: Uri = Uri.fromFile(file)
                val mediaScanIntent = Intent(
                    Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                    contentUri
                )
                this.sendBroadcast(mediaScanIntent)

            } else if (!mRecording and !switchSave.isChecked) {
                runInference()
            }
        }

        surfaceView.setOnTouchListener { _, event ->
            val center = event.x
            val widthHalf = mFeatureWidth / 2 * mRatio
            if ((center > widthHalf) && (surfaceView.width - center > widthHalf)) {
                mFeatureCenter = (center / mRatio).toInt()
                if (!mRecording) drawMFSCs()
            }
            radioButtonMFSCs.isChecked = true
            true
        }

        // TensorFlow Lite
        mAcousticEventDetector = AcousticEventDetector(
            this,
            Classifier.Device.CPU,
            1,
            melFilterbankSize = mMelFilterbankSize,
            featureWidth = mFeatureWidth
        )

        // Request camera permissions
        if (allPermissionsGranted(this)) {
            resetSpectrogram()

        } else {
            ActivityCompat.requestPermissions(
                this,
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun runInference() {
        val feature = mSpectrogram.getAudioFeature()
        val featureWidthHalf = mFeatureWidth / 2
        val begin = (mFeatureCenter - featureWidthHalf) * mMelFilterbankSize
        val end = (mFeatureCenter + featureWidthHalf) * mMelFilterbankSize
        val featureArea = feature.mfsc.sliceArray(begin until end)

        try {
            val results = mAcousticEventDetector.recognize(featureArea)
            textViewInference.text = results.toString().removeSurrounding("[", "]")
            Log.d(TAG, "inference: $results")
        } catch (e: java.lang.Exception) {
            textViewInference.text = "..."
            e.printStackTrace()
        }
    }

    private fun classLabel(): String {
        val pos = spinnerClasses.selectedItemPosition
        return mClassLabels[pos]
    }

    private fun saveParameters(fallback: Boolean = false) {
        val editor = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        if (mClassLabels.size == 0) {
            editor.remove("classLabels")
        } else {
            editor.putString("classLabels", mClassLabels.joinToString(","))
        }
        if (fallback) {
            editor.putInt("fs", 44100)  // 44.1kHz
            editor.putInt("fftSize", 512)  // 512 bins
            editor.putInt("melFilterbankSize", 40)  // 40 filters
            editor.putInt("specLength", 3)  // 3 sec
            editor.putInt("featureWidth", 64)  // 64 bins
            editor.putInt("featureCenter", 64)
        } else {
            editor.putInt("fs", mSamplingFreq)
            editor.putInt("fftSize", mFftSize)
            editor.putInt("melFilterbankSize", mMelFilterbankSize)
            editor.putInt("specLength", mSpecLength)
            editor.putInt("featureWidth", mFeatureWidth)
            editor.putInt("featureCenter", mFeatureCenter)
        }
        editor.apply()
    }

    private fun drawWave() {
        val canvas = surfaceView.holder.lockCanvas()
        canvas.drawColor(Color.BLACK)
        val ratioX = canvas.width.toFloat() / mBufRecord.size.toFloat()
        val offsetY = surfaceView.height / 2
        var prevY = -mBufRecord[0] * SCALEDOWN + offsetY

        canvas.drawLine(
            0F,
            offsetY.toFloat(),
            canvas.width.toFloat(),
            offsetY.toFloat(),
            mPaintDarkGrayStroke
        )
        for (i in 1 until mBufRecord.size) {
            val y = -mBufRecord[i] * SCALEDOWN + offsetY
            canvas.drawLine(
                (i - 1).toFloat() * ratioX,
                prevY,
                i.toFloat() * ratioX,
                y,
                mPaintCyanStroke
            )
            prevY = y
        }
        surfaceView.holder.unlockCanvasAndPost(canvas)
    }

    private fun drawPsd() {
        val psd = mSpectrogram.getPsd()
        val canvas = surfaceView.holder.lockCanvas()
        canvas.drawColor(Color.BLACK)
        val ratioX = canvas.width.toFloat() / psd.size.toFloat()
        val offsetY = (surfaceView.height - surfaceView.height / 2).toFloat()
        val ratioY = offsetY / 128F * SCALEUP

        canvas.drawLine(0F, offsetY, canvas.width.toFloat(), offsetY, mPaintDarkGrayStroke)
        for (i in psd.indices) {
            val y = -psd[i] * ratioY
            val x = i.toFloat() * ratioX
            canvas.drawLine(
                x,
                offsetY,
                x,
                y + offsetY,
                mPaintCyanStroke
            )
            canvas.drawCircle(x, y + offsetY, 4F, mPaintYellowFill)
        }
        surfaceView.holder.unlockCanvasAndPost(canvas)
    }

    private fun drawSpectrogram() {
        val bitmap = mSpectrogram.getSpectrogramBitmap(mCurrentRotaionDegrees, normalize = true)
        val dstRect = Rect()
        val canvas = surfaceView.holder.lockCanvas()
        canvas.getClipBounds(dstRect)
        canvas.drawBitmap(bitmap, null, dstRect, null)
        surfaceView.holder.unlockCanvasAndPost(canvas)
    }

    private fun drawFeatureArea(canvas: Canvas) {
        val height = canvas.height.toFloat()
        val featureWidthHalf = mFeatureWidth / 2F
        val center = mFeatureCenter * mRatio
        val x1 = (mFeatureCenter - featureWidthHalf) * mRatio
        val x2 = (mFeatureCenter + featureWidthHalf) * mRatio
        val y1 = 3F
        val y2 = height - y1
        canvas.drawLine(center, y1, center, y2, mPaintGreenStroke)
        canvas.drawLine(x1, y1, x1, y2, mPaintRedStroke)
        canvas.drawLine(x2, y1, x2, y2, mPaintRedStroke)
        canvas.drawLine(x1, y1, x2, y1, mPaintRedStroke)
        canvas.drawLine(x1, y2, x2, y2, mPaintRedStroke)
    }

    private fun drawMFSCs() {
        val bitmap = mSpectrogram.getMfscBitmap(mCurrentRotaionDegrees, normalize = true)
        val dstRect = Rect()
        val canvas = surfaceView.holder.lockCanvas()
        canvas.getClipBounds(dstRect)
        canvas.drawBitmap(bitmap, null, dstRect, null)
        mRatio = canvas.width.toFloat() / bitmap.width
        drawFeatureArea(canvas)
        surfaceView.holder.unlockCanvasAndPost(canvas)
    }

    /**
     * This is for a debugging purpose only.
     *
     */
    private fun drawMelFilterbank() {
        val paint = Paint()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1F
        paint.color = Color.CYAN

        val canvas = surfaceView.holder.lockCanvas()
        canvas.drawColor(Color.BLACK)
        val ratioX = canvas.width.toFloat() / (mFftSize / 2)
        val ratioY = canvas.height

        val details = mSpectrogram.getFilterbankDetails()
        val kRangeArray = details.kRangeArray
        val filterbank = details.filterbank

        for (m in 1 until mMelFilterbankSize + 1) {
            val leftK = kRangeArray[m].left
            val len = kRangeArray[m].len
            var prevX = leftK.toFloat() * ratioX
            var prevY = canvas.height.toFloat()
            for (i in 0 until len) {
                val x = (leftK + i).toFloat() * ratioX
                val y = canvas.height - filterbank[m][i] * ratioY
                canvas.drawLine(prevX, prevY, x, y, paint)
                prevX = x
                prevY = y
            }
        }
        surfaceView.holder.unlockCanvasAndPost(canvas)
    }

    private fun startRecording() {
        // TODO: check if the following works on other devices than Nexus5X
        when (surfaceView.display.rotation) {
            Surface.ROTATION_0 -> mCurrentRotaionDegrees = 180
            Surface.ROTATION_90 -> mCurrentRotaionDegrees = 270
            Surface.ROTATION_180 -> mCurrentRotaionDegrees = 0
            Surface.ROTATION_270 -> mCurrentRotaionDegrees = 90
        }
        Log.d(TAG, "rotation: $mCurrentRotaionDegrees")

        // Reset due to changes of parameter values
        var resetRequired = false
        mSamplingFreqWaiting?.let {
            mSamplingFreq = it
            resetRequired = true
            mSamplingFreqWaiting = null
        }
        mFftSizeWaiting?.let {
            mFftSize = it
            resetRequired = true
            mFftSizeWaiting = null
        }
        mMelFilterbankSizeWating?.let {
            mMelFilterbankSize = it
            resetRequired = true
            mMelFilterbankSizeWating = null
        }
        mSpecLengthWaiting?.let {
            mSpecLength = it
            resetRequired = true
            mSpecLengthWaiting = null
        }
        if (resetRequired) resetSpectrogram()

        thread {
            Log.d(TAG, "MSG_RECORD_START")
            mAudioRecord?.let {
                // Disable Automatic Gain Control
                if (AutomaticGainControl.isAvailable()) {
                    val agc: AutomaticGainControl =
                        AutomaticGainControl.create(
                            it.audioSessionId
                        )
                    agc.enabled = false
                }

                it.startRecording()
                var count = 0
                var infCnt = 0
                while (mRecording && !mDestroy) {
                    it.read(mBufRecord, 0, mBufferSizeInShort)

                    //mSpectrogram.update(mBufRecord, toggleButtonPreEmphasis.isChecked)
                    mSpectrogram.update(mBufRecord, enablePreEmphasis = true)

                    when {
                        radioButtonWave.isChecked -> drawWave()
                        radioButtonPSD.isChecked -> drawPsd()
                        radioButtonSpectrogram.isChecked -> drawSpectrogram()
                        radioButtonMFSCs.isChecked -> drawMFSCs()
                        radioButtonFilterbank.isChecked -> drawMelFilterbank()
                    }

                    // Ring buffer
                    if (count * mBufferSizeInShort >= mBufPlay.size) {
                        count = 0
                        progressBar.progress = 0
                    }
                    System.arraycopy(
                        mBufRecord,
                        0,
                        mBufPlay,
                        count * mBufferSizeInShort,
                        mBufferSizeInShort
                    )
                    progressBar.progress = ++count

                    if (++infCnt > INFERENCE_INTERVAL) {
                        runInference()
                        infCnt = 0
                    }
                }

                it.stop()

                runOnUiThread {
                    progressBar.progress = 0
                    Log.d(TAG, "MSG_RECORD_END")
                    toggleButtonRecord.isEnabled = false
                }

                if (!mDestroy) {
                    mAudioTrack?.let { _ ->
                        mRecordedAudio =
                            (mBufPlay.slice(count * mBufferSizeInShort until mBufPlay.size) +
                                    mBufPlay.slice(0 until count * mBufferSizeInShort)).toShortArray()

                        if (switchSave.isChecked) {
                            saveAudioAsWaveFile()
                        }

                        runOnUiThread {
                            toggleButtonRecord.isEnabled = true
                        }
                    }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted(this)) {
                resetSpectrogram()
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    private fun initAudio(): Int {

        // Minimum audio buffer size
        val bufferSizeInBytes = AudioRecord.getMinBufferSize(
            mSamplingFreq,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        // In case of LG Nexus 5X, the buffer size is 3584 bytes = 128 * 28 bytes
        Log.d(TAG, "minBufferSize: $bufferSizeInBytes (bytes)")

        // Minimum buffer size in 16bit length
        mBufferSizeInShort = bufferSizeInBytes / 2

        val numBlocks = mSpecLength * mSamplingFreq / mBufferSizeInShort

        // Buffers
        mBufRecord = ShortArray(mBufferSizeInShort)
        mBufPlay = ShortArray(mBufferSizeInShort * numBlocks)

        // Object for mRecording
        mAudioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC, mSamplingFreq,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSizeInBytes
        )

        mAudioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(mSamplingFreq)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSizeInBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        return numBlocks
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        mDestroy = true
        try {
            Thread.sleep(2000)
        } catch (e: InterruptedException) {
        }
        mAudioRecord?.let {
            if (it.recordingState != AudioRecord.RECORDSTATE_STOPPED) {
                Log.d(TAG, "cleanup mAudioRecord")
                it.stop()
            }
        }
        mAudioRecord = null
        if (mAudioTrack != null) {
            if (mAudioTrack!!.playState != AudioTrack.PLAYSTATE_STOPPED) {
                Log.d(TAG, "cleanup mAudioTrack")
                mAudioTrack!!.stop()
                mAudioTrack!!.flush()
            }
            mAudioTrack = null
        }
    }

}