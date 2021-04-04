package org.telegram.ui.AnimationSettings.Overlays;

import android.animation.AnimatorSet;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.AnimationSettings.ChatAnimationsOverlay;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.EditTextCaption;

public class ShortTextOverlay extends ChatAnimationsOverlay.OverlayView {
    private AnimatorSet animations;

    private int colorStart;
    private int colorCurrent;
    private int colorFinish;

    private RectF backgroundBounds;
    private RectF textBounds;

    private EditTextCaption editField;

    private float scrollOffsetStart;

    /* text offsets */
    float offsetLeft;
    float offsetTop;
    float offsetRight;
    float offsetBottom;

    Paint backgroundPaint;

    Drawable replyDrawable;

    AnimationParameter scrollOffset;
    AnimationParameter fontSize;
    AnimationParameter bubbleRadius;

    AnimationParameter replyX;
    AnimationParameter replyY;
    AnimationParameter replyExtraBackground;
    AnimationParameter replyLineHeight;

    AnimationParameter backgroundX;
    AnimationParameter backgroundY;
    AnimationParameter backgroundWidth;
    AnimationParameter backgroundHeight;

    AnimationParameter textPositionX;
    AnimationParameter textPositionY;

    boolean oldTop;
    boolean oldBottom;

    public ShortTextOverlay(ChatActivity activity, String typeName) {
        super(activity, typeName);
        setWillNotDraw(false);

        backgroundPaint = new Paint();
        backgroundPaint.setStyle(Paint.Style.FILL);

        replyDrawable = getResources().getDrawable(R.drawable.msg_panel_reply);
    }

    @Override
    public void startAnimation() {
        messageCell.drawCell(null);
        messageCell.setNeedDraw(false);

        /* calculate final bounds */
        Rect bounds = messageCell.getCurrentBackgroundDrawable().getBounds();
        backgroundBounds = new RectF(bounds.left, bounds.top, bounds.right, bounds.bottom);
        MessageObject.TextLayoutBlock block = messageObject.textLayoutBlocks.get(0);
        float textX = messageCell.getTextX() - (block.isRtl() ? (int) Math.ceil(messageObject.textXOffset) : 0);
        float textY = messageCell.getTextY() + block.textYOffset;
        textBounds = new RectF(textX, textY,
            textX + messageObject.textWidth,
            textY + messageObject.textHeight);

        /* calculate offsets */
        offsetLeft = textBounds.left - backgroundBounds.left;
        offsetTop = textBounds.top - backgroundBounds.top;
        offsetRight = backgroundBounds.right - textBounds.right;
        offsetBottom = backgroundBounds.bottom - textBounds.bottom;

        /* calculate background */
        backgroundX = new AnimationParameter(startPosition.left - offsetLeft, finishPosition.left + backgroundBounds.left);
        backgroundWidth = new AnimationParameter(startPosition.width() + offsetLeft * 2, backgroundBounds.width());
        if (hasReply) {
            backgroundY = new AnimationParameter(replyStartPosition.top, finishPosition.top + backgroundBounds.top);
            backgroundHeight = new AnimationParameter(startPosition.height() + offsetBottom * 2 + replyStartPosition.height(), backgroundBounds.height());
        } else {
            backgroundY = new AnimationParameter(startPosition.top - offsetTop, finishPosition.top + backgroundBounds.top);
            backgroundHeight = new AnimationParameter(startPosition.height() + offsetTop + offsetBottom, backgroundBounds.height());
        }

        textPositionX = new AnimationParameter(startPosition.left, finishPosition.left + textBounds.left);
        textPositionY = new AnimationParameter(startPosition.top, finishPosition.top + textBounds.top);

        scrollOffset = new AnimationParameter(scrollOffsetStart, 0);
        fontSize = new AnimationParameter(editField.getTextSize(), messageObject.getTextSize());
        bubbleRadius = new AnimationParameter(0, AndroidUtilities.dp(SharedConfig.bubbleRadius));

        colorStart = Theme.getColor(Theme.key_chat_messagePanelBackground);
        colorFinish = Theme.getColor(Theme.key_chat_outBubble);

        if (hasReply) {
            replyExtraBackground = new AnimationParameter(AndroidUtilities.dp(35), 0);
            replyLineHeight = new AnimationParameter(0, AndroidUtilities.dp(35));
            replyX = new AnimationParameter(replyStartPosition.left + replyOffset - AndroidUtilities.dp(7),
                    finishPosition.left + messageCell.getReplyStartX());
            replyY = new AnimationParameter(replyStartPosition.top + AndroidUtilities.dp(6),
                    finishPosition.top + messageCell.getReplyStartY());
        }

        super.startAnimation();

        animations = new AnimatorSet();
        animations.playTogether(getPropertyAnimator(POSITION_X));
        animations.playTogether(getPropertyAnimator(POSITION_Y));
        animations.playTogether(getPropertyAnimator(SCALE));
        animations.playTogether(getPropertyAnimator(COLOR_CHANGE));
        animations.playTogether(getPropertyAnimator(BUBBLE_SHAPE));
        addOnAnimationEndListener(animations);
        animations.start();
    }

    @Override
    public void onAnimationFinish() {
        messageCell.setNeedDraw(true);
        messageObject.generateLayout(null, 0, 0);
        messageCell.requestLayout();
        super.onAnimationFinish();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        messageCell.drawBackground(canvas,
            (int)(backgroundX.get()),
            (int)(backgroundY.get()),
            (int)(backgroundX.get() + backgroundWidth.get()),
            (int)(backgroundY.get() + backgroundHeight.get()),
            oldTop,
            oldBottom,
            backgroundPaint,
            (int)bubbleRadius.get());

        float fontScale = fontSize.get() / fontSize.finish();

        canvas.save();
        canvas.clipRect(backgroundX.get(), backgroundY.get(), backgroundX.get() + backgroundWidth.get(), backgroundY.get() + backgroundHeight.get());
        canvas.translate(0, -scrollOffset.get());
        canvas.scale(fontScale, fontScale, backgroundX.get() + offsetLeft, backgroundY.get() + offsetTop);
        canvas.translate(textPositionX.get() - textBounds.left, textPositionY.get() - textBounds.top);
        messageCell.drawMessageText(canvas, messageObject.textLayoutBlocks, false, 1f);
        canvas.restore();

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

        /* draw link */
        if (messageObject.isWebpage()) {
            canvas.save();
            canvas.clipRect(backgroundX.get(), backgroundY.get(), backgroundX.get() + backgroundWidth.get(), backgroundY.get() + backgroundHeight.get());
            canvas.translate(backgroundX.get() - backgroundBounds.left, backgroundY.get() - backgroundBounds.top);
            messageCell.drawContent(canvas, false);
            canvas.restore();
        }

        /* draw time */
        canvas.save();
        canvas.translate(
                backgroundX.get() - backgroundBounds.left - backgroundWidth.finish() + backgroundWidth.get(),
                backgroundY.get() - backgroundBounds.top - backgroundHeight.finish() + backgroundHeight.get());
        messageCell.setOverlayAnimated(false);
        messageCell.drawTime(canvas, COLOR_CHANGE.getValue(), false);
        messageCell.setOverlayAnimated(true);
        canvas.restore();
    }

    public void setEditField(EditTextCaption editField) {
        this.editField = editField;
        Log.i("TextSize", "start: " + editField.getTextSize());
    }

    public void setScrollOffset(float offset) {
        scrollOffsetStart = offset;
    }



    @Override
    public void setMessageCell(ChatMessageCell messageCell) {
        super.setMessageCell(messageCell);
        oldBottom = messageCell.getPinnedBottom();
        oldTop = messageCell.getPinnedTop();
    }

    @Override
    public void onPositionXUpdated(float x) {
        backgroundX.update(x);
        backgroundWidth.update(x);
        textPositionX.update(x);
        if (hasReply) replyX.update(x);
        invalidate();
    }

    @Override
    public void onPositionYUpdated(float y) {
        backgroundY.update(y, targetOffset, getTotalOffset());
        textPositionY.update(y, targetOffset, getTotalOffset());
        backgroundHeight.update(y);
        scrollOffset.update(y);
        if (hasReply) {
            replyY.update(y, targetOffset, getTotalOffset());
            replyExtraBackground.update(Math.min(1, y * 4));
            replyLineHeight.update(Math.min(1, Math.max((y * 4 - 1f), 0)));
        }
        invalidate();
    }

    public void onBubbleShapeUpdated(float bubbleShape) {
        bubbleRadius.update(bubbleShape);
    };

    @Override
    public void onScaleUpdated(float scale) {
        fontSize.update(scale);
        invalidate();
    }

    @Override
    public void onColorChangeUpdated(float color) {
        colorCurrent = Color.argb(255,
                calculateValue(Color.red(colorStart), Color.red(colorFinish), color),
                calculateValue(Color.green(colorStart), Color.green(colorFinish), color),
                calculateValue(Color.blue(colorStart), Color.blue(colorFinish), color));
        backgroundPaint.setColor(colorCurrent);
        invalidate();
    };

}
