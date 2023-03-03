package org.telegram.ui.Components.voip;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.utils.AnimationUtilities;
import org.telegram.messenger.voip.EncryptionKeyEmojifier;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.VoIPFragment;

import java.io.ByteArrayOutputStream;

@SuppressLint("ViewConstructor")
public class VoipEmojiLayout extends VoIPBackground.BackgroundedView implements NotificationCenter.NotificationCenterDelegate {
    private final VoIPFragment.BooleanAnimation emojiReady = new VoIPFragment.BooleanAnimation(this::checkEmojiLayout);

    public final LinearLayout emojiLayout;
    private final EmojiView[] emojiViews = new EmojiView[4];
    private final Drawable[] emojiDrawables = new Drawable[4];

    public final LinearLayout emojiRationalLayout;
    public final TextView hideEmojiTextView;

    private final Path emojiRationalBackgroundPath = new Path();
    private final RectF emojiRationalBackgroundRectStart = new RectF();
    private final RectF emojiRationalBackgroundRectEnd = new RectF();
    private final RectF emojiRationalBackgroundRect = new RectF();
    private final Path hideEmojiBackgroundPath = new Path();
    private final RectF hideEmojiBackgroundRect = new RectF();

    private Runnable onEmojiLoadedListener;

    public boolean emojiLoaded;
    private final int currentAccount;

    public VoipEmojiLayout(Context context, VoIPBackground backgroundView, int currentAccount, TLRPC.User callingUser) {
        super(context, backgroundView);
        setWillNotDraw(false);

        this.currentAccount = currentAccount;

        emojiLayout = new LinearLayout(context);
        emojiLayout.setOrientation(LinearLayout.HORIZONTAL);
        emojiLayout.setPadding(0, 0, 0, AndroidUtilities.dp(30));
        emojiLayout.setClipToPadding(false);
        for (int i = 0; i < 4; i++) {
            emojiViews[i] = new EmojiView(context);
            emojiViews[i].setScaleType(ImageView.ScaleType.FIT_XY);
            emojiLayout.addView(emojiViews[i], LayoutHelper.createLinear(30, 30, i == 0 ? 0 : 5, 0, 0, 0));
        }
        addView(emojiLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 20, 13, 20, 20));

        emojiRationalLayout = new LinearLayout(context);
        emojiRationalLayout.setOrientation(LinearLayout.VERTICAL);

        TextView emojiRationalHeaderTextView = new TextView(context);
        emojiRationalHeaderTextView.setText(LocaleController.getString(R.string.CallEmojiKeyHeaderTooltip));
        emojiRationalHeaderTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        emojiRationalHeaderTextView.setTextColor(Color.WHITE);
        emojiRationalHeaderTextView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        emojiRationalHeaderTextView.setGravity(Gravity.CENTER);

        TextView emojiRationalTextView = new TextView(context);
        emojiRationalTextView.setText(LocaleController.formatString("CallEmojiKeyTooltip", R.string.CallEmojiKeyTooltip, UserObject.getFirstName(callingUser)));
        emojiRationalTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        emojiRationalTextView.setTextColor(Color.WHITE);
        emojiRationalTextView.setGravity(Gravity.CENTER);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            emojiRationalTextView.setLineHeight(AndroidUtilities.dp(20));
        }

        emojiRationalLayout.addView(emojiRationalHeaderTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 70, 0, 70, 13));
        emojiRationalLayout.addView(emojiRationalTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 70, 0, 70, 0));
        addView(emojiRationalLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 56, 0, 0));

        hideEmojiTextView = new TextView(context);
        hideEmojiTextView.setText(LocaleController.getString(R.string.CallEmojiKeyHide));
        hideEmojiTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        hideEmojiTextView.setTextColor(Color.WHITE);
        hideEmojiTextView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        hideEmojiTextView.setGravity(Gravity.CENTER);
        addView(hideEmojiTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 56, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0));

        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        checkLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        emojiRationalBackgroundRectStart.set(
            getMeasuredWidth() / 2f - AndroidUtilities.dp(80), AndroidUtilities.dp(56 / 2f - 15),
            getMeasuredWidth() / 2f + AndroidUtilities.dp(80), AndroidUtilities.dp(56 / 2f + 15));
        emojiRationalBackgroundRectEnd.set(AndroidUtilities.dp(43), AndroidUtilities.dp(122),
            getMeasuredWidth() - AndroidUtilities.dp(43),
            AndroidUtilities.dp(122) + Math.max(AndroidUtilities.dp(184), emojiRationalLayout.getMeasuredHeight() + AndroidUtilities.dp(100)));
    }

    public void updateKeyView(boolean animated) {
        if (emojiLoaded) {
            return;
        }
        VoIPService service = VoIPService.getSharedInstance();
        if (service == null) {
            return;
        }
        byte[] auth_key = null;
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            buf.write(service.getEncryptionKey());
            buf.write(service.getGA());
            auth_key = buf.toByteArray();
        } catch (Exception checkedExceptionsAreBad) {
            FileLog.e(checkedExceptionsAreBad, false);
        }
        if (auth_key == null) {
            return;
        }
        byte[] sha256 = Utilities.computeSHA256(auth_key, 0, auth_key.length);
        String[] emoji = EncryptionKeyEmojifier.emojifyForCall(sha256);
        setEmoji(emoji);
        checkEmojiLoaded(animated);
    }

    /**/

    private float hasAnyVideoValue = 0f;
    public void updateLayout (float hasAnyVideoValue) {
        this.hasAnyVideoValue = hasAnyVideoValue;

        int alpha = (int) (AnimationUtilities.fromTo(180, 74, hasAnyVideoValue) * emojiExpandAnimatorValue);
        backgroundDarkPaint.setAlpha(alpha);

        invalidate();
    }

    private float emojiExpandAnimatorValue = 0f;

    public void onEmojiExpandAnimationUpdate(float emojiExpandAnimatorValue) {
        this.emojiExpandAnimatorValue = emojiExpandAnimatorValue;

        int alpha = (int) (AnimationUtilities.fromTo(180, 74, hasAnyVideoValue) * emojiExpandAnimatorValue);
        backgroundDarkPaint.setAlpha(alpha);

        checkLayout();
    }

    private void checkLayout () {
        float scale = 0.5f + 0.5f * emojiExpandAnimatorValue;
        // emojiRationalBackgroundPaint.setColor(ColorUtils.setAlphaComponent(Color.BLACK, (int)(74f * emojiExpandAnimatorValue)));

        emojiRationalLayout.setAlpha(emojiExpandAnimatorValue);
        emojiRationalLayout.setScaleX(scale);
        emojiRationalLayout.setScaleY(scale);
        emojiRationalLayout.setTranslationY(AndroidUtilities.dp(212 - 56) * emojiExpandAnimatorValue);

        hideEmojiTextView.setAlpha(emojiExpandAnimatorValue);
        hideEmojiTextView.setScaleX(scale);
        hideEmojiTextView.setScaleY(scale);
        hideEmojiTextView.setTranslationY(AndroidUtilities.dp(-20) * (1f - emojiExpandAnimatorValue));
        hideEmojiBackgroundRect.set(
            getMeasuredWidth() / 2f - (hideEmojiTextView.getMeasuredWidth() / 2f + AndroidUtilities.dp(15) * scale),
            AndroidUtilities.dp(56 / 2f - 14) * scale,
            getMeasuredWidth() / 2f + (hideEmojiTextView.getMeasuredWidth() / 2f + AndroidUtilities.dp(15) * scale),
            AndroidUtilities.dp(56 / 2f + 14) * scale
        );

        hideEmojiBackgroundPath.reset();
        hideEmojiBackgroundPath.addRoundRect(hideEmojiBackgroundRect, hideEmojiBackgroundRect.height() / 2f, hideEmojiBackgroundRect.height() / 2f, Path.Direction.CW);
        hideEmojiBackgroundPath.close();

        int radiusEnd = AndroidUtilities.dp(21);
        int radiusStart = AndroidUtilities.dp(5);

        AnimationUtilities.fromToRectF(emojiRationalBackgroundRect, emojiRationalBackgroundRectStart, emojiRationalBackgroundRectEnd, emojiExpandAnimatorValue);
        float radius = radiusStart + (radiusEnd - radiusStart) * emojiExpandAnimatorValue;

        emojiRationalBackgroundPath.reset();
        emojiRationalBackgroundPath.addRoundRect(emojiRationalBackgroundRect, radius, radius, Path.Direction.CW);
        emojiRationalBackgroundPath.close();
        invalidate();
    }

    /**/

    public void destroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        canvas.drawPath(hideEmojiBackgroundPath, backgroundDarkPaint);
        canvas.drawPath(emojiRationalBackgroundPath, backgroundDarkPaint);

        super.dispatchDraw(canvas);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded) {
            updateKeyView(true);
        }
    }

    public void setExpanded (boolean expanded) {
        if (expanded) {
            for (int i = 0; i < 4; i++) {
                if (emojiDrawables[i] instanceof AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable) {
                    ((AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable) emojiDrawables[i]).play();
                }
            }
        }
    }

    private void setEmoji (String[] emoji) {
        for (int i = 0; i < 4; i++) {
            Emoji.preloadEmoji(emoji[i]);
            TLRPC.Document animatedEmoji = MediaDataController.getInstance(currentAccount).getEmojiAnimatedSticker(emoji[i]);
            final Drawable drawable;

            if (animatedEmoji != null) {
                AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable d = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(emojiViews[i], AndroidUtilities.dp(30));
                d.set(animatedEmoji, false);
                d.attach();
                drawable = d;
            } else {
                drawable = Emoji.getEmojiDrawable(emoji[i]);
            }

            if (drawable != null) {
                drawable.setBounds(0, 0, AndroidUtilities.dp(30), AndroidUtilities.dp(30));
                if (drawable instanceof Emoji.EmojiDrawable) {
                    ((Emoji.EmojiDrawable) drawable).preload();
                }
                emojiViews[i].setImageDrawable(drawable);
                emojiViews[i].setContentDescription(emoji[i]);
                emojiViews[i].setVisibility(View.GONE);
            }
            emojiDrawables[i] = drawable;
        }
    }

    public void setOnEmojiLoadedListener(Runnable onEmojiLoadedListener) {
        this.onEmojiLoadedListener = onEmojiLoadedListener;
    }

    private void checkEmojiLoaded(boolean animated) {
        int count = 0;

        for (int i = 0; i < 4; i++) {
            if (emojiDrawables[i] != null) {
                if (emojiDrawables[i] instanceof Emoji.EmojiDrawable) {
                    if (((Emoji.EmojiDrawable) emojiDrawables[i]).isLoaded()) {
                        count++;
                    }
                } else {
                    count++;
                }
            }
        }

        if (count == 4) {
            if (!emojiLoaded && onEmojiLoadedListener != null) {
                onEmojiLoadedListener.run();
            }

            emojiLoaded = true;
            for (int i = 0; i < 4; i++) {
                if (emojiViews[i].getVisibility() != View.VISIBLE) {
                    emojiViews[i].setVisibility(View.VISIBLE);
                }
            }
            emojiReady.set(true, animated);
        }
    }

    private float emojiVisibleExternal = 1f;
    public void setEmojiVisible (float emojiVisible) {
        emojiVisibleExternal = emojiVisible;
        checkEmojiLayout();
    }

    private void checkEmojiLayout () {
        float visible = emojiVisibleExternal * emojiReady.get();
        for (int i = 0; i < 4; i++) {
            emojiViews[i].setAlpha(visible);
            emojiViews[i].setScaleX(visible);
            emojiViews[i].setScaleY(visible);
        }
    }

    private static class EmojiView extends ImageView {
        public EmojiView(Context context) {
            super(context);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            Drawable drawable = getDrawable();
            if (drawable instanceof AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable) {
                ((AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable) drawable).attach();
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            Drawable drawable = getDrawable();
            if (drawable instanceof AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable) {
                ((AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable) drawable).detach();
            }
        }
    }
}
