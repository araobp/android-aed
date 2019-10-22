# Acoustic Event Detection with TensorFlow Lite

I traveled Okinawa last summer, and [Okinawa music] was amazing.

I develop "Deeplearing for Audio" to run AI on my smart phone to study Okinawa music.

(This project is a remake of https://github.com/araobp/acoustic-features.)

![android_app](./doc/android_app.png)

And [the other screenshots](./SCREENSHOTS.md).

I can play a few of popular Okinawa music including Tinsagnuhana. Shimauta and Nadasousou, with my [Kankara sanshin](https://www.machidaya.jp/en/shop/kankara-sansin-en/kankarasanshin-en/kankara-sanshin-shamisen-diy-kit-%EF%BC%8B-e-learning/).

## Android app "spectrogram"

This app is for both train data collection and inference for acoustic event detection: [code](./android)

## Training CNN

This is a notebook for training CNN: [Jypyter notebook](./keras/training.ipynb)

## Audio processing pipeline

### Training data collection on Android (smart phone)

```
   << MEMS mic >>
         |
         V
  [16bit PCM data]
         |
  [ Pre-emphasis ]
         |
[Overlapping frames (50%)]
         |
  [Windowing(hann)]
         |
  [   Real FFT   ]
         |
  [     PSD      ]
         |
  [Filterbank(MFSCs)]
         |
     [Log scale]
         |
         V
 << Audio feature >>

```

### Training CNN on Keras

```
 << Audio feature >>
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
   [Trained CNN]
         |
         V
   << Results >>
```
