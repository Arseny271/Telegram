package org.telegram.ui.AnimationSettings;

import android.animation.Animator;
import android.app.Activity;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.animation.Interpolator;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BaseController;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.Components.CubicBezierInterpolator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Map;

public class AnimationsController extends BaseController {
    private SharedPreferences animationPreferences;

    public static class AnimationsSettings {
        public long duration;
        public long startDelay;
        public Interpolator interpolator;
    }

    AnimationsController(int num) {
        super(num);

        if (currentAccount == 0) {
            animationPreferences = ApplicationLoader.applicationContext.getSharedPreferences("Animations", Activity.MODE_PRIVATE);
        } else {
            animationPreferences = ApplicationLoader.applicationContext.getSharedPreferences("Animations" + currentAccount, Activity.MODE_PRIVATE);
        }
    }

    public Map<String,?> getAllPreferences() {return animationPreferences.getAll();}

    private final int defaultColors[] = {0xFFFFF6BF, 0xFF76A076, 0xFFF6E477, 0xFF316B4D};
    public void getColors(int[] colors) {
        colors[0] = animationPreferences.getInt("backgroundColor1", defaultColors[0]);
        colors[1] = animationPreferences.getInt("backgroundColor2", defaultColors[1]);
        colors[2] = animationPreferences.getInt("backgroundColor3", defaultColors[2]);
        colors[3] = animationPreferences.getInt("backgroundColor4", defaultColors[3]);
    }

    public int getColor(int number) {
        return animationPreferences.getInt("backgroundColor" + (number + 1), defaultColors[number]);
    }

    public void saveColors(int[] colors) {
        SharedPreferences.Editor editor = animationPreferences.edit();
        editor.putInt("backgroundColor1", colors[0]);
        editor.putInt("backgroundColor2", colors[1]);
        editor.putInt("backgroundColor3", colors[2]);
        editor.putInt("backgroundColor4", colors[3]);
        editor.commit();
    }

    public void saveColor(int color, int number) {
        SharedPreferences.Editor editor = animationPreferences.edit();
        editor.putInt("backgroundColor" + (number + 1), color);
        editor.commit();
    }

    public int getAnimationDuration(String name) {
        int defaultValue = name.equals("OpenChat") || name.equals("JumpToMessage") || name.equals("SendMessage") ? 1000:500;
        return animationPreferences.getInt(name + "_duration_time", defaultValue);
    }

    public void saveAnimationDuration(String name, int durationTime) {
        Log.i("Duration", name + " " + durationTime);
        SharedPreferences.Editor editor = animationPreferences.edit();
        editor.putInt(name + "_duration_time", durationTime);
        editor.commit();
    }

    public AnimationCubicBezierSlider.AnimationParams getAnimationCoefficients(String name) {
        float defaultValue = name.equals("OpenChat") || name.equals("JumpToMessage") || name.equals("SendMessage") || name.endsWith("Y") ? 1:0.5f;

        return new AnimationCubicBezierSlider.AnimationParams(
            animationPreferences.getFloat( name+ "_coef_start", 0f),
            animationPreferences.getFloat( name+ "_coef_end", defaultValue),
            Math.max(animationPreferences.getFloat( name+ "_coef_first", 0.3f), 0.001f),
            Math.max(animationPreferences.getFloat( name+ "_coef_second", 1f), 0.001f)
        );
    }

    public void importParameters(File settingsFile) {
        SharedPreferences.Editor editor = animationPreferences.edit();
        editor.clear();

        FileReader fileReader = null;
        BufferedReader bufferedReader = null;
        try {
            fileReader = new FileReader(settingsFile);
            bufferedReader = new BufferedReader(fileReader);
            String line = bufferedReader.readLine();
            while (line != null) {
                String[] splitted = line.split(": ");
                if (splitted.length == 2) {
                    if (splitted[1].contains(".")) {
                        editor.putFloat(splitted[0], Float.parseFloat(splitted[1]));
                    } else {
                        editor.putInt(splitted[0], Integer.parseInt(splitted[1]));
                    }
                }
                line = bufferedReader.readLine();
            }
            editor.commit();
        } catch (Exception e) {
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (Exception e) {}
            }
        }

    }

    public void clearParameters() {
        SharedPreferences.Editor editor = animationPreferences.edit();
        editor.clear();
        editor.commit();
    }

    public AnimationsSettings getAnimationSettings(String paramName) {
        long duration = getAnimationDuration(paramName);

        AnimationsSettings animationsSettings = new AnimationsSettings();
        AnimationCubicBezierSlider.AnimationParams params = getAnimationCoefficients(paramName);
        animationsSettings.startDelay = (int)(duration * params.startTimeСoefficient);
        animationsSettings.duration = (int)(duration * (params.endTimeСoefficient - params.startTimeСoefficient));
        animationsSettings.interpolator = new CubicBezierInterpolator(
                (params.firstBezierPoint), 0f,
                (1f - params.secondBezierPoint), 1f);

        return animationsSettings;
    }

    public Animator setAnimationSettings(Animator animator, String paramName) {
        int duration = getAnimationDuration(paramName);
        AnimationCubicBezierSlider.AnimationParams params = getAnimationCoefficients(paramName);

        int startDelay = (int)(duration * params.startTimeСoefficient);
        int totalDuration = (int)(duration * (params.endTimeСoefficient - params.startTimeСoefficient));

        CubicBezierInterpolator interpolator = new CubicBezierInterpolator(
                (params.firstBezierPoint), 0f,
                (1f - params.secondBezierPoint), 1f);
        animator.setStartDelay(startDelay);
        animator.setDuration(totalDuration);
        animator.setInterpolator(interpolator);
        return animator;
    }

    public void saveAnimationCoefficients(String name, AnimationCubicBezierSlider.AnimationParams params) {
        SharedPreferences.Editor editor = animationPreferences.edit();
        editor.putFloat(name + "_coef_start", params.startTimeСoefficient);
        editor.putFloat(name + "_coef_end", params.endTimeСoefficient);
        editor.putFloat(name + "_coef_first", params.firstBezierPoint);
        editor.putFloat(name + "_coef_second", params.secondBezierPoint);
        editor.commit();
    }

    private static volatile AnimationsController[] Instance = new AnimationsController[UserConfig.MAX_ACCOUNT_COUNT];
    public static AnimationsController getInstance(int num) {
        AnimationsController localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (AnimationsController.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new AnimationsController(num);
                }
            }
        }
        return localInstance;
    }
}
