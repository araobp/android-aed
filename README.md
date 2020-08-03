# Acoustic Event Detection with TensorFlow Lite

This project is an Android version of my other project [acoustic features](https://github.com/araobp/acoustic-features).

![android_app](./doc/android_app.png)

And [the other screenshots](./SCREENSHOTS.md).

## Android app "spectrogram"

This app is for both train data collection and inference for acoustic event detection: [code](./android)

I reused part of [this code](https://github.com/araobp/acoustic-features/tree/master/stm32/acoustic_feature_camera) that was written in C language by me for STM32 MCUs.

The DSP part used [JTransforms](https://github.com/wendykierp/JTransforms).

I confirmed that the app runs on LG Nexus 5X and Google Pixel4. The code is short and self-explanatory, but I must explain that the app saves all the feature files in JSON format under "/Android/media/audio.processing.spectrogram".

I developed a color map function "Coral reef sea" on my own to convert grayscale image into a color of the sea in Okinawa.

```
    private fun applyColorMap(src: IntArray, dst: IntArray) {
        for (i in src.indices) {
            val mag = src[i]
            dst[i] = Color.argb(0xff, 128 - mag / 2, mag, 128 + mag / 2)
        }
    }
```

## Training CNN

This is a notebook for training a CNN model for musical instruments recognition: [Jupyter notebook](https://nbviewer.jupyter.org/github/araobp/android-aed/blob/master/keras/training_musical_instruments.ipynb).

The audio feature corresponds to gray-scale image the size of 64(W) x 40(H).

Converting Keras model into TFLite model is just two lines of code:
```
converter = tf.lite.TFLiteConverter.from_keras_model(model)
tflite_model = converter.convert()
```

In case of "musical instruments" use case, the notebook generates two files, "musical_instruments-labels.txt" and "musical_instruments.tflite". I rename the files and place them under "assets" folder for the Android app:
```
-- assets --+-- labels.txt
            |
            +-- aed.tflite
```

## Audio processing pipeline

### Training data collection on Android (smart phone)

Basically, short-time FFT is applied to raw PCM data to obtain audio feature as gray-scale image.

```
   << MEMS mic >>
         |
         V
  [16bit PCM data]  16kHz mono sampling
         |
  [ Pre-emphasis ]  FIR, alpha = 0.97
         |
[Overlapping frames (50%)]
         |
     [Windowing]  Hann window
         |
  [   Real FFT   ]
         |
  [     PSD      ]  Absolute values of real FFT / N
         |
  [Filterbank(MFSCs)]  40 filters for obtaining Mel frequency spectral coefficients.
         |
     [Log scale]  20*Log10(x) in DB
         |
   [Normalzation]  0-255 range (like grayscale image)
         |
         V
 << Audio feature >>  64 bins x 40 mel filters

```

### Training CNN on Keras

The steps taken for training a CNN model for Acoustic Event Detection is same as that for classification of grayscale images.

```
 << Audio feature >>  64 bins x 40 mel filters
         |
         V
   [CNN training]
         |
         V
 [Keras model(.h5)]
         |
         V
 << TFLite model >>

```

### Run inference on Android

```
 << Audio feature >>  64 bins x 40 mel filters
         |
         V
[Trained CNN(.tflite)]
         |
         V
   << Results >>
```

## TODO

I am working on integrating this Android app with the STM32 version [acoustic-features](https://github.com/araobp/acoustic-features).

## References

### Speech processing for machine learning

I learned audio processing technique for machine learning from [this site](https://haythamfayek.com/2016/04/21/speech-processing-for-machine-learning.html).
