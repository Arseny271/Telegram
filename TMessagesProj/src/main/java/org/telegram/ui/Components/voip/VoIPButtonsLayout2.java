package org.telegram.ui.Components.voip;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.utils.AnimationUtilities;
import org.telegram.ui.Cells.GroupCallUserCell;
import org.telegram.ui.Components.RLottieDrawable;

import java.util.ArrayList;
import java.util.Objects;

@SuppressLint("ViewConstructor")
public class VoIPButtonsLayout2 extends VoIPBackground.BackgroundedView {
    public static final int DECLINE_COLOR = 0xFFf21827;
    public static final int ACCEPT_COLOR = 0xFF05be31;


    public static final float BORDER_PADDING = 4.6f;
    public static final float BORDER_BOTTOM_PADDING = 8f;
    public static final float BUTTON_PADDING = 4;

    private final ArrayList<View> optionButtons = new ArrayList<>(3);

    private int optionsVisibleButtonCount;
    private int optionButtonMaxHeight;
    private int childWidth;
    private boolean retryMod;
    private boolean videoCallMode;

    private final RectF acceptButtonRectStart = new RectF();
    private final RectF acceptButtonRectEnd = new RectF();
    private final RectF acceptButtonRect = new RectF();
    private final Paint acceptButtonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF declineButtonRectEnd = new RectF();
    private final Paint declineButtonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    
    private final RectF closeButtonRectStart = new RectF();
    private final RectF closeButtonRectEnd = new RectF();
    private final RectF closeButtonRect = new RectF();

    private final GroupCallUserCell.AvatarWavesDrawable acceptButtonWaves;

    private final StaticLayout acceptLayout;
    private final StaticLayout declineLayout;
    private final StaticLayout endCallLayout;
    private final StaticLayout retryLayout;
    private final TextPaint acceptLayoutPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint declineLayoutPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint endCallLayoutPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint retryLayoutPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private final RLottieDrawable lottieCallDrawable;
    private final Drawable staticCallDrawable;
    private final Drawable cancelDrawable;

    public interface Listener {
        void onAccept();
        void onDecline();
    }

    public VoIPButtonsLayout2(@NonNull Context context, VoIPBackground backgroundView) {
        super(context, backgroundView);
        setWillNotDraw(false);

        acceptButtonWaves = new GroupCallUserCell.AvatarWavesDrawable(AndroidUtilities.dp(50), AndroidUtilities.dp(60));
        acceptButtonWaves.setRadius(AndroidUtilities.dp(46f / 0.8f), AndroidUtilities.dp(51f / 0.8f), AndroidUtilities.dp(39f / 0.8f), AndroidUtilities.dp(44f / 0.8f), true);
        acceptButtonWaves.setColor(ColorUtils.setAlphaComponent(Color.WHITE, 20), ColorUtils.setAlphaComponent(Color.WHITE, 36));
        acceptButtonWaves.setShowWaves(true, this);

        backgroundDarkPaint.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        backgroundDarkPaint.setTextSize(AndroidUtilities.dp(16));
        backgroundDarkPaint.setTextAlign(Paint.Align.CENTER);

        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        declineButtonPaint.setColor(Color.WHITE);
        acceptButtonPaint.setColor(ACCEPT_COLOR);
        
        staticCallDrawable = Objects.requireNonNull(ContextCompat.getDrawable(context, R.drawable.calls_decline)).mutate();
        lottieCallDrawable = new RLottieDrawable(R.raw.call_accept, "" + R.raw.call_accept, AndroidUtilities.dp(60), AndroidUtilities.dp(60));
        lottieCallDrawable.setMasterParent(this);
        lottieCallDrawable.setAutoRepeat(1);
        lottieCallDrawable.start();

        cancelDrawable = Objects.requireNonNull(ContextCompat.getDrawable(context, R.drawable.ic_close_white)).mutate();
        cancelDrawable.setColorFilter(new PorterDuffColorFilter(Color.BLACK, PorterDuff.Mode.MULTIPLY));

        acceptLayoutPaint.setTextSize(AndroidUtilities.dp(13));
        declineLayoutPaint.setTextSize(AndroidUtilities.dp(13));
        endCallLayoutPaint.setTextSize(AndroidUtilities.dp(13));
        retryLayoutPaint.setTextSize(AndroidUtilities.dp(13));
        acceptLayoutPaint.setColor(Color.WHITE);
        declineLayoutPaint.setColor(Color.WHITE);
        endCallLayoutPaint.setColor(Color.WHITE);
        retryLayoutPaint.setColor(Color.WHITE);
        
        String acceptStr = LocaleController.getString("AcceptCall", R.string.AcceptCall);
        String declineStr = LocaleController.getString("DeclineCall", R.string.DeclineCall);
        String endCallStr = LocaleController.getString("VoipEndCall", R.string.VoipEndCall);
        String retryStr = LocaleController.getString("RetryCall", R.string.RetryCall);
        acceptLayout = new StaticLayout(acceptStr, acceptLayoutPaint, (int) acceptLayoutPaint.measureText(acceptStr), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        declineLayout = new StaticLayout(declineStr, declineLayoutPaint, (int) declineLayoutPaint.measureText(declineStr), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        endCallLayout = new StaticLayout(endCallStr, endCallLayoutPaint, (int) endCallLayoutPaint.measureText(endCallStr), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        retryLayout = new StaticLayout(retryStr, retryLayoutPaint, (int) retryLayoutPaint.measureText(retryStr), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
    }

    public void addOptionButton (View view) {
        optionButtons.add(view);
        addView(view);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        maxOffset = getMeasuredWidth() / 2f - AndroidUtilities.dp(46);

        optionButtonMaxHeight = 0;
        optionsVisibleButtonCount = 0;

        for (int i = 0; i < optionButtons.size(); i++) {
            if (optionButtons.get(i).getVisibility() != View.GONE) {
                optionsVisibleButtonCount++;
            }
        }

        int buttonsCount = optionsVisibleButtonCount + 1;
        childWidth = (width - AndroidUtilities.dp(BORDER_PADDING) * 2 - AndroidUtilities.dp(BUTTON_PADDING) * 2 * buttonsCount) / Math.max(buttonsCount, 1);
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i).getVisibility() != View.GONE) {
                getChildAt(i).measure(MeasureSpec.makeMeasureSpec(childWidth, MeasureSpec.EXACTLY), heightMeasureSpec);
                if (getChildAt(i).getMeasuredHeight() > optionButtonMaxHeight) {
                    optionButtonMaxHeight = getChildAt(i).getMeasuredHeight();
                }
            }
        }

        int h = Math.max(optionButtonMaxHeight, AndroidUtilities.dp(250));
        setMeasuredDimension(width, h);

        updateButtonsLayout();
    }

    private void updateButtonsLayout () {
        closeButtonRectStart.set(getButtonLeft(optionsVisibleButtonCount), getButtonsTop(), getButtonLeft(optionsVisibleButtonCount) + AndroidUtilities.dp(60), getButtonsTop() + AndroidUtilities.dp(60));
        closeButtonRectEnd.set(
                AndroidUtilities.dp(25), AndroidUtilities.dp(88 + 74),
                getMeasuredWidth() - AndroidUtilities.dp(25), AndroidUtilities.dp(88 + 74 + 50)
        );

        declineButtonRectEnd.set(getDeclineButtonLeft(), getAcceptDeclineButtonsTop(),
                getDeclineButtonLeft() + AndroidUtilities.dp(64),
                getAcceptDeclineButtonsTop() + AndroidUtilities.dp(64)
        );

        acceptButtonRectStart.set(getButtonLeft(0), getButtonsTop(), getButtonLeft(0) + AndroidUtilities.dp(60), getButtonsTop() + AndroidUtilities.dp(60));
        acceptButtonRectEnd.set(getAcceptButtonLeft(), getAcceptDeclineButtonsTop(),
                getAcceptButtonLeft() + AndroidUtilities.dp(64),
                getAcceptDeclineButtonsTop() + AndroidUtilities.dp(64)
        );

        updateLayout(acceptDeclineVisible, getRatingVisible, buttonsReady);
    }


    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int startFrom = AndroidUtilities.dp(BORDER_PADDING) + AndroidUtilities.dp(BUTTON_PADDING);
        int buttonTop = bottom - top - AndroidUtilities.dp(BORDER_BOTTOM_PADDING) - optionButtonMaxHeight;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != View.GONE) {
                child.layout(startFrom, buttonTop, startFrom + child.getMeasuredWidth(), buttonTop + child.getMeasuredHeight());
                startFrom += AndroidUtilities.dp(BUTTON_PADDING) * 2 + child.getMeasuredWidth();
            }
        }
    }

    public int getButtonsTop () {
        return getMeasuredHeight() - AndroidUtilities.dp(BORDER_BOTTOM_PADDING) - optionButtonMaxHeight;
    }

    public int getButtonLeft (int buttonNumber) {
        return AndroidUtilities.dp(BORDER_PADDING) + AndroidUtilities.dp(BUTTON_PADDING) +
            (AndroidUtilities.dp(BUTTON_PADDING) * 2 + childWidth) * buttonNumber +
            (childWidth / 2 - AndroidUtilities.dp(30));
    }

    private int getAcceptDeclineButtonsTop () {
        return AndroidUtilities.dp(100 - (videoCallMode ? 60 : 0));
    }

    private int getAcceptButtonLeft () {
        return AndroidUtilities.dp(57);
    }

    private int getDeclineButtonLeft () {
        return getMeasuredWidth() - AndroidUtilities.dp(57 + 64);
    }

    public void setVideoCallMode(boolean videoCallMode) {
        if (this.videoCallMode == videoCallMode) return;
        this.videoCallMode = videoCallMode;
        updateButtonsLayout();
    }

    public boolean isVideoCallMode() {
        return videoCallMode;
    }

    private float endButtonScale = 1f;
    private float endButtonTranslationY = 0;

    private float acceptDeclineVisible = 0f;
    private float getRatingVisible = 0f;
    private float buttonsReady = 0f;
    public void updateLayout (float acceptDeclineVisible, float getRatingVisible, float buttonsReady) {
        this.acceptDeclineVisible = acceptDeclineVisible;
        this.getRatingVisible = getRatingVisible;
        this.buttonsReady = buttonsReady;

        float acceptDeclineVisibleButtons = videoCallMode ? 0f: acceptDeclineVisible;

        acceptButtonWaves.setColor(
            ColorUtils.setAlphaComponent(Color.WHITE, (int)(20f * acceptDeclineVisible)),
            ColorUtils.setAlphaComponent(Color.WHITE, (int)(36f * acceptDeclineVisible))
        );



        int n = 0;
        for (int i = 0; i < optionButtons.size(); i++) {
            View button = optionButtons.get(i);

            float buttonReady = (float) Math.min(Math.max((buttonsReady - (i * 0.05)) / 0.8f, 0f), 1f);
            float buttonOffsetX = 0
                + AnimationUtilities.fromTo(0, getAcceptButtonLeft() - getButtonLeft(n), acceptDeclineVisibleButtons)
                + (videoCallMode ? (AndroidUtilities.dp(BUTTON_PADDING) * 2 + childWidth) / 2f * acceptDeclineVisible: 0f);
            float buttonOffsetY = 0
                + AnimationUtilities.fromTo(0, getAcceptDeclineButtonsTop() - getButtonsTop(), acceptDeclineVisibleButtons)
                + AndroidUtilities.dp(64) * (1f - buttonReady);

            if (button.getVisibility() != View.GONE) {
                button.setTranslationX(buttonOffsetX);
                button.setTranslationY(buttonOffsetY);
                button.setAlpha(Math.min(1f - getRatingVisible, 1f - acceptDeclineVisibleButtons));
                button.setScaleX((1f - getRatingVisible) * buttonReady);
                button.setScaleY((1f - getRatingVisible) * buttonReady);
                n++;
            }
        }

        float buttonReady = (float) Math.min(Math.max((buttonsReady - (optionsVisibleButtonCount * 0.05)) / 0.8f, 0f), 1f);
        endButtonTranslationY = AndroidUtilities.dp(64) * (1f - buttonReady);
        endButtonScale = buttonReady;

        AnimationUtilities.fromToRectF(acceptButtonRect, acceptButtonRectStart, acceptButtonRectEnd, acceptDeclineVisible);
        if (getRatingVisible > 0f) {
            AnimationUtilities.fromToRectF(closeButtonRect, closeButtonRectStart, closeButtonRectEnd, getRatingVisible);
        } else {
            AnimationUtilities.fromToRectF(closeButtonRect, closeButtonRectStart, declineButtonRectEnd, acceptDeclineVisible);
        }

        float size = Math.min(closeButtonRect.width(), closeButtonRect.height()) - AndroidUtilities.dp(20);

        declineLayoutPaint.setAlpha((int)(255f * Math.min(Math.max(1f - getRatingVisible * 2f, 0f), acceptDeclineVisible)));
        endCallLayoutPaint.setAlpha((int)(255f * Math.min(Math.max(1f - getRatingVisible * 2f, 0f), 1f - acceptDeclineVisible)));
        staticCallDrawable.setAlpha((int)(255f * Math.max(1f - getRatingVisible * 2f, 0f)));
        staticCallDrawable.setBounds(
            (int) (closeButtonRect.centerX() - size / 2f),
            (int) (closeButtonRect.centerY() - size / 2f),
            (int) (closeButtonRect.centerX() + size / 2f),
            (int) (closeButtonRect.centerY() + size / 2f)
        );
        cancelDrawable.setBounds(
            (int) (closeButtonRect.centerX() - size / 2f),
            (int) (closeButtonRect.centerY() - size / 2f),
            (int) (closeButtonRect.centerX() + size / 2f),
            (int) (closeButtonRect.centerY() + size / 2f)
        );

        float size2 = Math.min(acceptButtonRect.width(), acceptButtonRect.height());
        lottieCallDrawable.setAlpha((int)(255f * acceptDeclineVisible));
        lottieCallDrawable.setBounds(
                (int) (acceptButtonRect.centerX() - size2 / 2f),
                (int) (acceptButtonRect.centerY() - size2 / 2f),
                (int) (acceptButtonRect.centerX() + size2 / 2f),
                (int) (acceptButtonRect.centerY() + size2 / 2f)
        );

        acceptLayoutPaint.setAlpha((int)(255f * acceptDeclineVisible));
        retryLayoutPaint.setAlpha((int)(255f * acceptDeclineVisible));
        acceptButtonPaint.setColor(ColorUtils.setAlphaComponent(ACCEPT_COLOR, (int)(255f * acceptDeclineVisible)));
        if (!retryMod) {
            declineButtonPaint.setColor(ColorUtils.blendARGB(DECLINE_COLOR, Color.WHITE, getRatingVisible));
        } else {
            declineButtonPaint.setColor(Color.WHITE);
        }

        backgroundDarkPaint.setAlpha((int)(255f * getRatingVisible));
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        final float declineRadius = AnimationUtilities.fromTo(AndroidUtilities.dp(30), AndroidUtilities.dp(8), getRatingVisible);

        if (endButtonScale != 1f || endButtonTranslationY != 0f) {
            canvas.save();
            canvas.translate(0, endButtonTranslationY);
            canvas.scale(endButtonScale, endButtonScale, closeButtonRect.centerX(), closeButtonRect.centerY());
        }

        canvas.save();
        canvas.translate(rigthOffsetX, 0);
        canvas.drawRoundRect(closeButtonRect, declineRadius, declineRadius, declineButtonPaint);
        if (!retryMod) {
            staticCallDrawable.draw(canvas);
        } else {
            cancelDrawable.draw(canvas);
        }
        if (declineLayoutPaint.getAlpha() > 0) {
            canvas.save();
            canvas.translate(closeButtonRect.centerX() - declineLayout.getWidth() / 2f, closeButtonRect.bottom + AndroidUtilities.dp(10));
            declineLayout.draw(canvas);
            canvas.restore();
        }
        if (endCallLayoutPaint.getAlpha() > 0) {
            canvas.save();
            canvas.translate(closeButtonRect.centerX() - endCallLayout.getWidth() / 2f, closeButtonRect.bottom + AndroidUtilities.dp(10));
            endCallLayout.draw(canvas);
            canvas.restore();
        }
        canvas.restore();

        if (endButtonScale != 1f || endButtonTranslationY != 0f) {
            canvas.restore();
        }

        if (acceptDeclineVisible > 0) {
            canvas.save();
            canvas.translate(leftOffsetX, 0);

            acceptButtonWaves.update();
            acceptButtonWaves.draw(canvas, acceptButtonRect.centerX(), acceptButtonRect.centerY(), this);

            final float acceptRadius = acceptButtonRect.width() / 2;
            canvas.drawRoundRect(acceptButtonRect, acceptRadius, acceptRadius, acceptButtonPaint);

            lottieCallDrawable.draw(canvas);
            if (!retryMod) {
                canvas.translate(acceptButtonRect.centerX() - acceptLayout.getWidth() / 2f, acceptButtonRect.bottom + AndroidUtilities.dp(10));
                acceptLayout.draw(canvas);
            } else {
                canvas.translate(acceptButtonRect.centerX() - retryLayout.getWidth() / 2f, acceptButtonRect.bottom + AndroidUtilities.dp(10));
                retryLayout.draw(canvas);
            }
            canvas.restore();
        }

        if (getRatingVisible > 0) {
            final int y = (int) (closeButtonRect.centerY() - ((backgroundDarkPaint.descent() + backgroundDarkPaint.ascent()) / 2));
            canvas.drawText("Close", closeButtonRect.centerX(), y, backgroundDarkPaint);
        }
    }





    boolean startDrag;
    float touchSlop;
    float startX;
    float startY;
    boolean leftDrag;
    boolean captured;
    float leftOffsetX;
    float rigthOffsetX;
    float maxOffset;
    Animator leftAnimator;
    Animator rightAnimator;
    Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (!isEnabled()) {
            return false;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        float x = ev.getX();
        float y = ev.getY();
        if ((acceptDeclineVisible == 1f && acceptButtonRect.contains(x, y)) || closeButtonRect.contains(x, y)) {
            return true;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                startX = x; startY = y;
                if (leftAnimator == null && acceptButtonRect.contains(x, y) && acceptDeclineVisible == 1f) {
                    captured = true;
                    leftDrag = true;
                    setPressed(true);
                    return true;
                }
                if (rightAnimator == null && closeButtonRect.contains(x, y)) {
                    captured = true;
                    leftDrag = false;
                    setPressed(true);
                    return true;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (captured) {
                    float dx = x - startX;
                    if (!startDrag && Math.abs(dx) > touchSlop && acceptDeclineVisible == 1f) {
                        //if (!retryMod) {
                            startX = event.getX();
                            dx = 0;
                            startDrag = true;
                            setPressed(false);
                            getParent().requestDisallowInterceptTouchEvent(true);
                        /*} else {
                            setPressed(false);
                            captured = false;
                        }*/
                    }
                    if (startDrag) {
                        if (leftDrag) {
                            leftOffsetX = dx;
                            if (leftOffsetX < 0) {
                                leftOffsetX = 0;
                            } else if (leftOffsetX > maxOffset) {
                                leftOffsetX = maxOffset;
                                dispatchTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 0, 0, 0));
                            }
                        } else {
                            rigthOffsetX = dx;
                            if (rigthOffsetX > 0) {
                                rigthOffsetX = 0;
                            } else if (rigthOffsetX < -maxOffset) {
                                rigthOffsetX = -maxOffset;
                                dispatchTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 0, 0, 0));
                            }
                        }
                    }
                    return true;
                }
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                float dy = y - startY;
                if (captured) {
                    if (leftDrag) {
                        ValueAnimator animator = ValueAnimator.ofFloat(leftOffsetX, 0);
                        animator.addUpdateListener(valueAnimator -> {
                            leftOffsetX = (float) valueAnimator.getAnimatedValue();
                            invalidate();
                            leftAnimator = null;
                        });
                        animator.start();
                        leftAnimator = animator;
                        if (listener != null) {
                            if ((!startDrag && Math.abs(dy) < touchSlop) || leftOffsetX > maxOffset * 0.8f) {
                                listener.onAccept();
                            }
                        }
                    } else {
                        ValueAnimator animator = ValueAnimator.ofFloat(rigthOffsetX, 0);
                        animator.addUpdateListener(valueAnimator -> {
                            rigthOffsetX = (float) valueAnimator.getAnimatedValue();
                            invalidate();
                            rightAnimator = null;
                        });
                        animator.start();
                        rightAnimator = animator;
                        if (listener != null) {
                            if ((!startDrag && Math.abs(dy) < touchSlop) || -rigthOffsetX > maxOffset * 0.8f) {
                                listener.onDecline();
                            }
                        }
                    }
                }
                getParent().requestDisallowInterceptTouchEvent(false);
                startDrag = false;
                captured = false;
                setPressed(false);
                break;
        }

        return false;
    }

    public float getAcceptButtonCenterX () {
        return acceptButtonRect.centerX() + leftOffsetX;
    }

    public float getAcceptButtonCenterY () {
        return acceptButtonRect.centerY();
    }

    public void setRetryMod(boolean retryMod) {
        this.retryMod = retryMod;
        updateLayout(acceptDeclineVisible, getRatingVisible, buttonsReady);
    }
}
