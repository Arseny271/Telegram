package org.telegram.ui.Components.effects.sprayer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.SurfaceTexture;
import android.view.TextureView;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;

import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.HashSet;

public class SprayerEffectOverlayView extends FrameLayout implements SprayerEffectThread.Callback {
    private SprayerEffectThread thread;

    public SprayerEffectOverlayView(Context context) {
        super(context);

        TextureView textureView = new TextureView(context);
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                if (thread == null) {
                    thread = new SprayerEffectThread(getContext(), surface, getMeasuredWidth(), getMeasuredHeight(), SprayerEffectOverlayView.this);
                    for (SprayerBitmapState state : states) {
                        if (!thread.startAnimation(state)) {
                            clearAnimationStateImpl(state);
                        }
                    }
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
                if (thread != null) {
                    thread.updateSize(width, height);
                }
            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                if (thread != null) {
                    thread.halt();
                    thread = null;
                }
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
                if (!statesToClear.isEmpty()) {
                    for (SprayerBitmapState state : statesToClear) {
                        clearAnimationStateImpl(state);
                    }
                    statesToClear.clear();
                }
            }
        });
        textureView.setOpaque(false);
        addView(textureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    private final ArrayList<SprayerBitmapState> states = new ArrayList<>();
    private final HashSet<SprayerBitmapState> statesToClear = new HashSet<>();

    @UiThread
    public void startSprayAnimation(SprayerBitmapState state) {
        if (state.bitmap.isRecycled()) {
            return;
        }

        if (thread == null || thread.startAnimation(state)) {
            states.add(state);
            invalidate();
        }
    }

    @UiThread
    @Override
    public void onAnimationReady(SprayerBitmapState state) {
        statesToClear.add(state);
    }

    @Override
    public void onAnimationFinish(SprayerBitmapState state) {
        clearAnimationStateImpl(state);
    }

    private void clearAnimationStateImpl(SprayerBitmapState state) {
        state.recycle();
        states.remove(state);
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        for (SprayerBitmapState state : states) {
            canvas.drawBitmap(state.bitmap, state.pixels.left, state.pixels.top, null);
        }
        super.dispatchDraw(canvas);
    }
}
