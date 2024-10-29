package org.telegram.ui.Components.quickforward;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.AvatarDrawable;

class QuickShareAvatarCell implements ValueAnimator.AnimatorUpdateListener {
    private static final long DURATION = 200L;

    private final QuickShareSelectorDrawable parent;
    private final ChatMessageCell cell;
    private final ImageReceiver imageReceiver;
    private final AvatarDrawable avatarDrawable = new AvatarDrawable();
    private final int currentAccount = UserConfig.selectedAccount;
    public final long dialogId;

    private BlurVisibilityDrawable blurredAvatarDrawable;
    private BlurVisibilityDrawable blurredTextDrawable;
    private Paint blurredTextPaint;
    private StaticLayout textLayout;

    public QuickShareAvatarCell(QuickShareSelectorDrawable parent, long dialogId) {
        this.imageReceiver = new ImageReceiver(parent.parent);

        this.parent = parent;
        this.cell = parent.cell;
        this.dialogId = dialogId;

        this.setDialog(cell, dialogId);
    }



    private float bgX1, bgY;

    public void draw(Canvas c, float hardMinX, float hardMaxX, float softMinX, float softMaxX, float cx, float cy, float rad, float alpha, boolean withoutAvatar) {
        if (!withoutAvatar) {
            drawAvatarImpl(c, cx, cy, rad + dp(2) * selectedFactor, alpha);
        }

        if (selectedFactor > 0f && textLayout != null) {
            final float s = 0.85f + 0.15f * selectedFactor;
            c.save();
            c.scale(s, s, cx, cy);

            final float textAlpha = selectedFactor * alpha;
            final float textWidth = textLayout.getLineWidth(0) + dp(QuickShareSelectorDrawable.Sizes.TEXT_PADDING) * 2;
            final float textWidthHalf = textWidth / 2f;
            bgY = cy - dp(58);
            if (cx - textWidthHalf < hardMinX) {
                bgX1 = hardMinX;
            } else if (cx + textWidthHalf > hardMaxX) {
                bgX1 = hardMaxX - textWidth;
            } else if ((cx - textWidthHalf < softMinX) && (cx + textWidthHalf > softMaxX)) {
                bgX1 = (softMinX + softMaxX) / 2f - textWidthHalf;
            } else if (cx - textWidthHalf < softMinX) {
                bgX1 = softMinX;
            } else if (cx + textWidthHalf > softMaxX) {
                bgX1 = softMaxX - textWidth;
            }

            else {
                bgX1 = cx - textWidthHalf;
            }

            if (blurredTextDrawable == null && !parent.isDestroyed()) {
                blurredTextPaint = parent.getBlurBitmapPaint();
                blurredTextDrawable = new BlurVisibilityDrawable(this::renderText);
                blurredTextDrawable.render((int) textWidth, dp(21), dp(QuickShareSelectorDrawable.Sizes.TEXT_BLUR_RADIUS), 3);
            }

            if (blurredTextDrawable != null) {
                blurredTextDrawable.setBounds((int) bgX1, (int) bgY, (int) (bgX1 + textWidth), (int) (bgY + dp(21)));
                blurredTextDrawable.setAlpha((int) (textAlpha * 255));
                blurredTextDrawable.draw(c);
            }

            c.restore();
        }
    }

    public void recycle () {
        if (blurredTextDrawable != null) {
            blurredTextDrawable.recycle();
        }
        if (blurredAvatarDrawable != null) {
            blurredAvatarDrawable.recycle();
        }
    }


    /* Draw */

    public void drawBlurredAvatar(Canvas c, float cx, float cy, float radius, float alpha) {
        if (blurredAvatarDrawable == null) {
            blurredAvatarDrawable = new BlurVisibilityDrawable(this::renderAvatar);
            blurredAvatarDrawable.render(
                dp(QuickShareSelectorDrawable.Sizes.AVATAR),
                dp(QuickShareSelectorDrawable.Sizes.AVATAR),
                dp(QuickShareSelectorDrawable.Sizes.BLUR_RADIUS),
                4
            );
        }

        c.save();
        c.translate(cx - radius, cy - radius);
        c.scale(radius / dp(QuickShareSelectorDrawable.Sizes.AVATAR_RADIUS), radius / dp(QuickShareSelectorDrawable.Sizes.AVATAR_RADIUS));

        blurredAvatarDrawable.setAlpha((int) (alpha * 255));
        blurredAvatarDrawable.draw(c);

        c.restore();
    }

    private void renderAvatar(@NonNull Canvas canvas, int alpha) {
        final float radius = dp(QuickShareSelectorDrawable.Sizes.AVATAR_RADIUS);
        drawAvatarImpl(canvas, radius, radius, radius, alpha / 255f);
    }

    private void drawAvatarImpl(Canvas c, float cx, float cy, float radius, float alpha) {
        c.save();
        c.translate(cx - radius, cy - radius);
        c.scale(radius / dp(QuickShareSelectorDrawable.Sizes.AVATAR_RADIUS), radius / dp(QuickShareSelectorDrawable.Sizes.AVATAR_RADIUS));
        imageReceiver.setAlpha((0.75f + 0.25f * alphaFactor) * alpha);
        imageReceiver.draw(c);
        c.restore();
    }

    private void renderText(@NonNull Canvas canvas, int alpha) {
        canvas.save();
        canvas.translate(-bgX1, -bgY);
        drawTextImpl(canvas, bgX1, bgY, alpha / 255f);
        canvas.restore();
    }

    private void drawTextImpl(Canvas c, float x, float y, float textAlpha) {
        final RectF tmpRectF = AndroidUtilities.rectTmp;
        final float r = dp(21) / 2f;
        int cachedAlpha;

        tmpRectF.set(x, y, x + textLayout.getLineWidth(0) + dp(QuickShareSelectorDrawable.Sizes.TEXT_PADDING) * 2, y + dp(21));

        final boolean hasGradientService = cell.hasGradientService();
        final Paint blurPaint = blurredTextPaint;

        if (blurPaint != null) {
            cachedAlpha = blurPaint.getAlpha();
            blurPaint.setAlpha((int) (255 * textAlpha));
            c.drawRoundRect(tmpRectF, r, r, blurPaint);
            blurPaint.setAlpha(cachedAlpha);
        } else {
            cell.applyServiceShaderMatrix(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y, 0, 0);
            final Paint bgPaint = cell.getThemedPaint(Theme.key_paint_chatActionBackground);
            cachedAlpha = bgPaint.getAlpha();


            bgPaint.setAlpha((int) ((hasGradientService ? cachedAlpha : (255 * 0.9f)) * textAlpha));
            c.drawRoundRect(tmpRectF, r, r, bgPaint);
            bgPaint.setAlpha(cachedAlpha);
        }

        if (hasGradientService || blurPaint != null) {
            cachedAlpha = Theme.chat_actionBackgroundGradientDarkenPaint.getAlpha();
            Theme.chat_actionBackgroundGradientDarkenPaint.setAlpha((int) (cachedAlpha * textAlpha));
            c.drawRoundRect(tmpRectF, r, r, Theme.chat_actionBackgroundGradientDarkenPaint);
            Theme.chat_actionBackgroundGradientDarkenPaint.setAlpha(cachedAlpha);
        }

        c.save();
        c.translate(x + dp(QuickShareSelectorDrawable.Sizes.TEXT_PADDING), y + (dp(21) - textLayout.getHeight()) / 2f);
        cachedAlpha = textLayout.getPaint().getAlpha();
        textLayout.getPaint().setAlpha((int) (cachedAlpha * textAlpha));
        textLayout.draw(c);
        textLayout.getPaint().setAlpha(cachedAlpha);
        c.restore();
    }



    /* Animations */

    private ValueAnimator alphaAnimator;
    private float alphaFactor = 1f;
    private boolean alphaValue = true;

    private ValueAnimator selectedAnimator;
    private float selectedFactor = 0f;
    private boolean selectedValue = false;

    @Override
    public void onAnimationUpdate(@NonNull ValueAnimator animation) {
        if (animation == selectedAnimator) {
            selectedFactor = (float) animation.getAnimatedValue();
        } else if (animation == alphaAnimator) {
            alphaFactor = (float) animation.getAnimatedValue();
        }

        parent.invalidateSelf();
    }

    public void setSelected(boolean selected, boolean animated) {
        if (selectedValue == selected) {
            return;
        }

        if (selectedAnimator != null) {
            selectedAnimator.cancel();
        }

        selectedValue = selected;

        if (animated) {
            selectedAnimator = ValueAnimator.ofFloat(selectedFactor, selected ? 1f : 0f);
            selectedAnimator.setDuration(DURATION);
            selectedAnimator.addUpdateListener(this);
            selectedAnimator.setInterpolator(QuickShareSelectorDrawable.Interpolators.DECELERATE_INTERPOLATOR);
            selectedAnimator.start();
        } else {
            selectedFactor = selected ? 1f : 0f;
            parent.invalidateSelf();
        }
    }

    public void setFullVisible(boolean fullVisible, boolean animated) {
        if (alphaValue == fullVisible) {
            return;
        }

        if (alphaAnimator != null) {
            alphaAnimator.cancel();
        }

        alphaValue = fullVisible;

        if (animated) {
            alphaAnimator = ValueAnimator.ofFloat(alphaFactor, fullVisible ? 1f : 0f);
            alphaAnimator.setDuration(DURATION);
            alphaAnimator.addUpdateListener(this);
            alphaAnimator.setInterpolator(QuickShareSelectorDrawable.Interpolators.DECELERATE_INTERPOLATOR);
            alphaAnimator.start();
        } else {
            alphaFactor = fullVisible ? 1f : 0f;
            parent.invalidateSelf();
        }
    }



    /* Init */

    private void setDialog(ChatMessageCell cell, long uid) {
        CharSequence displayName;
        avatarDrawable.setScaleSize(1f);

        if (DialogObject.isUserDialog(uid)) {
            final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(uid);
            avatarDrawable.setInfo(currentAccount, user);
            if (UserObject.isUserSelf(user)) {
                displayName = LocaleController.getString(R.string.SavedMessages);
                avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
                avatarDrawable.setScaleSize(0.75f);
                imageReceiver.setImage(null, null, null, null, avatarDrawable, 0, null, user, 0);
            } else {

                if (user != null) {
                    displayName = ContactsController.formatName(user.first_name, user.last_name);
                } else {
                    displayName = "";
                }
                imageReceiver.setForUserOrChat(user, avatarDrawable);
            }
        } else {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-uid);
            if (chat != null) {
                displayName = chat.title;
            } else {
                displayName = "";
            }
            avatarDrawable.setInfo(currentAccount, chat);
            imageReceiver.setForUserOrChat(chat, avatarDrawable);
        }
        imageReceiver.setRoundRadius(dp(QuickShareSelectorDrawable.Sizes.AVATAR / 2f));
        imageReceiver.setImageCoords(0, 0, dp(QuickShareSelectorDrawable.Sizes.AVATAR), dp(QuickShareSelectorDrawable.Sizes.AVATAR));

        final Paint p = cell.getThemedPaint(Theme.key_paint_chatActionText);
        if (displayName != null && p != null) {
            final int padding = dp(QuickShareSelectorDrawable.Sizes.TEXT_PADDING);
            final int maxWidth = AndroidUtilities.displaySize.x - padding * 2;

            final TextPaint textPaint = new TextPaint(p);
            // int width = (int) Math.ceil(textPaint.measureText(displayName.toString())) + dp(16);
            textLayout = new StaticLayout(displayName, textPaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        }
    }
}
