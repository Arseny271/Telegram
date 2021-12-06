package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.transition.ChangeBounds;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.transition.TransitionValues;
import android.transition.Visibility;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ExtendedActionBarPopupWindowBaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ClippingImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.ExtendedGridLayoutManager;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.HideViewAfterAnimation;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerAnimationScrollHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScrollSlidingReactionsTabStrip;
import org.telegram.ui.Components.ScrollSlidingTextTabStrip;
import org.telegram.ui.Components.SharedMediaFastScrollTooltip;
import org.telegram.ui.Components.SharedMediaLayout;
import org.telegram.ui.Components.StickerEmptyView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;

public class SeenReactionsPopupFragment extends ExtendedActionBarPopupWindowBaseFragment {

    private TLRPC.Chat currentChat;
    private TLRPC.ChatFull info;
    private long chatId;
    private MessageObject messageObject;
    private int reactionsCount = -1;

    private LinearLayout linearLayout;
    private ActionBarMenuSubItem backMenuView;
    private FrameLayout shadowSectionFrameLayout;
    private ShadowSectionCell shadowSectionCell;
    private ScrollSlidingReactionsTabStrip scrollSlidingTextTabStrip;
    private ReactionsPagesLayout reactionPagesView;

    private boolean showMessageSeen;

    private ArrayList<TLRPC.User> seenUsersList = new ArrayList<>();
    private HashMap<Long, TLRPC.User> seenUsersMap = new HashMap<>();
    private ArrayList<TLRPC.User> seenUsersListToShow = new ArrayList<>();
    private HashMap<Long, TLRPC.User> allUsersMap = new HashMap<>();

    private HashMap<Integer, String> filtersMap = new HashMap<>();
    private HashMap<Integer, String> offsetsMap = new HashMap<>();
    private HashMap<Integer, Integer> countsMap = new HashMap<>();
    private HashMap<Integer, Boolean> loadingMap = new HashMap<>();
    private HashMap<Integer, ArrayList<TLRPC.TL_messageUserReaction>> reactedReactionsLists = new HashMap<>();
    private HashMap<Integer, HashMap<Long, TLRPC.TL_messageUserReaction>> reactedReactionsMaps = new HashMap<>();

    private boolean allSeensLoaded = false;

    private TLRPC.Chat chat;

    public boolean animatingForward;

    private HashMap<String, TLRPC.TL_availableReaction> availableReactionsMap;
    private int totalCount = 0;

    public SeenReactionsPopupFragment(long id, TLRPC.Chat chat, boolean showMessageSeen) {
        this.chatId = id;
        this.chat = chat;
        this.showMessageSeen = showMessageSeen;
    }

    public void setMessageObject(MessageObject messageObject) {
        this.messageObject = messageObject;
    }

    @Override
    public boolean onFragmentCreate() {
        currentChat = getMessagesController().getChat(chatId);
        info = getMessagesController().getChatFull(chatId);

        if (currentChat == null || info == null) {
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

        availableReactionsMap = getMessagesController().getAvailableReactionMap();

        if (messageObject.messageOwner.reactions != null) {
            reactionsCount = 0;

            for (int a = 0; a < messageObject.messageOwner.reactions.results.size(); a++) {
                TLRPC.TL_reactionCount reactionCount = messageObject.messageOwner.reactions.results.get(a);
                reactionsCount += reactionCount.count;
                countsMap.put(a, reactionCount.count);
                filtersMap.put(a, reactionCount.reaction);
                reactedReactionsLists.put(a, new ArrayList<>());
                reactedReactionsMaps.put(a, new HashMap<>());
            }

            countsMap.put(-1, totalCount);
            filtersMap.put(-1, "");
            reactedReactionsLists.put(-1, new ArrayList<>());
            reactedReactionsMaps.put(-1, new HashMap<>());
        }

        return super.onFragmentCreate();
    }

    private void loadNextReactions(int datasetId) {
        HashMap<Long, TLRPC.TL_messageUserReaction> reactedReactionsMap = reactedReactionsMaps.get(datasetId);
        ArrayList<TLRPC.TL_messageUserReaction> reactedReactionsList = reactedReactionsLists.get(datasetId);
        Integer count = countsMap.get(datasetId);
        String filter = filtersMap.get(datasetId);
        String offset = offsetsMap.get(datasetId);
        Boolean loading = loadingMap.get(datasetId);

        if (count == null || reactedReactionsList == null || reactedReactionsMap == null || (loading != null && ((boolean) loading))) {
            return;
        }

        if (count <= reactedReactionsList.size()) {
            return;
        }

        loadingMap.put(datasetId, true);

        TLRPC.TL_messages_getMessageReactionsList req = new TLRPC.TL_messages_getMessageReactionsList();
        req.peer = getMessagesController().getInputPeer(-chatId);
        req.id = messageObject.getId();
        req.limit = datasetId == -1 ? 100 : 50;

        if (offset != null) {
            req.offset = offset;
            req.flags |= 2;
        }

        if (filter != null) {
            req.reaction = filter;
            req.flags |= 1;
        }

        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                TLRPC.TL_messages_messageReactionsList reactions = (TLRPC.TL_messages_messageReactionsList) response;
                for (TLRPC.TL_messageUserReaction userReaction : reactions.reactions) {
                    reactedReactionsList.add(userReaction);
                    reactedReactionsMap.put(userReaction.user_id, userReaction);
                }

                for (TLRPC.User user : reactions.users) {
                    allUsersMap.put(user.id, user);
                }

                if ((reactions.flags & 1) != 0) {
                    offsetsMap.put(datasetId, reactions.next_offset);
                }

                updateView();
            }

            loadingMap.put(datasetId, false);
        }));

    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        AndroidUtilities.removeAdjustResize(getParentActivity(), classGuid);
    }

    @Override
    public void onResume() {
        super.onResume();
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
    }

    @Override
    public View createView(Context context) {
        linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        fragmentView = linearLayout;
        fragmentView.setWillNotDraw(false);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground));

        backMenuView = new ActionBarMenuSubItem(context, true, false, null);
        backMenuView.setMinimumWidth(AndroidUtilities.dp(200));
        backMenuView.setTextAndIcon("Back", R.drawable.msg_arrow_back);
        backMenuView.setOnClickListener(v -> {
            finishFragment();
        });
        linearLayout.addView(backMenuView);

        shadowSectionCell = new ShadowSectionCell(context, 8);
        shadowSectionCell.setBackgroundDrawable(Theme.getThemedDrawable(getParentActivity(), R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
        shadowSectionFrameLayout = new FrameLayout(context);
        shadowSectionFrameLayout.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        shadowSectionFrameLayout.setWillNotDraw(false);
        shadowSectionFrameLayout.addView(shadowSectionCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        linearLayout.addView(shadowSectionFrameLayout);

        if (messageObject.messageOwner.reactions != null && reactionsCount > 10) {
            scrollSlidingTextTabStrip = new ScrollSlidingReactionsTabStrip(context);
            scrollSlidingTextTabStrip.setColors(Theme.key_profile_tabSelectedLine, Theme.key_profile_tabSelectedText, Theme.key_profile_tabText, Theme.key_profile_tabSelector);
            scrollSlidingTextTabStrip.setDelegate(new ScrollSlidingReactionsTabStrip.ScrollSlidingTabStripDelegate() {
                @Override
                public void onPageSelected(int id, boolean forward) {
                    reactionPagesView.reactionPages[1].selectedType = id;
                    reactionPagesView.reactionPages[1].setVisibility(View.VISIBLE);
                    switchToCurrentSelectedMode(true);
                    animatingForward = forward;
                }

                @Override
                public void onPageScrolled(float progress) {
                    if (animatingForward) {
                        reactionPagesView.reactionPages[0].setTranslationX(-progress * reactionPagesView.reactionPages[0].getMeasuredWidth());
                        reactionPagesView.reactionPages[1].setTranslationX(reactionPagesView.reactionPages[0].getMeasuredWidth() - progress * reactionPagesView.reactionPages[0].getMeasuredWidth());
                    } else {
                        reactionPagesView.reactionPages[0].setTranslationX(progress * reactionPagesView.reactionPages[0].getMeasuredWidth());
                        reactionPagesView.reactionPages[1].setTranslationX(progress * reactionPagesView.reactionPages[0].getMeasuredWidth() - reactionPagesView.reactionPages[0].getMeasuredWidth());
                    }

                    if (progress == 1) {
                        ReactionsPage tempPage = reactionPagesView.reactionPages[0];
                        reactionPagesView.reactionPages[0] = reactionPagesView.reactionPages[1];
                        reactionPagesView.reactionPages[1] = tempPage;
                        reactionPagesView.reactionPages[1].setVisibility(View.GONE);
                    }
                }
            });

            HashMap<String, TLRPC.TL_availableReaction> reactionHashMap = getMessagesController().getAvailableReactionMap();

            scrollSlidingTextTabStrip.addReactionTab(-1, null, totalCount);
            for (int a = 0; a < messageObject.messageOwner.reactions.results.size(); a++) {
                TLRPC.TL_reactionCount reactionCount = messageObject.messageOwner.reactions.results.get(a);
                TLRPC.TL_availableReaction reaction = reactionHashMap.get(reactionCount.reaction);
                if (reaction == null) {
                    continue;
                }
                scrollSlidingTextTabStrip.addReactionTab(a, reaction.static_icon, reactionCount.count);
            }
            linearLayout.addView(scrollSlidingTextTabStrip, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 42, Gravity.LEFT | Gravity.TOP));
            shadowSectionFrameLayout.setVisibility(View.GONE);
        }

        reactionPagesView = new ReactionsPagesLayout(context) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                int height = AndroidUtilities.dp(Math.min(totalCount * 48, 360));

                super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            }
        };

        linearLayout.addView(reactionPagesView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));


        loadNextReactions(-1);
        if (messageObject.messageOwner.reactions != null && reactionsCount > 10) {
            for (int a = 0; a < messageObject.messageOwner.reactions.results.size(); a++) {
                loadNextReactions(a);
            }
        }


        if (showMessageSeen) {
            TLRPC.TL_messages_getMessageReadParticipants req = new TLRPC.TL_messages_getMessageReadParticipants();
            req.msg_id = messageObject.getId();
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(messageObject.getDialogId());

            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                FileLog.e("MessageSeenView request completed");
                if (error == null) {
                    TLRPC.Vector vector = (TLRPC.Vector) response;
                    ArrayList<Long> unknownUsers = new ArrayList<>();
                    HashMap<Long, TLRPC.User> usersLocal = new HashMap<>();
                    ArrayList<Long> allPeers = new ArrayList<>();
                    for (int i = 0, n = vector.objects.size(); i < n; i++) {
                        Object object = vector.objects.get(i);
                        if (object instanceof Long) {
                            Long peerId = (Long) object;
                            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(peerId);
                            allPeers.add(peerId);
                            if (true || user == null) {
                                unknownUsers.add(peerId);
                            } else {
                                usersLocal.put(peerId, user);
                            }
                        }
                    }

                    if (unknownUsers.isEmpty()) {
                        for (int i = 0; i < allPeers.size(); i++) {
                            allUsersMap.put(allPeers.get(i), usersLocal.get(allPeers.get(i)));
                            seenUsersMap.put(allPeers.get(i), usersLocal.get(allPeers.get(i)));
                            seenUsersList.add(usersLocal.get(allPeers.get(i)));
                            allSeensLoaded = true;
                        }
                        updateView();
                    } else {
                        if (ChatObject.isChannel(chat)) {
                            TLRPC.TL_channels_getParticipants usersReq = new TLRPC.TL_channels_getParticipants();
                            usersReq.limit = 50;
                            usersReq.offset = 0;
                            usersReq.filter = new TLRPC.TL_channelParticipantsRecent();
                            usersReq.channel = MessagesController.getInstance(currentAccount).getInputChannel(chat.id);
                            ConnectionsManager.getInstance(currentAccount).sendRequest(usersReq, (response1, error1) -> AndroidUtilities.runOnUIThread(() -> {
                                if (response1 != null) {
                                    TLRPC.TL_channels_channelParticipants users = (TLRPC.TL_channels_channelParticipants) response1;
                                    for (int i = 0; i < users.users.size(); i++) {
                                        TLRPC.User user = users.users.get(i);
                                        MessagesController.getInstance(currentAccount).putUser(user, false);
                                        usersLocal.put(user.id, user);

                                        allUsersMap.put(user.id, user);
                                        seenUsersMap.put(user.id, user);
                                        seenUsersList.add(user);
                                    }
                                    allSeensLoaded = true;
                                }
                                updateView();
                            }));
                        } else {
                            TLRPC.TL_messages_getFullChat usersReq = new TLRPC.TL_messages_getFullChat();
                            usersReq.chat_id = chat.id;
                            ConnectionsManager.getInstance(currentAccount).sendRequest(usersReq, (response1, error1) -> AndroidUtilities.runOnUIThread(() -> {
                                if (response1 != null) {
                                    TLRPC.TL_messages_chatFull chatFull = (TLRPC.TL_messages_chatFull) response1;
                                    for (int i = 0; i < chatFull.users.size(); i++) {
                                        TLRPC.User user = chatFull.users.get(i);
                                        MessagesController.getInstance(currentAccount).putUser(user, false);
                                        usersLocal.put(user.id, user);

                                        allUsersMap.put(user.id, user);
                                        seenUsersMap.put(user.id, user);
                                        seenUsersList.add(user);
                                    }
                                    allSeensLoaded = true;
                                }
                                updateView();
                            }));
                        }
                    }
                } else {
                    updateView();
                }
            }));
        }

        updateTabs(false);
        switchToCurrentSelectedMode(false);

        return fragmentView;
    }

    private void switchToCurrentSelectedMode(boolean animated) {
        for (int a = 0; a < reactionPagesView.reactionPages.length; a++) {
            reactionPagesView.reactionPages[a].listView.stopScroll();
        }

        int a = animated ? 1 : 0;
        ListAdapter currentAdapter = reactionPagesView.reactionPages[a].listAdapter;
        currentAdapter.setDatasetIndex(reactionPagesView.reactionPages[a].selectedType);
    }
    
    @SuppressLint("NotifyDataSetChanged" )
    private void updateView() {
        HashMap<Long, TLRPC.TL_messageUserReaction> reactedReactionsMap = reactedReactionsMaps.get(-1);
        ArrayList<TLRPC.TL_messageUserReaction> reactedReactionsList = reactedReactionsLists.get(-1);


        if (reactedReactionsList == null || reactedReactionsMap == null) {
            return;
        }

        if (reactedReactionsList.size() >= reactionsCount && allSeensLoaded && showMessageSeen) {
            seenUsersListToShow.clear();
            for (TLRPC.User user : seenUsersList) {
                if (!reactedReactionsMap.containsKey(user.id)) {
                    seenUsersListToShow.add(user);
                }
            }
        }

        reactionPagesView.reactionPages[0].listAdapter.notifyDataSetChanged();
        reactionPagesView.reactionPages[1].listAdapter.notifyDataSetChanged();
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    private void updateTabs(boolean animated) {
        if (scrollSlidingTextTabStrip == null) {
            return;
        }

        int id = scrollSlidingTextTabStrip.getCurrentTabId();
        if (id >= 0) {
            reactionPagesView.reactionPages[0].selectedType = id;
        }
        scrollSlidingTextTabStrip.finishAddingTabs();
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        public static final int REACTION_USER_CELL = 1;

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        int datasetIndex = 0;

        @SuppressLint("NotifyDataSetChanged")
        public void setDatasetIndex(int index) {
            datasetIndex = index;
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            Integer count = countsMap.get(datasetIndex);
            return (count != null)?((int) count) : 0;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = new View(mContext);

            switch (viewType) {
                case REACTION_USER_CELL:
                    UserCell userCell = new UserCell(mContext);
                    userCell.setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    view = userCell;
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ArrayList<TLRPC.TL_messageUserReaction> reactedReactionsList = reactedReactionsLists.get(datasetIndex);
            if (reactedReactionsList == null) {
                return;
            }

            switch (holder.getItemViewType()) {
                case REACTION_USER_CELL: {
                    UserCell cell = (UserCell) holder.itemView;
                    TLRPC.User user = null;
                    TLRPC.Document document = null;

                    if (position < getItemCount()) {
                        if (position < reactedReactionsList.size()) {
                            TLRPC.TL_messageUserReaction userReaction = reactedReactionsList.get(position);
                            user = allUsersMap.get(userReaction.user_id);
                            TLRPC.TL_availableReaction availableReaction = availableReactionsMap.get(userReaction.reaction);
                            if (availableReaction != null) {
                                document = availableReaction.static_icon;
                            }

                        } else if (datasetIndex == -1) {
                            if (position - reactedReactionsList.size() < seenUsersListToShow.size()) {
                                user = seenUsersListToShow.get(position - reactedReactionsList.size());
                            }
                        }
                    }

                    cell.setUser(user);
                    cell.setReaction(document);

                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            return REACTION_USER_CELL;
        }
    }

    private static class UserCell extends FrameLayout {

        BackupImageView reactionImageView;
        BackupImageView avatarImageView;
        TextView nameView;
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        FlickerLoadingView flickerLoadingView;

        public UserCell(Context context) {
            super(context);

            flickerLoadingView = new FlickerLoadingView(context);
            flickerLoadingView.setColors(Theme.key_actionBarDefaultSubmenuBackground, Theme.key_listSelector, null);
            flickerLoadingView.setViewType(FlickerLoadingView.REACTION_SEEN_TYPE);
            flickerLoadingView.setIsSingleCell(false);
            addView(flickerLoadingView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            avatarImageView = new BackupImageView(context);
            addView(avatarImageView, LayoutHelper.createFrame(34, 34, Gravity.CENTER_VERTICAL, 13, 0, 0, 0));
            avatarImageView.setRoundRadius(AndroidUtilities.dp(16));
            nameView = new TextView(context);
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            nameView.setLines(1);
            nameView.setEllipsize(TextUtils.TruncateAt.END);
            addView(nameView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 59, 0, 40, 0));

            reactionImageView = new BackupImageView(context);
            reactionImageView.setVisibility(GONE);
            addView(reactionImageView, LayoutHelper.createFrame(32, 32, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 6, 0));

            nameView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), View.MeasureSpec.EXACTLY));
        }

        public void setReaction(TLRPC.Document document) {
            if (document == null) {
                reactionImageView.setVisibility(GONE);
                return;
            }

            TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90);
            SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(document, Theme.key_windowBackgroundGray, 1.0f);

            if (svgThumb != null) {
                if (thumb != null) {
                    reactionImageView.setImage(ImageLocation.getForDocument(thumb, document), null, "webp", svgThumb, null);
                } else {
                    reactionImageView.setImage(ImageLocation.getForDocument(document), null, "webp", svgThumb, null);
                }
            } else {
                reactionImageView.setImage(ImageLocation.getForDocument(thumb, document), null, "webp", null, null);
            }

            reactionImageView.setVisibility(VISIBLE);
            reactionImageView.setSize(AndroidUtilities.dp(24), AndroidUtilities.dp(24));
        }

        private boolean wasAnimate = true;

        public void setUser(TLRPC.User user) {
            if (user != null) {
                avatarDrawable.setInfo(user);
                ImageLocation imageLocation = ImageLocation.getForUser(user, ImageLocation.TYPE_SMALL);
                avatarImageView.setImage(imageLocation, "50_50", avatarDrawable, user);
                nameView.setText(ContactsController.formatName(user.first_name, user.last_name));

                if (!wasAnimate) {
                    nameView.animate().alpha(1f).setDuration(220).start();
                    avatarImageView.animate().alpha(1f).setDuration(220).start();
                    reactionImageView.setVisibility(GONE);
                    reactionImageView.animate().alpha(1f).setDuration(220).start();
                    flickerLoadingView.animate().alpha(0f).setDuration(220).setListener(new HideViewAfterAnimation(flickerLoadingView)).start();
                    wasAnimate = true;
                } else {
                    nameView.setAlpha(1f);
                    avatarImageView.setAlpha(1f);
                    reactionImageView.setAlpha(1f);
                    flickerLoadingView.setAlpha(0f);
                    flickerLoadingView.setVisibility(GONE);
                }
            } else {
                nameView.setAlpha(0f);
                avatarImageView.setAlpha(0f);
                reactionImageView.setAlpha(0f);
                flickerLoadingView.setAlpha(1f);
                flickerLoadingView.setVisibility(VISIBLE);
            }
        }
    }

    private class ReactionsPagesLayout extends FrameLayout {
        public ReactionsPage[] reactionPages = new ReactionsPage[2];

        private Drawable headerShadowDrawable;

        public ReactionsPagesLayout(@NonNull Context context) {
            super(context);
            setWillNotDraw(false);

            headerShadowDrawable = getResources().getDrawable(R.drawable.header_shadow).mutate();

            for (int a = 0; a < reactionPages.length; a++) {
                final ReactionsPage mediaPage = new ReactionsPage(context);

                addView(mediaPage, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                reactionPages[a] = mediaPage;
                reactionPages[a].layoutManager = new LinearLayoutManager(context);
                reactionPages[a].listView = new RecyclerListView(context);
                reactionPages[a].listAdapter = new ListAdapter(context);
                reactionPages[a].listAdapter.setDatasetIndex(0);
                reactionPages[a].listView.setLayoutManager(reactionPages[a].layoutManager);
                reactionPages[a].listView.setAdapter(reactionPages[a].listAdapter);
                reactionPages[a].addView(reactionPages[a].listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

                int finalA = a;
                reactionPages[a].listView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
                    @Override
                    public void onItemClick(View view, int position) {
                        TLRPC.User currentUser = null;
                        ArrayList<TLRPC.TL_messageUserReaction> reactedReactionsList = reactedReactionsLists.get(reactionPages[finalA].selectedType);
                        if (reactedReactionsList == null) {
                            return;
                        }

                        if (position < reactionsCount) {
                            if (position < reactedReactionsList.size()) {
                                TLRPC.TL_messageUserReaction userReaction = reactedReactionsList.get(position);
                                currentUser = allUsersMap.get(userReaction.user_id);
                            }
                        } else {
                            if (position - reactionsCount < seenUsersListToShow.size()) {
                                currentUser = seenUsersListToShow.get(position - reactionsCount);
                            }
                        }

                        if (currentUser != null) {
                            Bundle args = new Bundle();
                            args.putLong("user_id", currentUser.id);
                            ProfileActivity fragment = new ProfileActivity(args);
                            fragment.setPlayProfileAnimation(0);
                            AndroidUtilities.setAdjustResizeToNothing(getParentActivity(), classGuid);
                            parentBaseFragment.presentFragment(fragment);

                            AndroidUtilities.runOnUIThread(() -> {
                                if (parentPopupWindow != null) {
                                    parentPopupWindow.dismiss();
                                }
                            }, 100);
                        }
                    }
                });
                reactionPages[a].listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
                    @Override
                    public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                        super.onScrolled(recyclerView, dx, dy);
                        int visibleItemCount = reactionPages[finalA].layoutManager.getChildCount();
                        int totalItemCount = reactionPages[finalA].layoutManager.getItemCount();
                        int firstVisibleItems = reactionPages[finalA].layoutManager.findFirstVisibleItemPosition();

                        ArrayList<TLRPC.TL_messageUserReaction> reactedReactionsList = reactedReactionsLists.get(reactionPages[finalA].selectedType);
                        if (reactedReactionsList == null) {
                            return;
                        }

                        if ( (visibleItemCount+firstVisibleItems) >= reactedReactionsList.size()) {
                            loadNextReactions(reactionPages[finalA].selectedType);
                        }
                    }
                });


                if (a != 0) {
                    reactionPages[a].setVisibility(View.GONE);
                }
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (shadowSectionFrameLayout.getVisibility() == GONE) {
                headerShadowDrawable.setBounds(0, 0, getMeasuredWidth(), headerShadowDrawable.getIntrinsicHeight());
                headerShadowDrawable.draw(canvas);
            }
        }
    }

    private static class ReactionsPage extends FrameLayout {
        public long lastCheckScrollTime;

        public RecyclerListView listView;
        public ListAdapter listAdapter;
        public LinearLayoutManager layoutManager;

        public RecyclerAnimationScrollHelper scrollHelper;
        public int selectedType = -1;

        public ReactionsPage(Context context) {
            super(context);
        }
    }
}
