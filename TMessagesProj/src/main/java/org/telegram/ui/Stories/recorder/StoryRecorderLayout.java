package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.view.Gravity;

import org.telegram.messenger.Utilities;

public class StoryRecorderLayout {
    public int previewMaxWidth;
    public int previewMaxHeight;
    public int previewWidth;
    public int previewHeight;
    public int previewTop;
    public int underViewsHeight;

    public int containerWidth;
    public int containerHeight;  // previewMaxHeight + underViewsHeight
    public int containerLeft, containerRight, containerTop, containerBottom;

    public int bottomNavHeight;
    public int bottomNavBottom;
    public int bottomNavTop;

    public int paddingTop;
    public int paddingBottom;

    public int insetTop;
    public int insetBottom;

    public boolean useFullScreen;

    public void measure(
        int width,
        int height,
        int insetLeft,
        int insetTop,
        int insetRight,
        int insetBottom,
        StoryRecorderOptions options,
        Ratio currentRatio
    ) {
        final int W = width;
        final int H = height;
        final int w = W - insetLeft - insetRight;

        final int statusbar = insetTop;
        final int navbar = insetBottom;
        final int underControlsMinH = dp(48);
        final int underControlsMaxH = dp(68);

        final int maxPotentialPreviewW;
        final int maxPotentialPreviewH;

        if (currentRatio == Ratio.RATIO_FULL || options.isChatAttachMode) {
            maxPotentialPreviewW = w;
            maxPotentialPreviewH = H;
        } else {
            final float proportion = currentRatio.ratio();
            final int hFromW = (int) Math.ceil(w / proportion);
            if (hFromW <= H - navbar) {
                maxPotentialPreviewW = w;
                maxPotentialPreviewH = hFromW;
            } else {
                maxPotentialPreviewH = H - navbar;
                maxPotentialPreviewW = (int) Math.ceil(maxPotentialPreviewH * proportion);
            }
        }

        final int previewW;
        final int previewH;
        if (currentRatio == Ratio.RATIO_FULL) {
            previewW = maxPotentialPreviewW;
            previewH = maxPotentialPreviewH;
        } else {
            final int rw = currentRatio.width() * 1000;
            final int rh = currentRatio.height() * 1000;
            final float s = StoryEntry.calculateScale(rw, rh, maxPotentialPreviewW, maxPotentialPreviewH);
            previewW = Math.min(maxPotentialPreviewW, Math.round(rw / s));
            previewH = Math.min(maxPotentialPreviewH, Math.round(rh / s));
        }

        final boolean isUnderBottomViews = maxPotentialPreviewH + underControlsMinH > H - navbar;
        final int bottomNavHeight = !options.isChatAttachMode ?
            Utilities.clamp(H - maxPotentialPreviewH - statusbar - navbar,
                underControlsMaxH,
                underControlsMinH
            ) :
            Utilities.clamp(H - (w * 16 / 9) - statusbar - navbar,
                    underControlsMaxH,
                    underControlsMinH
            );

        final int underBottomViewsH = !isUnderBottomViews ? bottomNavHeight : 0;
        final int containerViewW = maxPotentialPreviewW;
        final int containerViewH = maxPotentialPreviewH + underBottomViewsH;
        final boolean useFullScreen = containerViewH > H - navbar - statusbar;

        final int containerLeft = (insetLeft + (W - insetRight) - containerViewW) / 2;
        final int containerRight = containerLeft + containerViewW;
        int containerTop = 0;
        if (!useFullScreen) {
            containerTop = (H - navbar - statusbar - containerViewH) / 2;
            if (containerTop < statusbar + dp(40)) {
                containerTop = statusbar;
            }
        }

        this.insetTop = statusbar;
        this.insetBottom = navbar;
        this.containerWidth = containerViewW;
        this.containerHeight = containerViewH;

        this.containerLeft = containerLeft;
        this.containerRight = containerRight;
        this.containerTop = containerTop;
        this.containerBottom = containerTop + containerViewH;

        this.paddingTop = useFullScreen ? statusbar : 0;
        this.paddingBottom = ((containerViewH > H - navbar) ? navbar : 0);

        this.bottomNavHeight = bottomNavHeight;
        this.bottomNavBottom = containerViewH - this.paddingBottom;
        this.bottomNavTop = this.bottomNavBottom - bottomNavHeight;

        this.previewMaxWidth = maxPotentialPreviewW;
        this.previewMaxHeight = maxPotentialPreviewH;
        this.previewWidth = previewW;
        this.previewHeight = previewH;
        this.underViewsHeight = underBottomViewsH;
        this.useFullScreen = useFullScreen;

        this.previewTop = options.isChatAttachMode ? getPreviewTop(previewHeight) : 0;
    }

    public int getPreviewWidthForRatio(Ratio ratio) {
        final int previewW;
        if (ratio == Ratio.RATIO_FULL) {
            previewW = previewMaxWidth;
        } else {
            final int rw = ratio.width() * 1000;
            final int rh = ratio.height() * 1000;
            final float s = StoryEntry.calculateScale(rw, rh, previewMaxWidth, previewMaxHeight);
            previewW = Math.min(previewMaxWidth, Math.round(rw / s));
        }

        return previewW;
    }

    public int getPreviewHeightForRatio(Ratio ratio) {
        final int previewH;
        if (ratio == Ratio.RATIO_FULL) {
            previewH = previewMaxHeight;
        } else {
            final int rw = ratio.width() * 1000;
            final int rh = ratio.height() * 1000;
            final float s = StoryEntry.calculateScale(rw, rh, previewMaxWidth, previewMaxHeight);
            previewH = Math.min(previewMaxHeight, Math.round(rh / s));
        }

        return previewH;
    }



    private static final int TOP_ABSOLUTE = 0;
    private static final int TOP_INSET = 1;
    private static final int TOP_CONTROLS = 2;

    private static final int BOTTOM_CONTROLS_2 = 3;
    private static final int BOTTOM_CONTROLS_1 = 4;
    private static final int BOTTOM_INSET = 5;
    private static final int BOTTOM_ABSOLUTE = 6;

    private static final TopOption[] options = new TopOption[]{
        new TopOption(TOP_CONTROLS, BOTTOM_CONTROLS_2, Gravity.CENTER),
        new TopOption(TOP_CONTROLS, BOTTOM_CONTROLS_1, Gravity.BOTTOM),
        new TopOption(TOP_INSET, BOTTOM_INSET, Gravity.TOP),
        new TopOption(TOP_INSET, BOTTOM_CONTROLS_2, Gravity.TOP),
        new TopOption(TOP_INSET, BOTTOM_CONTROLS_1, Gravity.BOTTOM),
        new TopOption(TOP_ABSOLUTE, BOTTOM_ABSOLUTE, Gravity.TOP),
        new TopOption(TOP_ABSOLUTE, BOTTOM_INSET, Gravity.TOP),
    };

    private static class TopOption {
        public final int top;
        public final int bottom;
        public final int gravity;

        public TopOption(int top, int bottom, int gravity) {
            this.top = top;
            this.bottom = bottom;
            this.gravity = gravity;
        }
    }

    private final int[] borders = new int[7];
    private final int[] scores = new int[options.length];

    public int getPreviewTop(int previewHeight) {
        borders[TOP_ABSOLUTE] = 0;
        borders[TOP_INSET] = borders[TOP_ABSOLUTE] + insetTop;
        borders[TOP_CONTROLS] = borders[TOP_INSET] + dp(56);
        borders[BOTTOM_ABSOLUTE] = containerHeight;
        borders[BOTTOM_INSET] = borders[BOTTOM_ABSOLUTE] - insetBottom;
        borders[BOTTOM_CONTROLS_1] = borders[BOTTOM_INSET] - bottomNavHeight;
        borders[BOTTOM_CONTROLS_2] = borders[BOTTOM_CONTROLS_1] - dp(100);

        TopOption bestOption = null;
        int bestScore = Integer.MAX_VALUE;
        for (int a = 0; a < options.length; a++) {
            final TopOption option = options[a];
            final int availH = borders[option.bottom] - borders[option.top];
            final int freeH = availH - previewHeight;
            final int score = -freeH;

            if (bestScore > 0) {
                if (score < bestScore) {
                    bestOption = option;
                    bestScore = score;
                }
            } else {
                if (score > bestScore && score <= 0) {
                    bestOption = option;
                    bestScore = score;
                }
            }

            scores[a] = score;
        }

        if (bestOption == null) {
            return 0;
        }

        switch (bestOption.gravity) {
            case Gravity.TOP:
                return borders[bestOption.top];
            case Gravity.BOTTOM:
                return borders[bestOption.bottom] - previewHeight;
            case Gravity.CENTER:
                return (borders[bestOption.bottom] + borders[bestOption.top] - previewHeight) / 2;
        }

        return 0;
    }
}
