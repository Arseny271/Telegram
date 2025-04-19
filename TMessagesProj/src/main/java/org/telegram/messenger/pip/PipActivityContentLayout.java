package org.telegram.messenger.pip;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.pip.activity.IPipActivity;

@SuppressLint("ViewConstructor")
class PipActivityContentLayout extends FrameLayout {
    private final Activity activity;

    private int originalWidth, originalHeight;
    private boolean isViewInPip;

    PipActivityContentLayout(Activity activity) {
        super(activity);
        this.activity = activity;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        final int width = getMeasuredWidth();
        final int height = getMeasuredHeight();
        final boolean isActivityInPip = AndroidUtilities.isInPictureInPictureMode(activity);

        if (!isActivityInPip) {
            originalWidth = width;
            originalHeight = height;
        }

        isViewInPip = isActivityInPip && width < originalWidth && height < originalHeight;
    }

    public boolean isViewInPip() {
        return isViewInPip;
    }
}
