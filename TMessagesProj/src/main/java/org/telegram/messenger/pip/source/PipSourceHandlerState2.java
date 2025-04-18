package org.telegram.messenger.pip.source;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Picture;
import android.graphics.Rect;
import android.graphics.RenderNode;
import android.os.Build;
import android.util.Log;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.pip.PipSource;
import org.telegram.messenger.pip.activity.IPipActivityAnimationListener;
import org.telegram.messenger.pip.activity.IPipActivityListener;

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

    public Bitmap contentPlaceholder;
    public Picture contentPlaceholderBackgroundPicture;
    public Picture contentPlaceholderForegroundPicture;
    public RenderNode contentPlaceholderBackgroundNode;
    public RenderNode contentPlaceholderForegroundNode;

    public View pictureInPictureView;

    public Bitmap pictureInPicturePlaceholder;

    private final PipSource source;

    public PipSourceHandlerState2(PipSource source) {
        this.source = source;
    }




    private void performPreAttach() {
        if (state != STATE_DETACHED) {
            throw new IllegalStateException("wtf");
        }

        Log.i("PIP_DEBUG", "[HANDLER] pre attach start");

        source.params.getPosition(positionSource);

        final int width = source.controller.activity.getWindow().getDecorView().getMeasuredWidth();
        final int height = source.controller.activity.getWindow().getDecorView().getMeasuredHeight();

        contentPlaceholder = source.delegate.pipCreatePrimaryWindowViewBitmap();

        contentPlaceholderBackgroundPicture = new Picture();
        source.delegate.pipRenderBackground(contentPlaceholderBackgroundPicture.beginRecording(width, height));
        contentPlaceholderBackgroundPicture.endRecording();

        contentPlaceholderForegroundPicture = new Picture();
        source.delegate.pipRenderForeground(contentPlaceholderForegroundPicture.beginRecording(width, height));
        contentPlaceholderForegroundPicture.endRecording();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentPlaceholderBackgroundNode = new RenderNode("wtf-1");
            contentPlaceholderBackgroundNode.setPosition(0, 0, width, height);
            contentPlaceholderBackgroundNode.beginRecording().drawPicture(contentPlaceholderBackgroundPicture);
            contentPlaceholderBackgroundNode.endRecording();

            contentPlaceholderForegroundNode = new RenderNode("wtf-2");
            contentPlaceholderForegroundNode.setPosition(0, 0, width, height);
            contentPlaceholderForegroundNode.beginRecording().drawPicture(contentPlaceholderForegroundPicture);
            contentPlaceholderForegroundNode.endRecording();
        }

        pictureInPictureView = source.delegate.pipCreatePictureInPictureView();

        source.controller.pipContentView.addView(pictureInPictureView);
        source.controller.pipContentView.setState(this);

        state = STATE_PRE_ATTACHED;

        source.controller.pipContentView.invalidate();
        AndroidUtilities.doOnPreDraw(pictureInPictureView, () -> {
            AndroidUtilities.runOnUIThread(this::performAttach);
        }, 200);

        Log.i("PIP_DEBUG", "[HANDLER] pre attach end");
    }

    private void performAttach() {
        if (state != STATE_PRE_ATTACHED) {
            throw new IllegalStateException("wtf");
        }

        Log.i("PIP_DEBUG", "[HANDLER] attach");

        source.delegate.pipHidePrimaryWindowView();
        state = STATE_ATTACHED;
    }

    private void performPreDetach1() {
        if (state != STATE_ATTACHED) {
            throw new IllegalStateException("wtf");
        }

        pictureInPicturePlaceholder = source.delegate.pipCreatePictureInPictureViewBitmap();
        state = STATE_PRE_DETACHED_1;

        source.controller.pipContentView.removeView(pictureInPictureView);
        source.controller.pipContentView.invalidate();
        pictureInPictureView = null;

        AndroidUtilities.doOnPreDraw(source.controller.pipContentView, () -> {
            AndroidUtilities.runOnUIThread(this::performPreDetach2);
        }, 200);

        Log.i("PIP_DEBUG", "[HANDLER] pre detach 1");
    }

    private void performPreDetach2() {
        if (state != STATE_PRE_DETACHED_1) {
            throw new IllegalStateException("wtf");
        }

        source.delegate.pipShowPrimaryWindowView();
        source.controller.pipContentView.invalidate();
        state = STATE_PRE_DETACHED_2;

        AndroidUtilities.runOnUIThread(this::performDetach, 150);

        Log.i("PIP_DEBUG", "[HANDLER] pre detach 2");
    }

    private void performDetach() {
        if (state != STATE_PRE_DETACHED_2) {
            throw new IllegalStateException("wtf");
        }

        pictureInPictureView = null;
        contentPlaceholderBackgroundPicture = null;
        contentPlaceholderForegroundPicture = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (contentPlaceholderBackgroundNode != null) {
                contentPlaceholderBackgroundNode.discardDisplayList();
            }
            if (contentPlaceholderForegroundNode != null) {
                contentPlaceholderForegroundNode.discardDisplayList();
            }
        }

        if (pictureInPicturePlaceholder != null) {
            pictureInPicturePlaceholder.recycle();
            pictureInPicturePlaceholder = null;
        }

        if (contentPlaceholder != null) {
            contentPlaceholder.recycle();
            contentPlaceholder =  null;
        }

        state = STATE_DETACHED;
        source.controller.pipContentView.setState(null);
        source.controller.pipContentView.invalidate();

        Log.i("PIP_DEBUG", "[HANDLER] detach");
    }





    public void updatePositionViewRect(int width, int height, boolean isInPipMode) {
        if (isInPipMode) {
            position.set(0, 0, width, height);
        } else {
            position.set(positionSource);
        }
    }

    public void drawBackground(Canvas canvas) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (contentPlaceholderBackgroundNode != null) {
                canvas.drawRenderNode(contentPlaceholderBackgroundNode);
            }
        } else if (contentPlaceholderBackgroundPicture != null) {
            canvas.drawPicture(contentPlaceholderBackgroundPicture);
        }


        final Bitmap bitmap = state == STATE_PRE_ATTACHED || state == STATE_ATTACHED ?
            contentPlaceholder : pictureInPicturePlaceholder;

        if (bitmap != null && !bitmap.isRecycled()) {
            canvas.drawBitmap(bitmap, null, position, null);
        }
    }

    public void drawForeground(Canvas canvas) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (contentPlaceholderForegroundNode != null) {
                contentPlaceholderForegroundNode.setAlpha(1f - lastProgress);
                canvas.drawRenderNode(contentPlaceholderForegroundNode);
            }
        } else if (contentPlaceholderForegroundNode != null) {
            canvas.drawPicture(contentPlaceholderForegroundPicture);
        }
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
        source.controller.pipContentView.invalidate();
    }

    @Override
    public void onTransitionAnimationProgress(float estimatedProgress) {
        lastProgress = estimatedProgress;
        source.controller.pipContentView.invalidate();
    }

    public void onReceiveMaxPriority() {
        source.controller.addPipListener(this);
        source.controller.addAnimationListener(this);
    }

    public void onLoseMaxPriority() {
        if (state == STATE_ATTACHED) {
            performPreDetach1();
        }
        source.controller.removePipListener(this);
        source.controller.removeAnimationListener(this);
    }
}
