package org.telegram.messenger.pip;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.os.Build;
import android.util.Log;
import android.view.View;

import androidx.annotation.RequiresApi;

import com.google.android.exoplayer2.Player;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.pip.activity.IPipActivity;
import org.telegram.messenger.pip.source.IPipSourceDelegate;
import org.telegram.messenger.pip.source.PipSourceHandlerState2;
import org.telegram.messenger.pip.utils.PipSourceParams;
import org.telegram.messenger.pip.utils.PipUtils;
import org.webrtc.TextureViewRenderer;

public class PipSource {
    private static int sourceIdCounter = 0;
    public final int sourceId = sourceIdCounter++;

    public final PipSourcePlaceholderView placeholderView;
    public final PipActivityController controller;
    public final PipSourceHandlerState2 state2;

    public final String tag;
    public final int priority;
    public final int cornerRadius;
    public final boolean needMediaSession;

    public final IPipSourceDelegate delegate;
    public final PipSourceParams params = new PipSourceParams();
    private final View.OnLayoutChangeListener onLayoutChangeListener = this::onLayoutChange;

    private boolean isEnabled = true;
    public View contentView;
    Player player;

    private PipSource(PipActivityController controller, PipSource.Builder builder) {
        this.tag = (builder.tagPrefix != null ? builder.tagPrefix : "pip-source") + "-" + sourceId;

        this.delegate = builder.delegate;
        this.priority = builder.priority;
        this.cornerRadius = builder.cornerRadius;
        this.needMediaSession = builder.needMediaSession;
        this.controller = controller;
        this.params.setRatio(builder.width, builder.height);
        this.player = builder.player;
        this.placeholderView = builder.placeholderView;

        this.state2 = new PipSourceHandlerState2(this);

        setContentView(builder.contentView);

        checkAvailable(false);
        controller.dispatchSourceRegister(this);
    }

    public void setEnabled(boolean enabled) {
        isEnabled = enabled;
        checkAvailable(true);
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public void destroy() {
        controller.dispatchSourceUnregister(this);
        if (this.contentView != null) {
            this.contentView.removeOnLayoutChangeListener(onLayoutChangeListener);
        }
    }

    public void setContentView(View contentView) {
        if (this.contentView != null) {
            this.contentView.removeOnLayoutChangeListener(onLayoutChangeListener);
        }

        this.contentView = contentView;
        if (this.contentView != null) {
            this.contentView.addOnLayoutChangeListener(onLayoutChangeListener);
            updateContentPosition(this.contentView);
        }
    }

    public void setContentRatio(int width, int height) {
        if (params.setRatio(width, height)) {
            checkAvailable(true);
            controller.dispatchSourceParamsChanged(this);
        }
    }

    public void setPlayer(Player player) {
        this.player = player;
        checkAvailable(true);
        controller.dispatchSourceParamsChanged(this);
    }

    /* */

    private static final int[] tmpCords = new int[2];

    public void invalidatePosition() {
        if (contentView != null) {
            updateContentPosition(contentView);
        }
    }

    private void updateContentPosition(View v) {
        if (AndroidUtilities.isInPictureInPictureMode(controller.activity)) {
            return;
        }

        int x, y;
        v.getLocationOnScreen(tmpCords);
        x = tmpCords[0];
        y = tmpCords[1];

        if (controller.activity != null) {
            controller.activity.getWindow().getDecorView().getLocationOnScreen(tmpCords);

            Log.i(PipUtils.TAG, "[Debug] " + x + " " + tmpCords[0] + " " + y + " " + tmpCords[1]);

            x -= tmpCords[0];
            y -= tmpCords[1];
        }

        final int l = x, t = y, r = x + v.getWidth(), b = y + v.getHeight();
        boolean changed = params.setPosition(l, t, r, b);

        if (v instanceof TextureViewRenderer) {
            final int width = ((TextureViewRenderer) v).rotatedFrameWidth;
            final int height = ((TextureViewRenderer) v).rotatedFrameHeight;
            changed |= params.setRatio(width, height);
        }

        if (changed) {
            checkAvailable(true);
            controller.dispatchSourceParamsChanged(this);
        }
    }

    private void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
        updateContentPosition(v);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public PictureInPictureParams buildPictureInPictureParams() {
        PictureInPictureParams.Builder builder = params.build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(PipUtils.useAutoEnterInPictureInPictureMode());
            //builder.setSeamlessResizeEnabled(true);
        }
        return builder.build();
    }



    /* */

    private boolean isAvailable;
    private void checkAvailable(boolean notify) {
        boolean isAvailable = isEnabled && params.isValid();
        if (this.isAvailable != isAvailable) {
            this.isAvailable = isAvailable;
            if (notify) {
                controller.dispatchSourceAvailabilityChanged(this);
            }
        }
    }

    public boolean isAvailable() {
        return isAvailable;
    }


    /* Builder */

    public static class Builder {
        private final Activity activity;
        private final IPipSourceDelegate delegate;

        private String tagPrefix;
        private int cornerRadius;
        private int priority = 0;
        private boolean needMediaSession = false;
        private Player player;
        private int width, height;
        private View contentView;
        private PipSourcePlaceholderView placeholderView;

        public Builder(Activity activity, IPipSourceDelegate delegate) {
            this.activity = activity;
            this.delegate = delegate;
        }

        public Builder setTagPrefix(String tagPrefix) {
            this.tagPrefix = tagPrefix;
            return this;
        }

        public Builder setPriority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder setPlaceholderView(PipSourcePlaceholderView placeholderView) {
            this.placeholderView = placeholderView;
            return this;
        }

        public Builder setCornerRadius(int cornerRadius) {
            this.cornerRadius = cornerRadius;
            return this;
        }

        public Builder setNeedMediaSession(boolean needMediaSession) {
            this.needMediaSession = needMediaSession;
            return this;
        }

        public Builder setContentView(View contentView) {
            this.contentView = contentView;
            return this;
        }

        public Builder setContentRatio(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder setPlayer(Player player) {
            this.player = player;
            return this;
        }

        public PipSource build() {
            if (activity instanceof IPipActivity) {
                PipActivityController controller = ((IPipActivity) activity).getPipController();
                return new PipSource(controller, this);
            }

            return null;
        }
    }
}
