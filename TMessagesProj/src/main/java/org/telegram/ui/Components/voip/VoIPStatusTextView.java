package org.telegram.ui.Components.voip;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.utils.AnimationUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.StatusDrawable;
import org.telegram.ui.VoIPFragment;

@SuppressLint("ViewConstructor")
public class VoIPStatusTextView extends VoIPBackground.BackgroundedView {
    private final VoIPFragment.BooleanAnimation reconnectingVisible = new VoIPFragment.BooleanAnimation(this::checkLayout);
    private final VoIPFragment.BooleanAnimation weakSignalVisible = new VoIPFragment.BooleanAnimation(this::checkLayout);
    private final RectF reconnectingBackgroundRect = new RectF();

    TextView[] textView = new TextView[2];
    boolean[] ellipsisT = new boolean[2];
    TextView reconnectTextView;
    TextView weakSignalTextView;
    VoIPTimerView timerView;

    CharSequence nextTextToSet;
    boolean animationInProgress;

    ValueAnimator animator;
    boolean timerShowing;

    StatusDrawable statusDrawable;

    public VoIPStatusTextView(@NonNull Context context, VoIPBackground backgroundView) {
        super(context, backgroundView);
        setWillNotDraw(false);

        for (int i = 0; i < 2; i++) {
            textView[i] = new TextView(context);
            textView[i].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
            textView[i].setTextColor(Color.WHITE);
            textView[i].setGravity(Gravity.CENTER_HORIZONTAL);
            ellipsisT[i] = false;
            addView(textView[i], LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
        }

        statusDrawable = Theme.getChatStatusDrawable(0);
        statusDrawable.setColor(Color.WHITE);

        weakSignalTextView = new TextView(context);
        weakSignalTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        weakSignalTextView.setTextColor(Color.WHITE);
        weakSignalTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        addView(weakSignalTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 42, 0, 0));
        weakSignalTextView.setText(LocaleController.getString(R.string.VoipWeakSignal));
        weakSignalTextView.setVisibility(View.GONE);


        reconnectTextView = new TextView(context);
        reconnectTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        reconnectTextView.setTextColor(Color.WHITE);
        reconnectTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        addView(reconnectTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 42, 0, 0));

        SpannableStringBuilder ssb2 = new SpannableStringBuilder(LocaleController.getString("VoipReconnecting", R.string.VoipReconnecting));
        ssb2.append(".");
        ssb2.setSpan(new DialogCell.FixedWidthSpan(statusDrawable.getMinimumWidth() + AndroidUtilities.dp(6)), ssb2.length() - 1, ssb2.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        reconnectTextView.setText(ssb2);
        reconnectTextView.setVisibility(View.GONE);

        timerView = new VoIPTimerView(context);
        addView(timerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        checkLayout();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (backgroundDarkPaint.getAlpha() > 0) {
            float r = reconnectingBackgroundRect.height() / 2f;
            canvas.drawRoundRect(reconnectingBackgroundRect, r, r, backgroundDarkPaint);

            if (reconnectingVisible.get() > 0f) {
                TextView t = reconnectTextView;
                canvas.save();
                canvas.scale(reconnectTextScale, reconnectTextScale,
                        reconnectingBackgroundRect.centerX(),
                        reconnectingBackgroundRect.centerY());
                canvas.translate(
                        t.getX() + t.getMeasuredWidth() - statusDrawable.getMinimumWidth(),
                        t.getY() + t.getMeasuredHeight() / 2f - statusDrawable.getMinimumHeight() / 2f + AndroidUtilities.dp(2));
                statusDrawable.setColor(ColorUtils.setAlphaComponent(Color.WHITE, (int) (255f * reconnectingVisible.get())));
                statusDrawable.draw(canvas);
                canvas.restore();
            }
        }

        super.dispatchDraw(canvas);

        boolean needInvalidate = false;
        for (int i = 0; i < 2; i++) {
            TextView t = textView[i];
            if (t.getVisibility() == View.GONE || t.getAlpha() == 0f || !ellipsisT[i]) continue;
            needInvalidate = true;

            canvas.save();
            canvas.scale(t.getScaleX(), t.getScaleY(), getMeasuredWidth() / 2f, getMeasuredHeight() / 2f);
            canvas.translate(
                    t.getX() + t.getMeasuredWidth() - statusDrawable.getMinimumWidth(),
                    t.getY() + t.getMeasuredHeight() / 2f - statusDrawable.getMinimumHeight() / 2f + AndroidUtilities.dp(2));
            statusDrawable.setColor(ColorUtils.setAlphaComponent(Color.WHITE, (int)(255f * t.getAlpha())));
            statusDrawable.draw(canvas);
            canvas.restore();
        }

        if (needInvalidate) {
            invalidate();
        }
    }

    private String currentText;

    public void setText(String text, boolean ellipsis, boolean animated) {
        if (text.equals(currentText)) return;
        currentText = text;

        CharSequence nextString = text;
        if (ellipsis) {
            SpannableStringBuilder ssb = new SpannableStringBuilder(text);
            ssb.append(".");
            ssb.setSpan(new DialogCell.FixedWidthSpan(statusDrawable.getMinimumWidth() + AndroidUtilities.dp(6)), ssb.length() - 1, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            nextString = ssb;
        }

        if (TextUtils.isEmpty(textView[0].getText())) {
            animated = false;
        }

        if (!animated) {
            if (animator != null) {
                animator.cancel();
            }
            animationInProgress = false;
            ellipsisT[0] = ellipsis;
            textView[0].setText(nextString);
            textView[0].setVisibility(View.VISIBLE);
            textView[1].setVisibility(View.GONE);
            timerView.setVisibility(View.GONE);

        } else {
            if (animationInProgress) {
                nextTextToSet = nextString;
                return;
            }

            if (timerShowing) {
                ellipsisT[0] = ellipsis;
                textView[0].setText(nextString);
                replaceViews(timerView, textView[0], null);
            } else {
                if (!textView[0].getText().equals(nextString)) {
                    textView[1].setText(nextString);
                    ellipsisT[1] = ellipsis;
                    replaceViews(textView[0], textView[1], () -> {
                        TextView v = textView[0];
                        textView[0] = textView[1];
                        textView[1] = v;

                        boolean b = ellipsisT[0];
                        ellipsisT[0] = ellipsisT[1];
                        ellipsisT[1] = b;
                    });
                }
            }
        }
    }

    public void showTimer(boolean animated) {
        if (TextUtils.isEmpty(textView[0].getText())) {
            animated = false;
        }
        if (timerShowing) {
            return;
        }
        timerView.updateTimer();
        if (!animated) {
            if (animator != null) {
                animator.cancel();
            }
            timerShowing = true;
            animationInProgress = false;
            textView[0].setVisibility(View.GONE);
            textView[1].setVisibility(View.GONE);
            timerView.setVisibility(View.VISIBLE);
        } else {
            if (animationInProgress) {
                nextTextToSet = "timer";
                return;
            }
            timerShowing = true;
            replaceViews(textView[0], timerView, null);
        }
    }


    private void replaceViews(View out, View in, Runnable onEnd) {
        out.setVisibility(View.VISIBLE);
        in.setVisibility(View.VISIBLE);

        in.setTranslationY(AndroidUtilities.dp(15));
        in.setAlpha(0f);
        animationInProgress = true;
        animator = ValueAnimator.ofFloat(0, 1f);
        animator.addUpdateListener(valueAnimator -> {
            float v = (float) valueAnimator.getAnimatedValue();
            float inScale = 0.4f + 0.6f * v;
            float outScale = 0.4f + 0.6f * (1f - v);
            in.setTranslationY(AndroidUtilities.dp(10) * (1f - v));
            in.setAlpha(v);
            in.setScaleX(inScale);
            in.setScaleY(inScale);

            out.setTranslationY(-AndroidUtilities.dp(10) * v);
            out.setAlpha(1f - v);
            out.setScaleX(outScale);
            out.setScaleY(outScale);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                out.setVisibility(View.GONE);
                out.setAlpha(1f);
                out.setTranslationY(0);
                out.setScaleY(1f);
                out.setScaleX(1f);

                in.setAlpha(1f);
                in.setTranslationY(0);
                in.setVisibility(View.VISIBLE);
                in.setScaleY(1f);
                in.setScaleX(1f);

                if (onEnd != null) {
                    onEnd.run();
                }
                animationInProgress = false;
                if (nextTextToSet != null) {
                    if (nextTextToSet.equals("timer")) {
                        showTimer(true);
                    } else {
                        textView[1].setText(nextTextToSet);
                        replaceViews(textView[0], textView[1], () -> {
                            TextView v = textView[0];
                            textView[0] = textView[1];
                            textView[1] = v;

                            boolean b = ellipsisT[0];
                            ellipsisT[0] = ellipsisT[1];
                            ellipsisT[1] = b;
                        });
                    }
                    nextTextToSet = null;
                }
            }
        });
        animator.setDuration(250).setInterpolator(CubicBezierInterpolator.DEFAULT);
        animator.start();
    }

    public void setSignalBarCount(int count) {
        timerView.setSignalBarCount(count);
        weakSignalVisible.set(count == 1, true);
    }

    public void showReconnect(boolean showReconnecting, boolean animated) {
        reconnectingVisible.set(showReconnecting, animated);
    }

    float reconnectTextScale;
    float rateVisible;
    float hasAnyVideo;

    public void updateLayout (float rateVisible, float hasAnyVideo) {
        this.rateVisible = rateVisible;
        this.hasAnyVideo = hasAnyVideo;
        timerView.updateLayout(rateVisible);
        checkLayout();
    }

    private void checkLayout () {
        float isReconnecting = Math.min(reconnectingVisible.get(), 1f - rateVisible);
        float isWeakSignal = Math.min(Math.min(weakSignalVisible.get(), 1f - isReconnecting), 1f - rateVisible);
        int w1, w2;

        weakSignalTextView.setAlpha(isWeakSignal);
        weakSignalTextView.setScaleX(0.35f + 0.65f * isWeakSignal);
        weakSignalTextView.setScaleY(0.35f + 0.65f * isWeakSignal);
        weakSignalTextView.setVisibility(isWeakSignal > 0f ? VISIBLE: GONE);
        w1 = (int) (weakSignalTextView.getMeasuredWidth() * isWeakSignal);

        reconnectTextView.setAlpha(isReconnecting);
        reconnectTextView.setScaleX(reconnectTextScale = (0.35f + 0.65f * isReconnecting));
        reconnectTextView.setScaleY(reconnectTextScale);
        reconnectTextView.setVisibility(isReconnecting > 0f ? VISIBLE: GONE);
        w2 = (int) (reconnectTextView.getMeasuredWidth() * isReconnecting);

        View v = isReconnecting > isWeakSignal ? reconnectTextView: weakSignalTextView;
        float hw = Math.max(w1, w2) / 2f + AndroidUtilities.dp(12);
        float hh = AndroidUtilities.dp(14) * Math.max(isReconnecting, isWeakSignal);
        float yc = v.getY() + v.getMeasuredHeight() / 2f;
        backgroundDarkPaint.setAlpha((int)(AnimationUtilities.fromTo(180f, 74f, hasAnyVideo) * Math.max(isReconnecting, isWeakSignal)));
        reconnectingBackgroundRect.set(
            getMeasuredWidth() / 2f - hw,
            yc - hh,
            getMeasuredWidth() / 2f + hw,
            yc + hh
        );

        invalidate();
    }
}
