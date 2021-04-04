package org.telegram.ui.AnimationSettings;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;

import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.Paint.Shader;
import org.telegram.ui.Components.Paint.Utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.CountDownLatch;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

import static android.opengl.GLES20.GL_FLOAT;
import static android.opengl.GLES20.GL_TRIANGLES;

public class ChatBackgroundView extends TextureView {

    private static final String PAINT_BACKGROUND_VSH = "attribute vec4 a_position; void main() { gl_Position = a_position; }";
    private static final String PAINT_BACKGROUND_FSH = "precision highp float; uniform vec2 resolution; uniform float randSeed;uniform vec4 color1; uniform vec4 color2; uniform vec4 color3; uniform vec4 color4;uniform vec2 color1Pos; uniform vec2 color2Pos; uniform vec2 color3Pos; uniform vec2 color4Pos;float rand(vec2 co) {return fract(sin(dot(co.xy, vec2(12.9898,78.233) * (randSeed + 1.0))) * 43758.5453);}void main() {vec2 position = gl_FragCoord.xy / resolution.xy; float r = rand(position); position.y = 1.0 - position.y; float dp1 = distance(position, color1Pos); float dp2 = distance(position, color2Pos);float dp3 = distance(position, color3Pos);float dp4 = distance(position, color4Pos); float minD = min(dp1, min(dp2, min(dp3, dp4))); float p = 5.0; dp1 = pow(1.0 - (dp1 - minD), p); dp2 = pow(1.0 - (dp2 - minD), p);dp3 = pow(1.0 - (dp3 - minD), p);dp4 = pow(1.0 - (dp4 - minD), p);float dpt = dp1 + dp2 + dp3 + dp4; gl_FragColor = r * 0.03 + (color1 * dp1 / dpt) + (color2 * dp2 / dpt) + (color3 * dp3 / dpt) + (color4 * dp4 / dpt);}";


    private CanvasInternal internal;

    private boolean firstDrawSent;

    private int color;

    private boolean shuttingDown;

    private float width;
    private float height;
    private float colors[][] = {{0f, 0f, 0f, 0f}, {0f, 0f, 0f, 0f}, {0f, 0f, 0f, 0f}, {0f, 0f, 0f, 0f}};
    private int keyShift = 0;
    private final float keyPoints[][] = {
            {0.265f, 0.582f}, {0.176f,0.918f}, {1-0.585f,1f-0.164f}, {0.644f,0.755f},
            {1-0.265f,1-0.582f}, {1-0.176f,1-0.918f}, {0.585f,0.164f}, {1f-0.644f,1f-0.755f}
    };

    private float realColor1Pos[];
    private float realColor2Pos[];
    private float realColor3Pos[];
    private float realColor4Pos[];

    private float distColor1Pos[];
    private float distColor2Pos[];
    private float distColor3Pos[];
    private float distColor4Pos[];

    private float color1Pos[];
    private float color2Pos[];
    private float color3Pos[];
    private float color4Pos[];

    private float currentColor1Pos[];
    private float currentColor2Pos[];
    private float currentColor3Pos[];
    private float currentColor4Pos[];

    private float targetColor1Pos[];
    private float targetColor2Pos[];
    private float targetColor3Pos[];
    private float targetColor4Pos[];

    private float distanceCenter = 0;
    private float rotateOffset = 0;

    private BaseFragment fragment;

    public ChatBackgroundView(BaseFragment fragment) {
        super(fragment.getParentActivity());
        this.fragment = fragment;
        setOpaque(false);

        Log.i("Shader", "constructor");

        updateTargetColors();
        color1Pos = new float[] {targetColor1Pos[0], targetColor1Pos[1]};
        color2Pos = new float[] {targetColor2Pos[0], targetColor2Pos[1]};
        color3Pos = new float[] {targetColor3Pos[0], targetColor3Pos[1]};
        color4Pos = new float[] {targetColor4Pos[0], targetColor4Pos[1]};
        distColor1Pos = new float[] {targetColor1Pos[0], targetColor1Pos[1]};
        distColor2Pos = new float[] {targetColor2Pos[0], targetColor2Pos[1]};
        distColor3Pos = new float[] {targetColor3Pos[0], targetColor3Pos[1]};
        distColor4Pos = new float[] {targetColor4Pos[0], targetColor4Pos[1]};
        realColor1Pos = new float[] {targetColor1Pos[0], targetColor1Pos[1]};
        realColor2Pos = new float[] {targetColor2Pos[0], targetColor2Pos[1]};
        realColor3Pos = new float[] {targetColor3Pos[0], targetColor3Pos[1]};
        realColor4Pos = new float[] {targetColor4Pos[0], targetColor4Pos[1]};
        currentColor1Pos = new float[] {targetColor1Pos[0], targetColor1Pos[1]};
        currentColor2Pos = new float[] {targetColor2Pos[0], targetColor2Pos[1]};
        currentColor3Pos = new float[] {targetColor3Pos[0], targetColor3Pos[1]};
        currentColor4Pos = new float[] {targetColor4Pos[0], targetColor4Pos[1]};
        updateDistance();
        setColors(0xFFFFF6BF, 0xFF76A076, 0xFFF6E477, 0xFF316B4D);

        setSurfaceTextureListener(new SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                Log.i("Shader", "aviabl");
                if (surface == null || internal != null) {
                    return;
                }

                internal = new CanvasInternal(surface);
                internal.setBufferSize(width, height);
                internal.requestRender();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                if (internal == null) {
                    return;
                }

                Log.i("BGGL", "updated: " + height);

                internal.setBufferSize(width, height);
                internal.requestRender();
                internal.postRunnable(() -> {
                    if (internal != null) {
                        internal.requestRender();
                    }
                });
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                Log.i("Shader", "dest");
                if (internal == null) {
                    return true;
                }
                internal.finish();
                internal.shutdown();
                internal = null;
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });
    }

    public void setHeight(float height) {
        //this.height = height;
    }

    public void setResolution(float width, float height) {
        //this.width = width;
        //this.height = height;
    }

    public void setColor(int color, int number) {
        Log.i("Colors", String.format("Color #%08X", color) + " " + number);
        colors[number][0] = ((float) Color.red(color)) / 255f;
        colors[number][1] = ((float) Color.green(color)) / 255f;
        colors[number][2] = ((float) Color.blue(color)) / 255f;
        colors[number][3] = ((float) Color.alpha(color)) / 255f;
    }

    public void setColors(int color1, int color2, int color3, int color4) {
        setColor(color1, 0);
        setColor(color2, 1);
        setColor(color3, 2);
        setColor(color4, 3);
    };

    public void updateTargetColors() {
        targetColor1Pos = keyPoints[(keyShift) % 8];
        targetColor2Pos = keyPoints[(keyShift + 2) % 8];
        targetColor3Pos = keyPoints[(keyShift + 4) % 8];
        targetColor4Pos = keyPoints[(keyShift + 6) % 8];
        keyShift = (keyShift + 1) % 8;
    }

    ValueAnimator animator;

    public void rotate() {
        rotate("SendMessage");
    }

    public void rotate(String paramName) {
        updateTargetColors();
        currentColor1Pos = color1Pos;
        currentColor2Pos = color2Pos;
        currentColor3Pos = color3Pos;
        currentColor4Pos = color4Pos;

        if (animator != null) {
            animator.cancel();
        }

        animator = ValueAnimator.ofFloat(0, 1);
        fragment.getAnimationController().setAnimationSettings(animator, paramName);
        animator.addUpdateListener((animation) -> {
            float value = (Float) (animation.getAnimatedValue());
            color1Pos[0] = currentColor1Pos[0] + (targetColor1Pos[0] - currentColor1Pos[0]) * value;
            color1Pos[1] = currentColor1Pos[1] + (targetColor1Pos[1] - currentColor1Pos[1]) * value;
            color2Pos[0] = currentColor2Pos[0] + (targetColor2Pos[0] - currentColor2Pos[0]) * value;
            color2Pos[1] = currentColor2Pos[1] + (targetColor2Pos[1] - currentColor2Pos[1]) * value;
            color3Pos[0] = currentColor3Pos[0] + (targetColor3Pos[0] - currentColor3Pos[0]) * value;
            color3Pos[1] = currentColor3Pos[1] + (targetColor3Pos[1] - currentColor3Pos[1]) * value;
            color4Pos[0] = currentColor4Pos[0] + (targetColor4Pos[0] - currentColor4Pos[0]) * value;
            color4Pos[1] = currentColor4Pos[1] + (targetColor4Pos[1] - currentColor4Pos[1]) * value;
            updateDistance();
            if (internal != null) {
                internal.requestRender();
            }
        });
        animator.start();
    }

    public void setRotateOffset(float offset) {
        rotateOffset = offset;
        redraw();

        if (true) return;

        if (rotateOffset < 0) rotateOffset += (float)(Math.PI * 2);
        rotateOffset = rotateOffset % (float)(Math.PI * 2);

        if (Math.abs(rotateOffset - offset) < Math.PI) {
            animator = ValueAnimator.ofFloat(rotateOffset, offset);
        } else if (Math.abs(rotateOffset - (offset + (float)(Math.PI * 2))) < Math.PI) {
            animator = ValueAnimator.ofFloat(rotateOffset, offset + (float)(Math.PI * 2));
        } else if (Math.abs(rotateOffset - (offset - (float)(Math.PI * 2))) < Math.PI) {
            animator = ValueAnimator.ofFloat(rotateOffset, offset - (float)(Math.PI * 2));
        } else {return;}


        fragment.getAnimationController().setAnimationSettings(animator, "rotateOffset");
        animator.addUpdateListener((animation) -> {
            float value = (Float) (animation.getAnimatedValue());
            rotateOffset = value;
            redraw();
        });
        animator.start();
    }

    public void setDistanceCenter(float distanceCenter) {
        this.distanceCenter = distanceCenter;
        redraw();
    }

    void updateRotate() {
        float cs = (float) Math.cos(rotateOffset);
        float sn = (float) Math.sin(rotateOffset);

        realColor1Pos[0] = (distColor1Pos[0] - 0.5f) * cs - (distColor1Pos[1] - 0.5f) * sn + 0.5f;
        realColor1Pos[1] = (distColor1Pos[0] - 0.5f) * sn + (distColor1Pos[1] - 0.5f) * cs + 0.5f;
        realColor2Pos[0] = (distColor2Pos[0] - 0.5f) * cs - (distColor2Pos[1] - 0.5f) * sn + 0.5f;
        realColor2Pos[1] = (distColor2Pos[0] - 0.5f) * sn + (distColor2Pos[1] - 0.5f) * cs + 0.5f;
        realColor3Pos[0] = (distColor3Pos[0] - 0.5f) * cs - (distColor3Pos[1] - 0.5f) * sn + 0.5f;
        realColor3Pos[1] = (distColor3Pos[0] - 0.5f) * sn + (distColor3Pos[1] - 0.5f) * cs + 0.5f;
        realColor4Pos[0] = (distColor4Pos[0] - 0.5f) * cs - (distColor4Pos[1] - 0.5f) * sn + 0.5f;
        realColor4Pos[1] = (distColor4Pos[0] - 0.5f) * sn + (distColor4Pos[1] - 0.5f) * cs + 0.5f;
    }

    void updateDistance() {
        float length;

        float distance = (distanceCenter * 0.33f + 1f) / 2;

        length = (float) Math.sqrt(Math.pow(color1Pos[0] - 0.5, 2) + Math.pow(color1Pos[1] - 0.5, 2));
        distColor1Pos[0] = (color1Pos[0] - 0.5f) / (length + ((2 - length) - length) * distance) + 0.5f;
        distColor1Pos[1] = (color1Pos[1] - 0.5f) / (length + ((2 - length) - length) * distance) + 0.5f;

        length = (float) Math.sqrt(Math.pow(color2Pos[0] - 0.5, 2) + Math.pow(color2Pos[1] - 0.5, 2));
        distColor2Pos[0] = (color2Pos[0] - 0.5f) / (length + ((2 - length) - length) * distance) + 0.5f;
        distColor2Pos[1] = (color2Pos[1] - 0.5f) / (length + ((2 - length) - length) * distance) + 0.5f;

        length = (float) Math.sqrt(Math.pow(color3Pos[0] - 0.5, 2) + Math.pow(color3Pos[1] - 0.5, 2));
        distColor3Pos[0] = (color3Pos[0] - 0.5f) / (length + ((2 - length) - length) * distance) + 0.5f;
        distColor3Pos[1] = (color3Pos[1] - 0.5f) / (length + ((2 - length) - length) * distance) + 0.5f;

        length = (float) Math.sqrt(Math.pow(color4Pos[0] - 0.5, 2) + Math.pow(color4Pos[1] - 0.5, 2));
        distColor4Pos[0] = (color4Pos[0] - 0.5f) / (length + ((2 - length) - length) * distance) + 0.5f;
        distColor4Pos[1] = (color4Pos[1] - 0.5f) / (length + ((2 - length) - length) * distance) + 0.5f;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        setResolution(getMeasuredWidth(), getMeasuredHeight());
    }

    public void redraw() {
        if (internal == null) {
            return;
        }
        internal.requestRender();
    }

    public boolean onTouch(MotionEvent event) {
        if (event.getPointerCount() > 1) {
            return false;
        }
        if (internal == null || !internal.initialized) {
            return true;
        }
        return true;
    }

    public int getCurrentColor() {
        return color;
    }

    public void setColor(int value) {
        color = value;
    }

    public void shutdown() {
        shuttingDown = true;

        if (internal != null) {
            performInContext(() -> {
                internal.shutdown();
                internal = null;
            });
        }

        setVisibility(View.GONE);
    }

    Shader backgroundShader;

    int oldHeight = 0;

    private class CanvasInternal extends DispatchQueue {
        private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
        private static final int EGL_OPENGL_ES2_BIT = 4;
        private SurfaceTexture surfaceTexture;
        private EGL10 egl10;
        private EGLDisplay eglDisplay;
        private EGLContext eglContext;
        private EGLSurface eglSurface;
        private boolean initialized;

        private int bufferWidth;
        private int bufferHeight;

        public CanvasInternal(SurfaceTexture surface) {
            super("CanvasInternal");
            surfaceTexture = surface;
        }

        @Override
        public void run() {
            initialized = initGL();
            super.run();
        }

        private boolean initGL() {
            try {
                Log.i("Shader", "init");
                egl10 = (EGL10) EGLContext.getEGL();

                eglDisplay = egl10.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
                if (eglDisplay == EGL10.EGL_NO_DISPLAY) {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.e("eglGetDisplay failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                    }
                    finish();
                    return false;
                }

                int[] version = new int[2];
                if (!egl10.eglInitialize(eglDisplay, version)) {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.e("eglInitialize failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                    }
                    finish();
                    return false;
                }

                int[] configsCount = new int[1];
                EGLConfig[] configs = new EGLConfig[1];
                int[] configSpec = new int[]{
                        EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                        EGL10.EGL_RED_SIZE, 8,
                        EGL10.EGL_GREEN_SIZE, 8,
                        EGL10.EGL_BLUE_SIZE, 8,
                        EGL10.EGL_ALPHA_SIZE, 8,
                        EGL10.EGL_DEPTH_SIZE, 0,
                        EGL10.EGL_STENCIL_SIZE, 0,
                        EGL10.EGL_NONE
                };
                EGLConfig eglConfig;
                if (!egl10.eglChooseConfig(eglDisplay, configSpec, configs, 1, configsCount)) {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.e("eglChooseConfig failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                    }
                    finish();
                    return false;
                } else if (configsCount[0] > 0) {
                    eglConfig = configs[0];
                } else {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.e("eglConfig not initialized");
                    }
                    finish();
                    return false;
                }

                int[] attrib_list = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE};
                eglContext = egl10.eglCreateContext(eglDisplay, eglConfig, EGL10.EGL_NO_CONTEXT, attrib_list);
                if (eglContext == null) {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.e("eglCreateContext failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                    }
                    finish();
                    return false;
                }

                if (surfaceTexture instanceof SurfaceTexture) {
                    eglSurface = egl10.eglCreateWindowSurface(eglDisplay, eglConfig, surfaceTexture, null);
                } else {
                    finish();
                    return false;
                }

                if (eglSurface == null || eglSurface == EGL10.EGL_NO_SURFACE) {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.e("createWindowSurface failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                    }
                    finish();
                    return false;
                }
                if (!egl10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.e("eglMakeCurrent failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                    }
                    finish();
                    return false;
                }

                GLES20.glEnable(GLES20.GL_BLEND);
                GLES20.glDisable(GLES20.GL_DITHER);
                GLES20.glDisable(GLES20.GL_STENCIL_TEST);
                GLES20.glDisable(GLES20.GL_DEPTH_TEST);

                backgroundShader = new Shader(PAINT_BACKGROUND_VSH, PAINT_BACKGROUND_FSH, new String[]{"a_position"}, new String[]{"resolution", "randSeed", "color1", "color2", "color3", "color4", "color1Pos", "color2Pos", "color3Pos", "color4Pos"});


                Utils.HasGLError();
            } catch (Exception e) {
                return false;
            }
            return true;
        }

        private boolean setCurrentContext() {
            if (!initialized || eglSurface == null || egl10 == null) {
                return false;
            }

            try {
                if (!eglContext.equals(egl10.eglGetCurrentContext()) || !eglSurface.equals(egl10.eglGetCurrentSurface(EGL10.EGL_DRAW))) {
                    if (!egl10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                        return false;
                    }
                }
            } catch (Exception e) {
                return false;
            }
            return true;
        }

        private Runnable drawRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    if (!initialized || shuttingDown || backgroundShader == null || backgroundShader.getProgram() == 0) {
                        return;
                    }

                    FloatBuffer vertexData;
                    float[] vertices = {-1f, -1f, 1f, -1f, -1f, 1f, -1f, 1f, 1f, -1f, 1f, 1f};
                    vertexData = ByteBuffer.allocateDirect(vertices.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
                    vertexData.put(vertices);

                    setCurrentContext();

                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
                    GLES20.glViewport(0, 0, bufferWidth, bufferHeight);

                    width = getMeasuredWidth();
                    height = getMeasuredHeight();

                    if (oldHeight != bufferHeight) {
                        Log.i("BGGL", String.format("%dx%d %dx%d", (int) width, (int) height, bufferWidth, bufferHeight));
                        oldHeight = bufferHeight;
                    }

                    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
                    GLES20.glUseProgram(backgroundShader.getProgram());
                    GLES20.glUniform2f(backgroundShader.getUniform("resolution"), width, height);
                    GLES20.glUniform1f(backgroundShader.getUniform("randSeed"), 0f);

                    GLES20.glUniform4f(backgroundShader.getUniform("color1"), colors[0][0], colors[0][1], colors[0][2], colors[0][3]);
                    GLES20.glUniform4f(backgroundShader.getUniform("color2"), colors[1][0], colors[1][1], colors[1][2], colors[1][3]);
                    GLES20.glUniform4f(backgroundShader.getUniform("color3"), colors[2][0], colors[2][1], colors[2][2], colors[2][3]);
                    GLES20.glUniform4f(backgroundShader.getUniform("color4"), colors[3][0], colors[3][1], colors[3][2], colors[3][3]);

                    updateDistance();
                    updateRotate();

                    GLES20.glUniform2f(backgroundShader.getUniform("color1Pos"), realColor1Pos[0], realColor1Pos[1]);
                    GLES20.glUniform2f(backgroundShader.getUniform("color2Pos"), realColor2Pos[0], realColor2Pos[1]);
                    GLES20.glUniform2f(backgroundShader.getUniform("color3Pos"), realColor3Pos[0], realColor3Pos[1]);
                    GLES20.glUniform2f(backgroundShader.getUniform("color4Pos"), realColor4Pos[0], realColor4Pos[1]);


                    int aPositionLocation = GLES20.glGetAttribLocation(backgroundShader.getProgram(), "a_position");
                    vertexData.position(0);
                    GLES20.glVertexAttribPointer(aPositionLocation, 2, GL_FLOAT, false, 0, vertexData);
                    GLES20.glEnableVertexAttribArray(aPositionLocation);
                    GLES20.glDrawArrays(GL_TRIANGLES, 0, 6);


                    GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);

                    egl10.eglSwapBuffers(eglDisplay, eglSurface);
                    if (!firstDrawSent) {
                        firstDrawSent = true;
                    }
                } catch (Exception e) {

                }
            }
        };

        public void setBufferSize(int width, int height) {
            bufferWidth = width;
            bufferHeight = height;
        }

        public void requestRender() {
            postRunnable(() -> drawRunnable.run());
        }

        public void finish() {
            try {
                if (eglSurface != null) {
                    egl10.eglMakeCurrent(eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
                    egl10.eglDestroySurface(eglDisplay, eglSurface);
                    eglSurface = null;
                }
                if (eglContext != null) {
                    egl10.eglDestroyContext(eglDisplay, eglContext);
                    eglContext = null;
                }
                if (eglDisplay != null) {
                    egl10.eglTerminate(eglDisplay);
                    eglDisplay = null;
                }
            } catch (Exception e) {

            }
        }

        public void shutdown() {
            postRunnable(() -> {
                finish();
                Looper looper = Looper.myLooper();
                if (looper != null) {
                    looper.quit();
                }
            });
        }

        public Bitmap getTexture() {
            if (!initialized) {
                return null;
            }
            final CountDownLatch countDownLatch = new CountDownLatch(1);
            final Bitmap[] object = new Bitmap[1];
            try {
                countDownLatch.await();
            } catch (Exception e) {
                FileLog.e(e);
            }
            return object[0];
        }
    }

    public Bitmap getResultBitmap() {
        return internal != null ? internal.getTexture() : null;
    }

    public void performInContext(final Runnable action) {
        if (internal == null) {
            return;
        }

        internal.postRunnable(() -> {
            if (internal == null || !internal.initialized) {
                return;
            }

            internal.setCurrentContext();
            action.run();
        });
    }
}