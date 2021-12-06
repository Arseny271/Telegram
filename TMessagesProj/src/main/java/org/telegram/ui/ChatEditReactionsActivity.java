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
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.Keep;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ReactionCheckCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;

public class ChatEditReactionsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private TextCheckCell enableReactionsCell;
    private TextInfoPrivacyCell reactionsInfoCell;
    private HeaderCell headerCell;
    private ArrayList<ReactionCheckCell> reactionsEnableCells;
    private LinearLayout reactionsEnableLinearLayout;
    private ShadowSectionCell bottomShadowCell;

    private boolean enableReactions;
    private float enableReactionsProgress = 1f;
    private ObjectAnimator enableReactionsAnimator;

    private HashMap<String, Boolean> availableReactionsMap = new HashMap<>();

    private LinearLayout linearLayout;

    private boolean isPrivate;

    private TLRPC.Chat currentChat;
    private TLRPC.ChatFull info;
    private long chatId;
    private boolean isChannel;
    private boolean isForcePublic;

    public ChatEditReactionsActivity(long id, boolean forcePublic) {
        chatId = id;
        isForcePublic = forcePublic;
    }

    @Override
    public boolean onFragmentCreate() {
        currentChat = getMessagesController().getChat(chatId);
        if (currentChat == null) {
            currentChat = getMessagesStorage().getChatSync(chatId);
            if (currentChat != null) {
                getMessagesController().putChat(currentChat, true);
            } else {
                return false;
            }
            if (info == null) {
                info = getMessagesStorage().loadChatInfo(chatId, ChatObject.isChannel(currentChat), new CountDownLatch(1), false, false);
                if (info == null) {
                    return false;
                }
            }
        }
        isPrivate = !isForcePublic && TextUtils.isEmpty(currentChat.username);
        isChannel = ChatObject.isChannel(currentChat) && !currentChat.megagroup;
        if (isPrivate && info != null) {
            getMessagesController().loadFullChat(chatId, classGuid, true);
        }

        ArrayList<TLRPC.TL_availableReaction> allReactions = getMessagesController().getAvailableReactionsList();
        for (int a = 0; a < allReactions.size(); a++) {
            TLRPC.TL_availableReaction reaction = allReactions.get(a);
            availableReactionsMap.put(reaction.reaction, false);
        }

        getNotificationCenter().addObserver(this, NotificationCenter.chatInfoDidLoad);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        getNotificationCenter().removeObserver(this, NotificationCenter.chatInfoDidLoad);
        AndroidUtilities.removeAdjustResize(getParentActivity(), classGuid);
    }

    @Override
    public void onResume() {
        super.onResume();
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
            if (id == -1) {
                finishFragment();
            }
            }
        });

        fragmentView = new ScrollView(context) {
            @Override
            public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
                rectangle.bottom += AndroidUtilities.dp(60);
                return super.requestChildRectangleOnScreen(child, rectangle, immediate);
            }
        };
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        ScrollView scrollView = (ScrollView) fragmentView;
        scrollView.setFillViewport(true);
        linearLayout = new LinearLayout(context);
        scrollView.addView(linearLayout, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        linearLayout.setOrientation(LinearLayout.VERTICAL);
        actionBar.setTitle("Reactions");

        enableReactions = ChatObject.canSendReactions(info);
        enableReactionsCell = new TextCheckCell(context);
        enableReactionsCell.setTextAndCheck("Enable Reactions", enableReactions, false);
        enableReactionsCell.setDrawCheckRipple(true);
        enableReactionsCell.setTag(enableReactions ? Theme.key_windowBackgroundChecked : Theme.key_windowBackgroundUnchecked);
        enableReactionsCell.setBackgroundColor(Theme.getColor(enableReactions ? Theme.key_windowBackgroundChecked : Theme.key_windowBackgroundUnchecked));

        enableReactionsCell.setColors(Theme.key_windowBackgroundCheckText, Theme.key_switchTrackBlue, Theme.key_switchTrackBlueChecked, Theme.key_switchTrackBlueThumb, Theme.key_switchTrackBlueThumbChecked);
        enableReactionsCell.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

        enableReactionsCell.setHeight(56);
        enableReactionsCell.setOnClickListener(v -> {
            switchEnableReactions();
            saveReactions();
        });
        linearLayout.addView(enableReactionsCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        reactionsInfoCell = new TextInfoPrivacyCell(context);
        reactionsInfoCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
        reactionsInfoCell.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
        if (isChannel) {
            reactionsInfoCell.setText("Allow subscribers to react to channel posts");
        } else {
            reactionsInfoCell.setText("Allow group members to react to messages");
        }
        linearLayout.addView(reactionsInfoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        reactionsEnableLinearLayout = new LinearLayout(context);
        reactionsEnableLinearLayout.setOrientation(LinearLayout.VERTICAL);
        reactionsEnableLinearLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        linearLayout.addView(reactionsEnableLinearLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        headerCell = new HeaderCell(context, 23);
        headerCell.setHeight(46);
        headerCell.setText("Available reactions");
        reactionsEnableLinearLayout.addView(headerCell);

        if (info != null && info.available_reactions != null) {
            for (int a = 0; a < info.available_reactions.size(); a++) {
                availableReactionsMap.put(info.available_reactions.get(a), true);
            }
        }

        reactionsEnableCells = new ArrayList<>();
        ArrayList<TLRPC.TL_availableReaction> allReactions = getMessagesController().getAvailableReactionsList();
        for (int a = 0; a < allReactions.size(); a++) {
            TLRPC.TL_availableReaction reaction = allReactions.get(a);
            final Boolean checkedBoolean = availableReactionsMap.get(reaction.reaction);
            final boolean checked = checkedBoolean != null ? checkedBoolean : false;

            ReactionCheckCell reactionCheckCell = new ReactionCheckCell(context);
            reactionCheckCell.setBackgroundDrawable(Theme.getSelectorDrawable(true));
            reactionCheckCell.setTextAndCheck(reaction.title, checked, a + 1 < allReactions.size());

            reactionCheckCell.setReaction(reaction.static_icon, reaction);
            reactionCheckCell.setOnClickListener(v -> {
                boolean newChecked = !reactionCheckCell.isChecked();
                availableReactionsMap.put(reaction.reaction, newChecked);
                reactionCheckCell.setChecked(newChecked);
                saveReactions();
            });


            reactionsEnableLinearLayout.addView(reactionCheckCell);
            reactionsEnableCells.add(reactionCheckCell);
        }

        bottomShadowCell = new ShadowSectionCell(context);
        bottomShadowCell.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
        linearLayout.addView(bottomShadowCell);

        if (!enableReactions) {
            setEnableReactionsProgress(0);
        }

        updateReactions();

        return fragmentView;
    }

    @Keep
    public void setEnableReactionsProgress(float value) {
        if (enableReactionsProgress == value) {
            return;
        }
        enableReactionsProgress = value;
        //enableReactionsCell.setBackgroundRadiusProgress(enableReactionsProgress);

        int newVisibility = (value > 0) ? View.VISIBLE : View.GONE;
        if (newVisibility != reactionsEnableLinearLayout.getVisibility()) {
            reactionsEnableLinearLayout.setVisibility(newVisibility);
            bottomShadowCell.setVisibility(newVisibility);
        }

        reactionsEnableLinearLayout.setAlpha(value);
    }

    @Keep
    public float getEnableReactionsProgress() {
        return enableReactionsProgress;
    }

    private void switchEnableReactions() {
        setEnableReactions(!enableReactions);
    }

    private void setEnableReactions(boolean enabled) {
        if (enableReactions == enabled) {
            return;
        }
        enableReactions = enabled;
        enableReactionsCell.setChecked(enableReactions);
        enableReactionsCell.setBackgroundColorAnimated(enableReactions, Theme.getColor(enableReactions ? Theme.key_windowBackgroundChecked : Theme.key_windowBackgroundUnchecked));

        enableReactionsAnimator = ObjectAnimator.ofFloat(this, "enableReactionsProgress", enableReactions ? 1 : 0);
        enableReactionsAnimator.setDuration(250);
        enableReactionsAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                enableReactionsAnimator = null;
            }
        });
        enableReactionsAnimator.start();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.chatInfoDidLoad) {
            TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
            if (chatFull.id == chatId) {
                info = chatFull;
                updateReactions();
            }
        }
    }

    public void setInfo(TLRPC.ChatFull chatFull) {
        info = chatFull;
        updateReactions();
    }

    private void updateReactions() {
        if (fragmentView == null) {
            return;
        }
        setEnableReactions(ChatObject.canSendReactions(info));
    }

    private void saveReactions() {
        final ArrayList<String> reactions = new ArrayList<>();
        if (enableReactionsCell.isChecked()) {
            ArrayList<TLRPC.TL_availableReaction> allReactions = getMessagesController().getAvailableReactionsList();
            for (int a = 0; a < allReactions.size(); a++) {
                TLRPC.TL_availableReaction reaction = allReactions.get(a);
                final Boolean checkedBoolean = availableReactionsMap.get(reaction.reaction);
                final boolean checked = checkedBoolean != null ? checkedBoolean : false;
                if (checked) {
                    reactions.add(reaction.reaction);
                }
            }
        }

        getMessagesController().saveAvailableReactions(chatId, reactions);
        getMessagesController().getChatFull(chatId).available_reactions = reactions;
        info.available_reactions = reactions;
    }
}
