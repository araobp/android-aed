# Acoustic Event Detection with TensorFlow Lite

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
