package org.telegram.ui.AnimationSettings;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class SetColorCell extends FrameLayout {
    private TextView textView;
    private TextView valueTextView;
    private FrameLayout colorView;

    Paint backgroundPaint;

    private boolean needDivider;

    private int colorValue = 0xFFFF00FF;

    public SetColorCell(Context context) {
        this(context, 21);
    }

    public SetColorCell(Context context, int padding) {
        super(context);

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, padding, 0, padding, 0));

        backgroundPaint = new Paint();
        backgroundPaint.setColor(colorValue);
        colorView = new FrameLayout(context) {
            protected void onDraw(Canvas canvas) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    canvas.drawRoundRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), AndroidUtilities.dp(6), AndroidUtilities.dp(6), backgroundPaint);
                } else {
                    canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), backgroundPaint);
                }
            };
        };
        colorView.setWillNotDraw(false);
        addView(colorView, LayoutHelper.createFrame(AndroidUtilities.dp(80), AndroidUtilities.dp(30), (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, padding, 0, padding, 0));


        valueTextView = new TextView(context);
        valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        valueTextView.setLines(1);
        valueTextView.setMaxLines(1);
        valueTextView.setSingleLine(true);
        valueTextView.setEllipsize(TextUtils.TruncateAt.END);
        valueTextView.setGravity(Gravity.CENTER);
        valueTextView.setTextColor(0xFF000000);
        colorView.addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER, 0, 0, 0, 0));
    }

    private int colorId;
    public void setColorId(int colorId) {
        this.colorId = colorId;
    }

    public int getColorId() {
        return colorId;
    }

    public int getColorValue() {
        return colorValue;
    }

    public void setText(String text, boolean divider) {
        textView.setText(text);
        valueTextView.setVisibility(INVISIBLE);
        colorView.setVisibility(INVISIBLE);
        needDivider = divider;
        setWillNotDraw(!divider);
    }

    public void setTextAndValue(String text, int value, boolean divider) {
        textView.setText(text);
        colorValue = value;

        final int grayColor = ((value & 255) + ((value >> 8) & 255) + ((value >> 16) & 255)) / 3;
        final int textColor = (grayColor > 127) ? 0xFF000000 : 0xFFFFFFFF;

        colorView.setVisibility(VISIBLE);
        backgroundPaint.setColor(colorValue);
        valueTextView.setVisibility(VISIBLE);
        valueTextView.setText(String.format("#%06X", (0x00FFFFFF & value)));
        valueTextView.setTextColor(textColor);

        needDivider = divider;
        setWillNotDraw(!divider);
        requestLayout();
    }

    public void setColorValue(int value) {
        colorValue = value;

        final int grayColor = ((value & 255) + ((value >> 8) & 255) + ((value >> 16) & 255)) / 3;
        final int textColor = (grayColor > 127) ? 0xFF000000 : 0xFFFFFFFF;

        colorView.setVisibility(VISIBLE);
        backgroundPaint.setColor(colorValue);
        valueTextView.setVisibility(VISIBLE);
        valueTextView.setText(String.format("#%06X", (0x00FFFFFF & value)));
        valueTextView.setTextColor(textColor);
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(50) + (needDivider ? 1 : 0));

        int availableWidth = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
        int width = availableWidth - AndroidUtilities.dp(88);

        textView.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
        colorView.measure(
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(80), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(30), MeasureSpec.EXACTLY));
        valueTextView.measure(
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(80), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(30), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (needDivider) {
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(20), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(20) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }
}
