package org.telegram.ui.AnimationSettings.Overlays;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.util.Property;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.AnimationSettings.ChatAnimationsOverlay;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimationProperties;
import org.telegram.ui.Components.InstantCameraView;

public class RoundOverlay extends ChatAnimationsOverlay.OverlayView {
    private AnimatorSet animations;

    private float centerStartX;
    private float centerStartY;
    private float centerCurrentX;
    private float centerCurrentY;
    private float centerFinishX;
    private float centerFinishY;
    private float scaleStart;
    private float scaleCurrent;
    private float scaleFinish;
    private float radiusStart;
    private float radiusCurrent;
    private float radiusFinish;

    private org.telegram.ui.Components.Rect rect;
    private InstantCameraView.InstantViewCameraContainer cameraContainer;
    private InstantCameraView instantCameraView;
    private ImageReceiver imageReceiver;

    Drawable replyDrawable;

    AnimationParameter replyX;
    AnimationParameter replyY;
    AnimationParameter replyExtraBackground;
    AnimationParameter replyLineHeight;

    public RoundOverlay(ChatActivity activity) {
        super(activity, "Round");
        setWillNotDraw(false);
        replyDrawable = getResources().getDrawable(R.drawable.msg_panel_reply);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (hasReply) {
            float lineHeight = replyLineHeight.get();
            float extraBackground = replyExtraBackground.get();
            float backgroundOffset = (AndroidUtilities.dp(35) - extraBackground) / 2;
            float replyX = this.replyX.get();
            float replyY = this.replyY.get();

            drawReply(canvas, replyX, replyY, extraBackground, lineHeight, 0);
            replyDrawable.setBounds(
                    (int)(replyX - extraBackground + extraBackground * 0.15f),
                    (int)(replyY + backgroundOffset + extraBackground * 0.15f),
                    (int)(replyX - extraBackground * 0.15f),
                    (int)(replyY + backgroundOffset + extraBackground - extraBackground * 0.15f));
            replyDrawable.setColorFilter(Theme.getColor(Theme.key_chat_replyPanelIcons), PorterDuff.Mode.MULTIPLY);
            replyDrawable.draw(canvas);
        }
    }

    @Override
    public void startAnimation() {
        centerStartX = startPosition.centerX();
        centerStartY = startPosition.centerY();
        centerFinishX = finishPosition.centerX();
        centerFinishY = finishPosition.centerY();
        radiusStart = startPosition.width() / 2;
        radiusFinish = finishPosition.width() / 2;
        scaleStart = 1;
        scaleFinish = imageReceiver.getImageWidth() / instantCameraView.getCameraRect().width;

        messageCell.getTransitionParams().ignoreAlpha = true;
        messageCell.setAlpha(0.0f);
        messageCell.setTimeAlpha(0.0f);

        cameraContainer.setImageReceiver(imageReceiver);

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
        animations.playTogether(getPropertyAnimator(TIME_APPEARS));
        addOnAnimationEndListener(animations);
        animations.start();
    }

    @Override
    public void onAnimationFinish() {
        super.onAnimationFinish();
        messageCell.setAlpha(1.0f);
        messageCell.getTransitionParams().ignoreAlpha = false;
        Property<ChatMessageCell, Float> ALPHA = new AnimationProperties.FloatProperty<ChatMessageCell>("alpha") {
            @Override
            public void setValue(ChatMessageCell object, float value) {
                object.setTimeAlpha(value);
            }

            @Override
            public Float get(ChatMessageCell object) {
                return object.getTimeAlpha();
            }
        };

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(cameraContainer, View.ALPHA, 0.0f),
                ObjectAnimator.ofFloat(messageCell, ALPHA, 1.0f)
        );
        animatorSet.setDuration(100);
        animatorSet.setInterpolator(new DecelerateInterpolator());
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                instantCameraView.hideCamera(true);
                instantCameraView.setVisibility(View.INVISIBLE);
            }
        });
        animatorSet.start();
    }

    public void setInstantCameraView(InstantCameraView instantCameraView) {
        this.instantCameraView = instantCameraView;
        this.cameraContainer = instantCameraView.getCameraContainer();
        this.cameraContainer.setPivotX(0.0f);
        this.cameraContainer.setPivotY(0.0f);
        this.rect = instantCameraView.getCameraRect();
    }

    @Override
    public void setMessageCell(ChatMessageCell messageCell) {
        super.setMessageCell(messageCell);
        imageReceiver = messageCell.getPhotoImage();
    }

    @Override
    public void onPositionXUpdated(float x) {
        if (hasReply) replyX.update(x);
        centerCurrentX = calculateValue(centerStartX, centerFinishX, x);
        cameraContainer.setTranslationX(centerCurrentX - rect.x - radiusCurrent);
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
        cameraContainer.setTranslationY(centerCurrentY - rect.y - radiusCurrent);
        invalidate();
    };

    @Override
    public void onScaleUpdated(float scale) {
        scaleCurrent = calculateValue(scaleStart, scaleFinish, scale);
        radiusCurrent = calculateValue(radiusStart, radiusFinish, scale);
        cameraContainer.setScaleX(scaleCurrent);
        cameraContainer.setScaleY(scaleCurrent);
        invalidate();
    };

    @Override
    public void onTimeAppearsUpdated(float timeAppears) {
        instantCameraView.getSwitchButtonView().setAlpha(1f - timeAppears);
        instantCameraView.getPaint().setAlpha(255 - (int)(255 * timeAppears));
        instantCameraView.getMuteImageView().setAlpha(1f - timeAppears);
        instantCameraView.invalidate();
    }
}
