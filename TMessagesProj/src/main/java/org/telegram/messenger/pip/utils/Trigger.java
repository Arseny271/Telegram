package org.telegram.messenger.pip.utils;

import org.telegram.messenger.AndroidUtilities;

import java.util.concurrent.atomic.AtomicBoolean;

public class Trigger implements Runnable {

    public interface TimeoutRunnable {
        void run(boolean byTimeout);
    }

    private final TimeoutRunnable action;
    private final Runnable timeoutRunnable;
    private final AtomicBoolean triggered = new AtomicBoolean(false);

    private Trigger(TimeoutRunnable action, long timeoutMs) {
        this.action = action;
        this.timeoutRunnable = () -> {
            if (triggered.compareAndSet(false, true)) {
                action.run(true);
            }
        };

        AndroidUtilities.runOnUIThread(timeoutRunnable, timeoutMs);
    }

    public static Trigger run(TimeoutRunnable action, long timeoutMs) {
        return new Trigger(action, timeoutMs);
    }

    @Override
    public void run() {
        if (triggered.compareAndSet(false, true)) {
            AndroidUtilities.cancelRunOnUIThread(timeoutRunnable);
            action.run(false);
        }
    }

    public void cancel() {
        if (triggered.compareAndSet(false, true)) {
            AndroidUtilities.cancelRunOnUIThread(timeoutRunnable);
        }
    }
}
