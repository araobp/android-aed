# Acoustic Event Detection with TensorFlow Lite

I traveled [Okinawa](https://en.wikipedia.org/wiki/Okinawa_Island) last summer, and Okinawan music was amazing.

I develop an Android app "spectrogram" to run [TensorFlow Lite](https://www.tensorflow.org/lite?hl=ja) on my smart phone to study Okinawa music. The app can also be used for other use cases such as key word detection.

![android_app](./doc/android_app.png)

And [the other screenshots](./SCREENSHOTS.md).

## Android app "spectrogram"

This app is for both train data collection and inference for acoustic event detection: [code](./android)

I reused part of [this code](https://github.com/araobp/acoustic-features/tree/master/stm32/acoustic_feature_camera) that was written in C language by me for STM32 MCUs.

The DSP part used [JTransforms](https://github.com/wendykierp/JTransforms).

## Training CNN

This is a notebook for training a CNN model for musical instruments recognition: [Jypyter notebook](./keras/training.ipynb)

The audio feature corresponds to gray-scale image the size of 64(W) x 40(H).

## Audio processing pipeline

### Training data collection on Android (smart phone)

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
  [Windowing(hann)]
         |
  [   Real FFT   ]
         |
  [     PSD      ]
         |
  [Filterbank(MFSCs)]  40 filters
         |
     [Log scale]
         |
         V
 << Audio feature >>

```

### Training CNN on Keras

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
 << Audio feature >>
         |
         V
[Trained CNN(.tflite)]
         |
         V
   << Results >>
```

## References

### Speech processing for machine learning

- https://haythamfayek.com/2016/04/21/speech-processing-for-machine-learning.html

### Sanshin

I bought [Kankara sanshin](https://www.machidaya.jp/en/shop/kankara-sansin-en/kankarasanshin-en/kankara-sanshin-shamisen-diy-kit-%EF%BC%8B-e-learning/) after the visit, and practiced a few of popular Okinawan music including Tinsagnuhana, Shimauta and Nadasousou.

