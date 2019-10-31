package com.btechviral.objectdetector;

import android.graphics.Bitmap;
import android.graphics.RectF;

import java.util.List;

public interface Classifier {
    List<Recognition> recognizeImage(Bitmap bitmap);

    void enableStatLogging(final boolean debug);

    String getStatString();

    void close();

    void setNumThreads(int num_threads);

    void setUseNNAPI(boolean isChecked);

}
