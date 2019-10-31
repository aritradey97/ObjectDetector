package com.btechviral.objectdetector;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;

import java.util.Vector;

public class BorderedText {
    private final Paint interiorPaint;
    private final Paint exteriorPaint;

    private final float textSize;

    public BorderedText(final float textSize) {
        this(Color.WHITE, Color.BLACK, textSize);
    }

    public BorderedText(final int interiorColor, final int exteriorColor, final float textSize) {
        interiorPaint = new Paint();
        interiorPaint.setTextSize(textSize);
        interiorPaint.setColor(interiorColor);
        interiorPaint.setStyle(Paint.Style.FILL);
        interiorPaint.setAntiAlias(false);
        interiorPaint.setAlpha(255);

        exteriorPaint = new Paint();
        exteriorPaint.setTextSize(textSize);
        exteriorPaint.setColor(exteriorColor);
        exteriorPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        exteriorPaint.setStrokeWidth(textSize / 8);
        exteriorPaint.setAntiAlias(false);
        exteriorPaint.setAlpha(255);

        this.textSize = textSize;
    }

    public void setTypeface(Typeface typeface) {
        interiorPaint.setTypeface(typeface);
        exteriorPaint.setTypeface(typeface);
    }

    public void drawText(final Canvas canvas, final float posX, final float posY, final String text) {
        canvas.drawText(text, posX, posY, exteriorPaint);
        canvas.drawText(text, posX, posY, interiorPaint);
    }

    public void drawText(final Canvas canvas, final float posX, final float posY, final String text, Paint bgPaint) {

        float width = exteriorPaint.measureText(text);
        float textSize = exteriorPaint.getTextSize();
        Paint paint = new Paint(bgPaint);
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(160);
        canvas.drawRect(posX, (posY + (int) (textSize)), (posX + (int) (width)), posY, paint);

        canvas.drawText(text, posX, (posY + textSize), interiorPaint);
    }

}
