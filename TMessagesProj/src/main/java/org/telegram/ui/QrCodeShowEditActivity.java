/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.core.content.FileProvider;
import androidx.core.graphics.ColorUtils;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter2;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatThemeController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.EmojiThemes;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ChatThemeBottomSheet;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MotionBackgroundDrawable;
import org.telegram.ui.Components.QrThemeBottomSheet;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QrCodeShowEditActivity extends BaseFragment {

    public SizeNotifierFrameLayout contentView;
    private BackupImageView backgroundImage;
    private RLottieImageView iconImage;
    private AvatarDrawable avatarDrawable;
    private ImageReceiver avatarImage;
    private QrCodeDrawable qrDrawable;

    private final long dialogId;
    private String username;
    private final boolean showBottomSheet;

    QrThemeBottomSheet bottomSheet;
    private TLRPC.User currentUser;
    private TLRPC.Chat currentChat;
    private ThemeDelegate themeDelegate;

    public QrCodeShowEditActivity(long dialogId) {
        this.dialogId = dialogId;
        if (dialogId > 0) {
            final TLRPC.User user = getMessagesController().getUser(dialogId);
            if (user != null && user.username != null) {
                username = user.username;
            }
        } else if (dialogId < 0) {
            final TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
            if (chat != null && chat.username != null) {
                username = chat.username;
            }
        }

        if (dialogId > 0) {
            currentUser = getMessagesController().getUser(dialogId);
        } else if (dialogId < 0) {
            currentChat = getMessagesController().getChat(-dialogId);
        }

        showBottomSheet = UserObject.isUserSelf(currentUser);
        themeDelegate = new ThemeDelegate();
    }

    @Override
    public View createView(Context context) {
        FrameLayout frameLayout = new FrameLayout(context) {
            @Override
            public void invalidate() {
                contentView.invalidate();
                super.invalidate();
            }
        };

        contentView = new SizeNotifierFrameLayout(context, parentLayout) {
            @Override
            protected void onDraw(Canvas canvas) {
                setBackgroundTranslation(0);
                super.onDraw(canvas);
            }

            @Override
            public void invalidate() {
                super.invalidate();
                backgroundImage.invalidate();
            }

            @Override
            protected Drawable getNewDrawable() {
                Drawable drawable = themeDelegate.getWallpaperDrawable();

                if (drawable instanceof MotionBackgroundDrawable) {
                    ((MotionBackgroundDrawable) drawable).switchToNextPosition();
                    themeDelegate.getQrCodeBackgroundDrawable().switchToNextPosition();
                    invalidate();
                }

                return drawable != null ? drawable : super.getNewDrawable();
            }

            @Override
            protected boolean isActionBarVisible() {
                return false;
            }
        };
        contentView.setOccupyStatusBar(false);
        contentView.setOnTouchListener((v, event) -> true);

        fragmentView = frameLayout;
        frameLayout.addView(contentView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        frameLayout.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        iconImage = new RLottieImageView(context);
        iconImage.setAutoRepeat(true);
        iconImage.setAnimation(R.raw.qr_logo_without_bg, AndroidUtilities.dp(48), AndroidUtilities.dp(48));
        iconImage.playAnimation();

        avatarDrawable = new AvatarDrawable();
        avatarImage = new ImageReceiver(contentView);
        avatarImage.setRoundRadius(AndroidUtilities.dp(42));

        if (currentUser != null) {
            avatarDrawable.setInfo(currentUser);
            avatarImage.setForUserOrChat(currentUser, avatarDrawable);
        } else if (currentChat != null) {
            avatarDrawable.setInfo(currentChat);
            avatarImage.setForUserOrChat(currentChat, avatarDrawable);
        }


        qrDrawable = new QrCodeDrawable(contentView);
        qrDrawable.setCords(0, 0, AndroidUtilities.dp(260), AndroidUtilities.dp(330));
        qrDrawable.setUsername(username);

        backgroundImage = new BackupImageView(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                boolean inLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
                drawFragment(canvas, getMeasuredWidth(), getMeasuredHeight(), false, AndroidUtilities.isTablet() || inLandscape, inLandscape && !AndroidUtilities.isTablet());
            }
        };
        contentView.setBackgroundImage(Theme.getCachedWallpaper(), false);
        contentView.addView(backgroundImage, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 0));
        contentView.addView(iconImage, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.TOP));

        if (showBottomSheet) {
            bottomSheet = new QrThemeBottomSheet(this, themeDelegate, new QrThemeBottomSheet.OnShareDelegate() {
                @Override
                public void onShare() {
                    final int width = Math.min(contentView.getMeasuredWidth(), contentView.getMeasuredHeight());
                    final int height = Math.max(contentView.getMeasuredWidth(), contentView.getMeasuredHeight());

                    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(bitmap);
                    drawFragment(canvas, width, height, true, true, false);

                    File imagesFolder = new File(AndroidUtilities.getCacheDir(), "qrcodes");
                    imagesFolder.mkdirs();
                    File file = new File(imagesFolder, "shared_image.png");

                    try {
                        FileOutputStream stream = new FileOutputStream(file);
                        bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream);
                        stream.flush();
                        stream.close();
                    } catch (Exception e) {
                        return;
                    }

                    Uri uri = FileProvider.getUriForFile(context, ApplicationLoader.applicationContext.getPackageName() + ".provider", file);

                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.putExtra(Intent.EXTRA_STREAM, uri);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    intent.setType("image/png");
                    getParentActivity().startActivity(intent);
                }
            }) {
                @Override
                public void dismiss() {
                    super.dismiss();
                    AndroidUtilities.runOnUIThread(() -> finishFragment());
                }
            };
            showDialog(bottomSheet);
        };

        return fragmentView;
    }

    private boolean drawFragment(Canvas canvas, int width, int height, boolean forShare, boolean inCenter, boolean leftOffset) {
        if (inCenter) {
            contentView.getBackgroundImage().setBounds(0, 0, width, height);
            contentView.getBackgroundImage().draw(canvas);
        }

        final float qrBackgroundWidth = AndroidUtilities.dp(260);
        final float qrBackgroundHeight = AndroidUtilities.dp(330);

        final float rectStartX = leftOffset ? AndroidUtilities.dp(75) : (width - qrBackgroundWidth) / 2;
        float rectStartY = (height - qrBackgroundHeight - AndroidUtilities.dp(showBottomSheet?240:0) - AndroidUtilities.dp(49) - AndroidUtilities.statusBarHeight) / 2;

        if (inCenter) {
            rectStartY = (height - qrBackgroundHeight) / 2f;
        }

        float addY = !inCenter ? AndroidUtilities.dp(49) + AndroidUtilities.statusBarHeight : 0;


        qrDrawable.setCords(rectStartX, rectStartY + addY, qrBackgroundWidth, qrBackgroundHeight);
        qrDrawable.draw(canvas);

        if (height > AndroidUtilities.dp(420)) {
            avatarImage.setImageCoords(
                    (width - AndroidUtilities.dp(84)) / 2f,
                    rectStartY - AndroidUtilities.dp(50) + addY,
                    AndroidUtilities.dp(84), AndroidUtilities.dp(84));
            avatarImage.draw(canvas);
        }

        if (forShare) {
            int x = (int)(rectStartX + (qrBackgroundWidth - AndroidUtilities.dp(48)) / 2f);
            int y = (int)(rectStartY + AndroidUtilities.dp(132) + addY);

            iconImage.getAnimatedDrawable().setCurrentFrame(39, false);
            iconImage.getAnimatedDrawable().setBounds(x, y, x + AndroidUtilities.dp(48), y + AndroidUtilities.dp(48));
            iconImage.getAnimatedDrawable().draw(canvas);
        } else {
            iconImage.setTranslationX(rectStartX + (qrBackgroundWidth - AndroidUtilities.dp(48)) / 2f);
            iconImage.setTranslationY(rectStartY + AndroidUtilities.dp(130) + addY);
        }

        return true;
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(fragmentView, 0, null, null, null, null, Theme.key_chat_wallpaper));
        themeDescriptions.add(new ThemeDescription(fragmentView, 0, null, null, null, null, Theme.key_chat_wallpaper_gradient_to1));
        themeDescriptions.add(new ThemeDescription(fragmentView, 0, null, null, null, null, Theme.key_chat_wallpaper_gradient_to2));
        themeDescriptions.add(new ThemeDescription(fragmentView, 0, null, null, null, null, Theme.key_chat_wallpaper_gradient_to3));

        //themeDescriptions.add(new ThemeDescription(fragmentView, 0, null, null, new Drawable[]{themeDelegate.getQrCodeBackgroundDrawable()}, null, Theme.key_chat_qrcode));
        //themeDescriptions.add(new ThemeDescription(fragmentView, 0, null, null, new Drawable[]{themeDelegate.getQrCodeBackgroundDrawable()}, null, Theme.key_chat_qrcode_gradient_to1));
        //themeDescriptions.add(new ThemeDescription(fragmentView, 0, null, null, new Drawable[]{themeDelegate.getQrCodeBackgroundDrawable()}, null, Theme.key_chat_qrcode_gradient_to2));
        //themeDescriptions.add(new ThemeDescription(fragmentView, 0, null, null, new Drawable[]{themeDelegate.getQrCodeBackgroundDrawable()}, null, Theme.key_chat_qrcode_gradient_to3));

        for (ThemeDescription description : themeDescriptions) {
            description.resourcesProvider = themeDelegate;
        }

        return themeDescriptions;
    }

    @Override
    protected ActionBar createActionBar(Context context) {
        ActionBar actionBar = new ActionBar(context);
        actionBar.setBackgroundColor(Color.TRANSPARENT);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);

        actionBar.setItemsColor(Theme.getColor(Theme.key_actionBarDefaultIcon), false);
        actionBar.setItemsBackgroundColor(Theme.ACTION_BAR_WHITE_SELECTOR_COLOR, false);
        actionBar.setCastShadows(false);
        actionBar.setAddToContainer(false);
        actionBar.setClipContent(true);
        if (AndroidUtilities.isTablet()) {
            actionBar.setOccupyStatusBar(false);
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
            if (id == -1) {
                finishFragment();
            }
            }
        });

        return actionBar;
    }

    public class QrCodeDrawable {
        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Paint qrcodePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private StaticLayout usernameLayout;
        private View parentView;
        private RectF rect = new RectF();

        float x = 0f;
        float y = 0f;
        float width = 0f;
        float height = 0f;
        float imageSize;

        String link;
        Bitmap qrCode;
        Bitmap textBitmap;
        private Drawable shadowDrawable;

        public QrCodeDrawable(View view) {
            parentView = view;
            backgroundPaint.setColor(Color.WHITE);
            textPaint.setColor(Color.BLACK);
            textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rc_bold.ttf"));
            qrcodePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));

            shadowDrawable = parentView.getContext().getResources().getDrawable(R.drawable.lock_round_shadow).mutate();
            shadowDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelVoiceLockShadow), PorterDuff.Mode.MULTIPLY));
        }

        public void setUsername(String username) {
            if (username == null) {
                return;
            }

            final String username_ = "@" + username.toUpperCase();

            final float maxWidth = AndroidUtilities.dp(220);
            float textWidth = 0;
            float span = AndroidUtilities.dp(23);
            int icon = R.drawable.qr_at_large;

            textPaint.setTextSize(AndroidUtilities.dp(30));
            textWidth = textPaint.measureText(username_) + span;

            if (textWidth > maxWidth) {
                span = AndroidUtilities.dp(18);
                textPaint.setTextSize(AndroidUtilities.dp(24));
                textWidth = textPaint.measureText(username_) + span;
                icon = R.drawable.qr_at_medium;
            }

            if (textWidth > maxWidth) {
                span = AndroidUtilities.dp(14);
                textPaint.setTextSize(AndroidUtilities.dp(18));
                icon = R.drawable.qr_at_small;
            }

            SpannableStringBuilder builder = new SpannableStringBuilder();
            builder.append(username_);
            builder.setSpan(new ImageSpan(parentView.getContext(), icon), 0, 1, Spannable.SPAN_POINT_POINT);

            usernameLayout = new StaticLayout(builder, textPaint, (int) maxWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);

            textBitmap = Bitmap.createBitmap((int) width, (int) height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(textBitmap);
            canvas.save();
            canvas.translate(AndroidUtilities.dp(20), height - AndroidUtilities.dp(75 / 2f));
            canvas.translate(0, -(usernameLayout.getHeight() / 2f));
            usernameLayout.draw(canvas);
            canvas.restore();

            link = "https://" + MessagesController.getInstance(UserConfig.selectedAccount).linkPrefix + "/" + username;

            Bitmap src = createQR(parentView.getContext(), link);
            Bitmap dest = Bitmap.createBitmap(
                    src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);

            for (int x = 0; x < src.getWidth(); x++) {
                for (int y = 0; y < src.getHeight(); y++) {
                    int pixelColor = src.getPixel(x, y);
                    int pixelWhite = Color.red(pixelColor);
                    int newPixel = Color.argb(
                            255 - pixelWhite, 0, 0, 0);

                    dest.setPixel(x, y, newPixel);
                }
            }

            qrCode = dest;
        }

        public void setCords(float x, float y, float width, float height) {
            this.width = width;
            this.height = height;
            this.x = x;
            this.y = y;
        }

        Path path = new Path();

        private void drawQrColorPattern(Canvas canvas) {
            rect.set(x + 1, y + 1, x + width - 1, y + height - 1);
            path.reset();
            path.addRoundRect(rect, AndroidUtilities.dp(16), AndroidUtilities.dp(16), Path.Direction.CW);
            path.close();

            int a = canvas.save();
            canvas.clipPath(path);
            canvas.translate(x, y);
            themeDelegate.getQrCodeBackgroundDrawable().setBounds(0, 0, (int) width, (int) height);
            themeDelegate.getQrCodeBackgroundDrawable().draw(canvas);
            canvas.restoreToCount(a);
        }

        public boolean draw(Canvas canvas) {
            rect.set(x, y, x + width, y + height);
            if (qrCode == null || usernameLayout == null) {
                return false;
            }

            drawQrColorPattern(canvas);
            canvas.saveLayerAlpha(rect, 255, Canvas.ALL_SAVE_FLAG);
            canvas.drawRoundRect(rect, AndroidUtilities.dp(16), AndroidUtilities.dp(16), backgroundPaint);
            shadowDrawable.setBounds(
                (int)(rect.left - AndroidUtilities.dp(3)),
                (int)(rect.top - AndroidUtilities.dp(3)),
                (int)(rect.right + AndroidUtilities.dp(3)),
                (int)(rect.bottom + AndroidUtilities.dp(3)));
            shadowDrawable.draw(canvas);

            canvas.save();
            canvas.drawBitmap(textBitmap, x, y, qrcodePaint);
            canvas.restore();

            canvas.save();
            canvas.translate(
                (int)(x + AndroidUtilities.dp(30) - (qrCode.getWidth() - AndroidUtilities.dp(200)) / 2f),
                (int)(y + AndroidUtilities.dp(55) - (qrCode.getWidth() - AndroidUtilities.dp(200)) / 2f));
            canvas.drawBitmap(qrCode, 0, 0, qrcodePaint);
            canvas.restore();

            canvas.restore();

            return true;
        }

        private Bitmap createQR(Context context, String key) {
            try {
                HashMap<EncodeHintType, Object> hints = new HashMap<>();
                hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
                hints.put(EncodeHintType.MARGIN, 0);
                QRCodeWriter2 writer = new QRCodeWriter2();
                Bitmap bitmap = writer.encode(key, BarcodeFormat.QR_CODE, AndroidUtilities.dp(200), AndroidUtilities.dp(200), hints, null, context);
                imageSize = writer.getImageSize();
                return bitmap;
            } catch (Exception e) {
                FileLog.e(e);
            }
            return null;
        }
    }

    private class ThemeDelegate extends QrThemeBottomSheet.ThemeDelegate {
        private HashMap<String, Integer> currentColors = new HashMap<>();
        private HashMap<String, Integer> animatingColors;
        private EmojiThemes chatTheme;
        private Drawable backgroundDrawable;
        private ValueAnimator patternIntensityAnimator;
        private int currentColor;
        private boolean isDark;
        private AnimatorSet patternAlphaAnimator;

        private MotionBackgroundDrawable qrCodeBackgroundDrawable;
        HashMap<String, Integer[]> dayQrPatternHashMap = new HashMap<>();
        HashMap<String, Integer[]> nightQrPatternHashMap = new HashMap<>();

        ThemeDelegate() {
            qrCodeBackgroundDrawable = new MotionBackgroundDrawable();
            qrCodeBackgroundDrawable.setInterpolator(null);
            qrCodeBackgroundDrawable.setSlowMode(true);

            dayQrPatternHashMap.put("\uD83C\uDFE0", new Integer[]{0xFF71B654, 0xFF2C9077, 0xFF9ABB3E, 0xFF68B55E});
            dayQrPatternHashMap.put("\uD83D\uDC25", new Integer[]{0xFF43A371, 0xFF8ABD4C, 0xFF9DB139, 0xFF85B950});
            dayQrPatternHashMap.put("⛄", new Integer[]{0xFF66A1FF, 0xFF59B5EE, 0xFF41BAD2, 0xFF8A97FF});
            dayQrPatternHashMap.put("\uD83D\uDC8E", new Integer[]{0xFF5198F5, 0xFF4BB7D2, 0xFFAD79FB, 0xFFDF86C7});
            dayQrPatternHashMap.put("\uD83D\uDC68\u200D\uD83C\uDFEB", new Integer[]{0xFF9AB955, 0xFF48A896, 0xFF369ADD, 0xFF5DC67B});
            dayQrPatternHashMap.put("\uD83C\uDF37", new Integer[]{0xFFEE8044, 0xFFE19B23, 0xFFE55D93, 0xFFCB75D7});
            dayQrPatternHashMap.put("\uD83D\uDC9C", new Integer[]{0xFFEE597E, 0xFFE35FB2, 0xFFAD69F2, 0xFFFF9257});
            dayQrPatternHashMap.put("\uD83C\uDF84", new Integer[]{0xFFEC7046, 0xFFF79626, 0xFFE3761C, 0xFFF4AA2A});
            dayQrPatternHashMap.put("\uD83C\uDFAE", new Integer[]{0xFF19B3D2, 0xFFDC62F4, 0xFFE64C73, 0xFFECA222});

            nightQrPatternHashMap.put("\uD83C\uDFE0", new Integer[]{0xFF157FD1, 0xFF4A6CF2, 0xFF1876CD, 0xFF2CA6CE});
            nightQrPatternHashMap.put("\uD83D\uDC25", new Integer[]{0xFF57A518, 0xFF1E7650, 0xFF6D9B17, 0xFF3FAB55});
            nightQrPatternHashMap.put("⛄", new Integer[]{0xFF2B6EDA, 0xFF2F7CB6, 0xFF1DA6C9, 0xFF6B7CFF});
            nightQrPatternHashMap.put("\uD83D\uDC8E", new Integer[]{0xFFB256B8, 0xFF6F52FF, 0xFF249AC2, 0xFF347AD5});
            nightQrPatternHashMap.put("\uD83D\uDC68\u200D\uD83C\uDFEB", new Integer[]{0xFF238B68, 0xFF73A163, 0xFF15AC7F, 0xFF0E8C95});
            nightQrPatternHashMap.put("\uD83C\uDF37", new Integer[]{0xFFD95454, 0xFFD2770F, 0xFFCE4661, 0xFFAC5FC8});
            nightQrPatternHashMap.put("\uD83D\uDC9C", new Integer[]{0xFFD058AA, 0xFFE0743E, 0xFFD85568, 0xFFA369D3});
            nightQrPatternHashMap.put("\uD83C\uDF84", new Integer[]{0xFFD6681F, 0xFFCE8625, 0xFFCE6D30, 0xFFC98A1D});
            nightQrPatternHashMap.put("\uD83C\uDFAE", new Integer[]{0xFFC74343, 0xFFEC7F36, 0xFF06B0F9, 0xFFA347FF});

            ArrayList<ChatThemeBottomSheet.ChatThemeItem> themes = new ArrayList<>(Theme.defaultEmojiThemes);

            isDark = Theme.getActiveTheme().isDark();
            chatTheme = ChatThemeController.getInstance(currentAccount).getDialogTheme(dialogId);
            if (chatTheme != null) {
                setupChatTheme(chatTheme, false, true);
            } else if (!themes.isEmpty()) {
                setupChatTheme(themes.get(0).chatTheme, false, true);
            }
        }

        public MotionBackgroundDrawable getQrCodeBackgroundDrawable() {
            return qrCodeBackgroundDrawable;
        }

        public int[] getPatternQrColors(String emoji, boolean isDark) {
            HashMap<String, Integer[]> map = isDark ? nightQrPatternHashMap : dayQrPatternHashMap;
            Integer[] colors = map.get(emoji);

            if (colors != null) {
                return new int[]{colors[0], colors[1], colors[2], colors[3]};
            } else {
                return new int[]{0xFF000000, 0xFF000000, 0xFF000000, 0xFF000000};
            }
        }

        @Override
        public Integer getColor(String key) {
            if (chatTheme == null) {
                return Theme.getColor(key);
            }
            if (animatingColors != null) {
                Integer color = animatingColors.get(key);
                if (color != null) {
                    return color;
                }
            }
            Integer color = currentColors.get(key);

            if (color == null) {
                if (Theme.key_chat_qrcode.equals(key)) {
                    color = getPatternQrColors(chatTheme.getEmoticon(), isDark)[0];
                } else if (Theme.key_chat_qrcode_gradient_to1.equals(key)) {
                    color = getPatternQrColors(chatTheme.getEmoticon(), isDark)[1];
                } else if (Theme.key_chat_qrcode_gradient_to2.equals(key)) {
                    color = getPatternQrColors(chatTheme.getEmoticon(), isDark)[2];
                } else if (Theme.key_chat_qrcode_gradient_to3.equals(key)) {
                    color = getPatternQrColors(chatTheme.getEmoticon(), isDark)[3];
                }
            }

            if (color == null) {
                if (Theme.key_chat_outBubbleGradient1.equals(key) || Theme.key_chat_outBubbleGradient2.equals(key) || Theme.key_chat_outBubbleGradient3.equals(key)) {
                    color = currentColors.get(Theme.key_chat_outBubble);
                    if (color == null) {
                        color = Theme.getColorOrNull(key);
                    }
                    if (color == null) {
                        color = Theme.getColor(Theme.key_chat_outBubble);
                    }
                }
                if (color == null) {
                    String fallbackKey = Theme.getFallbackKey(key);
                    if (fallbackKey != null) {
                        color = currentColors.get(fallbackKey);
                    }
                }
            }
            if (color == null) {
                if (chatTheme != null) {
                    color = Theme.getDefaultColor(key);
                }
            }
            return color;
        }

        @Override
        public Integer getCurrentColor(String key) {
            return getCurrentColor(key, false);
        }

        public Integer getCurrentColor(String key, boolean ignoreAnimation) {
            if (chatTheme == null) {
                return Theme.getColorOrNull(key);
            }
            Integer color = null;
            if (!ignoreAnimation && animatingColors != null) {
                color = animatingColors.get(key);
            }
            if (color == null) {
                color = currentColors.get(key);
            }
            return color;
        }

        @Override
        public void setAnimatedColor(String key, int color) {
            if (animatingColors != null) {
                animatingColors.put(key, color);
            }
        }

        public int getCurrentColor() {
            return chatTheme != null ? currentColor : Theme.currentColor;
        }

        public EmojiThemes getCurrentTheme() {
            return chatTheme;
        }

        public Drawable getWallpaperDrawable() {
            return backgroundDrawable != null ? backgroundDrawable : Theme.getCachedWallpaperNonBlocking();
        }

        public void setCurrentTheme(final EmojiThemes chatTheme, boolean animated, Boolean forceDark) {
            if (parentLayout == null) {
                return;
            }

            boolean newIsDark = forceDark != null ? forceDark : Theme.getActiveTheme().isDark();
            String newEmoticon = chatTheme != null ? chatTheme.getEmoticon() : null;
            String oldEmoticon = this.chatTheme != null ? this.chatTheme.getEmoticon() : null;
            if (TextUtils.equals(oldEmoticon, newEmoticon) && this.isDark == newIsDark) {
                return;
            }
            this.isDark = newIsDark;

            Theme.ThemeInfo currentTheme = newIsDark ? Theme.getCurrentNightTheme() : Theme.getCurrentTheme();
            ActionBarLayout.ThemeAnimationSettings animationSettings = new ActionBarLayout.ThemeAnimationSettings(currentTheme, currentTheme.currentAccentId, currentTheme.isDark(), !animated);

            if (this.chatTheme == null) {
                Drawable background = Theme.getCachedWallpaperNonBlocking();
            }
            if (chatTheme != null) {
                int[] colors = AndroidUtilities.calcDrawableColor(backgroundDrawable);
                currentColor = colors[0];
            }

            animationSettings.applyTheme = false;
            animationSettings.afterStartDescriptionsAddedRunnable = () -> {
                setupChatTheme(chatTheme, animated, false);
            };
            if (animated) {
                animationSettings.beforeAnimationRunnable = () -> {
                    animatingColors = new HashMap<>();
                    contentView.invalidate();
                };
                animationSettings.afterAnimationRunnable = () -> {
                    animatingColors = null;
                };
            }
            animationSettings.onlyTopFragment = true;
            animationSettings.resourcesProvider = this;
            animationSettings.duration = 250;
            parentLayout.animateThemedValues(animationSettings);
        }

        private void setupChatTheme(EmojiThemes chatTheme, boolean withAnimation, boolean createNewResources) {
            /*if (isDefaultTheme(chatTheme)) {
                setupChatTheme(null, withAnimation, createNewResources);
                return;
            }*/

            this.chatTheme = chatTheme;

            Drawable prevDrawable = null;
            if (fragmentView != null) {
                prevDrawable = contentView.getBackgroundImage();
            }
            final MotionBackgroundDrawable prevMotionDrawable = (prevDrawable instanceof MotionBackgroundDrawable) ? (MotionBackgroundDrawable) prevDrawable : null;
            final int prevPhase = prevMotionDrawable != null ? prevMotionDrawable.getPhase() : 0;
            final float prevPos = prevMotionDrawable != null ? prevMotionDrawable.getPosAnimationProgress() : 0f;

            if (chatTheme == null) {
                currentColor = Theme.getServiceMessageColor();
            }
            if (chatTheme == null) {
                currentColors = new HashMap<>();
                Drawable wallpaper = Theme.getCachedWallpaperNonBlocking();
                if (wallpaper instanceof MotionBackgroundDrawable) {
                    ((MotionBackgroundDrawable) wallpaper).setPhase(prevPhase);
                    ((MotionBackgroundDrawable) wallpaper).setPosAnimationProgress(prevPos);
                }
                backgroundDrawable = null;

                Theme.ThemeInfo activeTheme;
                if (Theme.getActiveTheme().isDark() == isDark) {
                    activeTheme = Theme.getActiveTheme();
                } else {
                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("themeconfig", Activity.MODE_PRIVATE);
                    String dayThemeName = preferences.getString("lastDayTheme", "Blue");
                    if (Theme.getTheme(dayThemeName) == null || Theme.getTheme(dayThemeName).isDark()) {
                        dayThemeName = "Blue";
                    }
                    String nightThemeName = preferences.getString("lastDarkTheme", "Dark Blue");
                    if (Theme.getTheme(nightThemeName) == null || !Theme.getTheme(nightThemeName).isDark()) {
                        nightThemeName = "Dark Blue";
                    }
                    activeTheme = isDark ? Theme.getTheme(nightThemeName) : Theme.getTheme(dayThemeName);
                }

                Theme.applyTheme(activeTheme, false, isDark);
            } else {
                currentColors = chatTheme.createColors(currentAccount, getThemeIndex(chatTheme, isDark));
                backgroundDrawable = getBackgroundDrawableFromTheme(chatTheme, prevPhase, prevPos);

                if (patternAlphaAnimator != null) {
                    patternAlphaAnimator.cancel();
                }
                if (withAnimation) {
                    patternAlphaAnimator = new AnimatorSet();
                    if (prevMotionDrawable != null) {
                        ValueAnimator valueAnimator = ValueAnimator.ofFloat(1f, 0f);
                        valueAnimator.addUpdateListener(animator -> prevMotionDrawable.setPatternAlpha((float) animator.getAnimatedValue()));
                        valueAnimator.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                super.onAnimationEnd(animation);
                                prevMotionDrawable.setPatternAlpha(1f);
                            }
                        });
                        valueAnimator.setDuration(200);
                        patternAlphaAnimator.playTogether(valueAnimator);
                    }
                    if (backgroundDrawable instanceof MotionBackgroundDrawable) {
                        final MotionBackgroundDrawable currentBackgroundDrawable = (MotionBackgroundDrawable) backgroundDrawable;
                        currentBackgroundDrawable.setPatternAlpha(0f);
                        ValueAnimator valueAnimator = ValueAnimator.ofFloat(0f, 1f);
                        valueAnimator.addUpdateListener(animator -> currentBackgroundDrawable.setPatternAlpha((float) animator.getAnimatedValue()));
                        valueAnimator.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                super.onAnimationEnd(animation);
                                currentBackgroundDrawable.setPatternAlpha(1f);
                            }
                        });
                        valueAnimator.setDuration(250);
                        patternAlphaAnimator.playTogether(valueAnimator);
                    }
                    patternAlphaAnimator.start();
                }

                if (createNewResources) {
                    int[] colors = AndroidUtilities.calcDrawableColor(backgroundDrawable);
                    currentColor = colors[0];
                }

            }
        }

        private boolean isDefaultTheme(EmojiThemes chatTheme) {
            return chatTheme != null && chatTheme.getEmoticon().equals("\uD83C\uDFE0");
        }

        private void updateQrPatternColors(int prevPhase, float prevPos) {
            Integer qrGradientColor0 = getColor(Theme.key_chat_qrcode);
            Integer qrGradientColor1 = getColor(Theme.key_chat_qrcode_gradient_to1);
            Integer qrGradientColor2 = getColor(Theme.key_chat_qrcode_gradient_to2);
            Integer qrGradientColor3 = getColor(Theme.key_chat_qrcode_gradient_to3);

            qrCodeBackgroundDrawable.setColors(qrGradientColor0, qrGradientColor1, qrGradientColor2, qrGradientColor3);
            qrCodeBackgroundDrawable.setPhase(prevPhase);
            qrCodeBackgroundDrawable.setPosAnimationProgress(prevPos);
        }

        private Drawable getBackgroundDrawableFromTheme(EmojiThemes chatTheme, int prevPhase, float prevPos) {
            boolean isDefault = isDefaultTheme(chatTheme);
            updateQrPatternColors(prevPhase, prevPos);

            int backgroundColor = isDefault ? (isDark ? 0xFF5FA5A1 : chatTheme.getThemeItem(getThemeIndex(chatTheme, false)).patternBgColor) : getColor(Theme.key_chat_wallpaper);
            int gradientColor1 = isDefault ? (isDark ? 0xFF2158A0 : chatTheme.getThemeItem(getThemeIndex(chatTheme, false)).patternBgGradientColor1) : getColor(Theme.key_chat_wallpaper_gradient_to1);
            int gradientColor2 = isDefault ? (isDark ? 0xFF53639F : chatTheme.getThemeItem(getThemeIndex(chatTheme, false)).patternBgGradientColor2) : getColor(Theme.key_chat_wallpaper_gradient_to2);
            int gradientColor3 = isDefault ? (isDark ? 0xFF3B86B7 : chatTheme.getThemeItem(getThemeIndex(chatTheme, false)).patternBgGradientColor3) : getColor(Theme.key_chat_wallpaper_gradient_to3);

            Drawable drawable;
            MotionBackgroundDrawable motionDrawable = isDefault ? (MotionBackgroundDrawable) Theme.createDefaultWallpaper() : new MotionBackgroundDrawable();
            motionDrawable.setInterpolator(null);
            motionDrawable.setSlowMode(true);
            motionDrawable.setColors(backgroundColor, gradientColor1, gradientColor2, gradientColor3, 0,true);
            motionDrawable.setPatternBitmap(isDefault ? (isDark ? -50 : 50) : chatTheme.getWallpaper(getThemeIndex(chatTheme, isDark)).settings.intensity);
            motionDrawable.setPhase(prevPhase);
            motionDrawable.setPosAnimationProgress(prevPos);

            final int defaultPatternColor = MotionBackgroundDrawable.getPatternColor(0xFF5FA5A1, 0xFF2158A0, 0xFF53639F, 0xFF3B86B7);
            final int patternColor = isDefault ? defaultPatternColor : motionDrawable.getPatternColor();

            if (!isDefault) {
                final boolean isDarkTheme = isDark;
                chatTheme.loadWallpaper(getThemeIndex(chatTheme, isDark), pair -> {
                    if (pair == null) {
                        return;
                    }
                    long themeId = pair.first;
                    Bitmap bitmap = pair.second;
                    if (this.chatTheme != null && themeId == this.chatTheme.getTlTheme(getThemeIndex(this.chatTheme, isDark)).id && bitmap != null) {
                        if (patternIntensityAnimator != null) {
                            patternIntensityAnimator.cancel();
                        }
                        int intensity = chatTheme.getWallpaper(getThemeIndex(chatTheme, isDarkTheme)).settings.intensity;
                        motionDrawable.setPatternBitmap(intensity, bitmap);
                        motionDrawable.setPatternColorFilter(patternColor);
                        patternIntensityAnimator = ValueAnimator.ofFloat(0, 1f);
                        patternIntensityAnimator.addUpdateListener(animator -> {
                            float value = (float) animator.getAnimatedValue();
                            motionDrawable.setPatternAlpha(value);
                        });
                        patternIntensityAnimator.setDuration(250);
                        patternIntensityAnimator.start();
                    }
                });
            } else {
                motionDrawable.setPatternColorFilter(patternColor);
            }

            drawable = motionDrawable;
            return drawable;
        }

        private int getThemeIndex(EmojiThemes themes, boolean isDark) {
            if (themes.getThemeItems().size() > 2) {
                return isDark ? 2 : 0;
            } else {
                return isDark ? 1 : 0;
            }
        }
    }
}
