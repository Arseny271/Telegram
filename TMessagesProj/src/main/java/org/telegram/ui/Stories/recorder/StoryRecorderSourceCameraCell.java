package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.R;
import org.telegram.messenger.camera.CameraView;
import org.telegram.ui.Cells.PhotoAttachCameraCell;

public class StoryRecorderSourceCameraCell extends StoryRecorder.SourceView {
    private final PhotoAttachCameraCell cameraCell;
    private final CameraView cameraView;

    private StoryRecorderSourceCameraCell(PhotoAttachCameraCell cameraCell, CameraView cameraView) {
        this.cameraCell = cameraCell;
        this.cameraView = cameraView;

        int[] loc = new int[2];
        final View imageView = cameraCell.getImageView();
        imageView.getLocationOnScreen(loc);
        screenRect.set(loc[0], loc[1], loc[0] + imageView.getWidth(), loc[1] + imageView.getHeight());
        hasShadow = false;


        final Bitmap bitmap = cameraView.getTextureView().getBitmap();
        if (bitmap != null) {
            final Matrix matrix = cameraView.getMatrix();
            final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            final float bWidth = cameraView.getMeasuredWidth();
            final float bHeight = cameraView.getMeasuredWidth() * ((float) bitmap.getHeight() / bitmap.getWidth());
            backgroundDrawable = new Drawable() {
                private final Path path = new Path();

                @Override
                public void draw(@NonNull Canvas canvas) {
                    final Rect bounds = getBounds();
                    final float s = Math.max(bounds.width() / bWidth, bounds.height() / bHeight);
                    canvas.save();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        path.reset();
                        path.addRoundRect(bounds.left, bounds.top, bounds.right, bounds.bottom, dp(8), dp(8), Path.Direction.CW);
                        path.close();
                        canvas.clipPath(path);
                    } else {
                        canvas.clipRect(bounds);
                    }
                    canvas.translate(bounds.centerX(), bounds.centerY());
                    canvas.scale(s, s);
                    canvas.translate(-bWidth / 2f, -bHeight / 2f);
                    canvas.concat(matrix);
                    canvas.drawBitmap(bitmap, 0, 0, paint);
                    canvas.restore();
                }

                @Override
                public void setAlpha(int alpha) {
                    paint.setAlpha(alpha);
                }

                @Override
                public void setColorFilter(@Nullable ColorFilter colorFilter) {

                }

                @Override
                public int getOpacity() {
                    return PixelFormat.UNKNOWN;
                }
            };
        } else {
            backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            backgroundPaint.setColor(0x80000000);
        }

        iconDrawable = cameraCell.getContext().getResources().getDrawable(R.drawable.instant_camera).mutate();
        iconSizeW = iconDrawable.getMinimumWidth();
        iconSizeH = iconDrawable.getMinimumHeight();
        rounding = dp(8);
    }

    @Override
    protected void show(boolean sent) {
        cameraCell.setVisibility(View.VISIBLE);
        cameraView.resetCamera();
    }
    @Override
    protected void hide() {
        cameraCell.post(() -> {
            cameraCell.setVisibility(View.GONE);
        });
    }

    public static StoryRecorderSourceCameraCell fromCameraCell(PhotoAttachCameraCell cameraCell, CameraView cameraView) {
        if (cameraCell == null || cameraView == null) {
            return null;
        }

        return new StoryRecorderSourceCameraCell(cameraCell, cameraView);
    }
}
