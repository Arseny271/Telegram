package org.telegram.ui.Stories.recorder;

public enum Ratio {
    RATIO_1_1,
    RATIO_3_4,
    RATIO_9_16,
    RATIO_FULL;

    public static Ratio valueOf(int index) {
        return Ratio.values()[index];
    }

    public float ratio() {
        return (float) width() / height();
    }

    public int width() {
        switch (this) {
            case RATIO_1_1: return 1;
            case RATIO_3_4: return 3;
            case RATIO_9_16: return 9;
        }
        return -1;
    }

    public int height() {
        switch (this) {
            case RATIO_1_1: return 1;
            case RATIO_3_4: return 4;
            case RATIO_9_16: return 16;
        }
        return -1;
    }

    public String toText() {
        switch (this) {
            case RATIO_1_1: return "1:1";
            case RATIO_3_4: return "4:3";
            case RATIO_9_16: return "16:9";
            case RATIO_FULL: return "FULL";
        }

        return null;
    }
}
