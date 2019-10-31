package com.btechviral.objectdetector;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.ImageReader;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.util.Pair;
import android.util.Size;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import java.io.IOException;
import java.util.ConcurrentModificationException;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class DetectorActivity extends MainActivity implements ImageReader.OnImageAvailableListener {

    // Configuration values for the prepackaged SSD model.
    private static final int TF_OD_API_INPUT_SIZE = 300;
    private static final boolean TF_OD_API_IS_QUANTIZED = true;
    private static final String TF_OD_API_MODEL_FILE = "detect.tflite";
    private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/labelmap.txt";
    private static final DetectorMode MODE = DetectorMode.TF_OD_API;
    // Minimum detection confidence to track a detection.
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.55f;
    private static final boolean MAINTAIN_ASPECT = false;
    private static final android.util.Size DESIRED_PREVIEW_SIZE = new android.util.Size(640, 480);
    private static final boolean SAVE_PREVIEW_BITMAP = false;
    private static final float TEXT_SIZE_DIP = 10;
    OverlayView trackingOverlay;
    private Integer sensorOrientation;

    private Classifier detector;

    private long lastProcessingTimeMs;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;

    private boolean computingDetection = false;

    private long timestamp = 0;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    private MultiBoxTracker tracker;

    private BorderedText borderedText;
    private List<Pair<Pair<String, Float>, RectF>> screenRects;
    private TextToSpeech textToSpeech;

    @Override
    public void onPreviewSizeChosen(final android.util.Size size, final int rotation) {
        final float textSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        tracker = new MultiBoxTracker(this);

        int cropSize = TF_OD_API_INPUT_SIZE;

        try {
            detector =
                    TFLiteObjectDetectionAPIModel.create(
                            getAssets(),
                            TF_OD_API_MODEL_FILE,
                            TF_OD_API_LABELS_FILE,
                            TF_OD_API_INPUT_SIZE,
                            TF_OD_API_IS_QUANTIZED);
            cropSize = TF_OD_API_INPUT_SIZE;
        } catch (final IOException e) {
            e.printStackTrace();
            Toast toast = Toast.makeText(getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            finish();
        }

        previewWidth = size.getWidth();
        previewHeight = size.getHeight();

        sensorOrientation = rotation - getScreenOrientation();
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888);

        frameToCropTransform = ImageUtils.getTransformationMatrix(
                previewWidth, previewHeight,
                cropSize, cropSize,
                sensorOrientation, MAINTAIN_ASPECT);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
        trackingOverlay.addCallback(
                canvas -> {
                    tracker.draw(canvas);
                    if (isDebug()) {
                        tracker.drawDebug(canvas);
                    }
                });

        tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation);
        screenRects = tracker.getScreenRects();

        textToSpeech = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int i) {
                if (i == TextToSpeech.SUCCESS) {
                    int ttsLang = textToSpeech.setLanguage(Locale.US);

                    if (ttsLang == TextToSpeech.LANG_MISSING_DATA
                            || ttsLang == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e("TTS", "The Language is not supported!");
                    } else {
                        Log.i("TTS", "Language Supported.");
                    }
                    Log.i("TTS", "Initialization success.");
                } else {
                    Toast.makeText(getApplicationContext(), "TTS Initialization failed!", Toast.LENGTH_SHORT).show();
                }
            }
        });

        //For the speech action on tapping the rectangles
        trackingOverlay.setOnTouchListener((view, motionEvent) -> {
            float touchX = motionEvent.getX();
            float touchY = motionEvent.getY();
            switch(motionEvent.getAction()){
                case MotionEvent.ACTION_DOWN:
                    try {
                        for (Pair<Pair<String, Float>, RectF> pair : screenRects) {
                            if (pair.second.contains(touchX, touchY)) {
                                Toast.makeText(DetectorActivity.this, "Touched " + pair.first.first, Toast.LENGTH_SHORT).show();
                                int speechStatus = textToSpeech.speak(pair.first.first, TextToSpeech.QUEUE_FLUSH, null);

                                if (speechStatus == TextToSpeech.ERROR) {
                                    Log.e("TTS", "Error in converting Text to Speech!");
                                }
                            }
                        }
                    } catch (ConcurrentModificationException e){
                        e.printStackTrace();
                    }
                    break;
            }

            return true;
        });
    }

    @Override
    protected void processImage() {
        ++timestamp;
        final long currTimestamp = timestamp;
        trackingOverlay.postInvalidate();

        // No mutex needed as this method is not reentrant.
        if (computingDetection) {
            readyForNextImage();
            return;
        }
        computingDetection = true;

        rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

        readyForNextImage();

        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(croppedBitmap);
        }

        runInBackground(
                () -> {
                    Log.i("Running detection", Long.toString(currTimestamp));
                    final long startTime = SystemClock.uptimeMillis();
                    final List<Recognition> results = detector.recognizeImage(croppedBitmap);
                    lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

                    cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
                    final Canvas canvas1 = new Canvas(cropCopyBitmap);
                    final Paint paint = new Paint();
                    paint.setColor(Color.RED);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(2.0f);

                    float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                    switch (MODE) {
                        case TF_OD_API:
                            minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                            break;
                    }

                    final List<Recognition> mappedRecognitions = new LinkedList<>();

                    for (final Recognition result : results) {
                        final RectF location = result.getLocation();
                        if (location != null && result.getConfidence() >= minimumConfidence) {
                            canvas1.drawRect(location, paint);

                            cropToFrameTransform.mapRect(location);

                            result.setLocation(location);
                            mappedRecognitions.add(result);
                        }
                    }

                    tracker.trackResults(mappedRecognitions, currTimestamp);
                    trackingOverlay.postInvalidate();

                    computingDetection = false;

                    runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
                                    showFrameInfo(previewWidth + "x" + previewHeight);
                                    showCropInfo(cropCopyBitmap.getWidth() + "x" + cropCopyBitmap.getHeight());
                                    showInference(lastProcessingTimeMs + "ms");
                                }
                            });
                });
    }

    @Override
    protected int getLayoutId() {
        return R.layout.camera_connection_fragment_tracking;
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
        return DESIRED_PREVIEW_SIZE;
    }

    @Override
    public void onClick(View view) {
        
    }

    // Which detection model to use: by default uses Tensorflow Object Detection API frozen
    // checkpoints.
    private enum DetectorMode {
        TF_OD_API;
    }

    @Override
    protected void setUseNNAPI(final boolean isChecked) {
        runInBackground(() -> detector.setUseNNAPI(isChecked));
    }

    @Override
    protected void setNumThreads(final int numThreads) {
        runInBackground(() -> detector.setNumThreads(numThreads));
    }

    @Override
    public synchronized void onDestroy() {
        super.onDestroy();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
    }
}
