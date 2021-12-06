package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
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
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.HideViewAfterAnimation;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerAnimationScrollHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScrollSlidingReactionsTabStrip;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;

public class SeenReactionsSinglePopupFragment extends ExtendedActionBarPopupWindowBaseFragment {

    private TLRPC.Chat currentChat;
    private TLRPC.ChatFull info;
    private long chatId;
    private MessageObject messageObject;

    private LinearLayout linearLayout;
    private ReactionsPagesLayout reactionPagesView;

    private HashMap<Long, TLRPC.User> allUsersMap = new HashMap<>();
    private ArrayList<TLRPC.TL_messageUserReaction> reactedReactionsList = new ArrayList<>();
    private HashMap<Long, TLRPC.TL_messageUserReaction> reactedReactionsMap = new HashMap<>();

    private TLRPC.Chat chat;

    public boolean animatingForward;

    private HashMap<String, TLRPC.TL_availableReaction> availableReactionsMap;
    private int totalCount = 0;
    private TLRPC.TL_reactionCount reaction;

    public SeenReactionsSinglePopupFragment(long id, TLRPC.Chat chat, TLRPC.TL_reactionCount reaction) {
        super();
        this.chatId = id;
        this.chat = chat;
        this.reaction = reaction;
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

        return super.onFragmentCreate();
    }

    private boolean loading = false;
    private String offset;

    private void loadNextReactions() {
        if (reactedReactionsList == null || reactedReactionsMap == null || loading) {
            return;
        }

        if (totalCount <= reactedReactionsList.size()) {
            return;
        }

        loading = true;

        TLRPC.TL_messages_getMessageReactionsList req = new TLRPC.TL_messages_getMessageReactionsList();
        req.peer = getMessagesController().getInputPeer(-chatId);
        req.id = messageObject.getId();
        req.limit = 50;
        req.reaction = reaction.reaction;
        req.flags |= 1;

        if (offset != null) {
            req.offset = offset;
            req.flags |= 2;
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
                    offset = reactions.next_offset;
                }

                updateView();
            }

            loading = false;
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

        reactionPagesView = new ReactionsPagesLayout(context) {
            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                int height = AndroidUtilities.dp(Math.min(totalCount * 48, 360));

                super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
            }
        };

        linearLayout.addView(reactionPagesView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        loadNextReactions();

        return fragmentView;
    }

    @SuppressLint("NotifyDataSetChanged" )
    private void updateView() {
        reactionPagesView.reactionPages[0].listAdapter.notifyDataSetChanged();
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        public static final int REACTION_USER_CELL = 1;

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return totalCount;
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
            switch (holder.getItemViewType()) {
                case REACTION_USER_CELL: {
                    UserCell cell = (UserCell) holder.itemView;
                    TLRPC.User user = null;
                    TLRPC.Document document = null;

                    if (position < reactedReactionsList.size()) {
                        TLRPC.TL_messageUserReaction userReaction = reactedReactionsList.get(position);
                        user = allUsersMap.get(userReaction.user_id);
                        TLRPC.TL_availableReaction availableReaction = availableReactionsMap.get(userReaction.reaction);
                        if (availableReaction != null) {
                            document = availableReaction.static_icon;
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
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(220), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY));
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
        public ReactionsPage[] reactionPages = new ReactionsPage[1];

        public ReactionsPagesLayout(@NonNull Context context) {
            super(context);

            for (int a = 0; a < reactionPages.length; a++) {
                final ReactionsPage mediaPage = new ReactionsPage(context);

                addView(mediaPage, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                reactionPages[a] = mediaPage;
                reactionPages[a].layoutManager = new LinearLayoutManager(context);
                reactionPages[a].listView = new RecyclerListView(context);
                reactionPages[a].listAdapter = new ListAdapter(context);
                reactionPages[a].listView.setLayoutManager(reactionPages[a].layoutManager);
                reactionPages[a].listView.setAdapter(reactionPages[a].listAdapter);
                reactionPages[a].addView(reactionPages[a].listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

                int finalA = a;
                reactionPages[a].listView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
                    @Override
                    public void onItemClick(View view, int position) {
                        TLRPC.User currentUser = null;
                        if (position < totalCount) {
                            if (position < reactedReactionsList.size()) {
                                TLRPC.TL_messageUserReaction userReaction = reactedReactionsList.get(position);
                                currentUser = allUsersMap.get(userReaction.user_id);
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

                        if ( (visibleItemCount + firstVisibleItems) >= reactedReactionsList.size()) {
                            loadNextReactions();
                        }
                    }
                });


                if (a != 0) {
                    reactionPages[a].setVisibility(View.GONE);
                }
            }
        }
    }

    private static class ReactionsPage extends FrameLayout {
        public RecyclerListView listView;
        public ListAdapter listAdapter;
        public LinearLayoutManager layoutManager;

        public ReactionsPage(Context context) {
            super(context);
        }
    }
}
