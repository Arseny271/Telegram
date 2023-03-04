package org.telegram.ui.Components.voip;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.MotionBackgroundDrawable;

import java.util.ArrayList;

public class VoIPBackground extends View {
    protected ArrayList<View> childViews = new ArrayList<>();

    public static final int COLOR_DEFAULT = 0;
    public static final int COLOR_LIGHT = 1;
    public static final int COLOR_DARK = 2;

    public final BackgroundRenderer renderer;

    public VoIPBackground(Context context) {
        super(context);
        renderer = new BackgroundRenderer(this);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        renderer.setSize(getMeasuredWidth(), getMeasuredHeight());
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        boolean needInvalidate = renderer.update(stopWhenStableState);
        renderer.drawFullSize(canvas);
        if (needInvalidate && !hardStop || forceInvalidate) {
            invalidateInternal();
            forceInvalidate = false;
        }
    }

    private boolean stopWhenStableState = false;
    private boolean hardStop = false;
    private boolean forceInvalidate = false;

    public void setStopWhenStableState (boolean stopWhenStableState) {
        this.stopWhenStableState = stopWhenStableState;
        if (!stopWhenStableState) {
            invalidate();
        }
    }

    public void setHardStop (boolean hardStop) {
        this.hardStop = hardStop;
        if (!hardStop) {
            invalidate();
        }
    }

    protected void invalidateInternal() {
        for (int i = 0; i < childViews.size(); i++) {
            childViews.get(i).invalidate();
        }

        invalidate();
    }


    private float hasAnyVideoValue = 0f;

    public void setHasAnyVideoValue (float value) {
        if (this.hasAnyVideoValue == value) return;

        this.forceInvalidate = true;
        this.hasAnyVideoValue = value;
        this.renderer.setHasAnyVideoValue(value);
        invalidate();
    }



    private final MotionBackgroundDrawable blueVioletBackground = new MotionBackgroundDrawable(Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorBlueViolet1), Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorBlueViolet2), Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorBlueViolet3), Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorBlueViolet4), false);
    private final MotionBackgroundDrawable blueGreenBackground = new MotionBackgroundDrawable(Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorBlueGreen1), Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorBlueGreen2), Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorBlueGreen3), Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorBlueGreen4), false);
    private final MotionBackgroundDrawable greenBackground = new MotionBackgroundDrawable(Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorGreen1), Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorGreen2), Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorGreen3), Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorGreen4), false);
    private final MotionBackgroundDrawable orangeRedBackground = new MotionBackgroundDrawable(Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorOrangeRed1), Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorOrangeRed2), Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorOrangeRed3), Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorOrangeRed4), false);
    private final MotionBackgroundDrawable blueVioletBackgroundDark = new MotionBackgroundDrawable(Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorBlueVioletDark1), Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorBlueVioletDark2), Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorBlueVioletDark3), Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorBlueVioletDark4), false);
    private final MotionBackgroundDrawable blueGreenBackgroundDark = new MotionBackgroundDrawable(Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorBlueGreenDark1), Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorBlueGreenDark2), Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorBlueGreenDark3), Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorBlueGreenDark4), false);
    private final MotionBackgroundDrawable greenBackgroundDark = new MotionBackgroundDrawable(Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorGreenDark1), Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorGreenDark2), Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorGreenDark3), Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorGreenDark4), false);
    private final MotionBackgroundDrawable orangeRedBackgroundDark = new MotionBackgroundDrawable(Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorOrangeRedDark1), Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorOrangeRedDark2), Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorOrangeRedDark3), Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorOrangeRedDark4), false);
    private final MotionBackgroundDrawable blueVioletBackgroundLight = new MotionBackgroundDrawable(Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorBlueVioletLight1), Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorBlueVioletLight2), Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorBlueVioletLight3), Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorBlueVioletLight4), false);
    private final MotionBackgroundDrawable blueGreenBackgroundLight = new MotionBackgroundDrawable(Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorBlueGreenLight1), Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorBlueGreenLight2), Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorBlueGreenLight3), Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorBlueGreenLight4), false);
    private final MotionBackgroundDrawable greenBackgroundLight = new MotionBackgroundDrawable(Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorGreenLight1), Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorGreenLight2), Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorGreenLight3), Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorGreenLight4), false);
    private final MotionBackgroundDrawable orangeRedBackgroundLight = new MotionBackgroundDrawable(Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorOrangeRedLight1), Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorOrangeRedLight2), Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorOrangeRedLight3), Theme.getColor(Theme.key_voipgroup_gradientBackgroundColorOrangeRedLight4), false);

    private final MotionBackgroundTrio blueVioletBackgroundTrio = new MotionBackgroundTrio(blueVioletBackground, blueVioletBackgroundLight, blueVioletBackgroundDark);
    private final MotionBackgroundTrio blueGreenBackgroundTrio = new MotionBackgroundTrio(blueGreenBackground, blueGreenBackgroundLight, blueGreenBackgroundDark);
    private final MotionBackgroundTrio greenBackgroundTrio = new MotionBackgroundTrio(greenBackground, greenBackgroundLight, greenBackgroundDark);
    private final MotionBackgroundTrio orangeRedBackgroundTrio = new MotionBackgroundTrio(orangeRedBackground, orangeRedBackgroundLight, orangeRedBackgroundDark);

    @SuppressLint("ViewConstructor")
    public static class BackgroundedView extends FrameLayout {
        private final BitmapShader lightShader;
        private final BitmapShader darkShader;
        protected final Paint backgroundLightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        protected final Paint backgroundDarkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final VoIPBackground background;
        private final Matrix matrix = new Matrix();

        public BackgroundedView(@NonNull Context context, VoIPBackground background) {
            super(context);
            this.background = background;

            lightShader = new BitmapShader(background.renderer.getBitmap(COLOR_LIGHT), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            darkShader = new BitmapShader(background.renderer.getBitmap(COLOR_DARK), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            lightShader.setLocalMatrix(matrix);
            darkShader.setLocalMatrix(matrix);
            backgroundLightPaint.setShader(lightShader);
            backgroundDarkPaint.setShader(darkShader);
        }

        public float getOffsetX () {
            return getX();
        }

        public float getOffsetY () {
            return getY();
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            updateMatrix();
        }

        public void updateMatrix () {
            float scaleW = (60f / (float) background.renderer.width);
            float scaleH = (80f / (float) background.renderer.height);
            float scale = 1.0f / Math.min(scaleW, scaleH);

            matrix.reset();
            matrix.postScale(scale, scale);
            matrix.postTranslate(-getOffsetX(), -getOffsetY());

            lightShader.setLocalMatrix(matrix);
            darkShader.setLocalMatrix(matrix);
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

    public class BackgroundRenderer {
        private final BitmapShader[] bitmapShaders = new BitmapShader[3];
        private final Bitmap[] currentBitmaps = new Bitmap[3];
        private final Canvas[] currentCanvases = new Canvas[3];
        private final Paint[] gradientPaints = new Paint[3];

        private final Paint blackPaint = new Paint();
        private final Paint whitePaint = new Paint();

        private final VoIPBackground parent;
        private final BackgroundAnimationCycle backgroundInitCycle = new BackgroundAnimationCycle(new MotionBackgroundTrio[]{ blueVioletBackgroundTrio, blueGreenBackgroundTrio });
        private final BackgroundAnimationCycle backgroundConnectedCycle = new BackgroundAnimationCycle(new MotionBackgroundTrio[]{ greenBackgroundTrio, blueGreenBackgroundTrio, blueVioletBackgroundTrio, blueGreenBackgroundTrio });
        private final BackgroundAnimationCycle backgroundProblemsCycle = new BackgroundAnimationCycle(new MotionBackgroundTrio[]{ orangeRedBackgroundTrio });
        private final Path clipPath = new Path();

        private ValueAnimator problemsAnimator;
        private float connectedAnimatorValue = 0f;
        private float problemsAnimatorValue = 0f;
        private float connectedExpandStartX;
        private float connectedExpandStartY;
        private boolean problemsState = false;
        private boolean connected = false;
        public int width, height;

        private final Matrix matrix = new Matrix();
        private int offsetX, offsetY;

        public BackgroundRenderer (VoIPBackground parent) {
            this.parent = parent;

            for (int i = 0; i < 3; i++) {
                currentBitmaps[i] = Bitmap.createBitmap(60, 80, Bitmap.Config.ARGB_8888);
                currentCanvases[i] = new Canvas(currentBitmaps[i]);
                bitmapShaders[i] = new BitmapShader(currentBitmaps[i], Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                gradientPaints[i] = new Paint(Paint.ANTI_ALIAS_FLAG);
                gradientPaints[i].setShader(bitmapShaders[i]);
                gradientPaints[i].setColor(Color.WHITE);
            }

            blackPaint.setColor(Color.BLACK);
            whitePaint.setColor(Color.WHITE);
        }

        public BitmapShader getBitmapShader(int type) {
            return bitmapShaders[type];
        }

        public Bitmap getBitmap (int type) {
            return currentBitmaps[type];
        }

        public Paint getGradientPaint(int type, int alpha) {
            gradientPaints[type].setAlpha(alpha);
            return gradientPaints[type];
        }

        public void setTranslation (int x, int y) {
            this.offsetX = x;
            this.offsetY = y;
        }

        public void updateMatrix () {
            for (int i = 0; i < 3; i++) {
                float scaleW = (60 / (float) width);
                float scaleH = (80 / (float) height);
                float scale = 1.0f / Math.min(scaleW, scaleH);

                matrix.reset();
                matrix.postScale(scale, scale);
                matrix.postTranslate(-offsetX, -offsetY);
                bitmapShaders[i].setLocalMatrix(matrix);
            }
        }

        public void showProblems(boolean problemsState) {
            if (this.problemsState == problemsState) return;
            if (problemsAnimator != null) {
                problemsAnimator.cancel();
            }

            problemsAnimator = ValueAnimator.ofFloat(problemsAnimatorValue, problemsState ? 1: 0);
            problemsAnimator.setDuration(300);
            problemsAnimator.addUpdateListener(this::onAnimationProblemsUpdate);
            problemsAnimator.start();

            this.problemsState = problemsState;
        }

        public void showConnected(float x, float y) {
            showProblems(false);
            if (connected) return;

            connectedExpandStartX = x;
            connectedExpandStartY = y;

            ValueAnimator connectedAnimator = ValueAnimator.ofFloat(0, 1);
            connectedAnimator.setDuration(450);
            connectedAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            connectedAnimator.addUpdateListener(this::onAnimationConnectedUpdate);
            connectedAnimator.start();
            connected = true;
        }

        private void onAnimationProblemsUpdate(ValueAnimator valueAnimator) {
            problemsAnimatorValue = (float) valueAnimator.getAnimatedValue();
            parent.forceInvalidate = true;
            parent.invalidate();
            checkAvatarScale();
        }

        private void onAnimationConnectedUpdate(ValueAnimator valueAnimator) {
            connectedAnimatorValue = (float) valueAnimator.getAnimatedValue();
            parent.forceInvalidate = true;
            parent.invalidate();
            checkAvatarScale();
        }

        private Runnable onUpdateAvatarScale;

        private float currentAvatarScale = 1f;
        private float currentAcceptButtonScale = 1f;

        public void setOnUpdateAvatarScaleListener (Runnable onUpdateAvatarScale) {
            this.onUpdateAvatarScale = onUpdateAvatarScale;
        }

        private void checkAvatarScale () {
            float value1 = Math.min(connectedAnimatorValue, 1f - problemsAnimatorValue);
            float value2 = CubicBezierInterpolator.DEFAULT.getInterpolation(Math.min((backgroundInitCycle.progress / 0.2f) % 1f, 1f - Math.max(problemsAnimatorValue, connectedAnimatorValue)));
            float scale1 = 1f + (1f - Math.abs((value1 - 0.5f) * 2f)) * 0.15f;
            float scale2 = 1f + (1f - Math.abs((value2 - 0.5f) * 2f)) * 0.075f;
            float scale = Math.max(scale1, scale2);

            currentAcceptButtonScale = scale2;
            if (scale != currentAvatarScale) {
                currentAvatarScale = scale;
                if (onUpdateAvatarScale != null) {
                    onUpdateAvatarScale.run();
                }
            }
        }

        public float getAcceptButtonWavesScale () {
            return currentAcceptButtonScale;
        }

        public float getAvatarScale () {
            return currentAvatarScale;
        }


        public void setSize (int width, int height) {
            this.width = width;
            this.height = height;

            backgroundInitCycle.setSize(width, height);
            backgroundConnectedCycle.setSize(width, height);
            backgroundProblemsCycle.setSize(width, height);
        }



        private long lastUpdateTime;
        public boolean update (boolean stopWhenStableState) {     // call update to update finished background
            long newTime = SystemClock.elapsedRealtime();
            long dt = newTime - lastUpdateTime;
            if (dt > 20) {
                dt = 17;
            }
            lastUpdateTime = newTime;
            if (dt <= 1) {
                return false;
            }

            boolean needInvalidate = false;

            if (problemsAnimatorValue != 1f) {
                blueVioletBackgroundTrio.update();
                blueGreenBackgroundTrio.update();
                if (connected) {
                    greenBackgroundTrio.update();
                    needInvalidate = backgroundConnectedCycle.update(dt, stopWhenStableState);
                } else {
                    needInvalidate = backgroundInitCycle.update(dt, stopWhenStableState);
                }
            }
            if (problemsAnimatorValue != 0f) {
                orangeRedBackgroundTrio.update();
                needInvalidate |= backgroundProblemsCycle.update(dt, stopWhenStableState);
            }

            for (int i = 0; i < 3; i++) {
                drawToBitmap(currentCanvases[i], i);
            }

            checkAvatarScale();

            return needInvalidate;
        }

        public void drawFullSize (Canvas canvas) {
            if (problemsAnimatorValue != 1f) {
                if (connectedAnimatorValue != 1f) {
                    backgroundInitCycle.drawFullSize(canvas, COLOR_DEFAULT, 1f);
                    if (connectedAnimatorValue > 0f) {
                        if (connectedAnimatorValue < 1f) {
                            float endRadius = width + height;
                            float radius = AndroidUtilities.dp(32) + (endRadius - AndroidUtilities.dp(32)) * connectedAnimatorValue;
                            clipPath.reset();
                            clipPath.addCircle(connectedExpandStartX,connectedExpandStartY,radius, Path.Direction.CW);
                            canvas.save();
                            canvas.clipPath(clipPath);
                        }
                        backgroundConnectedCycle.drawFullSize(canvas, COLOR_DEFAULT, 1f);
                        if (connectedAnimatorValue < 1f) {
                            canvas.restore();
                        }
                    }
                } else {
                    backgroundConnectedCycle.drawFullSize(canvas, COLOR_DEFAULT, 1f);
                }
            }

            if (problemsAnimatorValue > 0f) {
                backgroundProblemsCycle.drawFullSize(canvas, COLOR_DEFAULT, problemsAnimatorValue);
            }
        }

        private float hasAnyVideoValue = 0f;

        public void setHasAnyVideoValue (float value) {
            if (hasAnyVideoValue == value) return;

            hasAnyVideoValue = value;
            blackPaint.setAlpha((int)(255f * value));
            whitePaint.setAlpha((int)(255f * value));
        }

        private void drawToBitmap (Canvas canvas, int type) {
            final Paint bgPaint = type == VoIPBackground.COLOR_DARK ? blackPaint: whitePaint;
            /*if (type == VoIPBackground.COLOR_DARK && hasAnyVideoValue != 1f && hasAnyVideoValue != 0f) {
                Log.i("BACKGROUND_RENDER", String.format("%d %d %f %b", type, bgPaint.getAlpha(), hasAnyVideoValue, hasAnyVideoValue != 0));
            }*/

            if (hasAnyVideoValue != 1f || type == VoIPBackground.COLOR_DEFAULT) {
                if (problemsAnimatorValue != 1f) {
                    if (connectedAnimatorValue != 1f) {
                        backgroundInitCycle.drawToBitmap(canvas, type, 1f);
                        if (connectedAnimatorValue > 0f) {
                            backgroundConnectedCycle.drawToBitmap(canvas, type, connectedAnimatorValue);
                        }
                    } else {
                        backgroundConnectedCycle.drawToBitmap(canvas, type, 1f);
                    }
                }
                if (problemsAnimatorValue > 0f) {
                    backgroundProblemsCycle.drawToBitmap(canvas, type, problemsAnimatorValue);
                }
                if (hasAnyVideoValue > 0f && type != VoIPBackground.COLOR_DEFAULT) {
                    canvas.drawRect(0, 0, 60, 80, bgPaint);
                }
            } else {
                canvas.drawRect(0, 0, 60, 80, bgPaint);
            }
        }
    }

    private static class BackgroundAnimationCycle {
        private final BitmapShader[] bitmapShaders = new BitmapShader[3];
        private final Bitmap[] currentBitmaps = new Bitmap[3];
        private final Canvas[] currentCanvases = new Canvas[3];
        private final Paint[] gradientPaints = new Paint[3];
        private float width, height;

        private final MotionBackgroundTrio[] cycle;
        private float progress = 0f;

        private final Matrix matrix = new Matrix();

        public BackgroundAnimationCycle (MotionBackgroundTrio[] cycle) {
            for (int i = 0; i < 3; i++) {
                currentBitmaps[i] = Bitmap.createBitmap(60, 80, Bitmap.Config.ARGB_8888);
                currentCanvases[i] = new Canvas(currentBitmaps[i]);
                bitmapShaders[i] = new BitmapShader(currentBitmaps[i], Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                gradientPaints[i] = new Paint(Paint.ANTI_ALIAS_FLAG);
                gradientPaints[i].setShader(bitmapShaders[i]);
                gradientPaints[i].setColor(Color.WHITE);
            }

            this.cycle = cycle;
        }

        // call update to switch colors
        private boolean isStableStateStop = false;

        public boolean update (long dt, boolean stopWhenStableState) {
            if (stopWhenStableState && isStableStateStop) return false;
            if (!stopWhenStableState) {
                isStableStateStop = false;
            }

            float oldProgress = progress;

            progress += ((float) dt) / (6000 * cycle.length);
            if (progress >= 1f) {
                progress %= 1f;
            }

            if (stopWhenStableState) {
                float s = 1f / (float) cycle.length;
                if ((oldProgress % s) > (progress % s)) {
                    progress = (float) (Math.floor(progress / s) * s);
                    isStableStateStop = true;
                }
            }

            for (int i = 0; i < 3; i++) {
                drawToBitmap(currentCanvases[i], i, 1f);
            }

            return !isStableStateStop;
        }

        public void setSize (int width, int height) {
            this.width = width;
            this.height = height;

            float scaleW = (60f / width);
            float scaleH = (80f / height);
            float scale = 1.0f / Math.min(scaleW, scaleH);

            matrix.reset();
            matrix.postScale(scale, scale);
            for (int i = 0; i < 3; i++) {
                bitmapShaders[i].setLocalMatrix(matrix);
            }
        }

        public void drawFullSize (Canvas canvas, int type, float alpha) {
            gradientPaints[type].setAlpha((int)(255f * alpha));
            canvas.drawRect(0, 0, width, height, gradientPaints[type]);
        }

        private void drawToBitmap(Canvas canvas, int type, float alpha) {
            if (progress != 1f && cycle.length > 1) {
                float part = 1f / cycle.length;
                int i = (int) (progress / part);
                cycle[i].draw(canvas, type, alpha);
                float a = (progress - i * part) / part;
                if (a > 0f) {
                    cycle[(i + 1) % cycle.length].draw(canvas, type, alpha * a);
                }
            } else {
                cycle[0].draw(canvas, type, alpha);
            }
        }
    }

    private static class MotionBackgroundTrio {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final MotionBackgroundDrawable defaultD;
        private final MotionBackgroundDrawable light;
        private final MotionBackgroundDrawable dark;

        public MotionBackgroundTrio (MotionBackgroundDrawable defaultD, MotionBackgroundDrawable light, MotionBackgroundDrawable dark) {
            this.defaultD = defaultD;
            this.light = light;
            this.dark = dark;

            defaultD.setIndeterminateAnimation(true);
            light.setIndeterminateAnimation(true);
            dark.setIndeterminateAnimation(true);
            defaultD.rotatePreview(true);
            light.rotatePreview(true);
            dark.rotatePreview(true);
        }

        // call update to rotate
        public void update () {
            defaultD.updateAnimation(false);
            light.updateAnimation(false);
            dark.updateAnimation(false);
        }

        public Bitmap bitmap (int type) {
            if (type == COLOR_DEFAULT) {
                return defaultD.getBitmap();
            } else if (type == COLOR_LIGHT) {
                return light.getBitmap();
            } else {
                return dark.getBitmap();
            }
        }

        public void draw (Canvas canvas, int type, float alpha) {
            paint.setAlpha((int) (255f * alpha));
            canvas.drawBitmap(bitmap(type), 0, 0, paint);
        }
    }
}
