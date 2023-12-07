package org.telegram.ui.Components.effects.sprayer;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;

import androidx.annotation.UiThread;

public class SprayerBitmapState {
    private static int bitmapsCount = 0;

    public final Bitmap bitmap;
    public final Rect pixels;
    public final RectF relative;
    public final int viewportWidth;
    public final int viewportHeight;

    @UiThread
    public SprayerBitmapState(Bitmap bitmap, Rect size, int viewportWidth, int viewportHeight) {
        this.bitmap = bitmap;
        this.pixels = new Rect(size);
        this.relative = new RectF(
                ((float) size.left ) / viewportWidth,
                ((float) size.top ) / viewportHeight,
                ((float) size.right ) / viewportWidth,
                ((float) size.bottom ) / viewportHeight
        );
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
        bitmapsCount += 1;
        Log.i("WTF_DEBUG", "SprayerEffect: Create Bitmap: " + bitmapsCount);
    }

    @UiThread
    public void recycle() {
        if (!bitmap.isRecycled()) {
            bitmap.recycle();
            bitmapsCount -= 1;
            Log.i("WTF_DEBUG", "SprayerEffect: Clear Bitmap: " + bitmapsCount);
        }
    }
}
