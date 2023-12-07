package org.telegram.ui.Components.effects.sprayer;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLES31;
import android.util.Log;

import androidx.annotation.UiThread;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.utils.GlUtils;
import org.telegram.ui.Components.effects.ShaderEffectThread;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class SprayerEffectThread extends ShaderEffectThread {
    private static int threadNum = 0;
    private final Context context;
    private final Callback callback;

    public interface Callback {
        void invalidate();

        void onAnimationReady(SprayerBitmapState state);

        void onAnimationFinish(SprayerBitmapState state);
    }

    private int width;
    private int height;

    public SprayerEffectThread(Context context, SurfaceTexture surfaceTexture, int width, int height, Callback callback) {
        super("SprayerThread-" + (threadNum++), surfaceTexture, callback::invalidate);
        this.context = context;
        this.callback = callback;
        this.width = width;
        this.height = height;
    }

    @Override
    protected boolean onInit() {
        if (!compileShadersAndLinkProgram()) {
            halt();
            return false;
        }

        GLES31.glViewport(0, 0, width, height);
        GLES31.glEnable(GLES31.GL_BLEND);
        GLES31.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES31.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES31.glUseProgram(programHandler_program);

        getUniformLocations();
        setUniformsOnInit();

        return true;
    }




    /* * */

    private int shaderHandler_vertex;
    private int shaderHandler_fragment;
    private int programHandler_program;


    private boolean compileShadersAndLinkProgram() {
        shaderHandler_fragment = GlUtils.compileShader(GLES31.GL_FRAGMENT_SHADER, R.raw.sprayer_fragment);
        shaderHandler_vertex = GlUtils.compileShader(GLES31.GL_VERTEX_SHADER, R.raw.sprayer_vertex);
        if (shaderHandler_fragment == 0 || shaderHandler_vertex == 0) {
            return false;
        }

        programHandler_program = GLES31.glCreateProgram();
        if (programHandler_program == 0) {
            return false;
        }

        GLES31.glAttachShader(programHandler_program, shaderHandler_fragment);
        GLES31.glAttachShader(programHandler_program, shaderHandler_vertex);

        String[] feedbackVaryings = {"feedbackNoise"};
        GLES31.glTransformFeedbackVaryings(programHandler_program, feedbackVaryings, GLES31.GL_INTERLEAVED_ATTRIBS);

        int[] status = new int[1];

        GLES31.glLinkProgram(programHandler_program);
        GLES31.glGetProgramiv(programHandler_program, GLES31.GL_LINK_STATUS, status, 0);
        if (status[0] == 0) {
            FileLog.e("SprayerEffect2, link draw program error: " + GLES31.glGetProgramInfoLog(programHandler_program));
            return false;
        }

        return true;
    }


    /* * */

    private int uniformHandler_textureSampler;
    private int uniformHandler_viewportSize;
    private int uniformHandler_imagePositionAndSize;
    private int uniformHandler_progress;
    private int uniformHandler_pointSize;
    private int uniformHandler_density;
    private int uniformHandler_reset;

    private void getUniformLocations() {
        uniformHandler_textureSampler = GLES31.glGetUniformLocation(programHandler_program, "textureSampler");
        uniformHandler_viewportSize = GLES31.glGetUniformLocation(programHandler_program, "viewportSize");
        uniformHandler_imagePositionAndSize = GLES31.glGetUniformLocation(programHandler_program, "imagePositionAndSize");
        uniformHandler_progress = GLES31.glGetUniformLocation(programHandler_program, "progress");
        uniformHandler_pointSize = GLES31.glGetUniformLocation(programHandler_program, "pointSize");
        uniformHandler_density = GLES31.glGetUniformLocation(programHandler_program, "density");
        uniformHandler_reset = GLES31.glGetUniformLocation(programHandler_program, "reset");
    }

    private void setUniformsOnInit() {
        GLES31.glUniform2f(uniformHandler_viewportSize, width, height);
        GLES31.glUniform1i(uniformHandler_textureSampler, 1);
        GLES31.glUniform1f(uniformHandler_density, AndroidUtilities.density);
    }



    private final Object resizeLock = new Object();
    private boolean resize;

    public void updateSize(int width, int height) {
        synchronized (resizeLock) {
            if (this.width != width || this.height != height) {
                this.width = width;
                this.height = height;
                resize = true;
            }
        }
    }

    private boolean checkResize() {
        synchronized (resizeLock) {
            if (resize) {
                GLES31.glUniform2f(uniformHandler_viewportSize, width, height);
                GLES31.glViewport(0, 0, width, height);
                resize = false;
                return true;
            }
            return false;
        }
    }


    /* * */

    private static final float timeScale = .50f;

    private final Set<Animation> animationsToAdd = new HashSet<>();
    private final ArrayList<Animation> animations = new ArrayList<>();
    private final Set<Animation> animationsToDelete = new HashSet<>();
    private final Set<Animation> animationsToNotifyReady = new HashSet<>();

    @Override
    protected boolean onDrawFrame(float dt) {
        if (checkResize()) {
            for (Animation animation : animations) {
                finishAnimation(animation);
            }
            animations.clear();
        }

        synchronized (animationsToAdd) {
            for (Animation animation : animationsToAdd) {
                if (!initAnimation(animation)) {
                    finishAnimation(animation);
                    continue;
                }

                animations.add(animation);
                animationsToNotifyReady.add(animation);

            }
            animationsToAdd.clear();
        }

        final boolean notingToDraw = animations.isEmpty();

        GLES31.glClear(GLES31.GL_COLOR_BUFFER_BIT);

        for (Animation animation : animations) {
            animation.progress += dt * timeScale;

            GLES31.glActiveTexture(GLES31.GL_TEXTURE1);
            GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, animation.textureHandler);

            GLES31.glUniform1f(uniformHandler_reset, animation.frameNumber == 0 ? -1f : 0f);
            GLES31.glUniform1f(uniformHandler_pointSize, animation.pointSize);
            GLES31.glUniform1f(uniformHandler_progress, Math.min(animation.progress, 1f));
            GLES31.glUniform4f(uniformHandler_imagePositionAndSize, animation.state.relative.left, animation.state.relative.top, animation.state.relative.width(), animation.state.relative.height());

            GLES31.glBindVertexArray(animation.vertexArrayHandler);


            GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, animation.feedbackBufferHandlers[animation.frameNumber % 2]);
            GLES31.glVertexAttribPointer(1, 3, GLES31.GL_FLOAT, false, 0, 0);
            GLES31.glEnableVertexAttribArray(1);

            GLES31.glBindBufferBase(GLES31.GL_TRANSFORM_FEEDBACK_BUFFER, 0, animation.feedbackBufferHandlers[(animation.frameNumber + 1) % 2]);
            GLES31.glVertexAttribPointer(1, 3, GLES31.GL_FLOAT, false, 0, 0);
            GLES31.glEnableVertexAttribArray(1);



            GLES31.glBeginTransformFeedback(GLES31.GL_POINTS);
            GLES31.glDrawArrays(GLES31.GL_POINTS, 0, animation.pointsCount);
            GLES31.glEndTransformFeedback();

            animation.frameNumber++;
            if (animation.progress > 1f) {
                animationsToDelete.add(animation);
            }
        }

        for (Animation animation : animationsToDelete) {
            animations.remove(animation);
            finishAnimation(animation);
        }
        animationsToDelete.clear();

        eglSwapBuffers();

        for (Animation animation : animationsToNotifyReady) {
            if (!animation.isFinished) {
                AndroidUtilities.runOnUIThread(() -> callback.onAnimationReady(animation.state));
            }
        }
        animationsToNotifyReady.clear();

        return !notingToDraw;
    }

    @Override
    protected void onDie() {
        GlUtils.deleteShader(shaderHandler_fragment);
        GlUtils.deleteShader(shaderHandler_vertex);
        GlUtils.deleteProgram(programHandler_program);

        finishAllAnimations();
    }


    @UiThread
    public boolean startAnimation(SprayerBitmapState state) {
        if (!isAlive()) {
            return false;
        }
        synchronized (animationsToAdd) {
            animationsToAdd.add(new Animation(state));
        }
        startDrawFramesLoop();
        return true;
    }



    private void finishAllAnimations() {
        synchronized (animationsToAdd) {
            for (Animation animation : animationsToAdd) {
                finishAnimation(animation);
            }
            animationsToAdd.clear();
        }
        for (Animation animation : animations) {
            finishAnimation(animation);
        }
        animations.clear();
    }

    private void finishAnimation(Animation animation) {
        if (!animation.isFinished) {
            animation.destroy();
            AndroidUtilities.runOnUIThread(() -> callback.onAnimationFinish(animation.state));
        }
    }

    private static class Animation {
        final SprayerBitmapState state;

        int vertexBufferHandler;

        int[] feedbackBufferHandlers = new int[2];
        int frameNumber = 0;

        int vertexArrayHandler;

        int textureHandler = 0;

        float pointSize;
        int pointsCount;

        float progress = 0f;
        boolean isFinished = false;

        public Animation(SprayerBitmapState state) {
            this.state = state;
        }

        public void destroy () {
            if (!isFinished) {
                isFinished = true;
                GlUtils.deleteTexture(textureHandler);
                GlUtils.deleteVertexArray(vertexArrayHandler);
                GlUtils.deleteBuffer(vertexBufferHandler);
                GlUtils.deleteBuffer(feedbackBufferHandlers[0]);
                GlUtils.deleteBuffer(feedbackBufferHandlers[1]);
            }
        }
    }



    private static boolean initAnimation(Animation animation) {
        animation.textureHandler = GlUtils.loadTexture(animation.state.bitmap, GLES31.GL_TEXTURE1, true, false);
        if (animation.textureHandler == 0) {
            return false;
        }

        int[] VAO = new int[1];
        int[] VBO = new int[3];
        GLES31.glGenVertexArrays(1, VAO, 0);
        GLES31.glGenBuffers(3, VBO, 0);
        animation.vertexArrayHandler = VAO[0];

        animation.vertexBufferHandler = VBO[0];
        animation.feedbackBufferHandlers[0] = VBO[1];
        animation.feedbackBufferHandlers[1] = VBO[2];

        if (animation.vertexArrayHandler == 0 || animation.vertexBufferHandler == 0 || VBO[1] == 0 || VBO[2] == 0) {
            return false;
        }

        float[] buffer = generateVertexBuffer(animation);
        FloatBuffer vertexDataBuffer = ByteBuffer.allocateDirect(buffer.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        vertexDataBuffer.put(buffer);
        vertexDataBuffer.position(0);

        GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, animation.feedbackBufferHandlers[0]);
        GLES31.glBufferData(GLES31.GL_ARRAY_BUFFER, animation.pointsCount * 3 * 4, null, GLES31.GL_DYNAMIC_DRAW);
        GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, animation.feedbackBufferHandlers[1]);
        GLES31.glBufferData(GLES31.GL_ARRAY_BUFFER, animation.pointsCount * 3 * 4, null, GLES31.GL_DYNAMIC_DRAW);

        GLES31.glBindVertexArray(animation.vertexArrayHandler);
        GLES31.glBindBuffer(GLES31.GL_ARRAY_BUFFER, animation.vertexBufferHandler);
        GLES31.glBufferData(GLES31.GL_ARRAY_BUFFER, buffer.length * 4, vertexDataBuffer, GLES31.GL_STATIC_DRAW);
        GLES31.glVertexAttribPointer(0, 2, GLES31.GL_FLOAT, false, 0, 0);
        GLES31.glEnableVertexAttribArray(0);

        return true;
    }

    private static final float POINT_SIZE_DP = 1.5f;

    private static int getPointsCountLimit () {
        switch (SharedConfig.getDevicePerformanceClass()) {
            case SharedConfig.PERFORMANCE_CLASS_HIGH:
                return 125_000;
            case SharedConfig.PERFORMANCE_CLASS_AVERAGE:
                return 80_000;
            default:
            case SharedConfig.PERFORMANCE_CLASS_LOW:
                return 35_000;
        }
        //return 25_000;
    }

    private static float[] generateVertexBuffer (Animation animation) {
        final float width = animation.state.pixels.width();
        final float height = animation.state.pixels.height();

        final int pointsCountLimit = getPointsCountLimit();
        final float targetPointSize = Math.round(AndroidUtilities.density * POINT_SIZE_DP);
        final int targetCountX = (int) Math.ceil(width / targetPointSize);
        final int targetCountY = (int) Math.ceil(height / targetPointSize);

        final int countX, countY;
        final float pointSize;
        if (targetCountX * targetCountY > pointsCountLimit) {
            pointSize = (float) Math.ceil(Math.sqrt(width * height / pointsCountLimit));
            countX = (int) Math.ceil(width / pointSize);
            countY = (int) Math.ceil(height / pointSize);
        } else {
            pointSize = targetPointSize;
            countX = targetCountX;
            countY = targetCountY;
        }

        animation.pointSize = pointSize;
        animation.pointsCount = countX * countY;

        Log.i("WTF_DEBUG", "SprayerEffect: Generate Buffer: " + countX + " * " + countY + " = " + animation.pointsCount + " | point size: " + pointSize);

        final float startX = (animation.state.pixels.centerX() - countX * pointSize / 2f) / animation.state.viewportWidth;
        final float startY = (animation.state.pixels.centerY() - countY * pointSize / 2f) / animation.state.viewportHeight;
        final float sizeX = pointSize / animation.state.viewportWidth;
        final float sizeY = pointSize / animation.state.viewportHeight;

        float[] buffer = new float[countX * countY * 2];

        for (int x = 0; x < countX; x++) {
            for (int y = 0; y < countY; y++) {
                final int index = ((countX * countY) - (y * countX + x) - 1) * 2;
                final float left = ((startX + sizeX * x) * 2f - 1f) + (sizeX / 2f);
                final float top =  (((startY + sizeY * y) * 2f - 1f) + (sizeY / 2f)) * -1f;

                buffer[index] = left;
                buffer[index + 1] = top;
            }
        }

        return buffer;
    }
}
