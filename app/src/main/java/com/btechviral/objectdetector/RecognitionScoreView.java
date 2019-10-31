package com.btechviral.objectdetector;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import java.util.List;

public class RecognitionScoreView extends View implements ResultsView {

    private static final float TEXT_SIZE_DIP = 14;
    private float textSizePx;
    private Paint fgPaint;
    private Paint bgPaint;
    private List<Recognition> results;
    public RecognitionScoreView(Context context) {
        super(context);
    }

    public RecognitionScoreView(Context context, AttributeSet attrs) {
        super(context, attrs);
        textSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        fgPaint = new Paint();
        fgPaint.setTextSize(textSizePx);

        bgPaint = new Paint();
        bgPaint.setColor(0xcc4285f4);
    }

    public RecognitionScoreView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public RecognitionScoreView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setResults(List<Recognition> results) {
        this.results = results;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final int x = 10;
        int y = (int) (fgPaint.getTextSize() * 1.5f);

        canvas.drawPaint(bgPaint);

        if (results != null) {
            for (final Recognition recog : results) {
                canvas.drawText(recog.getTitle() + ": " + recog.getConfidence(), x, y, fgPaint);
                y += (int) (fgPaint.getTextSize() * 1.5f);
            }
        }
    }
}
