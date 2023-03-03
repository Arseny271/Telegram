package org.telegram.ui.Components.voip;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;

@SuppressLint("ViewConstructor")
public class VoIPRateView extends VoIPBackground.BackgroundedView {
    private final RLottieDrawable clickEffect;
    private boolean needDrawClickEffect = false;

    private final RectF backgroundRect = new RectF();
    private final LinearLayout linearLayout;

    private final StarView[] stars = new StarView[5];
    private int currentRating = 0;

    public VoIPRateView(@NonNull Context context, VoIPBackground backgroundView) {
        super(context, backgroundView);

        clickEffect = new RLottieDrawable(R.raw.voip_rate_effect, "" + R.raw.voip_rate_effect, AndroidUtilities.dp(150), AndroidUtilities.dp(150));
        clickEffect.setMasterParent(this);

        backgroundDarkPaint.setAlpha(180);

        linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        TextView headerTextView = new TextView(context);
        headerTextView.setText(LocaleController.getString(R.string.VoipRateCallHeader));
        headerTextView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        headerTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        headerTextView.setTextColor(Color.WHITE);
        headerTextView.setGravity(Gravity.CENTER);

        TextView alertTextView = new TextView(context);
        alertTextView.setText(LocaleController.getString(R.string.VoipRateCallAlert2));
        alertTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        alertTextView.setTextColor(Color.WHITE);
        alertTextView.setGravity(Gravity.CENTER);

        LinearLayout starsLayout = new LinearLayout(context);
        starsLayout.setOrientation(LinearLayout.HORIZONTAL);
        starsLayout.setGravity(Gravity.CENTER);
        for (int i = 0; i < 5; i++) {
            stars[i] = new StarView(context);
            stars[i].setTag(i + 1);
            stars[i].setOnClickListener(this::setRating);
            starsLayout.addView(stars[i], LayoutHelper.createFrame(32, 32, Gravity.CENTER, 5.5f, 0, 5.5f, 0));
        }

        linearLayout.addView(headerTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 25, 1, 25, 6));
        linearLayout.addView(alertTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 25, 0, 25, 0));
        linearLayout.addView(starsLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 16, 0, 0));

        addView(linearLayout, LayoutHelper.createFrame(310, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 411, 0, 0));

        setWillNotDraw(false);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int height = Math.max(AndroidUtilities.dp(142),
            linearLayout.getMeasuredHeight() + AndroidUtilities.dp(30));

        backgroundRect.set(
            getMeasuredWidth() / 2f - AndroidUtilities.dp(155), AndroidUtilities.dp(392),
            getMeasuredWidth() / 2f + AndroidUtilities.dp(155), AndroidUtilities.dp(392) + height);
    }

    private void setRating (View v) {
        int rating = (int) v.getTag();
        if (currentRating == rating) return;

        int[] cords = new int[2];
        v.getLocationInWindow(cords);
        int x = cords[0];
        int y = cords[1];
        getLocationInWindow(cords);
        x += v.getMeasuredWidth() / 2 - cords[0];
        y += v.getMeasuredHeight() / 2 - cords[1];


        if (rating > 3 && !clickEffect.isRunning()) {
            needDrawClickEffect = true;
            clickEffect.stop();
            clickEffect.setCurrentFrame(0);
            clickEffect.start();
            clickEffect.setBounds(
                    x - AndroidUtilities.dp(75),
                    y - AndroidUtilities.dp(75),
                    x + AndroidUtilities.dp(75),
                    y + AndroidUtilities.dp(75)
            );
        }


        for (int i = rating; i < currentRating; i++) {
            stars[i].starDrawable.stop();
            stars[i].starDrawable.setCurrentFrame(0);
        }

        for (int i = currentRating; i < rating; i++) {
            stars[i].starDrawable.start();
        }

        currentRating = rating;
    }

    public int getRating () {
        return currentRating;
    }


    private float ratingShowValue = -1;

    public void updateLayout (float ratingShowValue) {
        if (this.ratingShowValue == ratingShowValue) return;

        setAlpha(ratingShowValue);
        setScaleX(0.75f + 0.25f * ratingShowValue);
        setScaleY(0.75f + 0.25f * ratingShowValue);

        for (int i = 0; i < 5; i++) {
            float starReady = (float) Math.min(Math.max((ratingShowValue - (i * 0.05)) / 0.8f, 0f), 1f);

            View star = stars[i];
            star.setAlpha(0.5f + 0.5f * starReady);
            star.setScaleX(0.5f + 0.5f * starReady);
            star.setScaleY(0.5f + 0.5f * starReady);
        }

        this.ratingShowValue = ratingShowValue;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        canvas.drawRoundRect(backgroundRect, AndroidUtilities.dp(20), AndroidUtilities.dp(20), backgroundDarkPaint);
        super.dispatchDraw(canvas);
        if (needDrawClickEffect) {
            clickEffect.draw(canvas);
        }
    }

    private static class StarView extends View {
        public final RLottieDrawable starDrawable;

        public StarView(Context context) {
            super(context);
            starDrawable = new RLottieDrawable(R.raw.star, "" + R.raw.star, AndroidUtilities.dp(35), AndroidUtilities.dp(35));
            starDrawable.setMasterParent(this);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            starDrawable.draw(canvas);
        }
    }
}
