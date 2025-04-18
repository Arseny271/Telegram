package org.telegram.messenger.pip.source;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

public interface IPipSourceDelegate {
    default void pipRenderBackground(Canvas canvas) {}
    default void pipRenderForeground(Canvas canvas) {}

    Bitmap pipCreatePrimaryWindowViewBitmap();

    View pipCreatePictureInPictureView();

    void pipHidePrimaryWindowView();

    Bitmap pipCreatePictureInPictureViewBitmap();

    void pipShowPrimaryWindowView();
}
