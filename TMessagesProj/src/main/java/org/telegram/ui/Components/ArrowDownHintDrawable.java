package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;

public class ArrowDownHintDrawable extends Drawable {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path;
    private int top, left;

    private long lastFrameMillis = 0;
    private float offset = 0f;

    public ArrowDownHintDrawable() {
        paint.setColor(0xFFFFFFFF);
        paint.setStrokeWidth(AndroidUtilities.dp(1.1f));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);

        path = new Path();
        path.moveTo(AndroidUtilities.dp(-5), AndroidUtilities.dp(-5));
        path.lineTo(0, 0);
        path.lineTo(AndroidUtilities.dp(5), AndroidUtilities.dp(-5));
    }



    @Override
    public void draw(@NonNull Canvas canvas) {
        canvas.save();
        canvas.translate(left + AndroidUtilities.dp(12), top + AndroidUtilities.dp(6f + 6f * offset));

        paint.setAlpha((int) (alpha * offset));
        canvas.drawPath(path, paint);

        canvas.translate(0, AndroidUtilities.dp(6f));
        paint.setAlpha(alpha);
        canvas.drawPath(path, paint);

        canvas.translate(0, AndroidUtilities.dp(6f));
        paint.setAlpha((int) (alpha * (1f - offset)));
        canvas.drawPath(path, paint);

        canvas.restore();


        long millis = SystemClock.uptimeMillis();
        long delay = Math.min(millis - lastFrameMillis, 100);
        lastFrameMillis = millis;

        offset = (offset + delay / 333f) % 1f;
        invalidateSelf();
    }

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        super.setBounds(left, top, right, bottom);
        this.left = left;
        this.top = top;
    }

    private int alpha = 255;

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
    }

    @Override
    public int getAlpha() {
        return alpha;
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.UNKNOWN;
    }

    @Override
    public int getIntrinsicWidth() {
        return AndroidUtilities.dp(24);
    }

    @Override
    public int getIntrinsicHeight() {
        return AndroidUtilities.dp(24);
    }

    @Override
    public int getMinimumWidth() {
        return AndroidUtilities.dp(24);
    }

    @Override
    public int getMinimumHeight() {
        return AndroidUtilities.dp(24);
    }
}
