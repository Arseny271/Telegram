package org.telegram.messenger.pip.utils;

import android.app.Activity;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewParent;
import android.view.WindowManager;

import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.pip.PipSource;

public class PipUtils {
    public static final String TAG = "PIP_DEBUG";

    public static WindowManager.LayoutParams createWindowLayoutParams(Context context, boolean inAppOnly) {
        WindowManager.LayoutParams windowLayoutParams = new WindowManager.LayoutParams();
        windowLayoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        windowLayoutParams.format = PixelFormat.TRANSLUCENT;
        windowLayoutParams.type = getWindowLayoutParamsType(context, inAppOnly);
        windowLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

        return windowLayoutParams;
    }

    public static int getWindowLayoutParamsType(Context context, boolean inAppOnly) {
        if (!inAppOnly && AndroidUtilities.checkInlinePermissions(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                return WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
            }
        } else {
            return WindowManager.LayoutParams.TYPE_APPLICATION;
        }
    }

    public static @PipPermissions int checkPermissions(Context context) {
        if (AndroidUtilities.checkInlinePermissions(context)) {
            return PipPermissions.PIP_GRANTED_OVERLAY;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (AndroidUtilities.checkPipPermissions(context)) {
                return PipPermissions.PIP_GRANTED_PIP;
            } else {
                return PipPermissions.PIP_DENIED_PIP;
            }
        } else {
            return PipPermissions.PIP_DENIED_OVERLAY;
        }
    }

    public static boolean checkAnyPipPermissions(Context context) {
        return checkPermissions(context) > 0;
    }

    public static boolean useAutoEnterInPictureInPictureMode() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    public static void applyPictureInPictureParams(Activity activity, PipSource source) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (source != null) {
                AndroidUtilities.setPictureInPictureParams(activity, source.buildPictureInPictureParams());
            } else {
                AndroidUtilities.resetPictureInPictureParams(activity);
            }
        }
    }

    public static void logParentChain(View view) {
        int level = 0;
        View current = view;
        while (current != null) {
            int[] loc = new int[2];
            current.getLocationOnScreen(loc);

            String log = String.format(
                    "Level %d: %s | x=%d, y=%d, w=%d, h=%d",
                    level,
                    current.getClass().getSimpleName(),
                    loc[0],
                    loc[1],
                    current.getWidth(),
                    current.getHeight()
            );

            Log.d(TAG, "[] parents " + log);

            // Переход к родителю
            ViewParent parent = current.getParent();
            if (parent instanceof View) {
                current = (View) parent;
            } else {
                break;
            }
            level++;
        }
    }

    public static void logViewInfo(View view) {
        if (view == null) {
            Log.d("ViewDebug", "View is null");
            return;
        }

        int[] loc = new int[2];
        view.getLocationOnScreen(loc);

        StringBuilder sb = new StringBuilder();
        sb.append("🧩 View Info:\n");
        sb.append("• Class: ").append(view.getClass().getSimpleName()).append("\n");
        sb.append("• id: ").append(view.getId()).append("\n");
        sb.append("• Size: ").append(view.getWidth()).append(" x ").append(view.getHeight()).append("\n");
        sb.append("• Position: x=").append(loc[0]).append(", y=").append(loc[1]).append("\n");
        sb.append("• Visibility: ").append(visibilityToString(view.getVisibility())).append("\n");
        sb.append("• Alpha: ").append(view.getAlpha()).append("\n");
        sb.append("• Translation: x=").append(view.getTranslationX()).append(", y=").append(view.getTranslationY()).append("\n");
        sb.append("• Scale: x=").append(view.getScaleX()).append(", y=").append(view.getScaleY()).append("\n");
        sb.append("• Rotation: ").append(view.getRotation()).append("°\n");
        sb.append("• Focusable: ").append(view.isFocusable()).append("\n");
        sb.append("• Clickable: ").append(view.isClickable()).append("\n");
        sb.append("• Attached: ").append(view.isAttachedToWindow()).append("\n");
        sb.append("• Parent: ").append(
                (view.getParent() instanceof View) ? view.getParent().getClass().getSimpleName() : "null"
        );

        Log.d(TAG, "[View render] " + sb.toString());
    }

    private static String visibilityToString(int v) {
        switch (v) {
            case View.VISIBLE: return "VISIBLE";
            case View.INVISIBLE: return "INVISIBLE";
            case View.GONE: return "GONE";
            default: return "UNKNOWN(" + v + ")";
        }
    }
}
