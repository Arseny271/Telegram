package org.telegram.ui.AnimationSettings;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class ColorEditorAlert extends BottomSheet {
    public interface ColorEditorAlertDelegate {
        void onColorChanged(int color);
        void onColorApplied(int color);
        void onColorCanceled();
    }

    private ColorEditorAlertDelegate editorDelegate;
    public void setDelegate(ColorEditorAlertDelegate delegate) {
        this.editorDelegate = delegate;
    }

    private boolean unsaved;

    public void setColor(int color) {
        colorPicker.setColor(color);
    }

    private class ColorPicker extends FrameLayout {
        private Paint colorWheelPaint;
        private Paint valueSliderPaint;
        private Paint circlePaint;
        private Drawable circleDrawable;

        private Bitmap colorWheelBitmap;

        private float[] colorHSV = new float[] { 0.0f, 0.0f, 1.0f };

        private float[] hsvTemp = new float[3];
        private LinearGradient colorGradient;

        private boolean brightnessPressed;
        private boolean colorPressed;

        private Paint whitePaint;

        private DecelerateInterpolator decelerateInterpolator = new DecelerateInterpolator();

        public ColorPicker(Context context) {
            super(context);
            setWillNotDraw(false);
            circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            circleDrawable = context.getResources().getDrawable(R.drawable.knob_shadow).mutate();

            colorWheelPaint = new Paint();
            colorWheelPaint.setAntiAlias(true);
            colorWheelPaint.setDither(true);

            valueSliderPaint = new Paint();
            valueSliderPaint.setAntiAlias(true);
            valueSliderPaint.setDither(true);

            whitePaint = new Paint();
            whitePaint.setColor(0xFFFFFFFF);

            setCanDismissWithSwipe(false);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(250));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawBitmap(colorWheelBitmap, 0, AndroidUtilities.dp(15), null);
            canvas.drawRect(0, AndroidUtilities.dp(194), getMeasuredWidth(), getMeasuredHeight(), whitePaint);
            canvas.drawLine(0, AndroidUtilities.dp(195), getMeasuredWidth(), AndroidUtilities.dp(195), Theme.dividerPaint);
            canvas.drawLine(0, AndroidUtilities.dp(195 + 60)-1, getMeasuredWidth(), AndroidUtilities.dp(195 + 60)-1, Theme.dividerPaint);

            int colorPointX = (int)(colorHSV[0] / 360f * getMeasuredWidth());
            int colorPointY = (int)((1f - colorHSV[1]) * AndroidUtilities.dp(180)) + AndroidUtilities.dp(15);
            hsvTemp[0] = colorHSV[0]; hsvTemp[1] = colorHSV[1]; hsvTemp[2] = 1.0f;
            drawPointerArrow(canvas, colorPointX, colorPointY, Color.HSVToColor(hsvTemp));

            if (colorGradient == null) {
                colorGradient = new LinearGradient(getMeasuredWidth() - AndroidUtilities.dp(21), 0, AndroidUtilities.dp(21), 0, new int[]{Color.BLACK, Color.HSVToColor(hsvTemp)}, null, Shader.TileMode.MIRROR);
            }

            valueSliderPaint.setShader(colorGradient);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                canvas.drawRoundRect(
                    AndroidUtilities.dp(21),
                    AndroidUtilities.dp(195 + 30 - 4),
                    getMeasuredWidth() - AndroidUtilities.dp(21),
                    AndroidUtilities.dp(195 + 30 + 4),
                    AndroidUtilities.dp(4),
                    AndroidUtilities.dp(4),
                    valueSliderPaint);
            } else {
                canvas.drawRect(
                        AndroidUtilities.dp(21),
                        AndroidUtilities.dp(195 + 30 - 4),
                        getMeasuredWidth() - AndroidUtilities.dp(21),
                        AndroidUtilities.dp(195 + 30 + 4),
                        valueSliderPaint);
            }

            drawPointerArrow(canvas,
                (int)(AndroidUtilities.dp(21) + (1f - colorHSV[2]) * (getMeasuredWidth() - AndroidUtilities.dp(42))),
                AndroidUtilities.dp(195 + 30),
                Color.HSVToColor(colorHSV));

        }

        private void drawPointerArrow(Canvas canvas, int x, int y, int color) {
            int side = AndroidUtilities.dp(13);
            circleDrawable.setBounds(x - side, y - side, x + side, y + side);
            circleDrawable.draw(canvas);

            circlePaint.setColor(0xffffffff);
            canvas.drawCircle(x, y, AndroidUtilities.dp(11), circlePaint);
            circlePaint.setColor(color);
            canvas.drawCircle(x, y, AndroidUtilities.dp(9), circlePaint);
        }

        @Override
        protected void onSizeChanged(int width, int height, int oldw, int oldh) {
            colorWheelBitmap = createColorWheelBitmap(width, AndroidUtilities.dp(180));
            colorGradient = null;
        }

        private Bitmap createColorWheelBitmap(int width, int height) {
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

            int colorCount = 12;
            int colorAngleStep = 360 / 12;
            int[] colors = new int[colorCount];
            float[] hsv = new float[]{0.0f, 1.0f, 1.0f};
            for (int i = 0; i < colors.length; i++) {
                hsv[0] = (i * colorAngleStep) % 360;
                colors[i] = Color.HSVToColor(hsv);
            }

            LinearGradient linearGradient = new LinearGradient(0, 0, width, 0, colors, null, Shader.TileMode.MIRROR);
            LinearGradient linearGradient2 = new LinearGradient(0, 0,0, height, new int[] {Color.TRANSPARENT, Color.WHITE}, null, Shader.TileMode.MIRROR);
            ComposeShader composeShader = new ComposeShader(linearGradient, linearGradient2, PorterDuff.Mode.SRC_OVER);

            colorWheelPaint.setShader(composeShader);

            Canvas canvas = new Canvas(bitmap);
            canvas.drawRect(0, 0, width, height, colorWheelPaint);

            return bitmap;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getAction();
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:

                    int x = (int) event.getX();
                    int y = (int) event.getY();

                    if (!brightnessPressed && (colorPressed || y < AndroidUtilities.dp(195) && y > AndroidUtilities.dp(15))) {
                        colorPressed = true;
                        colorHSV[0] = Math.max(0.0f, Math.min(1.0f, (float) x / getMeasuredWidth())) * 360f;
                        colorHSV[1] = Math.max(0.0f, Math.min(1.0f, 1f - ((float) y / AndroidUtilities.dp(180))));
                        colorGradient = null;
                    } else if (!colorPressed && (brightnessPressed || y > AndroidUtilities.dp(195))) {
                        brightnessPressed = true;
                        colorHSV[2] = Math.max(0.0f, Math.min(1.0f, 1f - ((float)(x - AndroidUtilities.dp(21)) / (getMeasuredWidth() - AndroidUtilities.dp(42)) )));
                    }

                    if (colorPressed || brightnessPressed) {
                        if (editorDelegate != null) {
                            editorDelegate.onColorChanged(Color.HSVToColor(colorHSV));
                        }
                        invalidate();
                        unsaved = true;
                    }

                    return true;
                case MotionEvent.ACTION_UP:
                    colorPressed = false;
                    brightnessPressed = false;
                    break;
            }
            return super.onTouchEvent(event);
        }

        public void setColor(int color) {
            colorGradient = null;
            Color.colorToHSV(color, colorHSV);
            invalidate();
        }

        public int getColor() {
            return (Color.HSVToColor(colorHSV) & 0x00ffffff) | (255 << 24);
        }
    }

    private ColorPicker colorPicker;
    private FrameLayout bottomLayout;

    public ColorEditorAlert(final Context context) {
        super(context, true);

        containerView = new FrameLayout(context);
        containerView.setWillNotDraw(false);

        FrameLayout mainFrameLayout = new FrameLayout(context);
        containerView.addView(mainFrameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 298));

        colorPicker = new ColorPicker(context);
        mainFrameLayout.addView(colorPicker, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        bottomLayout = new FrameLayout(context);
        bottomLayout.setBackgroundColor(0xffffffff);
        mainFrameLayout.addView(bottomLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM));

        FrameLayout frameLayout = new FrameLayout(context);
        bottomLayout.addView(frameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.RIGHT, 21, 0, 21, 0));

        unsaved = false;

        TextView defaultButtom = new TextView(context);
        defaultButtom.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        defaultButtom.setTextColor(0xff19a7e8);
        defaultButtom.setGravity(Gravity.CENTER);
        defaultButtom.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_AUDIO_SELECTOR_COLOR, 0));
        defaultButtom.setText("CANCEL");
        defaultButtom.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        defaultButtom.setOnClickListener((ev)-> {
            dismiss();
        });
        frameLayout.addView(defaultButtom, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

        TextView saveButton = new TextView(context);
        saveButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        saveButton.setTextColor(0xff19a7e8);
        saveButton.setGravity(Gravity.CENTER);
        saveButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.ACTION_BAR_AUDIO_SELECTOR_COLOR, 0));
        saveButton.setText("APPLY");
        saveButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        saveButton.setOnClickListener((ev)-> {
            unsaved = false;
            dismiss();
            if (editorDelegate != null) {
                editorDelegate.onColorApplied(Color.HSVToColor(colorPicker.colorHSV));
            }
        });
        frameLayout.addView(saveButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.RIGHT));

        super.setOnHideListener((ev) -> {
            if (unsaved && editorDelegate != null) {
                unsaved = false;
                editorDelegate.onColorCanceled();
            }
        });
    }
}
