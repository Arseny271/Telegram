package org.telegram.ui.Components.voip;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.voip.VoIPService;

import java.util.Objects;

public class VoIPTimerView extends View {

    StaticLayout timerLayout;
    RectF rectF = new RectF();
    Paint activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint inactivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    String currentTimeStr;
    TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private int signalBarCount = 4;
    private final Drawable staticCallDrawable;

    Runnable updater = () -> {
        if (getVisibility() == View.VISIBLE) {
            updateTimer();
        }
    };

    public VoIPTimerView(Context context) {
        super(context);
        textPaint.setTextSize(AndroidUtilities.dp(17));
        textPaint.setColor(Color.WHITE);
        activePaint.setColor(ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.9f)));
        inactivePaint.setColor(ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.4f)));
        staticCallDrawable = Objects.requireNonNull(ContextCompat.getDrawable(context, R.drawable.calls_decline)).mutate();
        staticCallDrawable.setBounds(0, 0, AndroidUtilities.dp(30), AndroidUtilities.dp(30));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final StaticLayout timerLayout = this.timerLayout;
        if (timerLayout != null) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), timerLayout.getHeight());
        } else {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(15));
        }
    }

    public void updateTimer() {
        removeCallbacks(updater);
        VoIPService service = VoIPService.getSharedInstance();
        if (service == null) {
            return;
        }
        String str = AndroidUtilities.formatLongDuration((int) (service.getCallDuration() / 1000));
        if (currentTimeStr == null || !currentTimeStr.equals(str)) {
            currentTimeStr = str;
            if (timerLayout == null) {
                requestLayout();
            }
            timerLayout = new StaticLayout(currentTimeStr, textPaint, (int) textPaint.measureText(currentTimeStr), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        }
        postDelayed(updater, 300);

        invalidate();
    }

    @Override
    public void setVisibility(int visibility) {
        if (getVisibility() != visibility) {
            if (visibility == VISIBLE) {
                currentTimeStr = "00:00";
                timerLayout = new StaticLayout(currentTimeStr, textPaint, (int) textPaint.measureText(currentTimeStr), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                updateTimer();
            } else {
                currentTimeStr = null;
                timerLayout = null;
            }
        }
        super.setVisibility(visibility);

    }

    @Override
    protected void onDraw(Canvas canvas) {
        final StaticLayout timerLayout = this.timerLayout;
        int totalWidth = timerLayout == null ? 0 : timerLayout.getWidth() + AndroidUtilities.dp(27);
        canvas.save();
        canvas.translate((getMeasuredWidth() - totalWidth) / 2f, 0);

        if (rateVisible < 1f) {
            canvas.save();
            canvas.translate(0, (getMeasuredHeight() - AndroidUtilities.dp(12)) / 2f);
            for (int i = 0; i < 4; i++) {
                Paint p = i + 1 > signalBarCount ? inactivePaint : activePaint;
                rectF.set(
                        AndroidUtilities.dpf2(5.33f) * i,
                        AndroidUtilities.dpf2(3f) * (3 - i),
                        AndroidUtilities.dpf2(5.33f) * i + AndroidUtilities.dpf2(3f),
                        AndroidUtilities.dp(12));
                canvas.drawRoundRect(rectF, AndroidUtilities.dpf2(0.7f), AndroidUtilities.dpf2(0.7f), p);
            }
            canvas.restore();
        }
        if (rateVisible > 0f) {
            canvas.save();
            canvas.translate(-AndroidUtilities.dp(12), (getMeasuredHeight() - AndroidUtilities.dp(30)) / 2f);
            staticCallDrawable.draw(canvas);
            canvas.restore();
        }


        if (timerLayout != null) {
            canvas.translate(AndroidUtilities.dp(27), 0);
            timerLayout.draw(canvas);
        }
        canvas.restore();
    }

    public void setSignalBarCount(int count) {
        signalBarCount = count;
        invalidate();
    }

    float rateVisible = 0f;

    public void updateLayout (float rateVisible) {
        this.rateVisible = rateVisible;
        activePaint.setColor(ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.9f * (1f - rateVisible))));
        inactivePaint.setColor(ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.4f * (1f - rateVisible))));
        staticCallDrawable.setAlpha((int)(255 * rateVisible));
        invalidate();
    }
}
