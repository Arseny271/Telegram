package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatThemeController;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.ResultCallback;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.EmojiThemes;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.DrawerProfileCell;
import org.telegram.ui.Cells.ThemesHorizontalListCell;

import java.io.File;
import java.io.FileInputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class QrThemeBottomSheet extends BottomSheet implements NotificationCenter.NotificationCenterDelegate {

    public static abstract class ThemeDelegate implements Theme.ResourcesProvider {
        abstract public EmojiThemes getCurrentTheme();
        abstract public void setCurrentTheme(final EmojiThemes chatTheme, boolean animated, Boolean forceDark);
    }

    private final ChatThemeBottomSheet.Adapter adapter;
    private final ThemeDelegate themeDelegate;
    private final EmojiThemes originalTheme;
    private final boolean originalIsDark;
    private final BaseFragment parentFragment;
    private final RecyclerListView recyclerView;
    private final LinearLayoutManager layoutManager;
    private final FlickerLoadingView progressView;
    private final TextView titleView;
    private final RLottieDrawable darkThemeDrawable;
    private final RLottieImageView darkThemeView;
    private final LinearSmoothScroller scroller;
    private final View applyButton;
    private TextView applyTextView;
    private ChatThemeBottomSheet.ChatThemeItem selectedItem;
    private boolean forceDark;
    private boolean isLightDarkChangeAnimation;
    private int prevSelectedPosition = -1;
    private int selectedPosition = -1;
    private View changeDayNightView;
    private float changeDayNightViewProgress;
    private ValueAnimator changeDayNightViewAnimator;
    private OnShareDelegate onShareDelegate;

    public interface OnShareDelegate {
        void onShare();
    }

    public QrThemeBottomSheet(final BaseFragment parentFragment, ThemeDelegate themeDelegate, OnShareDelegate delegate) {
        super(parentFragment.getParentActivity(), true, themeDelegate);
        this.parentFragment = parentFragment;
        this.themeDelegate = themeDelegate;
        this.onShareDelegate = delegate;
        this.originalTheme = themeDelegate.getCurrentTheme();
        this.originalIsDark = Theme.getActiveTheme().isDark();
        adapter = new ChatThemeBottomSheet.Adapter(currentAccount, themeDelegate, ThemeSmallPreviewView.TYPE_QRCODE);
        setDimBehind(false);
        setCanDismissWithSwipe(false);
        setApplyBottomPadding(false);

        FrameLayout rootLayout = new FrameLayout(getContext());
        setCustomView(rootLayout);

        titleView = new TextView(getContext());
        titleView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        titleView.setLines(1);
        titleView.setSingleLine(true);
        titleView.setText("QR Code");
        titleView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleView.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(6), AndroidUtilities.dp(21), AndroidUtilities.dp(8));
        rootLayout.addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.START, 0, 0, 62, 0));

        int drawableColor = getThemedColor(Theme.key_featuredStickers_addButton);
        int drawableSize = AndroidUtilities.dp(28);
        darkThemeDrawable = new RLottieDrawable(R.raw.sun_outline, "" + R.raw.sun_outline, drawableSize, drawableSize, true, null);
        darkThemeDrawable.setPlayInDirectionOfCustomEndFrame(true);
        darkThemeDrawable.beginApplyLayerColors();
        setDarkButtonColor(drawableColor);
        darkThemeDrawable.commitApplyLayerColors();

        darkThemeView = new RLottieImageView(getContext());
        darkThemeView.setAnimation(darkThemeDrawable);
        darkThemeView.setScaleType(ImageView.ScaleType.CENTER);
        darkThemeView.setOnClickListener(view -> {
            if (changeDayNightViewAnimator != null) {
                return;
            }
            setupLightDarkTheme(!forceDark);
        });
        rootLayout.addView(darkThemeView, LayoutHelper.createFrame(44, 44, Gravity.TOP | Gravity.END, 0, 0, 7, 0));
        forceDark = !Theme.getActiveTheme().isDark();
        setForceDark(Theme.getActiveTheme().isDark(), false);

        scroller = new LinearSmoothScroller(getContext()) {
            @Override
            protected int calculateTimeForScrolling(int dx) {
                return super.calculateTimeForScrolling(dx) * 6;
            }
        };
        recyclerView = new RecyclerListView(getContext());
        recyclerView.setAdapter(adapter);
        recyclerView.setClipChildren(false);
        recyclerView.setClipToPadding(false);
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemAnimator(null);
        recyclerView.setNestedScrollingEnabled(false);
        recyclerView.setLayoutManager(layoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        recyclerView.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
        recyclerView.setOnItemClickListener((view, position) -> {
            if (adapter.items.get(position) == selectedItem || changeDayNightView != null) {
                return;
            }
            selectedItem = adapter.items.get(position);
            isLightDarkChangeAnimation = false;
            themeDelegate.setCurrentTheme(selectedItem.chatTheme, true, forceDark);
            adapter.setSelectedItem(position);
            containerView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
                    if (layoutManager != null) {
                        final int targetPosition = position > prevSelectedPosition
                                ? Math.min(position + 1, adapter.items.size() - 1)
                                : Math.max(position - 1, 0);
                        scroller.setTargetPosition(targetPosition);
                        layoutManager.startSmoothScroll(scroller);
                    }
                    prevSelectedPosition = position;
                }
            }, 100);
            for (int i = 0; i < recyclerView.getChildCount(); i++) {
                ThemeSmallPreviewView child = (ThemeSmallPreviewView) recyclerView.getChildAt(i);
                if (child != view) {
                    child.cancelAnimation();
                }
            }
            ((ThemeSmallPreviewView) view).playEmojiAnimation();
        });

        progressView = new FlickerLoadingView(getContext(), resourcesProvider);
        progressView.setViewType(FlickerLoadingView.CHAT_THEMES_TYPE);
        progressView.setVisibility(View.VISIBLE);
        rootLayout.addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 104, Gravity.START, 0, 44, 0, 0));

        rootLayout.addView(recyclerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 104, Gravity.START, 0, 44, 0, 0));

        applyButton = new View(getContext());
        applyButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(6), getThemedColor(Theme.key_featuredStickers_addButton), getThemedColor(Theme.key_featuredStickers_addButtonPressed)));
        applyButton.setEnabled(false);
        applyButton.setOnClickListener((view) -> shareCurrentQrCode());
        rootLayout.addView(applyButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.START, 16, 162, 16, 16));

        applyTextView = new TextView(getContext());
        applyTextView.setEllipsize(TextUtils.TruncateAt.END);
        applyTextView.setGravity(Gravity.CENTER);
        applyTextView.setLines(1);
        applyTextView.setSingleLine(true);
        applyTextView.setText("Share QR Code");
        applyTextView.setTextColor(getThemedColor(Theme.key_featuredStickers_buttonText));
        applyTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        applyTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        rootLayout.addView(applyTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.START, 16, 162, 16, 16));
    }

    @Override
    protected boolean onCustomMeasureContainerLayout(View view, int width, int height) {
        boolean isLandscape = parentFragment.getParentActivity().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        boolean isTablet = AndroidUtilities.isTablet();

        if (!isTablet && isLandscape) {
            view.setTranslationX(AndroidUtilities.dp(335 / 2f));
            view.measure(
                View.MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x - AndroidUtilities.dp(375), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST));
            return true;
        } else {
            view.setTranslationX(0);
        }

        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ChatThemeController.preloadAllWallpaperThumbs(true);
        ChatThemeController.preloadAllWallpaperThumbs(false);
        ChatThemeController.preloadAllWallpaperImages(true);
        ChatThemeController.preloadAllWallpaperImages(false);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);

        initAdapter();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setupLightDarkTheme(boolean isDark) {
        if (changeDayNightViewAnimator != null) {
            changeDayNightViewAnimator.cancel();
        }
        FrameLayout decorView1 = (FrameLayout) parentFragment.getParentActivity().getWindow().getDecorView();
        FrameLayout decorView2 = (FrameLayout) getWindow().getDecorView();
        Bitmap bitmap = Bitmap.createBitmap(decorView2.getWidth(), decorView2.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas bitmapCanvas = new Canvas(bitmap);
        darkThemeView.setAlpha(0f);
        decorView1.draw(bitmapCanvas);
        decorView2.draw(bitmapCanvas);
        darkThemeView.setAlpha(1f);

        Paint xRefPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        xRefPaint.setColor(0xff000000);
        xRefPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bitmapPaint.setFilterBitmap(true);
        int[] position = new int[2];
        darkThemeView.getLocationInWindow(position);
        float x = position[0];
        float y = position[1];
        float cx = x + darkThemeView.getMeasuredWidth() / 2f;
        float cy = y + darkThemeView.getMeasuredHeight() / 2f;

        float r = Math.max(bitmap.getHeight(), bitmap.getWidth()) * 0.9f;

        Shader bitmapShader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        bitmapPaint.setShader(bitmapShader);
        changeDayNightView = new View(getContext()) {

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                if (isDark) {
                    if (changeDayNightViewProgress > 0f) {
                        bitmapCanvas.drawCircle(cx, cy, r * changeDayNightViewProgress, xRefPaint);
                    }
                    canvas.drawBitmap(bitmap, 0, 0, bitmapPaint);
                } else {
                    canvas.drawCircle(cx, cy, r * (1f - changeDayNightViewProgress), bitmapPaint);
                }
                canvas.save();
                canvas.translate(x, y);
                darkThemeView.draw(canvas);
                canvas.restore();
            }
        };
        changeDayNightViewProgress = 0f;
        changeDayNightViewAnimator = ValueAnimator.ofFloat(0, 1f);
        changeDayNightViewAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                changeDayNightViewProgress = (float) valueAnimator.getAnimatedValue();
                changeDayNightView.invalidate();
            }
        });
        changeDayNightViewAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (changeDayNightView != null) {
                    if (changeDayNightView.getParent() != null) {
                        ((ViewGroup) changeDayNightView.getParent()).removeView(changeDayNightView);
                    }
                    changeDayNightView = null;
                }
                changeDayNightViewAnimator = null;
                super.onAnimationEnd(animation);
            }
        });
        changeDayNightViewAnimator.setDuration(400);
        changeDayNightViewAnimator.setInterpolator(Easings.easeInOutQuad);
        changeDayNightViewAnimator.start();

        decorView2.addView(changeDayNightView, new ViewGroup.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        AndroidUtilities.runOnUIThread(() -> {
            if (adapter == null || adapter.items == null) {
                return;
            }
            setForceDark(isDark, true);
            if (selectedItem != null) {
                isLightDarkChangeAnimation = true;
                themeDelegate.setCurrentTheme(selectedItem.chatTheme, false, isDark);
            }
            if (adapter != null && adapter.items != null) {
                for (int i = 0; i < adapter.items.size(); i++) {
                    adapter.items.get(i).themeIndex = isDark ? 2 : 0;
                }
                adapter.notifyDataSetChanged();
            }
        });
    }

    private void initAdapter() {
        ArrayList<ChatThemeBottomSheet.ChatThemeItem> themes = new ArrayList<>(Theme.defaultEmojiThemes);
        EmojiThemes currentTheme = themeDelegate.getCurrentTheme();

        for (int i = 0; i < themes.size(); ++i) {
            ChatThemeBottomSheet.ChatThemeItem chatTheme = themes.get(i);
            chatTheme.chatTheme.loadPreviewColors(currentAccount);
            chatTheme.themeIndex = forceDark ? 2 : 0;
        }

        adapter.setItems(themes);
        applyButton.setEnabled(true);
        recyclerView.setAlpha(0f);
        darkThemeView.setVisibility(View.VISIBLE);

        if (currentTheme != null) {
            for (int i = 0; i != themes.size(); ++i) {
                if (themes.get(i).chatTheme.getEmoticon().equals(currentTheme.getEmoticon())) {
                    selectedItem = themes.get(i);
                    selectedPosition = i;
                    break;
                }
            }

            if (selectedPosition != -1) {
                prevSelectedPosition = selectedPosition;
                adapter.setSelectedItem(selectedPosition);
                if (selectedPosition > 0 && selectedPosition < themes.size() / 2) {
                    selectedPosition -= 1;
                }
                int finalSelectedPosition = Math.min(selectedPosition, adapter.items.size() - 1);
                layoutManager.scrollToPositionWithOffset(finalSelectedPosition, 0);
            }
        } else {
            selectedPosition = 0;
            layoutManager.scrollToPositionWithOffset(0, 0);
        }

        if (selectedPosition > -1) {
            for (int i = 0; i < adapter.items.size(); i++) {
                adapter.items.get(i).isSelected = i == selectedPosition;
            }
            adapter.setSelectedItem(selectedPosition);
        }

        recyclerView.animate().alpha(1f).setDuration(150).start();
        progressView.animate().alpha(0f).setListener(new HideViewAfterAnimation(progressView)).setDuration(150).start();
    }

    private void setDarkButtonColor(int color) {
        darkThemeDrawable.setLayerColor("Sunny.**", color);
        darkThemeDrawable.setLayerColor("Path.**", color);
        darkThemeDrawable.setLayerColor("Path 10.**", color);
        darkThemeDrawable.setLayerColor("Path 11.**", color);
    }

    private void setForceDark(boolean isDark, boolean playAnimation) {
        useLightNavBar = isDark;
        useLightStatusBar = isDark;
        if (forceDark == isDark) {
            return;
        }
        forceDark = isDark;
        if (playAnimation) {
            darkThemeDrawable.setCustomEndFrame(isDark ? darkThemeDrawable.getFramesCount() : 0);
            darkThemeView.playAnimation();
        } else {
            darkThemeDrawable.setCurrentFrame(isDark ? darkThemeDrawable.getFramesCount() - 1 : 0, false, true);
            darkThemeView.invalidate();
        }
    }

    private void shareCurrentQrCode() {
        this.onShareDelegate.onShare();
    }

    @Override
    public void onBackPressed() {
        dismiss();
    }

    @Override
    public void dismiss() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        super.dismiss();
        themeDelegate.setCurrentTheme(originalTheme, true, originalIsDark);
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();
        themeDescriptions.add(new ThemeDescription(containerView, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_dialogBackground));
        themeDescriptions.add(new ThemeDescription(titleView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_dialogTextBlack));
        themeDescriptions.add(new ThemeDescription(recyclerView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{ThemeSmallPreviewView.class}, null, null, null, Theme.key_dialogBackgroundGray));
        themeDescriptions.add(new ThemeDescription(applyButton, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_featuredStickers_addButton));
        themeDescriptions.add(new ThemeDescription(applyButton, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_featuredStickers_addButtonPressed));
        for (ThemeDescription description : themeDescriptions) {
            description.resourcesProvider = themeDelegate;
        }
        return themeDescriptions;
    }
}
