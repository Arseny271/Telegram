package org.telegram.messenger.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES31;
import android.opengl.GLUtils;

import androidx.annotation.DrawableRes;
import androidx.annotation.RawRes;

import org.telegram.messenger.FileLog;
import org.telegram.ui.Components.RLottieDrawable;

public class GlUtils {

    public static int loadTexture (Context context, @DrawableRes int resId, int textureUnit, boolean needGenerateMipmap) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;

        final Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), resId, options);

        return loadTexture(bitmap, textureUnit, needGenerateMipmap, true);
    }

    public static int loadTexture (Bitmap bitmap, int textureUnit, boolean needGenerateMipmap, boolean needRecycle) {
        if (bitmap == null || bitmap.isRecycled()) {
            return 0;
        }

        int[] textureHandle = new int[1];

        GLES31.glGenTextures(1, textureHandle, 0);

        if (textureHandle[0] != 0) {
            GLES31.glActiveTexture(textureUnit);
            GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, textureHandle[0]);

            GLUtils.texImage2D(GLES31.GL_TEXTURE_2D, 0, bitmap, 0);
            GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_WRAP_S, GLES31.GL_REPEAT);
            GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_WRAP_T, GLES31.GL_REPEAT);
            GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MIN_FILTER, needGenerateMipmap ? GLES31.GL_LINEAR_MIPMAP_NEAREST : GLES31.GL_LINEAR);
            GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MAG_FILTER, GLES31.GL_LINEAR);

            if (needGenerateMipmap) {
                GLES31.glGenerateMipmap(GLES31.GL_TEXTURE_2D);
            }
        } else {
            FileLog.e("GlUtils: Error loading texture");
        }

        if (needRecycle) {
            bitmap.recycle();
        }

        return textureHandle[0];
    }

    public static void deleteTexture (int texture) {
        if (texture != 0) {
            try {
                GLES31.glDeleteTextures(1, new int[]{texture}, 0);
            } catch (Exception e) {
                FileLog.e("GlUtils: delete texture error: " + e);
            }
        }
    }

    public static int compileShader (int type, @RawRes int shaderRes) {
        final int shader = GLES31.glCreateShader(type);
        if (shader == 0) {
            return 0;
        }

        GLES31.glShaderSource(shader, RLottieDrawable.readRes(null, shaderRes) + "\n// " + Math.random());
        GLES31.glCompileShader(shader);

        final int[] status = new int[1];
        GLES31.glGetShaderiv(shader, GLES31.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            String err = GLES31.glGetShaderInfoLog(shader);
            FileLog.e("GlUtils: compile shader error: " + err);
            GLES31.glDeleteShader(shader);
            return 0;
        }

        return shader;
    }

    public static void deleteShader (int shader) {
        if (shader != 0) {
            try {
                GLES31.glDeleteShader(shader);
            } catch (Exception e) {
                FileLog.e("GlUtils: delete shader error: " + e);
            }
        }
    }

    public static void deleteProgram (int program) {
        if (program != 0) {
            try {
                GLES31.glDeleteProgram(program);
            } catch (Exception e) {
                FileLog.e("GlUtils: delete shader error: " + e);
            }
        }
    }

    public static void deleteVertexArray (int vertexArray) {
        if (vertexArray != 0) {
            try {
                GLES31.glDeleteVertexArrays(1, new int[]{ vertexArray }, 0);
            } catch (Exception e) {
                FileLog.e("GlUtils: delete vertex array error: " + e);
            }
        }
    }

    public static void deleteBuffer (int buffer) {
        if (buffer != 0) {
            try {
                GLES31.glDeleteBuffers(1, new int[]{ buffer }, 0);
            } catch (Exception e) {
                FileLog.e("GlUtils: delete vertex buffer error: " + e);
            }
        }
    }
}
