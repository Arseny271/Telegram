package org.telegram.messenger.pip.utils;

import org.telegram.messenger.AndroidUtilities;

import java.util.concurrent.atomic.AtomicBoolean;

public class Trigger implements Runnable {

    private final Runnable action;
    private final Runnable timeoutRunnable;
    private final AtomicBoolean triggered = new AtomicBoolean(false);

    private Trigger(Runnable action, long timeoutMs) {
        this.action = action;
        this.timeoutRunnable = () -> {
            if (triggered.compareAndSet(false, true)) {
                action.run();
            }
        };

        AndroidUtilities.runOnUIThread(timeoutRunnable, timeoutMs);
    }

    public static Trigger run(Runnable action, long timeoutMs) {
        return new Trigger(action, timeoutMs);
    }

    @Override
    public void run() {
        if (triggered.compareAndSet(false, true)) {
            AndroidUtilities.cancelRunOnUIThread(timeoutRunnable);
            action.run();
        }
    }

    public void cancel() {
        if (triggered.compareAndSet(false, true)) {
            AndroidUtilities.cancelRunOnUIThread(timeoutRunnable);
        }
    }
}
