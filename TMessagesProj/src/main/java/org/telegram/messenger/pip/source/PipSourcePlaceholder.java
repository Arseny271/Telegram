package org.telegram.messenger.pip.source;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

class PipSourcePlaceholder {
    private final @NonNull View placeholderActivityView;
    private final @Nullable View placeholderSourceView;
    private final Resources resources;

    private Bitmap placeholder;
    private BitmapDrawable placeholderSourceDrawable;
    private BitmapDrawable placeholderActivityDrawable;

    public PipSourcePlaceholder(@NonNull View placeholderActivityView, @Nullable View placeholderSourceView) {
        this.placeholderActivityView = placeholderActivityView;
        this.placeholderSourceView = placeholderSourceView;
        this.resources = placeholderActivityView.getResources();
    }

    public void setPlaceholder(Bitmap bitmap) {
        if (placeholder == bitmap) {
            return;
        }

        clear();

        placeholder = bitmap;
        placeholderActivityDrawable = new BitmapDrawable(resources, placeholder);
        placeholderActivityView.setBackground(placeholderActivityDrawable);
        if (placeholderSourceView != null) {
            placeholderSourceDrawable = new BitmapDrawable(resources, placeholder);
            placeholderSourceView.setBackground(placeholderSourceDrawable);
        }
    }

    public void stopPlaceholderForActivity() {
        if (placeholderActivityDrawable != null) {
            placeholderActivityView.setBackground(null);
            placeholderActivityDrawable = null;
        }
        maybeClearPlaceholder();
    }

    public void stopPlaceholderForSource() {
        if (placeholderSourceDrawable != null) {
            placeholderSourceDrawable = null;
            if (placeholderSourceView != null) {
                placeholderSourceView.setBackground(null);
            }
        }
        maybeClearPlaceholder();
    }

    public void clear() {
        stopPlaceholderForActivity();
        stopPlaceholderForSource();
    }

    private void maybeClearPlaceholder() {
        if (placeholderSourceDrawable == null && placeholderActivityDrawable == null) {
            if (placeholder != null) {
                placeholder.recycle();
                placeholder = null;
            }
        }
    }
}
