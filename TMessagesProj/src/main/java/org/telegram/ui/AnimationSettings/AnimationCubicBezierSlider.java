package org.telegram.ui.AnimationSettings;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class AnimationCubicBezierSlider extends FrameLayout {
    private Paint innerPaint1;
    private Paint outerPaint1;
    private Paint borderPaint1;
    private Paint backgroundPaint;
    private Paint cubicPaint;
    private Paint sliderPaint;
    private Path cubicPath;

    private Paint sliderTextPaint;
    private Paint borderTextPaint;

    private RectF leftSlider;
    private RectF rightSlider;

    final int lineHalfWidth = AndroidUtilities.dp(1);
    final int horizontalPadding = AndroidUtilities.dp(28);
    final int verticalPadding = AndroidUtilities.dp(45);
    final int thumbRadius = AndroidUtilities.dp(9f);
    final int sliderWidth = AndroidUtilities.dp(6.2f);
    final int sliderHeight = AndroidUtilities.dp(13.5f);
    final int bigBorderCircleRadius = AndroidUtilities.dp(5.5f);
    final int middleBorderCircleRadius = AndroidUtilities.dp(3.3f);
    final int smallBorderCircleRadius = AndroidUtilities.dp(1.4f);
    final int shadowRadius = AndroidUtilities.dp(3f);
    final int sensitivityRadius = AndroidUtilities.dp(38);

    float topSliderValue = 0.66f;
    float bottomSliderValue = 0.33f;

    float leftBorderValue = 0.1f;
    float rightBorderValue = 0.9f;

    int paddingLeft;
    int paddingRight;
    int paddingTop;
    int paddingBottom;

    int leftSliderX;
    int rightSliderX;
    int topSliderX;
    int bottomSliderX;

    int halfHeight;

    public AnimationCubicBezierSlider(Context context) {
        super(context);
        setWillNotDraw(false);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        innerPaint1 = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerPaint1.setColor(0xFFEBEDF0);

        cubicPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cubicPaint.setColor(0xFFEBEDF0);
        cubicPaint.setStyle(Paint.Style.STROKE);
        cubicPaint.setStrokeWidth(lineHalfWidth * 2);

        outerPaint1 = new Paint(Paint.ANTI_ALIAS_FLAG);
        outerPaint1.setColor(0xFF54AAEB);

        sliderTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        sliderTextPaint.setColor(0xFF54AAEB);
        sliderTextPaint.setStyle(Paint.Style.FILL);
        sliderTextPaint.setTextSize(AndroidUtilities.dp(16));
        sliderTextPaint.setTextAlign(Paint.Align.CENTER);

        borderTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderTextPaint.setColor(0xFFFFCD00);
        borderTextPaint.setStyle(Paint.Style.FILL);
        borderTextPaint.setTextSize(AndroidUtilities.dp(16));
        borderTextPaint.setTextAlign(Paint.Align.CENTER);

        borderPaint1 = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint1.setColor(0xFFFFCD00);

        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        sliderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        sliderPaint.setColor(0xFFFFFFFF);
        sliderPaint.setShadowLayer(shadowRadius, 0, 0, 0x20000000);

        cubicPath = new Path();

        leftSlider = new RectF();
        rightSlider = new RectF();

        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);

        updateDrawCoords();
    }

    int durationTime = 0;
    public void setDurationTime(int durationTime) {
        this.durationTime = durationTime;
        updateDrawCoords();
    }

    public void setAnimationParams(AnimationParams params) {
        topSliderValue = params.secondBezierPoint;
        bottomSliderValue = params.firstBezierPoint;
        leftBorderValue = params.startTimeСoefficient;
        rightBorderValue = params.endTimeСoefficient;
        updateDrawCoords();
    }

    public static class AnimationParams {
        float endTimeСoefficient;
        float startTimeСoefficient;
        float firstBezierPoint;
        float secondBezierPoint;

        AnimationParams(float start, float end, float first, float second) {
            startTimeСoefficient = start;
            endTimeСoefficient = end;
            firstBezierPoint = first;
            secondBezierPoint = second;
        }
    }

    AnimationCubicBezierSliderDelegate delegate;
    public interface AnimationCubicBezierSliderDelegate {
        void onChangeParams(AnimationParams params);
    }

    public void setDelegate(AnimationCubicBezierSliderDelegate delegate) {
        this.delegate = delegate;
    }


    private String topSliderText;
    private String bottomSliderText;
    private String startTimeText;
    private String endTimeText;

    private PointF topSliderTextCoords;
    private PointF bottomSliderTextCoords;
    private PointF startTimeTextCoords;
    private PointF endTimeTextCoords;

    private void updateDrawCoords() {
        paddingLeft = horizontalPadding;
        paddingRight = getMeasuredWidth() - horizontalPadding;
        paddingTop = verticalPadding;
        paddingBottom = getMeasuredHeight() - verticalPadding;

        leftSliderX = paddingLeft + (int)((paddingRight - paddingLeft) * (leftBorderValue));
        rightSliderX = paddingLeft + (int)((paddingRight - paddingLeft) * (rightBorderValue));

        topSliderX = leftSliderX + (int)((rightSliderX - leftSliderX) * (1f - topSliderValue));
        bottomSliderX = leftSliderX + (int)((rightSliderX - leftSliderX) * (bottomSliderValue));

        cubicPath.reset();
        cubicPath.moveTo(leftSliderX, paddingBottom);
        cubicPath.cubicTo(bottomSliderX, paddingBottom, topSliderX, paddingTop, rightSliderX, paddingTop);

        halfHeight = getMeasuredHeight() / 2;

        leftSlider.left = leftSliderX - sliderWidth;
        leftSlider.right = leftSliderX + sliderWidth;
        leftSlider.top = halfHeight - sliderHeight;
        leftSlider.bottom = halfHeight + sliderHeight;

        rightSlider.left = rightSliderX - sliderWidth;
        rightSlider.right = rightSliderX + sliderWidth;
        rightSlider.top = halfHeight - sliderHeight;
        rightSlider.bottom = halfHeight + sliderHeight;

        /* text coords */
        topSliderText = String.format("%.0f%%", topSliderValue * 100f);
        bottomSliderText = String.format("%.0f%%", bottomSliderValue * 100f);
        startTimeText = String.format("%d ms", (int)((float)durationTime * leftBorderValue));
        endTimeText = String.format("%d ms", (int)((float)durationTime * rightBorderValue));

        Rect sizeTop = getTextSize(topSliderText, sliderTextPaint);
        Rect sizeBottom = getTextSize(bottomSliderText, sliderTextPaint);
        Rect sizeLeft = getTextSize(startTimeText, borderTextPaint);
        Rect sizeRight = getTextSize(endTimeText, borderTextPaint);

        topSliderTextCoords = new PointF(
                topSliderX, paddingTop + sizeTop.height() / 2 - thumbRadius * 2.5f);

        bottomSliderTextCoords = new PointF(
                bottomSliderX, paddingBottom + sizeBottom.height() / 2 + thumbRadius * 2.5f);

        startTimeTextCoords = new PointF(
                leftSlider.right + thumbRadius + sizeLeft.width() / 2,
                leftSlider.centerY() + sizeLeft.height() / 2);

        endTimeTextCoords = new PointF(
                rightSlider.left - thumbRadius - sizeRight.width() / 2,
                rightSlider.centerY() + sizeRight.height() / 2);

        if ((rightSliderX - leftSliderX) < (sizeLeft.width() + sizeRight.width() + thumbRadius * 5)) {
            startTimeTextCoords.y -= thumbRadius * 1.25;
            endTimeTextCoords.y += thumbRadius * 1.25;
        }

    }

    public Rect getTextSize(String text, Paint paint) {
        Rect textBounds = new Rect();
        paint.getTextBounds(text, 0, text.length(), textBounds);
        return textBounds;
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawRect(paddingLeft, paddingTop - lineHalfWidth, paddingRight, paddingTop + lineHalfWidth, innerPaint1);
        canvas.drawRect(paddingLeft, paddingBottom - lineHalfWidth, paddingRight, paddingBottom + lineHalfWidth, innerPaint1);
        canvas.drawPath(cubicPath, cubicPaint);

        canvas.drawRect(topSliderX, paddingTop - lineHalfWidth, rightSliderX, paddingTop + lineHalfWidth, outerPaint1);
        canvas.drawRect(leftSliderX, paddingBottom - lineHalfWidth, bottomSliderX, paddingBottom + lineHalfWidth, outerPaint1);

        canvas.drawText(topSliderText, topSliderTextCoords.x, topSliderTextCoords.y, sliderTextPaint);
        canvas.drawText(bottomSliderText, bottomSliderTextCoords.x, bottomSliderTextCoords.y, sliderTextPaint);
        canvas.drawText(startTimeText, startTimeTextCoords.x, startTimeTextCoords.y, borderTextPaint);
        canvas.drawText(endTimeText, endTimeTextCoords.x, endTimeTextCoords.y, borderTextPaint);

        canvas.drawCircle(leftSliderX, paddingTop, bigBorderCircleRadius, backgroundPaint);
        canvas.drawCircle(leftSliderX, paddingBottom, bigBorderCircleRadius, backgroundPaint);
        canvas.drawCircle(rightSliderX, paddingTop, bigBorderCircleRadius, backgroundPaint);
        canvas.drawCircle(rightSliderX, paddingBottom, bigBorderCircleRadius, backgroundPaint);

        for (int i = 0; i < 20; i++) {
            int radius = (i == 0 || i == 19) ? middleBorderCircleRadius : smallBorderCircleRadius;
            int circleY = paddingTop + (int)((paddingBottom - paddingTop) * ((float) i / 19f));
            canvas.drawCircle(leftSliderX, circleY, radius, borderPaint1);
            canvas.drawCircle(rightSliderX, circleY, radius, borderPaint1);
        }

        canvas.drawCircle(topSliderX, paddingTop, thumbRadius, sliderPaint);
        canvas.drawCircle(bottomSliderX, paddingBottom, thumbRadius, sliderPaint);

        canvas.drawRoundRect(leftSlider, sliderWidth, sliderWidth, sliderPaint);
        canvas.drawRoundRect(rightSlider, sliderWidth, sliderWidth, sliderPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(245), MeasureSpec.EXACTLY));
        updateDrawCoords();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return onTouch(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return onTouch(event);
    }

    int targetCounter = 0;
    final int TARGET_NO_TARGET = targetCounter++;
    final int TARGET_LEFT_SLIDER = targetCounter++;
    final int TARGET_RIGHT_SLIDER = targetCounter++;
    final int TARGET_TOP_SLIDER = targetCounter++;
    final int TARGET_BOTTOM_SLIDER = targetCounter++;
    int currentTarget = TARGET_NO_TARGET;

    boolean checkCoord(int coord1, int coord2, int sensitivity) {
        return Math.abs(coord1 - coord2) < sensitivity;
    }

    boolean checkCoords(int x1, int y1, int x2, int y2, int sensitivity) {
        return checkCoord(x1, x2, sensitivity) && checkCoord(y1, y2, sensitivity);
    }

    float normailze(float value) {
        return borders(value, 0f, 1f);
    }

    float borders(float value, float border1, float border2) {
        return Math.max(border1, Math.min(border2, value));
    }

    boolean captured;
    float sx, sy;
    boolean onTouch(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            sx = ev.getX();
            sy = ev.getY();
            return true;
        } else if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
            captured = false;
            currentTarget = TARGET_NO_TARGET;

            if (delegate != null) {
                delegate.onChangeParams(new AnimationParams(leftBorderValue, rightBorderValue, bottomSliderValue, topSliderValue));
            }

        } else if (ev.getAction() == MotionEvent.ACTION_MOVE) {
            if (!captured) {
                final ViewConfiguration vc = ViewConfiguration.get(getContext());
                if (Math.abs(ev.getY() - sy) > vc.getScaledTouchSlop()) {
                    return false;
                }
                if (Math.abs(ev.getX() - sx) > vc.getScaledTouchSlop()) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                    int x = (int) ev.getX();
                    int y = (int) ev.getY();

                    if (checkCoord(paddingTop, y, sensitivityRadius)) {
                        currentTarget = TARGET_TOP_SLIDER;
                    } else if (checkCoord(paddingBottom, y, sensitivityRadius)) {
                        currentTarget = TARGET_BOTTOM_SLIDER;
                    } else if (checkCoords(leftSliderX, halfHeight, x, y, sensitivityRadius)) {
                        currentTarget = TARGET_LEFT_SLIDER;
                    } else if (checkCoords(rightSliderX, halfHeight, x, y, sensitivityRadius)) {
                        currentTarget = TARGET_RIGHT_SLIDER;
                    } else {
                        currentTarget = TARGET_NO_TARGET;
                    }

                    captured = true;
                }
            } else {
                int x = (int) ev.getX();
                if (currentTarget == TARGET_LEFT_SLIDER) {
                    leftBorderValue = borders((float)(x - paddingLeft) / (paddingRight - paddingLeft), 0f, rightBorderValue - 0.25f);
                } else if (currentTarget == TARGET_RIGHT_SLIDER) {
                    rightBorderValue = borders((float)(x - paddingLeft) / (paddingRight - paddingLeft), leftBorderValue + 0.25f, 1f);
                } else if (currentTarget == TARGET_TOP_SLIDER) {
                    topSliderValue = 1 - normailze((float)(x - leftSliderX) / (rightSliderX - leftSliderX));
                } else if (currentTarget == TARGET_BOTTOM_SLIDER) {
                    bottomSliderValue = normailze((float)(x - leftSliderX) / (rightSliderX - leftSliderX));
                } else {
                    return false;
                }

                updateDrawCoords();
                invalidate();
                return true;

            }
        }
        return false;
    }

}
