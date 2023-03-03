package org.telegram.ui.Components.voip;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.utils.AnimationUtilities;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.VoIPFragment;

@SuppressLint("ViewConstructor")
public class VoIPToggleButton2 extends VoIPBackground.BackgroundedView {
    private static final int RADIUS = 30;

    private final VoIPFragment.BooleanAnimation isChecked = new VoIPFragment.BooleanAnimation(this::checkLayout, 350, 350);
    private final VoIPFragment.BooleanAnimation clickAnimation = new VoIPFragment.BooleanAnimation(this::checkLayout, 350, 350);

    private RLottieDrawable lottieDrawable;
    private int currentAnimation = -1;

    private final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint whitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    boolean animationInProgress;
    ValueAnimator animator;

    CharSequence nextTextToSet;
    FrameLayout textLayoutContainer;
    TextView[] textView = new TextView[2];
    String currentText;


    public VoIPToggleButton2(@NonNull Context context, VoIPBackground backgroundView) {
        super(context, backgroundView);
        setWillNotDraw(false);

        whitePaint.setColor(Color.WHITE);
        clearPaint.setColor(Color.WHITE);
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));

        textLayoutContainer = new FrameLayout(context);
        addView(textLayoutContainer);

        for (int i = 0; i < 2; i++) {
            TextView textView = new TextView(context);
            textView.setGravity(Gravity.CENTER_HORIZONTAL);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            textView.setTextColor(Color.WHITE);
            textView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            textLayoutContainer.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 68, 0, 0));
            this.textView[i] = textView;
        }
        textView[1].setVisibility(View.GONE);

        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int cx = getMeasuredWidth() / 2;
        int cy = AndroidUtilities.dp(RADIUS);
        int iconRadius = AndroidUtilities.dp(30);

        if (lottieDrawable != null) {
            lottieDrawable.setBounds(cx - iconRadius, cy - iconRadius, cx + iconRadius, cy + iconRadius);
        }
    }

    private void drawButton (Canvas canvas, boolean checked) {
        float cx = getWidth() / 2f;
        float cy = AndroidUtilities.dp(RADIUS);
        float radius = AndroidUtilities.dp(RADIUS);

        if (!checked) {
            int bgAlpha = (int) AnimationUtilities.fromTo(255, 74, hasAnyVideoValue);
            backgroundLightPaint.setAlpha(bgAlpha);
            canvas.drawCircle(cx, cy, radius, backgroundLightPaint);
            backgroundLightPaint.setAlpha(255);
            if (lottieDrawable != null) {
                lottieDrawable.draw(canvas, whitePaint);
            }
        } else {
            int bgAlpha = (int) AnimationUtilities.fromTo(255, 50, hasAnyVideoValue);
            backgroundDarkPaint.setAlpha(bgAlpha);
            canvas.drawCircle(cx, cy, radius - 3, backgroundDarkPaint);
            backgroundDarkPaint.setAlpha(255);
            canvas.saveLayerAlpha(0, 0, getMeasuredWidth(), getMeasuredHeight(), 255, Canvas.ALL_SAVE_FLAG);
            canvas.drawCircle(cx, cy, radius, whitePaint);
            if (lottieDrawable != null) {
                lottieDrawable.draw(canvas, clearPaint);
            }
            canvas.restore();
        }
    }

    private final Path clipPath = new Path();
    private final RectF clipRect = new RectF();

    @Override
    protected void onDraw(Canvas canvas) {
        boolean isChecked = this.isChecked.getValue();
        float checked = this.isChecked.get();
        float cx = getWidth() / 2f;
        float cy = AndroidUtilities.dp(RADIUS);

        canvas.save();
        canvas.scale(clickedValue, clickedValue, cx, cy);

        if ((checked == 0f && !isChecked) || (checked == 1f && isChecked)) {
            drawButton(canvas, isChecked);
        } else {
            float r = AndroidUtilities.dp(RADIUS) * (isChecked ? checked: (1f - checked));



            clipRect.set(cx - r, cy - r, cx + r, cy + r);
            clipPath.reset();
            clipPath.addRoundRect(clipRect, r, r, Path.Direction.CW);
            clipPath.close();

            canvas.save();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                canvas.clipOutPath(clipPath);
            }
            drawButton(canvas, !isChecked);
            canvas.restore();

            canvas.save();
            canvas.clipPath(clipPath);
            drawButton(canvas, isChecked);
            canvas.restore();
        }

        canvas.restore();
    }

    public void setText(String text, boolean animated) {
        if (getVisibility() != View.VISIBLE) {
            setVisibility(View.VISIBLE);
        }

        if (currentText != null && currentText.equals(text)) {
            return;
        }

        currentText = text;

        if (!animated) {
            if (animator != null) {
                animator.cancel();
            }
            animationInProgress = false;
            textView[0].setText(text);
            textView[0].setVisibility(View.VISIBLE);
            textView[1].setVisibility(View.GONE);
        } else {
            if (animationInProgress) {
                nextTextToSet = text;
                return;
            }
            if (!textView[0].getText().equals(text)) {
                textView[1].setText(text);
                replaceViews(textView[0], textView[1], () -> {
                    TextView v = textView[0];
                    textView[0] = textView[1];
                    textView[1] = v;
                });
            }
        }

        //textView[0].setText(text);
        invalidate();
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
                    textView[1].setText(nextTextToSet);
                    replaceViews(textView[0], textView[1], () -> {
                        TextView v = textView[0];
                        textView[0] = textView[1];
                        textView[1] = v;
                    });
                    nextTextToSet = null;
                }
            }
        });
        animator.setDuration(250).setInterpolator(CubicBezierInterpolator.DEFAULT);
        animator.start();
    }

    public int getCurrentAnimation() {
        return currentAnimation;
    }

    public void startLottieAnimation (int id) {
        startLottieAnimation(id, true);
    }

    public void startLottieAnimation (int id, boolean animated) {
        if (currentAnimation == id) return;
        boolean needPlay = currentAnimation != -1 && animated;

        lottieDrawable = new RLottieDrawable(id, "" + id, AndroidUtilities.dp(60), AndroidUtilities.dp(60));
        lottieDrawable.setMasterParent(this);
        if (needPlay) {
            lottieDrawable.start();
        } else {
            lottieDrawable.setCurrentFrame(lottieDrawable.getFramesCount() - 1);
        }

        int cx = getMeasuredWidth() / 2;
        int cy = AndroidUtilities.dp(RADIUS);
        int iconRadius = AndroidUtilities.dp(30);

        if (lottieDrawable != null) {
            lottieDrawable.setBounds(cx - iconRadius, cy - iconRadius, cx + iconRadius, cy + iconRadius);
        }

        currentAnimation = id;
    }

    public void restartLottieAnimation () {
        if (lottieDrawable != null) {
            lottieDrawable.setCurrentFrame(0);
            lottieDrawable.start();
        }
        invalidate();
    }

    public void setChecked (boolean value, boolean animated) {
        isChecked.set(value, animated);
    }

    public boolean getChecked () {
        return isChecked.getValue();
    }

    public void playClickAnimation () {
        clickAnimation.set(false, false);
        clickAnimation.set(true);
    }


    private float clickedValue = 1f;
    private void checkLayout() {
        this.clickedValue = 0.75f + Math.abs(clickAnimation.get() * 2f - 1f) * 0.25f;
        invalidate();
    }

    private float hasAnyVideoValue = 0f;

    public void setHasAnyVideoValue (float value) {
        if (hasAnyVideoValue == value) return;
        hasAnyVideoValue = value;
        invalidate();
    }
}