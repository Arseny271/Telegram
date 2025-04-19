package org.telegram.messenger.pip.source;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.pip.PipSourceContentView;
import org.telegram.messenger.pip.PipSourcePlaceholderView;
import org.telegram.messenger.pip.PipSource;
import org.telegram.messenger.pip.activity.IPipActivityAnimationListener;
import org.telegram.messenger.pip.activity.IPipActivityListener;
import org.telegram.messenger.pip.utils.Trigger;

public class PipSourceHandlerState2 implements IPipActivityListener, IPipActivityAnimationListener {

    /**
     * STATE_DETACHED
     * PhotoViewer is visible, default state
     */

    public static final int STATE_DETACHED = 0;


    /**
     * STATE_PRE_ATTACHED
     * PhotoViewer is still visible
     * Waits for the next render activity to ensure
     * that the placeholder bitmap is guaranteed to be rendered
     */

    public static final int STATE_PRE_ATTACHED = 1;


    /**
     * STATE_ATTACHED
     * PhotoViewer is hidden, PipContentView is ready
     */

    public static final int STATE_ATTACHED = 2;


    /**
     * STATE_PRE_DETACHED_1
     * PhotoViewer is still hidden
     * Waits for the next render activity to ensure
     * that the placeholder bitmap is guaranteed to be rendered
     */

    public static final int STATE_PRE_DETACHED_1 = 3;

    /**
     * STATE_PRE_DETACHED_2
     * PhotoViewer starts to show, waiting for the first render
     */

    public static final int STATE_PRE_DETACHED_2 = 4;



    private int state = STATE_DETACHED;



    final public Rect positionSource = new Rect();
    public final Rect position = new Rect();

    private PipSourceSnapshot contentBackground;
    private PipSourceSnapshot contentForeground;
    private Bitmap contentPlaceholder;



    private PipSourceContentView pictureInPictureWrapperView;
    public View pictureInPictureView;

    public PipSourcePlaceholderView pipSourcePlaceholder;

    public Bitmap pictureInPicturePlaceholder;

    private final PipSource source;

    public PipSourceHandlerState2(PipSource source) {
        this.source = source;
    }



    private void performPreAttach() {
        if (state != STATE_DETACHED) {
            throw new IllegalStateException("wtf");
        }

        source.params.getPosition(positionSource);

        Log.i("PIP_DEBUG", "[HANDLER] pre attach start " + positionSource);

        final int width = source.controller.activity.getWindow().getDecorView().getMeasuredWidth();
        final int height = source.controller.activity.getWindow().getDecorView().getMeasuredHeight();

        contentPlaceholder = source.delegate.pipCreatePrimaryWindowViewBitmap();

        pipSourcePlaceholder = source.placeholderView;

        contentBackground = new PipSourceSnapshot(width, height, source.delegate::pipRenderBackground);
        contentForeground = new PipSourceSnapshot(width, height, source.delegate::pipRenderForeground);

        pictureInPictureView = source.delegate.pipCreatePictureInPictureView();
        pictureInPictureWrapperView = new PipSourceContentView(source.controller.activity, this);
        pictureInPictureWrapperView.addView(pictureInPictureView);

        source.controller.getPipContentView()
            .addView(pictureInPictureWrapperView);

        if (pipSourcePlaceholder != null) {
            pipSourcePlaceholder.setPlaceholder(contentPlaceholder);
        }

        state = STATE_PRE_ATTACHED;

        // wait render activity placeholder
        pictureInPictureWrapperView.invalidate();
        AndroidUtilities.doOnPreDraw(pictureInPictureView, () -> {
            AndroidUtilities.runOnUIThread(this::performAttach);
        }, 300);

        Log.i("PIP_DEBUG", "[HANDLER] pre attach end");
    }

    private void performAttach() {
        if (state != STATE_PRE_ATTACHED) {
            throw new IllegalStateException("wtf");
        }

        Log.i("PIP_DEBUG", "[HANDLER] attach");

        source.delegate.pipHidePrimaryWindowView(Trigger.run(timeout -> {
            if (pipSourcePlaceholder != null) {
                pipSourcePlaceholder.setPlaceholder(null);
            }
            Log.i("PIP_DEBUG", "[HANDLER] on new source render first frame " + timeout);
        }, 400));

        state = STATE_ATTACHED;
    }

    private void performPreDetach1() {
        if (state != STATE_ATTACHED) {
            throw new IllegalStateException("wtf");
        }

        pictureInPicturePlaceholder = source.delegate.pipCreatePictureInPictureViewBitmap();
        state = STATE_PRE_DETACHED_1;

        pictureInPictureWrapperView.removeView(pictureInPictureView);
        pictureInPictureWrapperView.invalidate();
        pictureInPictureView = null;

        if (pipSourcePlaceholder != null) {
            pipSourcePlaceholder.setPlaceholder(pictureInPicturePlaceholder);
        }

        // wait render activity placeholder
        AndroidUtilities.doOnPreDraw(pictureInPictureWrapperView, () -> {
            AndroidUtilities.runOnUIThread(this::performPreDetach2);
        }, 300);

        Log.i("PIP_DEBUG", "[HANDLER] pre detach 1");
    }

    private void performPreDetach2() {
        if (state != STATE_PRE_DETACHED_1) {
            throw new IllegalStateException("wtf");
        }

        a = 2;

        source.delegate.pipShowPrimaryWindowView(Trigger.run(timeout -> {
            Log.i("PIP_DEBUG", "[HANDLER] on old source render first frame " + timeout);
            AndroidUtilities.runOnUIThread(() -> {
                a--;
                if (a == 0 && pictureInPicturePlaceholder != null) {
                    if (pipSourcePlaceholder != null) {
                        pipSourcePlaceholder.setPlaceholder(null);
                    }

                    pictureInPicturePlaceholder.recycle();
                    pictureInPicturePlaceholder = null;
                }
            });

        }, 400));
        pictureInPictureWrapperView.invalidate();
        state = STATE_PRE_DETACHED_2;

        // wait first render window
        AndroidUtilities.doOnPreDraw(source.contentView, () -> {
            AndroidUtilities.runOnUIThread(this::performDetach);
        }, 300);

        Log.i("PIP_DEBUG", "[HANDLER] pre detach 2");
    }

    private int a;

    private void performDetach() {
        if (state != STATE_PRE_DETACHED_2) {
            throw new IllegalStateException("wtf");
        }

        source.controller.getPipContentView()
            .removeView(pictureInPictureWrapperView);

        pictureInPictureView = null;
        pictureInPictureWrapperView = null;

        if (contentForeground != null) {
            contentForeground.release();
            contentForeground = null;
        }
        if (contentBackground != null) {
            contentBackground.release();
            contentBackground = null;
        }


        a--;
        if (a == 0 && pictureInPicturePlaceholder != null) {
            pictureInPicturePlaceholder.recycle();
            pictureInPicturePlaceholder = null;
            if (pipSourcePlaceholder != null) {
                pipSourcePlaceholder.setPlaceholder(null);
            }
        }

        if (contentPlaceholder != null) {
            contentPlaceholder.recycle();
            contentPlaceholder =  null;
        }

        state = STATE_DETACHED;

        Log.i("PIP_DEBUG", "[HANDLER] detach");

        AndroidUtilities.cancelRunOnUIThread(updateRunnable);
        doUpdate();
    }





    public void updatePositionViewRect(int width, int height, boolean isInPipMode) {
        if (isInPipMode) {
            position.set(0, 0, width, height);
        } else {
            position.set(positionSource);
        }
    }

    private float lastRadius;
    private final RectF rect = new RectF();
    private final Path path = new Path();

    private void rebuildPath(float radius) {
        if (lastRadius == radius) {
            return;
        }

        lastRadius = radius;
        rect.set(position);

        path.reset();
        path.addRoundRect(rect, radius, radius, Path.Direction.CW);
        path.close();
    }

    public void draw(Canvas canvas, Utilities.Callback<Canvas> content) {
        final float radius = source.cornerRadius * (1f - lastProgress);
        final boolean needClipCorners = radius > 1f;

        drawBackground(canvas);

        if (needClipCorners) {
            rebuildPath(radius);
            canvas.save();
            canvas.clipPath(path);
        }

        drawPlaceholder(canvas);
        content.run(canvas);
        drawForeground(canvas);

        if (needClipCorners) {
            canvas.restore();
        }
    }

    private void drawBackground(Canvas canvas) {
        contentBackground.draw(canvas, 1f);
    }

    private void drawPlaceholder(Canvas canvas) {
        final Bitmap bitmap = state == STATE_PRE_ATTACHED || state == STATE_ATTACHED ?
                contentPlaceholder : pictureInPicturePlaceholder;

        if (bitmap != null && !bitmap.isRecycled()) {
            canvas.drawBitmap(bitmap, null, position, null);
        }
    }

    private void drawForeground(Canvas canvas) {
        contentForeground.draw(canvas, 1f - lastProgress);
    }



    private float lastProgress;

    public boolean isAttachedToPip() {
        return state != STATE_DETACHED;
    }

    @Override
    public void onStartEnterToPip() {
        performPreAttach();
    }

    @Override
    public void onCompleteExitFromPip(boolean byActivityStop) {
        performPreDetach1();
    }

    @Override
    public void onTransitionAnimationFrame() {
        pictureInPictureWrapperView.invalidate();
    }

    @Override
    public void onTransitionAnimationProgress(float estimatedProgress) {
        lastProgress = estimatedProgress;
        pictureInPictureWrapperView.invalidate();
    }



    public void onReceiveMaxPriority() {
        source.controller.addPipListener(this);
        source.controller.addAnimationListener(this);

        isInMaxPriority = true;
        AndroidUtilities.cancelRunOnUIThread(updateRunnable);
        AndroidUtilities.runOnUIThread(updateRunnable, 750);
    }

    public void onLoseMaxPriority() {
        if (state == STATE_ATTACHED) {
            performPreDetach1();
        }
        source.controller.removePipListener(this);
        source.controller.removeAnimationListener(this);

        isInMaxPriority = false;
        AndroidUtilities.cancelRunOnUIThread(updateRunnable);
    }

    private boolean isInMaxPriority;
    private final Runnable updateRunnable = this::doUpdate;
    private void doUpdate() {
        if (isInMaxPriority && state == STATE_DETACHED) {
            source.invalidatePosition();
            AndroidUtilities.runOnUIThread(updateRunnable, 750);
        }
    }
}
