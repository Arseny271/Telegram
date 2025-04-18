package org.telegram.messenger.pip;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Canvas;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.pip.source.PipSourceHandlerState2;

@SuppressLint("ViewConstructor")
public class PipActivityPipContentView extends ViewGroup {
    private final Activity activity;

    private int originalWidth, originalHeight;
    private boolean isViewInPip;

    PipActivityPipContentView(Activity activity) {
        super(activity);
        this.activity = activity;

        // getViewTreeObserver().addOnPreDrawListener(this::onPreDraw);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int height = MeasureSpec.getSize(heightMeasureSpec);

        setMeasuredDimension(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        );

        final boolean isActivityInPip = AndroidUtilities.isInPictureInPictureMode(activity);
        if (!isActivityInPip) {
            originalWidth = getMeasuredWidth();
            originalHeight = getMeasuredHeight();
        }

        this.isViewInPip = isActivityInPip && width < originalWidth && height < originalHeight;

        if (state != null) {
            state.updatePositionViewRect(width, height, isViewInPip);
            if (state.pictureInPictureView != null) {
                state.pictureInPictureView.measure(
                    MeasureSpec.makeMeasureSpec(state.position.width(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(state.position.height(), MeasureSpec.EXACTLY)
                );
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (state != null && state.pictureInPictureView != null) {
            state.pictureInPictureView.layout(
                state.position.left,
                state.position.top,
                state.position.right,
                state.position.bottom
            );
        }
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        if (state != null) {
            state.drawBackground(canvas);
        }

        super.dispatchDraw(canvas);

        if (state != null) {
            state.drawForeground(canvas);
        }
    }




    private PipSourceHandlerState2 state;

    public void setState(PipSourceHandlerState2 state) {
        this.state = state;
        invalidate();
    }



    /*private void skipFrames(int n) {
        if (n <= 0) return;

        drawsToSkip = n;
        invalidate();
    }

    private boolean lastDrawIsPip;
    private int drawsToSkip = 0;

    private boolean onPreDraw() {
        final boolean isPip = isViewInPip;
        if (lastDrawIsPip != isPip) {
            lastDrawIsPip = isPip;
            if (isPip) {
                skipFrames(5);
            }
            return true;
        }

        if (drawsToSkip > 0) {
            drawsToSkip--;
            invalidate();
            return false;
        }

        return true;
    }*/
}
