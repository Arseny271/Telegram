package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.Theme;


@SuppressWarnings("FieldCanBeLocal")
public class ForwardHintView extends FrameLayout {

    private TextView textView;
    private ImageView arrowImageView;
    private AnimatorSet animatorSet;
    private Runnable hideRunnable;
    private float translationY;

    private long showingDuration = 2000;

    public ForwardHintView(Context context) {
        super(context);

        FrameLayout backgroundView = new FrameLayout(context);
        backgroundView.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(6), getThemedColor(Theme.key_chat_gifSaveHintBackground)));
        backgroundView.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
        addView(backgroundView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 6));

        textView = new TextView(context);
        textView.setTextColor(getThemedColor(Theme.key_chat_gifSaveHintText));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setMaxWidth(AndroidUtilities.dp(280));
        textView.setGravity(Gravity.LEFT | Gravity.TOP);
        textView.setPivotX(0);
        backgroundView.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 10, 2, 10, 0));
        textView.setText("Forwards from this channel are restricted");

        arrowImageView = new ImageView(context);
        arrowImageView.setImageResource(R.drawable.tooltip_arrow);
        arrowImageView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_gifSaveHintBackground), PorterDuff.Mode.MULTIPLY));
        addView(arrowImageView, LayoutHelper.createFrame(14, 6, Gravity.LEFT | Gravity.BOTTOM, 0, 0, 0, 0));
    }

    public float getBaseTranslationY() {
        return translationY;
    }

    public boolean showForMenuItem(ActionBarMenuItem item, boolean animated) {
        if (hideRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(hideRunnable);
            hideRunnable = null;
        }
        int[] position = new int[2];
        item.getLocationInWindow(position);
        int left = position[0];
        int top = position[1];

        measure(MeasureSpec.makeMeasureSpec(1000, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(1000, MeasureSpec.AT_MOST));

        setTranslationX(left - getMeasuredWidth() + AndroidUtilities.dp(35));
        setTranslationY(top - getMeasuredHeight() - AndroidUtilities.dp(00));

        float arrowX = getMeasuredWidth() - AndroidUtilities.dp(25);
        arrowImageView.setTranslationX(arrowX);

        textView.setPivotX(getMeasuredWidth() - AndroidUtilities.dp(25));
        setPivotX(arrowX);
        setPivotY(getMeasuredHeight());

        if (animatorSet != null) {
            animatorSet.cancel();
            animatorSet = null;
        }

        setTag(1);
        setVisibility(VISIBLE);
        if (animated) {
            animatorSet = new AnimatorSet();
            animatorSet.playTogether(
                    ObjectAnimator.ofFloat(this, View.ALPHA, 0.0f, 1.0f),
                    ObjectAnimator.ofFloat(this, View.SCALE_X, 0.0f, 1.0f),
                    ObjectAnimator.ofFloat(this, View.SCALE_Y, 0.0f, 1.0f)
            );
            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    animatorSet = null;
                    AndroidUtilities.runOnUIThread(hideRunnable = () -> hide(), 3000);
                }
            });
            animatorSet.setDuration(180);
            animatorSet.start();

            textView.animate().scaleX(1f).scaleY(1f).setInterpolator(CubicBezierInterpolator.EASE_IN).setStartDelay(132 + 140).setDuration(100).start();

        } else {
            setAlpha(1.0f);
        }

        return true;
    }

    public void hide() {
        if (getTag() == null) {
            return;
        }
        setTag(null);
        if (hideRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(hideRunnable);
            hideRunnable = null;
        }
        if (animatorSet != null) {
            animatorSet.cancel();
            animatorSet = null;
        }
        animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(this, View.ALPHA, 0.0f),
                ObjectAnimator.ofFloat(this, View.SCALE_X, 0.0f),
                ObjectAnimator.ofFloat(this, View.SCALE_Y, 0.0f)
        );
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                setVisibility(View.INVISIBLE);
                animatorSet = null;
            }
        });
        animatorSet.setDuration(180);
        animatorSet.start();
    }

    private int getThemedColor(String key) {
        return Theme.getColor(key);
    }
}
