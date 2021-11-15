/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.ScamDrawable;
import org.telegram.ui.Components.StatusDrawable;

public class ChannelSelectCell extends FrameLayout {

    private ShareDialogCell avatarImageView;
    private SimpleTextView titleTextView;
    private SimpleTextView subtitleTextView;
    private ChatActivity parentFragment;
    private AvatarDrawable avatarDrawable = new AvatarDrawable();
    private boolean occupyStatusBar = true;
    private int leftPadding = AndroidUtilities.dp(8);
    private long dialog_id;
    StatusDrawable currentTypingDrawable;

    private CharSequence lastSubtitle;

    private Theme.ResourcesProvider resourcesProvider;

    public ChannelSelectCell(Context context, ChatActivity chatActivity) {
        this(context, chatActivity, null);
    }

    public ChannelSelectCell(Context context, ChatActivity chatActivity, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        parentFragment = chatActivity;

        final boolean avatarClickable = parentFragment != null && parentFragment.getChatMode() == 0 && !UserObject.isReplyUser(parentFragment.getCurrentUser());
        avatarImageView = new ShareDialogCell(context, 38);
        avatarImageView.setContentDescription(LocaleController.getString("AccDescrProfilePicture", R.string.AccDescrProfilePicture));
        addView(avatarImageView);

        titleTextView = new SimpleTextView(context);
        titleTextView.setTextColor(getThemedColor(Theme.key_actionBarDefaultTitle));
        titleTextView.setTextSize(17);
        titleTextView.setGravity(Gravity.LEFT);
        titleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleTextView.setLeftDrawableTopPadding(-AndroidUtilities.dp(1.3f));
        addView(titleTextView);

        subtitleTextView = new SimpleTextView(context);
        subtitleTextView.setTextColor(getThemedColor(Theme.key_actionBarDefaultSubtitle));
        subtitleTextView.setTag(Theme.key_actionBarDefaultSubtitle);
        subtitleTextView.setTextSize(14);
        subtitleTextView.setGravity(Gravity.LEFT);
        addView(subtitleTextView);
    }

    public void setOccupyStatusBar(boolean value) {
        occupyStatusBar = value;
    }

    public void setTitleColors(int title, int subtitle) {
        titleTextView.setTextColor(title);
        subtitleTextView.setTextColor(subtitle);
        subtitleTextView.setTag(subtitle);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int availableWidth = width - AndroidUtilities.dp((avatarImageView.getVisibility() == VISIBLE ? 54 : 0) + 16);
        avatarImageView.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY));
        titleTextView.measure(MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24), MeasureSpec.AT_MOST));
        subtitleTextView.measure(MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(20), MeasureSpec.AT_MOST));
        setMeasuredDimension(width, AndroidUtilities.dp(56));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int viewTop = AndroidUtilities.dp(56 - 48) / 2;
        avatarImageView.layout(leftPadding - AndroidUtilities.dp(5), viewTop, leftPadding + AndroidUtilities.dp(43), viewTop + AndroidUtilities.dp(48));
        int l = leftPadding + (avatarImageView.getVisibility() == VISIBLE ? AndroidUtilities.dp( 54) : 0);
        if (subtitleTextView.getVisibility() != GONE) {
            titleTextView.layout(l, viewTop + AndroidUtilities.dp(1.3f), l + titleTextView.getMeasuredWidth(), viewTop + titleTextView.getTextHeight() + AndroidUtilities.dp(1.3f));
        } else {
            titleTextView.layout(l, viewTop + AndroidUtilities.dp(11), l + titleTextView.getMeasuredWidth(), viewTop + titleTextView.getTextHeight() + AndroidUtilities.dp(11));
        }
         subtitleTextView.layout(l, viewTop + AndroidUtilities.dp(24), l + subtitleTextView.getMeasuredWidth(), viewTop + subtitleTextView.getTextHeight() + AndroidUtilities.dp(24));
    }

    public void setLeftPadding(int value) {
        leftPadding = value;
    }

    public void setTitleIcons(Drawable leftIcon, Drawable rightIcon) {
        titleTextView.setLeftDrawable(leftIcon);
        if (!(titleTextView.getRightDrawable() instanceof ScamDrawable)) {
            titleTextView.setRightDrawable(rightIcon);
        }
    }

    public void setTitle(CharSequence value) {
        setTitle(value, false, false);
    }

    public void setTitle(CharSequence value, boolean scam, boolean fake) {
        titleTextView.setText(value);
        if (scam || fake) {
            if (!(titleTextView.getRightDrawable() instanceof ScamDrawable)) {
                ScamDrawable drawable = new ScamDrawable(11, scam ? 0 : 1);
                drawable.setColor(getThemedColor(Theme.key_actionBarDefaultSubtitle));
                titleTextView.setRightDrawable(drawable);
            }
        } else if (titleTextView.getRightDrawable() instanceof ScamDrawable) {
            titleTextView.setRightDrawable(null);
        }
    }

    public void setSubtitle(CharSequence value) {
        if (lastSubtitle == null) {
            subtitleTextView.setText(value);
        } else {
            lastSubtitle = value;
        }
    }

    public SimpleTextView getTitleTextView() {
        return titleTextView;
    }

    public SimpleTextView getSubtitleTextView() {
        return subtitleTextView;
    }

    public void setDialog(long uid, boolean checked) {
        dialog_id = uid;
        avatarImageView.setDialog(uid, checked, "");
    }

    public long getDialogId() {
        return dialog_id;
    }




    public void setChecked(boolean checked, boolean animated) {
        avatarImageView.setChecked(checked, animated);
    }

    private int getThemedColor(String key) {
        Integer color = resourcesProvider != null ? resourcesProvider.getColor(key) : null;
        return color != null ? color : Theme.getColor(key);
    }

    public void updateColors() {
        if (currentTypingDrawable != null) {
            currentTypingDrawable.setColor(getThemedColor(Theme.key_chat_status));
        }
    }
}
