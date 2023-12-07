package org.telegram.ui.Components.effects;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLExt;
import android.opengl.GLES31;
import android.util.Log;

import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

public abstract class ShaderEffectThread extends DispatchQueue {
    private volatile boolean running = true;

    private final double MIN_DELTA;
    private final double MAX_DELTA;

    private final long MIN_DELTA_NS;
    private final long MAX_DELTA_NS;
    private final static long SLEEP_ACCURACY_NS = 800000;

    private final SurfaceTexture surfaceTexture;
    private final @Nullable Runnable invalidate;

    protected ShaderEffectThread(String name, SurfaceTexture surfaceTexture, @Nullable Runnable invalidate) {
        super(name);

        final int MAX_FPS = (int) AndroidUtilities.screenRefreshRate;
        MIN_DELTA = 1.0 / MAX_FPS;
        MAX_DELTA = MIN_DELTA * 4;

        MIN_DELTA_NS = (long) (MIN_DELTA * 1_000_000_000d);
        MAX_DELTA_NS = (long) (MAX_DELTA * 1_000_000_000d);

        this.surfaceTexture = surfaceTexture;
        this.invalidate = invalidate;
    }

    public final void halt() {
        running = false;
        if (getHandler() != null) {
            recycle();
        }
    }

    @Override
    public void run() {
        if (init()) {
            super.run();
        }
        die();
    }

    private void drawFramesLoop() {
        boolean needNextFrame = running;

        long loopTimeStart = System.currentTimeMillis();
        long minRenderTime = 1_000_000_000_000L;
        long maxRenderTime = 0;
        long totalRenderTime = 0;
        int frames = 0;

        double dt = MIN_DELTA;

        while (needNextFrame) {
            final long now = System.nanoTime();

            needNextFrame = drawFrame((float) dt);
            needNextFrame &= running;
            frames += 1;

            if (invalidate != null) {
                AndroidUtilities.cancelRunOnUIThread(this.invalidate);
                AndroidUtilities.runOnUIThread(this.invalidate);
            }

            final long renderTime = System.nanoTime() - now;
            minRenderTime = Math.min(minRenderTime, renderTime);
            maxRenderTime = Math.max(maxRenderTime, renderTime);
            totalRenderTime += renderTime;

            /*int fps = (int) Math.round(((double) 1_000_000_000 / renderTime));
            if (fps < 60) {
                Log.i("WTF_DEBUG", "Pizdotto! FPS: " + fps);
            }*/

            dt = (renderTime) / 1_000_000_000d;

            if (dt > MAX_DELTA) {
                dt = MAX_DELTA;
            } else if (dt < MIN_DELTA) {
                long wait = MIN_DELTA_NS - renderTime - SLEEP_ACCURACY_NS;
                if (wait > 0) {
                    try {
                        long milli = wait / 1_000_000;
                        int nano = (int) (wait % 1_000_000);
                        sleep(milli, nano);
                    } catch (Exception ignore) { }
                }
                // dt = MIN_DELTA;
                dt = (System.nanoTime() - now) / 1_000_000_000d;
            }
        }

        try {
            final long time = System.currentTimeMillis() - loopTimeStart;
            // Log.i("WTF_DEBUG", "Average FPS: " + Math.round(1000f / ((double) time / frames)) + " time: " + time + " frames: " + frames);
            Log.i("WTF_DEBUG", "Average FPS: " + Math.round(((double) 1_000_000_000 / ((double) totalRenderTime / frames)))
                + " | Min FPS: " + Math.round(((double) 1_000_000_000 / maxRenderTime))
                + " | Max FPS: " + Math.round(((double) 1_000_000_000 / minRenderTime))
            );
        } catch (Exception ignore) {
        }

    }


    private EGL10 egl;
    private EGLDisplay eglDisplay;
    private EGLSurface eglSurface;
    private EGLContext eglContext;

    private boolean init() {
        egl = (EGL10) javax.microedition.khronos.egl.EGLContext.getEGL();

        eglDisplay = egl.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (eglDisplay == egl.EGL_NO_DISPLAY) {
            halt();
            return false;
        }
        int[] version = new int[2];
        if (!egl.eglInitialize(eglDisplay, version)) {
            halt();
            return false;
        }

        int[] configAttributes = {EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR, EGL14.EGL_NONE};
        EGLConfig[] eglConfigs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        if (!egl.eglChooseConfig(eglDisplay, configAttributes, eglConfigs, 1, numConfigs)) {
            halt();
            return false;
        }
        EGLConfig eglConfig = eglConfigs[0];

        int[] contextAttributes = {EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE};
        eglContext = egl.eglCreateContext(eglDisplay, eglConfig, egl.EGL_NO_CONTEXT, contextAttributes);
        if (eglContext == null) {
            halt();
            return false;
        }

        eglSurface = egl.eglCreateWindowSurface(eglDisplay, eglConfig, surfaceTexture, null);
        if (eglSurface == null) {
            halt();
            return false;
        }

        if (!egl.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            halt();
            return false;
        }

        return onInit();
    }

    private boolean drawFrame(float dt) {
        if (!egl.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            halt();
            return false;
        }

        return onDrawFrame(dt);
    }

    private void die() {
        onDie();

        if (egl != null) {
            try {
                egl.eglMakeCurrent(eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
            } catch (Exception e) {
                FileLog.e(e);
            }
            try {
                egl.eglDestroySurface(eglDisplay, eglSurface);
            } catch (Exception e) {
                FileLog.e(e);
            }
            try {
                egl.eglDestroyContext(eglDisplay, eglContext);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        try {
            surfaceTexture.release();
        } catch (Exception e) {
            FileLog.e(e);
        }

        checkGlErrors();
    }


    protected abstract boolean onInit();

    protected abstract boolean onDrawFrame(float dt);

    protected abstract void onDie();

    protected final void startDrawFramesLoop() {
        if (isReady()) {
            postRunnable(this::drawFramesLoop);
        } else if (isAlive()) {
            AndroidUtilities.runOnUIThread(this::startDrawFramesLoop, 1000);
        }
    }

    protected final void eglSwapBuffers() {
        egl.eglSwapBuffers(eglDisplay, eglSurface);
        checkGlErrors();
    }

    protected final void checkGlErrors() {
        int err;
        while ((err = GLES31.glGetError()) != GLES31.GL_NO_ERROR) {
            FileLog.e("GlEs error: " + err);
        }
    }
}
