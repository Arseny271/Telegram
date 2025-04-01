package org.telegram.ui.Components;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import androidx.annotation.RawRes;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class InstantCameraVideoEncoderOverlayHelper {
    private static final int BLUR_TEXTURE_FULL_SAMPLE = 0;
    private static final int BLUR_TEXTURE_DOWN_SAMPLE = 1;
    private static final int BLUR_TEXTURE_DOWN_SAMPLE_TMP = 2;
    private static final int BLUR_TEXTURE_WATER_MARK_TEXT = 3;
    private static final int BLUR_TEXTURE_WATER_MARK_LOGO = 4;

    private final int videoWidth, videoHeight;
    private final int downscaledWidth = 48, downscaledHeight = 48;

    private final Program programRenderTexture = new Program(R.raw.round_blur_stage_0_frag);
    private final Program programRenderWatermark = new Program(R.raw.round_blur_stage_3_frag);
    private final BlurProgram programRenderBlur = new BlurProgram();
    private final MixProgram programRenderMixed = new MixProgram();

    private final FloatBuffer attributeVertexBuffer;
    private final FloatBuffer attributeTextureBuffer;

    private float logoFrame = 0f;

    private final int[] blurFBO = new int[1];
    private final int[] blurTexture = new int[5];

    public InstantCameraVideoEncoderOverlayHelper(int width, int height) {
        this.videoWidth = width;
        this.videoHeight = height;

        final float[] texData = new float[8 * (27 + 2)];
        setTextureCords(texData, 0, 0, 1, 1, 0);
        setTextureCords(texData, 1, 0, 0, 1, 1);

        final float[] verData = new float[12 * 3];
        setVertexCords(verData, 0, -1, 1, 1, -1);

        float scale = 372f / 1536f;
        setVertexCords(verData, 1, 1f - scale * 2f, -1f + scale * 2f, 1, -1);

        GLES20.glGenTextures(5, blurTexture, 0);
        for (int i = 0; i < 5; i++) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurTexture[i]);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

            if (i == BLUR_TEXTURE_WATER_MARK_LOGO) {
                final int logoSize = Math.round(width * 0.2f);
                final int logoOffset = Math.round(width * 28 / 1536f);
                final int trueSize = logoSize - logoOffset - logoOffset;

                final int[] logoMetaData = new int[3];
                final long logoPtr = RLottieDrawable.createWithJson(AndroidUtilities.readRes(R.raw.plane_logo_plain), "logo_plane", logoMetaData, null);
                final Bitmap logoBitmap = Bitmap.createBitmap(logoSize, logoSize, Bitmap.Config.ARGB_8888);

                Bitmap bitmap = Bitmap.createBitmap(trueSize * 8, trueSize * 4, Bitmap.Config.ALPHA_8);
                Canvas canvas = new Canvas(bitmap);
                for (int x = 0; x < 8; x++) {
                    for (int y = 0; y < 4; y++) {
                        final int index = y * 8 + x;
                        if (index >= 27) {
                            continue;
                        }

                        final float l, t, r, b;
                        l = x / 8f;
                        t = y / 4f;
                        r = (x + 1) / 8f;
                        b = (y + 1) / 4f;

                        setTextureCords(texData, index + 2, l, t, r, b);
                        RLottieDrawable.getFrame(logoPtr, index * 2, logoBitmap, logoSize, logoSize, logoBitmap.getRowBytes(), true);
                        canvas.drawBitmap(logoBitmap, trueSize * x - logoOffset, trueSize * y - logoOffset, null);
                    }
                }

                float scale2 = (float) trueSize / videoWidth;
                setVertexCords(verData, 2, -1, -1f + scale2 * 2f, -1f + scale2 * 2f, -1);

                GLES20.glTexImage2D(
                    GLES20.GL_TEXTURE_2D,
                    0,
                    GLES20.GL_ALPHA,
                    trueSize * 8,
                    trueSize * 4,
                    0,
                    GLES20.GL_ALPHA,
                    GLES20.GL_UNSIGNED_BYTE,
                    null
                );
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);

                bitmap.recycle();
                logoBitmap.recycle();
                RLottieDrawable.destroy(logoPtr);
            } else if (i == BLUR_TEXTURE_WATER_MARK_TEXT) {
                Bitmap bitmap = AndroidUtilities.getBitmapFromRaw(R.raw.round_blur_overlay_text);
                if (bitmap != null) {
                    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
                    bitmap.recycle();
                }
            } else  {
                GLES20.glTexImage2D(
                        GLES20.GL_TEXTURE_2D,
                        0,
                        GLES20.GL_RGBA,
                        i == 0 ? videoWidth : downscaledWidth,
                        i == 0 ? videoHeight : downscaledHeight,
                        0,
                        GLES20.GL_RGBA,
                        GLES20.GL_UNSIGNED_BYTE,
                        null
                );
            }
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glGenFramebuffers(1, blurFBO, 0);

        attributeVertexBuffer = ByteBuffer.allocateDirect(verData.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        attributeVertexBuffer.put(verData).position(0);

        attributeTextureBuffer = ByteBuffer.allocateDirect(texData.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        attributeTextureBuffer.put(texData).position(0);
    }

    public void bind() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, blurFBO[0]);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, blurTexture[BLUR_TEXTURE_FULL_SAMPLE], 0);
        GLES20.glViewport(0, 0, videoWidth, videoHeight);
    }

    public void render() {
        GLES20.glDisable(GLES20.GL_BLEND);

        {
            final Program program = programRenderTexture;

            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, blurTexture[BLUR_TEXTURE_DOWN_SAMPLE], 0);
            GLES20.glViewport(0, 0, downscaledWidth, downscaledHeight);

            GLES20.glUseProgram(program.program);
            GLES20.glVertexAttribPointer(program.aPositionHandle, 3, GLES20.GL_FLOAT, false, 12, attributeVertexBuffer.position(0));
            GLES20.glEnableVertexAttribArray(program.aPositionHandle);
            GLES20.glVertexAttribPointer(program.aTextureHandle, 2, GLES20.GL_FLOAT, false, 8, attributeTextureBuffer.position(0));
            GLES20.glEnableVertexAttribArray(program.aTextureHandle);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurTexture[BLUR_TEXTURE_FULL_SAMPLE]);

             GLES20.glUniform1i(program.sTextureHandle, 0);

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
            GLES20.glDisableVertexAttribArray(program.aTextureHandle);
            GLES20.glDisableVertexAttribArray(program.aPositionHandle);
            GLES20.glUseProgram(0);
        }

        for (int a = 0; a < 2; a++) {
            final BlurProgram program = programRenderBlur;

            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, blurTexture[a == 0 ? BLUR_TEXTURE_DOWN_SAMPLE_TMP : BLUR_TEXTURE_DOWN_SAMPLE], 0);
            GLES20.glViewport(0, 0, downscaledWidth, downscaledHeight);

            GLES20.glUseProgram(program.program);
            GLES20.glVertexAttribPointer(program.aPositionHandle, 3, GLES20.GL_FLOAT, false, 12, attributeVertexBuffer.position(0));
            GLES20.glEnableVertexAttribArray(program.aPositionHandle);
            GLES20.glVertexAttribPointer(program.aTextureHandle, 2, GLES20.GL_FLOAT, false, 8, attributeTextureBuffer.position(0));
            GLES20.glEnableVertexAttribArray(program.aTextureHandle);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurTexture[a == 0 ? BLUR_TEXTURE_DOWN_SAMPLE : BLUR_TEXTURE_DOWN_SAMPLE_TMP]);

            GLES20.glUniform1i(program.sTextureHandle, 0);
            GLES20.glUniform2f(program.texOffsetHandle, a == 0 ? 1f / downscaledWidth : 0f, a == 1 ? 1f / downscaledHeight : 0f);

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
            GLES20.glDisableVertexAttribArray(program.aTextureHandle);
            GLES20.glDisableVertexAttribArray(program.aPositionHandle);
            GLES20.glUseProgram(0);
        }

        {
            final MixProgram program = programRenderMixed;

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, blurTexture[BLUR_TEXTURE_DOWN_SAMPLE], 0);
            GLES20.glViewport(0, 0, videoWidth, videoHeight);

            GLES20.glUseProgram(program.program);
            GLES20.glVertexAttribPointer(program.aPositionHandle, 3, GLES20.GL_FLOAT, false, 12, attributeVertexBuffer.position(0));
            GLES20.glEnableVertexAttribArray(program.aPositionHandle);
            GLES20.glVertexAttribPointer(program.aTextureHandle, 2, GLES20.GL_FLOAT, false, 8, attributeTextureBuffer.position(0));
            GLES20.glEnableVertexAttribArray(program.aTextureHandle);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurTexture[BLUR_TEXTURE_DOWN_SAMPLE]);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurTexture[BLUR_TEXTURE_FULL_SAMPLE]);

            GLES20.glUniform1i(program.sTextureHandle, 0);
            GLES20.glUniform1i(program.bTextureHandle, 1);
            GLES20.glUniform2f(program.uniformHalfResolutionHandle, videoWidth / 2f, videoHeight / 2f);

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

            GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
            GLES20.glDisableVertexAttribArray(program.aTextureHandle);
            GLES20.glDisableVertexAttribArray(program.aPositionHandle);
            GLES20.glUseProgram(0);
        }

        {
            GLES20.glEnable(GLES20.GL_BLEND);

            for (int a = 0; a < 2; a++) {
                final Program program = a == 0 ? programRenderTexture : programRenderWatermark;

                GLES20.glUseProgram(program.program);
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);

                if (a == 0) {
                    GLES20.glVertexAttribPointer(program.aPositionHandle, 3, GLES20.GL_FLOAT, false, 12, attributeVertexBuffer.position(12));
                    GLES20.glEnableVertexAttribArray(program.aPositionHandle);
                    GLES20.glVertexAttribPointer(program.aTextureHandle, 2, GLES20.GL_FLOAT, false, 8, attributeTextureBuffer.position(8));
                    GLES20.glEnableVertexAttribArray(program.aTextureHandle);

                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurTexture[BLUR_TEXTURE_WATER_MARK_TEXT]);
                } else {
                    final int frame = ((int) logoFrame) % 27;
                    logoFrame += 1f;

                    GLES20.glVertexAttribPointer(program.aPositionHandle, 3, GLES20.GL_FLOAT, false, 12, attributeVertexBuffer.position(24));
                    GLES20.glEnableVertexAttribArray(program.aPositionHandle);
                    GLES20.glVertexAttribPointer(program.aTextureHandle, 2, GLES20.GL_FLOAT, false, 8, attributeTextureBuffer.position(16 + frame * 8));
                    GLES20.glEnableVertexAttribArray(program.aTextureHandle);

                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurTexture[BLUR_TEXTURE_WATER_MARK_LOGO]);
                }

                GLES20.glUniform1i(program.sTextureHandle, 0);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
                GLES20.glDisableVertexAttribArray(program.aTextureHandle);
                GLES20.glDisableVertexAttribArray(program.aPositionHandle);
                GLES20.glUseProgram(0);
            }

            GLES20.glDisable(GLES20.GL_BLEND);
        }
    }

    public void destroy() {
        programRenderTexture.destroy();
        programRenderBlur.destroy();
        programRenderMixed.destroy();
        programRenderWatermark.destroy();

        GLES20.glDeleteTextures(5, blurTexture, 0);
        GLES20.glDeleteFramebuffers(1, blurFBO, 0);
    }

    private static class MixProgram extends Program {
        final int sTextureHandle;
        final int bTextureHandle;
        final int uniformHalfResolutionHandle;

        public MixProgram() {
            super(R.raw.round_blur_stage_2_frag);
            sTextureHandle = GLES20.glGetUniformLocation(program, "sTexture");
            bTextureHandle = GLES20.glGetUniformLocation(program, "bTexture");
            uniformHalfResolutionHandle = GLES20.glGetUniformLocation(program, "center");
        }
    }

    private static class BlurProgram extends Program {
        final int texOffsetHandle;

        public BlurProgram() {
            super(R.raw.round_blur_stage_1_frag);
            texOffsetHandle = GLES20.glGetUniformLocation(program, "texOffset");
        }
    }

    private static class Program {
        final int program;
        final int vertexShader;
        final int fragmentShader;

        final int aPositionHandle;
        final int aTextureHandle;
        final int sTextureHandle;

        public Program(@RawRes int fragmentShaderRes) {
            vertexShader = createShader(GLES20.GL_VERTEX_SHADER, R.raw.round_blur_vert);
            fragmentShader = createShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderRes);

            program = createProgram(vertexShader, fragmentShader);
            aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition");
            aTextureHandle = GLES20.glGetAttribLocation(program, "aTextureCoord");
            sTextureHandle = GLES20.glGetUniformLocation(program, "sTexture");
        }

        public void destroy() {
            GLES20.glDeleteProgram(program);
            GLES20.glDeleteShader(vertexShader);
            GLES20.glDeleteShader(fragmentShader);
        }
    }

    private static int createShader(int type, @RawRes int shaderRes) {
        final int shader = GLES20.glCreateShader(type);
        if (shader == 0) {
            return 0;
        }

        GLES20.glShaderSource(shader, AndroidUtilities.readRes(shaderRes));
        GLES20.glCompileShader(shader);

        final int[] status = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            String err = GLES20.glGetShaderInfoLog(shader);
            FileLog.e("GlUtils: compile shader error: " + err);
            GLES20.glDeleteShader(shader);
            return 0;
        }

        return shader;
    }



    private static int createProgram(int vertexShader, int fragmentShader) {
        final int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);

        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] == 0) {
            GLES20.glDeleteProgram(program);
            return 0;
        }

        return program;
    }

    private static void setVertexCords(float[] buffer, int index, float left, float top, float right, float bottom) {
        final int i = index * 12;

        buffer[i    ] = left;
        buffer[i + 1] = bottom;
        buffer[i + 2] = 0f;

        buffer[i + 3] = right;
        buffer[i + 4] = bottom;
        buffer[i + 5] = 0f;

        buffer[i + 6] = left;
        buffer[i + 7] = top;
        buffer[i + 8] = 0f;

        buffer[i + 9] = right;
        buffer[i + 10] = top;
        buffer[i + 11] = 0f;
    }

    private static void setTextureCords(float[] buffer, int index, float left, float top, float right, float bottom) {
        final int i = index * 8;

        buffer[i    ] = left;
        buffer[i + 1] = bottom;
        buffer[i + 2] = right;
        buffer[i + 3] = bottom;
        buffer[i + 4] = left;
        buffer[i + 5] = top;
        buffer[i + 6] = right;
        buffer[i + 7] = top;
    }
}
