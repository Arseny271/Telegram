package org.telegram.ui.AnimationSettings.Overlays;

import android.animation.AnimatorSet;
import android.graphics.Canvas;
import android.graphics.RectF;

import org.telegram.ui.AnimationSettings.ChatAnimationsOverlay;
import org.telegram.ui.ChatActivity;

public class PhotoOverlay extends ChatAnimationsOverlay.OverlayView {
    private AnimatorSet animations;

    private float centerStartX;
    private float centerStartY;
    private float widthStart;
    private float heightStart;

    private float currentX;
    private float currentY;
    private float centerCurrentX;
    private float centerCurrentY;
    private float widthCurrent;
    private float heightCurrent;

    private float centerFinishX;
    private float centerFinishY;
    private float widthFinish;
    private float heightFinish;

    private float scaleStart;
    private float scaleCurrent;
    private float scaleFinish;

    private float ratioStart;
    private float ratioCurrent;
    private float ratioFinish;

    public PhotoOverlay(ChatActivity activity) {
        super(activity, "Photo");
        setWillNotDraw(false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float scale = Math.max(widthCurrent / widthFinish, heightCurrent / heightFinish);
        float photoImageX = messageCell.getPhotoImage().getImageX();
        float photoImageY = messageCell.getPhotoImage().getImageY();

        canvas.save();
        canvas.clipRect(new RectF(currentX, currentY, widthCurrent + currentX, heightCurrent + currentY));
        canvas.scale(scale, scale, currentX, currentY);
        canvas.translate(
                currentX - photoImageX - (widthFinish * scale - widthCurrent) / 2,
                currentY - photoImageY - (heightFinish * scale - heightCurrent) / 2);
        messageCell.getPhotoImage().draw(canvas);
        canvas.restore();
    }

    @Override
    public void startAnimation() {
        centerStartX = startPosition.centerX();
        centerStartY = startPosition.centerY();
        centerFinishX = finishPosition.centerX();
        centerFinishY = finishPosition.centerY();
        widthStart = startPosition.width();
        heightStart = startPosition.height();
        widthFinish = finishPosition.width();
        heightFinish = finishPosition.height();
        ratioStart = widthStart / heightStart;
        ratioFinish = widthFinish / heightFinish;

        scaleFinish = 1;
        scaleStart = widthStart / widthFinish;

        super.startAnimation();

        animations = new AnimatorSet();
        animations.playTogether(getPropertyAnimator(POSITION_X));
        animations.playTogether(getPropertyAnimator(POSITION_Y));
        animations.playTogether(getPropertyAnimator(SCALE));
        addOnAnimationEndListener(animations);
        animations.start();
    }

    boolean firstOffset = true;

    @Override
    public void setDeltaOffset(float y, boolean isNew, boolean isTargetOffset) {
        if (firstOffset && isNew) {
            firstOffset = false;
            return;
        }
        
        super.setDeltaOffset(y, isNew, isTargetOffset);
    }

    @Override
    public void onPositionXUpdated(float x) {
        centerCurrentX = calculateValue(centerStartX, centerFinishX, x);
        currentX = centerCurrentX - widthCurrent / 2;
        invalidate();
    }

    @Override
    public void onPositionYUpdated(float y) {
        centerCurrentY = calculateValue(centerStartY, centerFinishY + targetOffset, y) + getTotalOffset();
        currentY = centerCurrentY - heightCurrent / 2;
        invalidate();
    }

    @Override
    public void onScaleUpdated(float scale) {
        widthCurrent = calculateValue(widthStart, widthFinish, scale);
        heightCurrent = calculateValue(heightStart, heightFinish, scale);
        scaleCurrent = calculateValue(scaleStart, scaleFinish, scale);
        ratioCurrent = calculateValue(ratioStart, ratioFinish, scale);
        currentX = centerCurrentX - widthCurrent / 2;
        currentY = centerCurrentY - heightCurrent / 2;
        invalidate();
    }

}
