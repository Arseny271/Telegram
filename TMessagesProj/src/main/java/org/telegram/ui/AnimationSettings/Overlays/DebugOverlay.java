package org.telegram.ui.AnimationSettings.Overlays;

import android.animation.AnimatorSet;
import android.graphics.Canvas;
import android.graphics.Paint;

import org.telegram.ui.AnimationSettings.ChatAnimationsOverlay;
import org.telegram.ui.ChatActivity;

public class DebugOverlay extends ChatAnimationsOverlay.OverlayView {
    private AnimatorSet animations;

    private AnimationParameter overlayY;
    private AnimationParameter overlayHeight;

    public DebugOverlay(ChatActivity activity) {
        super(activity, "Debug");
        setWillNotDraw(false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        Paint paint = new Paint();
        paint.setColor(/*messageCell.ignoreTranslationOffset*/true?0xFFFF00FF:0xFF00FF00);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        canvas.drawRect(finishPosition.left, overlayY.get(), finishPosition.right, overlayY.get() + overlayHeight.get(), paint);
    }

    @Override
    public void startAnimation() {
        overlayY = new AnimationParameter(0, finishPosition.top);
        overlayHeight = new AnimationParameter(0, finishPosition.height());

        super.startAnimation();

        animations = new AnimatorSet();
        animations.playTogether(getPropertyAnimator(POSITION_Y).setDuration(10000));
        addOnAnimationEndListener(animations);
        animations.start();
    }

    @Override
    public void onPositionYUpdated(float y) {
        overlayY.update(1f, targetOffset, getTotalOffset());
        overlayHeight.update(1f);
        invalidate();
    }

}
