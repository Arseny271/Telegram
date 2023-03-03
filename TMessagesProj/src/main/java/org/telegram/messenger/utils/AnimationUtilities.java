package org.telegram.messenger.utils;

import android.graphics.RectF;

public class AnimationUtilities {
    public static float fromTo (float from, float to, float progress) {
        return from + (to - from) * progress;
    }

    public static void fromToRectF (RectF target, RectF from, RectF to, float progress) {
        target.left = fromTo(from.left, to.left, progress);
        target.right = fromTo(from.right, to.right, progress);
        target.top = fromTo(from.top, to.top, progress);
        target.bottom = fromTo(from.bottom, to.bottom, progress);
    }
}
