package org.telegram.ui.AnimationSettings;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.util.Property;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.RecyclerView;

import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimationProperties;
import org.telegram.ui.Components.FilterTabsView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.SwipeGestureSettingsView;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Map;

public class AnimationSettingsActivity extends BaseFragment {
    private AnimatorSet tabsAnimation;
    private boolean tabsAnimationInProgress;
    private boolean animatingForward;
    private FilterTabsView filterTabsView;

    private ActionBarMenuItem menuItem;

    private Paint actionBarDefaultPaint = new Paint();

    private int topPadding;

    private AnimationSettingsActivity.ViewPage[] viewPages;

    public class AnimationRecyclerView extends RecyclerListView {
        private boolean firstLayout = true;
        private boolean ignoreLayout;
        private AnimationSettingsActivity.ViewPage parentPage;
        private int appliedPaddingTop;
        private int lastTop;
        private int lastListPadding;

        Paint paint = new Paint();
        RectF rectF = new RectF();

        public AnimationRecyclerView(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int t = 0;
            if (filterTabsView != null && filterTabsView.getVisibility() == VISIBLE) {
                t = AndroidUtilities.dp(44);
            } else {
                t = actionBar.getMeasuredHeight();
            }

            ignoreLayout = true;
            if (filterTabsView != null && filterTabsView.getVisibility() == VISIBLE) {
                t = ActionBar.getCurrentActionBarHeight() + (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0);
            } else {
                t = inPreviewMode && Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0;
            }
            setTopGlowOffset(t);
            setPadding(0, t, 0, 0);
            ignoreLayout = false;

            super.onMeasure(widthSpec, heightSpec);
            if (appliedPaddingTop != t && viewPages != null && viewPages.length > 1) {
                viewPages[1].setTranslationX(viewPages[0].getMeasuredWidth());
            }

        }

    }

    private class ViewPage extends FrameLayout {
        private int selectedType;
        public AnimationBackgroundSettingsLayout layout;
        public AnimationRecyclerView listView;

        public ViewPage(Context context) {
            super(context);
        }
    }

    private class ContentView extends SizeNotifierFrameLayout {
        public ContentView(Context context) {
            super(context);
        }

        private int[] pos = new int[2];

        @Override
        public void setPadding(int left, int top, int right, int bottom) {
            topPadding = top;
            requestLayout();
        }

        public boolean checkTabsAnimationInProgress() {
            if (tabsAnimationInProgress) {
                boolean cancel = false;
                if (Math.abs(viewPages[1].getTranslationX()) < 1) {
                    viewPages[0].setTranslationX(viewPages[0].getMeasuredWidth() * (animatingForward ? -1 : 1));
                    viewPages[1].setTranslationX(0);
                    cancel = true;
                }
                if (cancel) {
                    showScrollbars(true);
                    if (tabsAnimation != null) {
                        tabsAnimation.cancel();
                        tabsAnimation = null;
                    }
                    tabsAnimationInProgress = false;
                }
                return tabsAnimationInProgress;
            }
            return false;
        }

        public int getActionBarFullHeight() {
            float h = actionBar.getHeight();
            float filtersTabsHeight = 0;
            if (filterTabsView != null && filterTabsView.getVisibility() != GONE) {
                filtersTabsHeight = filterTabsView.getMeasuredHeight();
            }
            h += filtersTabsHeight;
            return (int) h;
        }

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            boolean result;
            if (child == viewPages[0] || (viewPages.length > 1 && child == viewPages[1])) {
                canvas.save();
                canvas.clipRect(0, -getY() + actionBar.getY() + getActionBarFullHeight(), getMeasuredWidth(), getMeasuredHeight());
                result = super.drawChild(canvas, child, drawingTime);
                canvas.restore();
            } else {
                result = super.drawChild(canvas, child, drawingTime);
            }
            if (child == actionBar && parentLayout != null) {
                int y = (int) (actionBar.getY() + getActionBarFullHeight());
                parentLayout.drawHeaderShadow(canvas, 255, y);
            }
            return result;
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            int actionBarHeight = getActionBarFullHeight();
            int top = (int) (-getY() + actionBar.getY());

            canvas.drawRect(0, top, getMeasuredWidth(), top + actionBarHeight, actionBarDefaultPaint);

            if (filterTabsView != null && filterTabsView.getVisibility() == View.VISIBLE) {
                filterTabsView.setTranslationY(actionBar.getTranslationY());
                filterTabsView.setAlpha(1f);
            }
            super.dispatchDraw(canvas);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int widthSize = View.MeasureSpec.getSize(widthMeasureSpec);
            int heightSize = View.MeasureSpec.getSize(heightMeasureSpec);

            setMeasuredDimension(widthSize, heightSize);
            heightSize -= getPaddingTop();

            measureChildWithMargins(actionBar, widthMeasureSpec, 0, heightMeasureSpec, 0);

            int keyboardSize = measureKeyboardHeight();
            int childCount = getChildCount();

            for (int i = 0; i < childCount; i++) {
                View child = getChildAt(i);
                if (child == null || child.getVisibility() == GONE || child == actionBar) {
                    continue;
                }
                if (child instanceof AnimationSettingsActivity.ViewPage) {
                    int contentWidthSpec = View.MeasureSpec.makeMeasureSpec(widthSize, View.MeasureSpec.EXACTLY);
                    int h;
                    if (filterTabsView != null && filterTabsView.getVisibility() == VISIBLE) {
                        h = heightSize + AndroidUtilities.dp(2) - AndroidUtilities.dp(44) - topPadding;
                    } else {
                        h = heightSize + AndroidUtilities.dp(2) - (actionBar.getMeasuredHeight()) - topPadding;
                    }

                    //if (filtersTabAnimator != null && filterTabsView != null && filterTabsView.getVisibility() == VISIBLE) {
                    //    h += filterTabsMoveFrom;
                    //} else {
                        child.setTranslationY(0);
                    //}
                    child.measure(contentWidthSpec, View.MeasureSpec.makeMeasureSpec(Math.max(AndroidUtilities.dp(10), h), View.MeasureSpec.EXACTLY));
                    child.setPivotX(child.getMeasuredWidth() / 2);
                } else {
                    measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                }
            }
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            final int count = getChildCount();

            int paddingBottom;
            paddingBottom = 0;
            setBottomClip(paddingBottom);

            for (int i = 0; i < count; i++) {
                final View child = getChildAt(i);
                if (child.getVisibility() == GONE) {
                    continue;
                }
                final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) child.getLayoutParams();

                final int width = child.getMeasuredWidth();
                final int height = child.getMeasuredHeight();

                int childLeft;
                int childTop;

                int gravity = lp.gravity;
                if (gravity == -1) {
                    gravity = Gravity.TOP | Gravity.LEFT;
                }

                final int absoluteGravity = gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
                final int verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;

                switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                    case Gravity.CENTER_HORIZONTAL:
                        childLeft = (r - l - width) / 2 + lp.leftMargin - lp.rightMargin;
                        break;
                    case Gravity.RIGHT:
                        childLeft = r - width - lp.rightMargin;
                        break;
                    case Gravity.LEFT:
                    default:
                        childLeft = lp.leftMargin;
                }

                switch (verticalGravity) {
                    case Gravity.TOP:
                        childTop = lp.topMargin + getPaddingTop();
                        break;
                    case Gravity.CENTER_VERTICAL:
                        childTop = ((b - paddingBottom) - t - height) / 2 + lp.topMargin - lp.bottomMargin;
                        break;
                    case Gravity.BOTTOM:
                        childTop = ((b - paddingBottom) - t) - height - lp.bottomMargin;
                        break;
                    default:
                        childTop = lp.topMargin;
                }

                if (child == filterTabsView) {
                    childTop = actionBar.getMeasuredHeight();
                } else if (child instanceof AnimationSettingsActivity.ViewPage) {
                    if (filterTabsView != null && filterTabsView.getVisibility() == VISIBLE) {
                        childTop = AndroidUtilities.dp(44);
                    } else {
                        childTop = actionBar.getMeasuredHeight();
                    }
                    childTop += topPadding;
                    Log.i("Scroll", "s " + Integer.toString(childTop)+ " " + Integer.toString(topPadding));
                }
                child.layout(childLeft, childTop, childLeft + width, childTop + height);
            }

            notifyHeightChanged();
        }

        @Override
        public boolean hasOverlappingRendering() {
            return false;
        }
    }

    private static final int share_params = 2;
    private static final int import_params = 3;
    private static final int restore_params = 4;

    private boolean scrollingManually;
    private boolean disableActionBarScrolling;
    private boolean waitingForScrollFinished;
    private boolean updatePullAfterScroll;

    @Override
    public View createView(final Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);

        actionBar.setTitle("Animation Settings");
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefault));
        actionBar.setAddToContainer(false);
        actionBar.setCastShadows(false);
        actionBar.setClipContent(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
            if (id == -1) {
                finishFragment();
            } else if (id == share_params) {
                if (!(getParentActivity() instanceof LaunchActivity)) {
                    return;
                }

                Bundle args = new Bundle();
                args.putBoolean("onlySelect", true);
                args.putInt("dialogsType", 3);
                DialogsActivity fragment = new DialogsActivity(args);
                fragment.setDelegate((fragment1, dids, message, param) -> {
                    final String filename_dest = "/data/data/" + getParentActivity().getApplicationContext().getPackageName() + "/shared_prefs/settings.tga";
                    try {
                        FileWriter fw = new FileWriter(filename_dest);
                        PrintWriter pw = new PrintWriter(fw);
                        Map<String,?> prefsMap = getAnimationController().getAllPreferences();
                        pw.println("default_v0: 0");
                        for (Map.Entry<String,?> entry : prefsMap.entrySet()) {
                            pw.println(entry.getKey() + ": " + entry.getValue().toString());
                        }

                        pw.close();
                        fw.close();
                    } catch (Exception e) {}
                    try {
                        for (int a = 0; a < dids.size(); a++) {
                            long did = dids.get(a);
                            SendMessagesHelper.prepareSendingDocument(getAccountInstance(), filename_dest, filename_dest, null, (message != null) ? message.toString() : null, null, did, null, null, null, null, false, 0);
                        }
                    } catch (Exception e) {
                    } finally {
                        fragment1.finishFragment();
                    }
                });
                ((LaunchActivity) getParentActivity()).presentFragment(fragment, false, true);
            } else if (id == import_params) {

                try {
                    //if (parentAlert.baseFragment instanceof ChatActivity || parentAlert.avatarPicker == 2) {

                        Intent settingsPickerIntent = new Intent(Intent.ACTION_PICK);
                        settingsPickerIntent.setType("*/*");
                        Intent chooserIntent = Intent.createChooser(settingsPickerIntent, null);

                        actionBar.getParentFragment().startActivityForResult(chooserIntent, 852);

                        /*if (parentAlert.avatarPicker != 0) {
                            parentAlert.baseFragment.startActivityForResult(chooserIntent, 14);
                        } else {
                            parentAlert.baseFragment.startActivityForResult(chooserIntent, 1);
                        }*/
                    /*} else {
                        Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
                        photoPickerIntent.setType("image/*");
                        if (parentAlert.avatarPicker != 0) {
                            parentAlert.baseFragment.startActivityForResult(photoPickerIntent, 14);
                        } else {
                            parentAlert.baseFragment.startActivityForResult(photoPickerIntent, 1);
                        }
                    }
                    parentAlert.dismiss();*/
                } catch (Exception e) {
                    FileLog.e(e);
                }

            } else if (id == restore_params) {
                AlertsCreator.createRestoreAnimationParamsAlert(actionBar.getParentFragment(), () -> {
                    getAnimationController().clearParameters();
                    viewPages[0].layout.adapter.notifyDataSetChanged();
                    viewPages[1].layout.adapter.notifyDataSetChanged();
                });
            }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        menuItem = menu.addItem(0, R.drawable.ic_ab_other);
        menuItem.addSubItem(share_params, "Share Parameters");
        menuItem.addSubItem(import_params, "Import Parameters");
        menuItem.addSubItem(restore_params, "Restore to Default")
                .setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText5));

        actionBarDefaultPaint.setColor(Theme.getColor(Theme.key_actionBarDefault));

        AnimationSettingsActivity.ContentView contentView = new AnimationSettingsActivity.ContentView(context);
        fragmentView = contentView;

        int pagesCount = 2;
        viewPages = new AnimationSettingsActivity.ViewPage[pagesCount];
        for (int a = 0; a < pagesCount; a++) {
            final AnimationSettingsActivity.ViewPage viewPage = new AnimationSettingsActivity.ViewPage(context) {
                @Override
                public void setTranslationX(float translationX) {
                    super.setTranslationX(translationX);
                    if (tabsAnimationInProgress) {
                        if (viewPages[0] == this) {
                            float scrollProgress = Math.abs(viewPages[0].getTranslationX()) / (float) viewPages[0].getMeasuredWidth();
                            filterTabsView.selectTabWithId(viewPages[1].selectedType, scrollProgress);
                        }
                    }
                }
            };
            contentView.addView(viewPage, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            viewPages[a] = viewPage;

            viewPage.listView = new AnimationRecyclerView(context);
            viewPage.layout = new AnimationBackgroundSettingsLayout(this, viewPage.listView);
            viewPage.addView(viewPage.layout.listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));


            viewPage.layout.listView.setOnScrollListener(new RecyclerView.OnScrollListener() {

                private boolean wasManualScroll;

                @Override
                public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        wasManualScroll = true;
                        scrollingManually = true;
                    } else {
                        scrollingManually = false;
                    }
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        wasManualScroll = false;
                        disableActionBarScrolling = false;
                        if (waitingForScrollFinished) {
                            waitingForScrollFinished = false;
                            if (updatePullAfterScroll) {
                                //viewPage.layout.listView.updatePullState();
                                updatePullAfterScroll = false;
                            }
                            viewPage.layout.adapter.notifyDataSetChanged();
                        }

                        if (filterTabsView != null && filterTabsView.getVisibility() == View.VISIBLE && viewPages[0].layout.listView == recyclerView) {
                            int scrollY = (int) -actionBar.getTranslationY();
                            int actionBarHeight = ActionBar.getCurrentActionBarHeight();
                            if (scrollY != 0 && scrollY != actionBarHeight) {
                                if (scrollY < actionBarHeight / 2) {
                                    recyclerView.smoothScrollBy(0, -scrollY);
                                } else if (viewPages[0].layout.listView.canScrollVertically(1)) {
                                    recyclerView.smoothScrollBy(0, actionBarHeight - scrollY);
                                }
                            }
                        }
                    }
                }

                @Override
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                    //viewPage.dialogsItemAnimator.onListScroll(-dy);
                    //checkListLoad(viewPage);

                    if (filterTabsView != null && filterTabsView.getVisibility() == View.VISIBLE && recyclerView == viewPages[0].layout.listView && !actionBar.isActionModeShowed() && !disableActionBarScrolling /*&& filterTabsViewIsVisible*/) {
                        float currentTranslation = actionBar.getTranslationY();
                        float newTranslation = currentTranslation - dy;
                        if (newTranslation < -ActionBar.getCurrentActionBarHeight()) {
                            newTranslation = -ActionBar.getCurrentActionBarHeight();
                        } else if (newTranslation > 0) {
                            newTranslation = 0;
                        }
                        if (newTranslation != currentTranslation) {
                            setScrollY(newTranslation);
                        }
                    }
                }
            });


            if (a != 0) {
                viewPages[a].setVisibility(View.GONE);
            }

            viewPages[a].setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        }


        filterTabsView = new FilterTabsView(context) {

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                getParent().requestDisallowInterceptTouchEvent(true);
                return super.onInterceptTouchEvent(ev);
            }

            @Override
            public void setTranslationY(float translationY) {
                if (getTranslationY() != translationY) {
                    super.setTranslationY(translationY);
                    //updateContextViewPosition();
                    if (fragmentView != null) {
                        fragmentView.invalidate();
                    }
                }
            }
        };
        filterTabsView.setNeedRenameFirstTab(false);
        filterTabsView.setDelegate(new FilterTabsView.FilterTabsViewDelegate() {
            @Override
            public void onSamePageSelected() {
                scrollToTop();
            }

            @Override
            public void onPageReorder(int fromId, int toId) {
                for (int a = 0; a < viewPages.length; a++) {
                    if (viewPages[a].selectedType == fromId) {
                        viewPages[a].selectedType = toId;
                    } else if (viewPages[a].selectedType == toId) {
                        viewPages[a].selectedType = fromId;
                    }
                }
            }

            @Override
            public void onPageSelected(int id, boolean forward) {
                if (viewPages[0].selectedType == id) {
                    return;
                }
                if (parentLayout != null) {
                    parentLayout.getDrawerLayoutContainer().setAllowOpenDrawerBySwipe(id == filterTabsView.getFirstTabId() || SharedConfig.getChatSwipeAction(currentAccount) != SwipeGestureSettingsView.SWIPE_GESTURE_FOLDERS);
                }
                viewPages[1].selectedType = id;
                viewPages[1].setVisibility(View.VISIBLE);
                viewPages[1].layout.updateRowsIds(id == Integer.MAX_VALUE?0:id);
                viewPages[1].layout.adapter.notifyDataSetChanged();
                viewPages[1].setTranslationX(viewPages[0].getMeasuredWidth());
                showScrollbars(false);
                switchToCurrentSelectedMode(true);
                animatingForward = forward;
            }

            @Override
            public boolean canPerformActions() {
                return true;
            }

            @Override
            public void onPageScrolled(float progress) {
                if (progress == 1 && viewPages[1].getVisibility() != View.VISIBLE) {
                    return;
                }
                if (animatingForward) {
                    viewPages[0].setTranslationX(-progress * viewPages[0].getMeasuredWidth());
                    viewPages[1].setTranslationX(viewPages[0].getMeasuredWidth() - progress * viewPages[0].getMeasuredWidth());
                } else {
                    viewPages[0].setTranslationX(progress * viewPages[0].getMeasuredWidth());
                    viewPages[1].setTranslationX(progress * viewPages[0].getMeasuredWidth() - viewPages[0].getMeasuredWidth());
                }
                if (progress == 1) {
                    AnimationSettingsActivity.ViewPage tempPage = viewPages[0];
                    viewPages[0] = viewPages[1];
                    viewPages[1] = tempPage;
                    viewPages[1].setVisibility(View.GONE);
                    showScrollbars(true);
                    //updateCounters(false);
                    //checkListLoad(viewPages[0]);
                    //viewPages[0].dialogsAdapter.resume();
                    //viewPages[1].dialogsAdapter.pause();
                }
            }


            @Override
            public int getTabCounter(int tabId) {
                return 0;
            }

            @Override
            public boolean didSelectTab(FilterTabsView.TabView tabView, boolean selected) {
                if (actionBar.isActionModeShowed()) {
                    return false;
                }

                return true;
            }

            @Override
            public boolean isTabMenuVisible() {
                return false;
            }

            @Override
            public void onDeletePressed(int id) {}
        });
        filterTabsView.addTab(Integer.MAX_VALUE, 20, "Background");
        filterTabsView.addTab(1, 21, "Short Text");
        filterTabsView.addTab(2, 22, "Long Text");
        filterTabsView.addTab(3, 23, "Link");
        filterTabsView.addTab(4, 24, "Emoji");
        filterTabsView.addTab(5, 25, "Voice");
        filterTabsView.addTab(6, 26, "Video");
        filterTabsView.addTab(7, 27, "Photos");
        filterTabsView.finishAddingTabs(false);
        filterTabsView.setAlpha(0.1f);
        contentView.addView(filterTabsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44));

        filterTabsView.selectFirstTab();

        final FrameLayout.LayoutParams layoutParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        contentView.addView(actionBar, layoutParams);

        return fragmentView;
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == 852) {
                if (data == null || data.getData() == null) {
                    return;
                }

                File file = new File(AndroidUtilities.getPath(data.getData()));
                if (file.getPath().endsWith(".tga")) {
                    AlertsCreator.createImportAnimationParamsAlert(this, () -> {
                        getAnimationController().importParameters(file);
                        viewPages[0].layout.adapter.notifyDataSetChanged();
                        viewPages[1].layout.adapter.notifyDataSetChanged();
                    });
                } else {
                    AlertsCreator.showSimpleAlert(this, "Error", "This file is not Telegram animations settings");
                }
            }
        }
    }

    private void scrollToTop() {
        int scrollDistance = viewPages[0].layout.layoutManager.findFirstVisibleItemPosition() * AndroidUtilities.dp(SharedConfig.useThreeLinesLayout ? 78 : 72);
        if (scrollDistance >= viewPages[0].listView.getMeasuredHeight() * 1.2f) {
            //viewPages[0].scrollHelper.setScrollDirection(RecyclerAnimationScrollHelper.SCROLL_DIRECTION_UP);
            //viewPages[0].scrollHelper.scrollToPosition(0, 0, false, true);
            resetScroll();
        } else {
            viewPages[0].listView.smoothScrollToPosition(0);
        }
    }

    public final Property<AnimationSettingsActivity, Float> SCROLL_Y = new AnimationProperties.FloatProperty<AnimationSettingsActivity>("animationValue") {
        @Override
        public void setValue(AnimationSettingsActivity object, float value) {
            object.setScrollY(value);
        }

        @Override
        public Float get(AnimationSettingsActivity object) {
            return actionBar.getTranslationY();
        }
    };

    private void resetScroll() {
        if (actionBar.getTranslationY() == 0) {
            return;
        }
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(ObjectAnimator.ofFloat(this, SCROLL_Y, 0));
        animatorSet.setInterpolator(new DecelerateInterpolator());
        animatorSet.setDuration(180);
        animatorSet.start();
    }

    private void setScrollY(float value) {
        Log.i("Scroll", "set scroll y" + value);
        actionBar.setTranslationY(value);
        if (filterTabsView != null) {
            filterTabsView.setTranslationY(value);
        }

        if (viewPages != null) {
            for (int a = 0; a < viewPages.length; a++) {
                viewPages[a].layout.listView.setTopGlowOffset(viewPages[a].layout.listView.getPaddingTop() + (int) value);
            }
        }
        fragmentView.invalidate();
    }

    @Override
    protected ActionBar createActionBar(Context context) {
        ActionBar actionBar = new ActionBar(context) {

            @Override
            public void setTranslationY(float translationY) {
                if (translationY != getTranslationY()) {
                    fragmentView.invalidate();
                }
                super.setTranslationY(translationY);
            }

            @Override
            protected boolean shouldClipChild(View child) {
                return super.shouldClipChild(child);
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                return super.drawChild(canvas, child, drawingTime);
            }
        };
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSelector), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_actionBarActionModeDefaultSelector), true);
        actionBar.setItemsColor(Theme.getColor(Theme.key_actionBarDefaultIcon), false);
        actionBar.setItemsColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon), true);

        return actionBar;
    }

    private boolean scrollBarVisible = true;
    private void showScrollbars(boolean show) {
        if (viewPages == null || scrollBarVisible == show) {
            return;
        }
        scrollBarVisible = show;
    }

    private void switchToCurrentSelectedMode(boolean animated) {
        for (int a = 0; a < viewPages.length; a++) {
            viewPages[a].layout.listView.stopScroll();
        }
        int a = animated ? 1 : 0;
        RecyclerView.Adapter currentAdapter = viewPages[a].layout.listView.getAdapter();

        if (viewPages[a].selectedType == Integer.MAX_VALUE) {
            //viewPages[a].dialogsType = 0;
            //viewPages[a].listView.updatePullState();
        } else {
            viewPages[a].layout.listView.setScrollEnabled(true);
        }
        //viewPages[a].dialogsAdapter.setDialogsType(viewPages[a].dialogsType);
        viewPages[a].layout.layoutManager.scrollToPositionWithOffset(0, (int) actionBar.getTranslationY());
        //checkListLoad(viewPages[a]);
    }

    @Override
    protected void onPanTranslationUpdate(float y) {
        if (viewPages == null) {
            return;
        }

        for (int a = 0; a < viewPages.length; a++) {
            viewPages[a].setTranslationY(y);
        }

        actionBar.setTranslationY(y);
    }
}
