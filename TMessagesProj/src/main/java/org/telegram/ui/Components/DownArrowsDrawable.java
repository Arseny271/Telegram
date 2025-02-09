package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;

public class DownArrowsDrawable extends Drawable {
    private static final int ARROW_WIDTH = 14;
    private static final int ARROW_HEIGHT = 8;
    private static final int DRAWABLE_SIZE = 24;
    private static final float ANIMATION_DURATION_FULL = 4000f; // 4.0 seconds
    private static final float ANIMATION_DURATION = 1100f; // 1.1 seconds

    private static final float[] FRAMES_TOP = new float[]{ 0f, 6f, -0.5f, 6f, 0f };
    private static final float[] FRAMES_BOTTOM = new float[]{ 0f, 4f, -0.5f, 4f, 0f };
    private static final float[] FRAMES_PARENT = new float[]{ 0f, 1f, -0.5f, 1f, 0f };

    private final Delegate delegate;
    private final Path path = new Path();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int left, top, right, bottom;
    private float progress = 0f;
    private long lastFrameMillis = 0;

    public interface Delegate {
        void updateParentY(float y);
    }

    public DownArrowsDrawable(Delegate delegate) {
        this.delegate = delegate;

        paint.setColor(0xFFFFFFFF);
        paint.setStrokeWidth(AndroidUtilities.dp(1f) + 1);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);

        path.moveTo(AndroidUtilities.dp(1), AndroidUtilities.dp(1));
        path.lineTo(AndroidUtilities.dp(ARROW_WIDTH / 2f), AndroidUtilities.dp(ARROW_HEIGHT - 1));
        path.lineTo(AndroidUtilities.dp(ARROW_WIDTH - 1f), AndroidUtilities.dp(1f));
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        final float p = Math.min(progress * ANIMATION_DURATION_FULL / ANIMATION_DURATION, 1f);
        float offsetTop = AndroidUtilities.dp(interpolate(p, FRAMES_TOP));
        float offsetBottom = AndroidUtilities.dp(interpolate(p, FRAMES_BOTTOM));
        float offsetParent = AndroidUtilities.dp(interpolate(p, FRAMES_PARENT));
        delegate.updateParentY(offsetParent);

        float x = left + (right - left - AndroidUtilities.dp(ARROW_WIDTH)) / 2f;
        float yTop = top + (bottom - top - AndroidUtilities.dp(16)) / 2f + offsetTop;
        float yBottom = yTop - offsetTop + AndroidUtilities.dp(7f) + offsetBottom;

        canvas.save();
        canvas.translate(x, yTop);
        canvas.drawPath(path, paint);
        canvas.restore();

        canvas.save();
        canvas.translate(x, yBottom);
        canvas.drawPath(path, paint);
        canvas.restore();

        long millis = SystemClock.uptimeMillis();
        long delay = Math.min(millis - lastFrameMillis, 100);
        lastFrameMillis = millis;
        progress = (progress + (delay / ANIMATION_DURATION_FULL)) % 1f;
        invalidateSelf();
    }

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        super.setBounds(left, top, right, bottom);
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(android.graphics.ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getAlpha() {
        return paint.getAlpha();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return AndroidUtilities.dp(DRAWABLE_SIZE);
    }

    @Override
    public int getIntrinsicHeight() {
        return AndroidUtilities.dp(DRAWABLE_SIZE);
    }

    @Override
    public int getMinimumWidth() {
        return AndroidUtilities.dp(DRAWABLE_SIZE);
    }

    @Override
    public int getMinimumHeight() {
        return AndroidUtilities.dp(DRAWABLE_SIZE);
    }

    public static float interpolate(float progress, float[] values) {
        if (progress < 0f || progress > 1f) {
            throw new IllegalArgumentException("Progress must be between 0 and 1");
        }
        if (values.length == 0) {
            throw new IllegalArgumentException("Values array must not be empty");
        }

        if (values.length == 1) {
            return values[0];
        }

        float scaledProgress = progress * (values.length - 1);
        int index = (int) scaledProgress;
        float fraction = scaledProgress - index;

        return (index < values.length - 1)
                ? values[index] * (1 - fraction) + values[index + 1] * fraction
                : values[values.length - 1];
    }
}
