package org.telegram.ui.AnimationSettings.Overlays;

import android.animation.AnimatorSet;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.AnimationSettings.ChatAnimationsOverlay;
import org.telegram.ui.ChatActivity;

public class EmojiOverlay extends ChatAnimationsOverlay.OverlayView {
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

    Drawable replyDrawable;

    AnimationParameter replyX;
    AnimationParameter replyY;
    AnimationParameter replyExtraBackground;
    AnimationParameter replyLineHeight;

    public EmojiOverlay(ChatActivity activity) {
        super(activity, "Emoji");
        setWillNotDraw(false);
        replyDrawable = getResources().getDrawable(R.drawable.msg_panel_reply);

        backgroundSubView = new OverlaySubView(activity.getParentActivity()) {
            @Override
            protected void onDraw(Canvas canvas) {
                if (hasReply) {
                    float lineHeight = replyLineHeight.get();
                    float extraBackground = replyExtraBackground.get();
                    float replyXpos = replyX.get();
                    float replyYpos = replyY.get();

                    drawReply(canvas, replyXpos, replyYpos, extraBackground, lineHeight, 1);
                }
            }
        };
    }

    @Override
    public void startAnimation() {
        messageCell.drawCell(null);
        messageCell.setNeedDraw(false);

        centerStartX = startPosition.centerX();
        centerStartY = startPosition.centerY();
        centerFinishX = finishPosition.centerX();
        centerFinishY = finishPosition.centerY();
        widthFinish = finishPosition.width();
        heightFinish = finishPosition.height();
        float ratio = widthFinish / heightFinish;
        if (startPosition.width() / startPosition.height() > ratio) {
            heightStart = startPosition.height();
            widthStart = heightStart * ratio;
        } else {
            widthStart = startPosition.width();
            heightStart = widthStart / ratio;
        }

        scaleFinish = 1f;
        scaleStart = widthStart / widthFinish;

        if (hasReply) {
            replyExtraBackground = new AnimationParameter(AndroidUtilities.dp(35), 0);
            replyLineHeight = new AnimationParameter(0, AndroidUtilities.dp(35));
            replyX = new AnimationParameter(replyStartPosition.left + replyOffset - AndroidUtilities.dp(7),
                finishPosition.left - messageCell.getPhotoImage().getImageX() + messageCell.getReplyStartX());
            replyY = new AnimationParameter(replyStartPosition.top + AndroidUtilities.dp(6),
                finishPosition.top - messageCell.getPhotoImage().getImageY() + messageCell.getReplyStartY());
        }

        super.startAnimation();

        animations = new AnimatorSet();
        animations.playTogether(getPropertyAnimator(POSITION_X));
        animations.playTogether(getPropertyAnimator(POSITION_Y));
        animations.playTogether(getPropertyAnimator(SCALE));
        addOnAnimationEndListener(animations);
        animations.start();
    }

    @Override
    public void onAnimationFinish() {
        messageCell.setNeedDraw(true);
        super.onAnimationFinish();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        messageCell.setNeedDraw(true);

        canvas.save();
        canvas.scale(scaleCurrent, scaleCurrent, currentX, currentY);
        canvas.translate(-messageCell.getPhotoImage().getImageX() + currentX, -messageCell.getPhotoImage().getImageY() + currentY);
        messageCell.getPhotoImage().draw(canvas);
        canvas.restore();

        if (hasReply) {
            float lineHeight = replyLineHeight.get();
            float extraBackground = replyExtraBackground.get();
            float backgroundOffset = (AndroidUtilities.dp(35) - extraBackground) / 2;
            float replyXpos = replyX.get();
            float replyYpos = replyY.get();

            drawReply(canvas, replyXpos, replyYpos, extraBackground, lineHeight, 2);
            replyDrawable.setBounds(
                    (int)(replyXpos - extraBackground + extraBackground * 0.15f),
                    (int)(replyYpos + backgroundOffset + extraBackground * 0.15f),
                    (int)(replyXpos - extraBackground * 0.15f),
                    (int)(replyYpos + backgroundOffset + extraBackground - extraBackground * 0.15f));
            replyDrawable.setColorFilter(Theme.getColor(Theme.key_chat_replyPanelIcons), PorterDuff.Mode.MULTIPLY);
            replyDrawable.draw(canvas);
        }

        messageCell.setNeedDraw(false);
    }

    @Override
    public void onPositionXUpdated(float x) {
        if (hasReply) replyX.update(x);
        centerCurrentX = calculateValue(centerStartX, centerFinishX, x);
        currentX = centerCurrentX - widthCurrent / 2;
        backgroundSubView.invalidate();
        invalidate();
    }

    @Override
    public void onPositionYUpdated(float y) {
        if (hasReply) {
            replyY.update(y, targetOffset, getTotalOffset());
            replyExtraBackground.update(Math.min(1, y * 4));
            replyLineHeight.update(Math.min(1, Math.max((y * 4 - 1f), 0)));
        }
        centerCurrentY = calculateValue(centerStartY, centerFinishY + targetOffset, y) + getTotalOffset();
        currentY = centerCurrentY - heightCurrent / 2;
        backgroundSubView.invalidate();
        invalidate();
    }

    @Override
    public void onScaleUpdated(float scale) {
        widthCurrent = calculateValue(widthStart, widthFinish, scale);
        heightCurrent = calculateValue(heightStart, heightFinish, scale);
        scaleCurrent = calculateValue(scaleStart, scaleFinish, scale);
        currentX = centerCurrentX - widthCurrent / 2;
        currentY = centerCurrentY - heightCurrent / 2;
        invalidate();
    }
}
