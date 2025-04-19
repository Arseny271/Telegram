package org.telegram.messenger.pip;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.pip.utils.PipUtils;

public class PipSourcePlaceholderView extends View {
    private final Rect tmpRect = new Rect();
    private Bitmap bitmap;

    public PipSourcePlaceholderView(Context context) {
        super(context);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        tmpRect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
    }

    public void setPlaceholder(Bitmap bitmap) {
        if (this.bitmap != bitmap) {
            this.bitmap = bitmap;
            Log.i(PipUtils.TAG, "[Bitmap] setPlaceholder " + bitmap);
            invalidate();
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (bitmap == null) {
            return;
        }

        if (bitmap.isRecycled()) {
            Log.i(PipUtils.TAG, "[Bitmap] placeholderRecycled " + bitmap.hashCode());
            bitmap = null;
            return;
        }

        canvas.drawBitmap(bitmap, null, tmpRect, null);
    }
}
