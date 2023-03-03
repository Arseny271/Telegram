package org.telegram.ui.Components.voip;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.TypedValue;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.utils.AnimationUtilities;
import org.telegram.ui.Components.HintView;

@SuppressLint("ViewConstructor")
public class VoIPHintView extends HintView {
    private final VoIPBackground background;
    private final Paint backgroundDarkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final BitmapShader darkShader;
    private final Matrix matrix = new Matrix();
    private final RectF bgRect = new RectF();
    private final Path trianglePath = new Path();

    public VoIPHintView (Context context, VoIPBackground background, boolean topArrow) {
        super(context, HintView.TYPE_VOIP, topArrow);
        setWillNotDraw(false);
        this.background = background;

        darkShader = new BitmapShader(background.renderer.getBitmap(VoIPBackground.COLOR_DARK), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        darkShader.setLocalMatrix(matrix);
        backgroundDarkPaint.setShader(darkShader);

        arrowImageView.setAlpha(0);
        textView.setBackground(null);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);

        if (topArrow) {
            trianglePath.moveTo(AndroidUtilities.dp(7.5f), 0); // Top
            trianglePath.lineTo(0, AndroidUtilities.dp(5f)); // Bottom left
            trianglePath.lineTo(AndroidUtilities.dp(15f), AndroidUtilities.dp(5f)); // Bottom right
            trianglePath.lineTo(AndroidUtilities.dp(7.5f), 0); // Back to Top
        } else {
            trianglePath.moveTo(AndroidUtilities.dp(7.5f), AndroidUtilities.dp(5f));
            trianglePath.lineTo(0, 0); // Bottom left
            trianglePath.lineTo(AndroidUtilities.dp(15f), 0);
            trianglePath.lineTo(AndroidUtilities.dp(7.5f), AndroidUtilities.dp(5f));
        }
        trianglePath.close();
    }

    public float getOffsetX () {
        return getX();
    }

    public float getOffsetY () {
        return getY();
    }

    public void updateMatrix () {
        float scaleW = (60f / (float) background.renderer.width);
        float scaleH = (80f / (float) background.renderer.height);
        float scale = 1.0f / Math.min(scaleW, scaleH);

        matrix.reset();
        matrix.postScale(scale, scale);
        matrix.postTranslate(-getOffsetX(), -getOffsetY());

        darkShader.setLocalMatrix(matrix);
    }

    public void updateLayout (float hasAnyVideo) {
        int alpha = (int) AnimationUtilities.fromTo(180f, 74f, hasAnyVideo);
        backgroundDarkPaint.setAlpha(alpha);
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        bgRect.set(textView.getX(), textView.getY(), textView.getX() + textView.getMeasuredWidth(), textView.getY() + textView.getMeasuredHeight());
        updateMatrix();

        canvas.drawRoundRect(bgRect, AndroidUtilities.dp(6), AndroidUtilities.dp(6), backgroundDarkPaint);

        canvas.save();
        canvas.translate(arrowImageView.getX() + arrowImageView.getMeasuredWidth() / 2f - AndroidUtilities.dp(7.5f),
                !isTopArrow ? bgRect.bottom: (bgRect.top - AndroidUtilities.dp(5f)));

        canvas.drawPath(trianglePath, backgroundDarkPaint);
        canvas.restore();


        super.dispatchDraw(canvas);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        updateMatrix();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        background.childViews.add(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        background.childViews.remove(this);
    }
}
