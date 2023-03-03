package org.telegram.ui.Components.voip;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.GroupCallUserCell;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

public class VoIPAvatarWithWavesView extends FrameLayout {
    private final GroupCallUserCell.AvatarWavesDrawable avatarWavesDrawable = new GroupCallUserCell.AvatarWavesDrawable(AndroidUtilities.dp(80), AndroidUtilities.dp(110));
    private final BackupImageView callingUserPhotoViewMini;


    public VoIPAvatarWithWavesView(@NonNull Context context) {
        super(context);
        setWillNotDraw(false);

        avatarWavesDrawable.setRadius(AndroidUtilities.dp(90f / 0.8f), AndroidUtilities.dp(96f / 0.8f), AndroidUtilities.dp(80f / 0.8f), AndroidUtilities.dp(86f / 0.8f), true);
        avatarWavesDrawable.setColor(ColorUtils.setAlphaComponent(Color.WHITE, 20), ColorUtils.setAlphaComponent(Color.WHITE, 36));

        callingUserPhotoViewMini = new BackupImageView(context);
        callingUserPhotoViewMini.setRoundRadius(AndroidUtilities.dp(75));
        addView(callingUserPhotoViewMini, LayoutHelper.createFrame(150, 150, Gravity.CENTER));

        setShowWaves(true);
    }

    public void setUser (TLRPC.User callingUser) {
        callingUserPhotoViewMini.setImage(ImageLocation.getForUserOrChat(callingUser, ImageLocation.TYPE_SMALL), null, Theme.createCircleDrawable(AndroidUtilities.dp(150), 0xFF000000), callingUser);
    }

    public void setLiteMode (boolean liteMode) {
        avatarWavesDrawable.setStopMode(liteMode);
        invalidate();
    }

    public void setShowWaves (boolean showWaves) {
        avatarWavesDrawable.setShowWaves(showWaves, this);
        if (!showWaves) {
            avatarWavesDrawable.setAmplitude(0);
        }
    }

    private boolean updateRunnableScheduled;

    private final Runnable updateRunnable = () -> {
        avatarWavesDrawable.setAmplitude(0);
        updateRunnableScheduled = false;
    };

    public void setAmplitude(double value) {
        if (value > 1.5f) {
            if (updateRunnableScheduled) {
                AndroidUtilities.cancelRunOnUIThread(updateRunnable);
            }

            avatarWavesDrawable.setAmplitude(value / 3f);
            AndroidUtilities.runOnUIThread(updateRunnable, 500);
            updateRunnableScheduled = true;
        } else {
            avatarWavesDrawable.setAmplitude(0);
        }
    }


    /**/

    @Override
    protected void dispatchDraw(Canvas canvas) {
        avatarWavesDrawable.update();
        avatarWavesDrawable.draw(canvas, getMeasuredWidth() / 2f, getMeasuredHeight() / 2f, this);

        callingUserPhotoViewMini.setScaleX(avatarWavesDrawable.getAvatarScale(0.05f));
        callingUserPhotoViewMini.setScaleY(avatarWavesDrawable.getAvatarScale(0.05f));

        super.dispatchDraw(canvas);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (updateRunnableScheduled) {
            AndroidUtilities.cancelRunOnUIThread(updateRunnable);
            updateRunnableScheduled = false;
        }
    }
}
