/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.text.Layout;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

public class ScrollSlidingReactionsTabStrip extends HorizontalScrollView {

    public interface ScrollSlidingTabStripDelegate {
        void onPageSelected(int page, boolean forward);
        void onPageScrolled(float progress);
        default void onSamePageSelected() {

        }
    }

    private LinearLayout tabsContainer;
    private ScrollSlidingTabStripDelegate delegate;

    private int tabCount;
    private int currentPosition;
    private int selectedTabId = -1;

    private boolean animatingIndicator;
    private float animationIdicatorProgress;

    private int scrollingToChild = -1;

    private GradientDrawable selectorDrawable;

    private String tabLineColorKey = Theme.key_actionBarTabLine;
    private String activeTextColorKey = Theme.key_actionBarTabActiveText;
    private String unactiveTextColorKey = Theme.key_actionBarTabUnactiveText;
    private String selectorColorKey = Theme.key_actionBarTabSelector;

    private CubicBezierInterpolator interpolator = CubicBezierInterpolator.EASE_OUT_QUINT;

    private SparseIntArray positionToId = new SparseIntArray(5);
    private SparseIntArray idToPosition = new SparseIntArray(5);
    private SparseIntArray positionToWidth = new SparseIntArray(5);

    private float animationTime;
    private int previousPosition;

    private Runnable animationRunnable = new Runnable() {
        @Override
        public void run() {
            if (!animatingIndicator) {
                return;
            }
            long newTime = SystemClock.elapsedRealtime();
            long dt = (newTime);
            if (dt > 17) {
                dt = 17;
            }
            animationTime += dt / 280.0f;
            setAnimationIdicatorProgress(interpolator.getInterpolation(animationTime));
            if (animationTime > 1.0f) {
                animationTime = 1.0f;
            }
            if (animationTime < 1.0f) {
                AndroidUtilities.runOnUIThread(animationRunnable);
            } else {
                animatingIndicator = false;
                setEnabled(true);
                if (delegate != null) {
                    delegate.onPageScrolled(1.0f);
                }
            }
        }
    };

    public ScrollSlidingReactionsTabStrip(Context context) {
        super(context);

        selectorDrawable = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, null);
        float rad = AndroidUtilities.dpf2(3);
        selectorDrawable.setCornerRadii(new float[]{rad, rad, rad, rad, 0, 0, 0, 0});
        selectorDrawable.setColor(Theme.getColor(tabLineColorKey));

        setFillViewport(true);
        setWillNotDraw(false);

        setHorizontalScrollBarEnabled(false);
        tabsContainer = new LinearLayout(context) {
            @Override
            public void setAlpha(float alpha) {
                super.setAlpha(alpha);
                ScrollSlidingReactionsTabStrip.this.invalidate();
            }
        };
        tabsContainer.setOrientation(LinearLayout.HORIZONTAL);
        tabsContainer.setPadding(AndroidUtilities.dp(7), 0, AndroidUtilities.dp(7), 0);
        tabsContainer.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        addView(tabsContainer);
    }

    public void setDelegate(ScrollSlidingTabStripDelegate scrollSlidingTabStripDelegate) {
        delegate = scrollSlidingTabStripDelegate;
    }

    public boolean isAnimatingIndicator() {
        return animatingIndicator;
    }

    private void setAnimationProgressInernal(ReactionView newTab, ReactionView prevTab, float value) {
        if (newTab == null || prevTab == null) {
            return;
        }

        prevTab.setSelectedAlpha(1f - value);
        newTab.setSelectedAlpha(value);
        invalidate();
    }

    @Keep
    public void setAnimationIdicatorProgress(float value) {
        animationIdicatorProgress = value;

        ReactionView newTab = (ReactionView) tabsContainer.getChildAt(currentPosition);
        ReactionView prevTab = (ReactionView) tabsContainer.getChildAt(previousPosition);
        if (prevTab == null || newTab == null) {
            return;
        }
        setAnimationProgressInernal(newTab, prevTab, value);

        if (value >= 1f) {
            prevTab.setTag(unactiveTextColorKey);
            newTab.setTag(activeTextColorKey);
        }

        if (delegate != null) {
            delegate.onPageScrolled(value);
        }
    }

    public Drawable getSelectorDrawable() {
        return selectorDrawable;
    }

    public ViewGroup getTabsContainer() {
        return tabsContainer;
    }

    @Keep
    public float getAnimationIdicatorProgress() {
        return animationIdicatorProgress;
    }

    public int getNextPageId(boolean forward) {
        return positionToId.get(currentPosition + (forward ? 1 : -1), -1);
    }

    public SparseArray<View> removeTabs() {
        SparseArray<View> views = new SparseArray<>();
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            views.get(positionToId.get(i), child);
        }
        positionToId.clear();
        idToPosition.clear();
        positionToWidth.clear();
        tabsContainer.removeAllViews();
        tabCount = 0;

        return views;
    }

    public int getTabsCount() {
        return tabCount;
    }

    public boolean hasTab(int id) {
        return idToPosition.get(id, -1) != -1;
    }

    public void addReactionTab(final int id, final TLRPC.Document document, final int count) {
        int position = tabCount++;
        if (position == 0 && selectedTabId == -1) {
            selectedTabId = id;
        }
        positionToId.put(position, id);
        idToPosition.put(id, position);
        if (selectedTabId != -1 && selectedTabId == id) {
            currentPosition = position;
        }

        ReactionView tab = new ReactionView(getContext(), document, count);
        tab.setOnClickListener(v -> {
            int position1 = tabsContainer.indexOfChild(v);
            if (position1 < 0) {
                return;
            }
            if (position1 == currentPosition && delegate != null) {
                delegate.onSamePageSelected();
                return;
            }
            boolean scrollingForward = currentPosition < position1;
            scrollingToChild = -1;
            previousPosition = currentPosition;
            currentPosition = position1;
            selectedTabId = id;

            if (animatingIndicator) {
                AndroidUtilities.cancelRunOnUIThread(animationRunnable);
                animatingIndicator = false;
            }

            animationTime = 0;
            animatingIndicator = true;
            setEnabled(false);

            AndroidUtilities.runOnUIThread(animationRunnable, 16);

            if (delegate != null) {
                delegate.onPageSelected(id, scrollingForward);
            }
            scrollToChild(position1);
        });

        int tabWidth = tab.getViewWidth();
        tabsContainer.addView(tab, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));
        positionToWidth.put(position, tabWidth);
    }

    public void finishAddingTabs() {
        int count = tabsContainer.getChildCount();
        for (int a = 0; a < count; a++) {
            ReactionView tab = (ReactionView) tabsContainer.getChildAt(a);
            tab.setTag(currentPosition == a ? activeTextColorKey : unactiveTextColorKey);
            tab.setSelectedAlpha(currentPosition == a ? 1: 0);
        }
    }

    public void setColors(String line, String active, String unactive, String selector) {
        tabLineColorKey = line;
        activeTextColorKey = active;
        unactiveTextColorKey = unactive;
        selectorColorKey = selector;
        selectorDrawable.setColor(Theme.getColor(tabLineColorKey));
    }

    public int getCurrentTabId() {
        return selectedTabId;
    }

    public void setInitialTabId(int id) {
        selectedTabId = id;
        int pos = idToPosition.get(id);
        ReactionView child = (ReactionView) tabsContainer.getChildAt(pos);
        if (child != null) {
            currentPosition = pos;
            finishAddingTabs();
            requestLayout();
        }
    }

    public void resetTab() {
        selectedTabId = -1;
    }

    public int getFirstTabId() {
        return positionToId.get(0, 0);
    }

    private void scrollToChild(int position) {
        if (tabCount == 0 || scrollingToChild == position) {
            return;
        }
        scrollingToChild = position;
        ReactionView child = (ReactionView) tabsContainer.getChildAt(position);
        if (child == null) {
            return;
        }
        int currentScrollX = getScrollX();
        int left = child.getLeft();
        int width = child.getMeasuredWidth();
        if (left - AndroidUtilities.dp(50) < currentScrollX) {
            smoothScrollTo(left - AndroidUtilities.dp(50), 0);
        } else if (left + width + AndroidUtilities.dp(21) > currentScrollX + getWidth()) {
            smoothScrollTo(left /*+ width*/, 0);
        }
    }

    public int getCurrentPosition() {
        return currentPosition;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        int count = tabsContainer.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = tabsContainer.getChildAt(a);
            child.setEnabled(enabled);
        }
    }

    public void selectTabWithId(int id, float progress) {
        int position = idToPosition.get(id, -1);
        if (position < 0) {
            return;
        }
        if (progress < 0) {
            progress = 0;
        } else if (progress > 1.0f) {
            progress = 1.0f;
        }
        ReactionView child = (ReactionView) tabsContainer.getChildAt(currentPosition);
        ReactionView nextChild = (ReactionView) tabsContainer.getChildAt(position);
        if (child != null && nextChild != null) {
            setAnimationProgressInernal(nextChild, child, progress);
            if (progress >= 1f) {
                child.setTag(unactiveTextColorKey);
                nextChild.setTag(activeTextColorKey);
            }
            scrollToChild(tabsContainer.indexOfChild(nextChild));
        }
        if (progress >= 1.0f) {
            currentPosition = position;
            selectedTabId = id;
        }
    }

    public static class ReactionView extends FrameLayout {
        private ReactionButtonDrawable drawable;
        private ImageView defaultImageView;

        public ReactionView(@NonNull Context context, TLRPC.Document document, int count) {
            super(context);
            setWillNotDraw(false);

            if (document == null) {
                defaultImageView = new ImageView(context);
                defaultImageView.setScaleType(ImageView.ScaleType.CENTER);
                defaultImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_inForwardedNameText), PorterDuff.Mode.MULTIPLY));
                defaultImageView.setImageResource(R.drawable.msg_reactions_filled);
                addView(defaultImageView, LayoutHelper.createFrame(20, 20, Gravity.CENTER_VERTICAL, 9, 0, 0, 0));
            }

            drawable = new ReactionButtonDrawable(ReactionView.this);
            drawable.setImage(document);
            drawable.setText(LocaleController.formatShortNumber(count, null));
            drawable.setSelectedAlpha(0f);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec((int) drawable.getButtonWidth() + AndroidUtilities.dp(6), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(32), MeasureSpec.EXACTLY));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            drawable.setColors(Theme.getColor(Theme.key_chat_inForwardedNameText) & 0x20FFFFFF, Theme.getColor(Theme.key_chat_inForwardedNameText));
            drawable.setButtonCords(AndroidUtilities.dp(3), AndroidUtilities.dp(3), getMeasuredWidth() - AndroidUtilities.dp(6), getMeasuredHeight() - AndroidUtilities.dp(6));
            drawable.draw(canvas);
        }

        public void setSelectedAlpha(float alpha) {
            drawable.setSelectedAlpha(alpha);
            invalidate();
        }

        public int getViewWidth() {
            return (int) drawable.getButtonWidth();
        }
    }
}
