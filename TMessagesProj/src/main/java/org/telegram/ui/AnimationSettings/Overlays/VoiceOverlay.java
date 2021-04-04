package org.telegram.ui.AnimationSettings.Overlays;

import android.animation.AnimatorSet;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.AnimationSettings.ChatAnimationsOverlay;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.RadialProgress2;

public class VoiceOverlay extends ChatAnimationsOverlay.OverlayView {
    private AnimatorSet animations;

    private float centerStartX;
    private float centerStartY;
    private float radiusStart;
    private float centerCurrentX;
    private float centerCurrentY;
    private float radiusCurrent;
    private float centerFinishX;
    private float centerFinishY;
    private float radiusFinish;

    private int colorStart;
    private int colorCurrent;
    private int colorFinish;
    private Paint circlePaint;

    RadialProgress2 radialProgress;
    ChatActivityEnterView.RecordCircle recordCircle;

    Drawable replyDrawable;

    AnimationParameter replyX;
    AnimationParameter replyY;
    AnimationParameter replyExtraBackground;
    AnimationParameter replyLineHeight;

    public VoiceOverlay(ChatActivity activity) {
        super(activity, "Voice");
        setWillNotDraw(false);
        circlePaint = new Paint();
        replyDrawable = getResources().getDrawable(R.drawable.msg_panel_reply);
    }

    @Override
    public void startAnimation() {
        //messageCell.drawCell(null);

        centerStartX = startPosition.centerX();
        centerStartY = startPosition.centerY();
        centerFinishX = finishPosition.centerX();
        centerFinishY = finishPosition.centerY();
        radiusStart = startPosition.width() / 2;
        radiusFinish = finishPosition.width() / 2;

        messageCell.setVoiceTransitionInProgress(true);
        recordCircle.voiceEnterTransitionInProgress = true;
        recordCircle.skipDraw = true;

        if (hasReply) {
            replyExtraBackground = new AnimationParameter(AndroidUtilities.dp(35), 0);
            replyLineHeight = new AnimationParameter(0, AndroidUtilities.dp(35));
            replyX = new AnimationParameter(replyStartPosition.left + replyOffset - AndroidUtilities.dp(7),
                    finishPosition.left - messageCell.getRadialProgress().getProgressRect().left + messageCell.getReplyStartX());
            replyY = new AnimationParameter(replyStartPosition.top + AndroidUtilities.dp(6),
                    finishPosition.top - messageCell.getRadialProgress().getProgressRect().top + messageCell.getReplyStartY());
        }

        super.startAnimation();
        animations = new AnimatorSet();
        animations.playTogether(getPropertyAnimator(POSITION_X));
        animations.playTogether(getPropertyAnimator(POSITION_Y));
        animations.playTogether(getPropertyAnimator(SCALE));
        animations.playTogether(getPropertyAnimator(COLOR_CHANGE));
        addOnAnimationEndListener(animations);
        animations.start();
    }

    @Override
    public void onAnimationFinish() {
        super.onAnimationFinish();
        messageCell.setVoiceTransitionInProgress(false);
        recordCircle.voiceEnterTransitionInProgress = false;
        recordCircle.skipDraw = false;
    }



    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawCircle(centerCurrentX, centerCurrentY, radiusCurrent, circlePaint);

        canvas.save();
        float scale = radiusCurrent / radiusFinish;
        canvas.scale(scale, scale, centerCurrentX, centerCurrentY);
        canvas.translate(
                centerCurrentX - radialProgress.getProgressRect().centerX(),
                centerCurrentY -  radialProgress.getProgressRect().centerY());
        radialProgress.setDrawBackground(false);
        radialProgress.draw(canvas);
        radialProgress.setDrawBackground(true);
        canvas.restore();
    }

    @Override
    public void setMessageCell(ChatMessageCell messageCell) {
        super.setMessageCell(messageCell);
        radialProgress = messageCell.getRadialProgress();
        activity.getChatActivityEnterView().startMessageTransition();
        recordCircle = activity.getChatActivityEnterView().getRecordCicle();
        colorStart = Theme.getColor(Theme.key_chat_messagePanelVoiceBackground);
        colorFinish = Theme.getColor(radialProgress.getCircleColorKey());
    }

    @Override
    public void onPositionXUpdated(float x) {
        if (hasReply) replyX.update(x);
        centerCurrentX = calculateValue(centerStartX, centerFinishX, x);
        invalidate();
    };

    @Override
    public void onPositionYUpdated(float y) {
        if (hasReply) {
            replyY.update(y, targetOffset, getTotalOffset());
            replyExtraBackground.update(Math.min(1, y * 4));
            replyLineHeight.update(Math.min(1, Math.max((y * 4 - 1f), 0)));
        }
        centerCurrentY = calculateValue(centerStartY, centerFinishY + targetOffset, y) + getTotalOffset();
        invalidate();
    };

    @Override
    public void onScaleUpdated(float scale) {
        radiusCurrent = calculateValue(radiusStart, radiusFinish, scale);
        invalidate();
    };

    @Override
    public void onColorChangeUpdated(float color) {
        colorCurrent = Color.argb(255,
            calculateValue(Color.red(colorStart), Color.red(colorFinish), color),
            calculateValue(Color.green(colorStart), Color.green(colorFinish), color),
            calculateValue(Color.blue(colorStart), Color.blue(colorFinish), color));
        circlePaint.setColor(colorCurrent);
        invalidate();
    };

}
