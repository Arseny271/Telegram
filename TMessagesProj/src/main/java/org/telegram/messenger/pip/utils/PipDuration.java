package org.telegram.messenger.pip.utils;

import android.os.SystemClock;

import androidx.core.math.MathUtils;

public class PipDuration {
    private long estimated = 400L;
    private long start;
    private int count;

    public void start() {
        this.start = SystemClock.uptimeMillis();
    }

    public long estimated() {
        return estimated;
    }

    public float progress() {
        if (estimated > 0) {
            return MathUtils.clamp((float) (SystemClock.uptimeMillis() - start) / estimated, 0, 1);
        }

        return 0.5f;
    }

    public boolean isStarted() {
        return start != 0;
    }

    public long end() {
        if (start == 0) {
            return 0;
        }

        final long duration = SystemClock.uptimeMillis() - start;
        final int weight = MathUtils.clamp(count, 0, 9);
        estimated = (estimated * weight / 10) + (duration * (10 - weight) / 10);
        start = 0;
        count++;

        return duration;
    }
}
