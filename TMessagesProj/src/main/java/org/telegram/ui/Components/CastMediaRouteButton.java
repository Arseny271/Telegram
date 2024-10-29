package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.mediarouter.app.MediaRouteButton;

import org.telegram.ui.ActionBar.Theme;

public class CastMediaRouteButton extends MediaRouteButton {
    private final Paint paint = new Paint();
    private Theme.ResourcesProvider resourcesProvider;

    public CastMediaRouteButton(@NonNull Context context) {
        super(context);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
    }

    public void setResourcesProvider(Theme.ResourcesProvider resourcesProvider) {
        this.resourcesProvider = resourcesProvider;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        paint.setColor(resourcesProvider != null ? resourcesProvider.getColor(Theme.key_actionBarDefaultIcon) : Theme.getColor(Theme.key_actionBarDefaultIcon));

        canvas.saveLayer(0, 0, getMeasuredWidth(), getMeasuredHeight(), null, Canvas.ALL_SAVE_FLAG);
        super.onDraw(canvas);
        canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), paint);
        canvas.restore();
    }
}
