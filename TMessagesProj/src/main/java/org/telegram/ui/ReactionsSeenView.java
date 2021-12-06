package org.telegram.ui;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.AvatarsImageView;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.HideViewAfterAnimation;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.HashMap;

public class ReactionsSeenView extends FrameLayout {

    HashMap<String, TLRPC.TL_availableReaction> availableReactionMap;

    ArrayList<Long> peerIds = new ArrayList<>();
    public ArrayList<TLRPC.User> users = new ArrayList<>();
    AvatarsImageView avatarsImageView;

    TextView titleView;
    ImageView iconView;
    BackupImageView reactionView;

    int currentAccount;
    boolean isVoice;

    boolean showMessageSeen;
    MessageObject messageObject;
    TLRPC.Chat chat;
    long finalFromId;

    int viewsCount = -1;
    int reactionsCount = -1;


    FlickerLoadingView flickerLoadingView;

    public ReactionsSeenView(@NonNull Context context, HashMap<String, TLRPC.TL_availableReaction> availableReactionMap, int currentAccount, MessageObject messageObject, TLRPC.Chat chat, boolean showMessageSeen) {
        super(context);
        this.currentAccount = currentAccount;
        this.messageObject = messageObject;
        this.showMessageSeen = showMessageSeen;
        this.availableReactionMap = availableReactionMap;
        this.chat = chat;

        isVoice = (messageObject.isRoundVideo() || messageObject.isVoice());
        flickerLoadingView = new FlickerLoadingView(context);
        flickerLoadingView.setColors(Theme.key_actionBarDefaultSubmenuBackground, Theme.key_listSelector, null);
        flickerLoadingView.setViewType(FlickerLoadingView.MESSAGE_SEEN_TYPE);
        flickerLoadingView.setIsSingleCell(false);
        addView(flickerLoadingView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setLines(1);
        titleView.setEllipsize(TextUtils.TruncateAt.END);

        addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 44, 0, 62, 0));

        avatarsImageView = new AvatarsImageView(context, false);
        avatarsImageView.setStyle(AvatarsImageView.STYLE_MESSAGE_SEEN);
        addView(avatarsImageView, LayoutHelper.createFrame(24 + 12 + 12 + 8, LayoutHelper.MATCH_PARENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 0, 0));

        titleView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));

        iconView = new ImageView(context);
        addView(iconView, LayoutHelper.createFrame(24, 24, Gravity.LEFT | Gravity.CENTER_VERTICAL, 11, 0, 0, 0));
        Drawable drawable = ContextCompat.getDrawable(context, R.drawable.msg_reactions).mutate();
        drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon), PorterDuff.Mode.MULTIPLY));
        iconView.setImageDrawable(drawable);

        reactionView = new BackupImageView(context);
        reactionView.setVisibility(GONE);
        addView(reactionView, LayoutHelper.createFrame(24, 24, Gravity.LEFT | Gravity.CENTER_VERTICAL, 11, 0, 0, 0));

        avatarsImageView.setAlpha(0);
        titleView.setAlpha(0);
        long fromId = 0;
        if (messageObject.messageOwner.from_id != null) {
            fromId = messageObject.messageOwner.from_id.user_id;
        }
        finalFromId = fromId;


        if (showMessageSeen) {
            TLRPC.TL_messages_getMessageReadParticipants req = new TLRPC.TL_messages_getMessageReadParticipants();
            req.msg_id = messageObject.getId();
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(messageObject.getDialogId());

            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                FileLog.e("MessageSeenView request completed");
                if (error == null) {
                    TLRPC.Vector vector = (TLRPC.Vector) response;
                    viewsCount = vector.objects.size() + 1;
                    updateView();
                } else {
                    updateView();
                }
            }));
        } else {
            updateView();
        }

        getUsers();

        setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), AndroidUtilities.dp(4), AndroidUtilities.dp(4)));
        setEnabled(true);
    }

    boolean ignoreLayout;

    @Override
    public void requestLayout() {
        if (ignoreLayout) {
            return;
        }
        super.requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (flickerLoadingView.getVisibility() == View.VISIBLE) {
            ignoreLayout = true;
            flickerLoadingView.setVisibility(View.GONE);
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            flickerLoadingView.getLayoutParams().width = getMeasuredWidth();
            flickerLoadingView.setVisibility(View.VISIBLE);
            ignoreLayout = false;
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    private void updateView() {
        //setEnabled(users.size() > 0);

        for (int i = 0; i < 3; i++) {
            if (i < users.size()) {
                avatarsImageView.setObject(i, currentAccount, users.get(i));
            } else {
                avatarsImageView.setObject(i, currentAccount, null);
            }
        }
        if (users.size() == 1) {
            avatarsImageView.setTranslationX(AndroidUtilities.dp(24));
        } else if (users.size() == 2) {
            avatarsImageView.setTranslationX(AndroidUtilities.dp(12));
        } else {
            avatarsImageView.setTranslationX(0);
        }

        if ((viewsCount != -1 || !showMessageSeen) && reactionsCount != -1) {
            avatarsImageView.commitTransition(false);
            if (peerIds.size() == 1 && users.get(0) != null && !showMessageSeen) {
                titleView.setText(ContactsController.formatName(users.get(0).first_name, users.get(0).last_name));
                if (messageObject.messageOwner.reactions != null && messageObject.messageOwner.reactions.results.size() == 1) {
                    TLRPC.TL_reactionCount reactionCount = messageObject.messageOwner.reactions.results.get(0);
                    TLRPC.TL_availableReaction reaction = availableReactionMap.get(reactionCount.reaction);
                    if (reaction != null) {
                        TLRPC.Document document = reaction.static_icon;
                        TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90);
                        SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(document, Theme.key_windowBackgroundGray, 1.0f);
                        if (svgThumb != null) {
                            if (thumb != null) {
                                reactionView.setImage(ImageLocation.getForDocument(thumb, document), null, "webp", svgThumb, null);
                            } else {
                                reactionView.setImage(ImageLocation.getForDocument(document), null, "webp", svgThumb, null);
                            }
                        } else {
                            reactionView.setImage(ImageLocation.getForDocument(thumb, document), null, "webp", null, null);
                        }
                    }
                    reactionView.setVisibility(VISIBLE);
                    iconView.setVisibility(GONE);
                }
            } else if (showMessageSeen) {
                titleView.setText(reactionsCount + "/" + viewsCount + " Reacted");
            } else {
                titleView.setText(reactionsCount + " Reactions");
            }
            titleView.animate().alpha(1f).setDuration(220).start();
            avatarsImageView.animate().alpha(1f).setDuration(220).start();
            flickerLoadingView.animate().alpha(0f).setDuration(220).setListener(new HideViewAfterAnimation(flickerLoadingView)).start();
        }
    }

    public int getTotalCount() {
        if (showMessageSeen) {
            return viewsCount;
        }

        return reactionsCount;
    }

    private void getUsers() {
        if (messageObject.messageOwner.reactions != null) {
            reactionsCount = 0;
            for (TLRPC.TL_reactionCount reactionCount : messageObject.messageOwner.reactions.results) {
                reactionsCount += reactionCount.count;
            }

            ArrayList<Long> unknownUsers = new ArrayList<>();
            HashMap<Long, TLRPC.User> usersLocal = new HashMap<>();
            ArrayList<Long> allPeers = new ArrayList<>();
            for ( TLRPC.TL_messageUserReaction userReaction : messageObject.messageOwner.reactions.recent_reactons ) {
                long peerId = userReaction.user_id;
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(peerId);
                allPeers.add(peerId);
                if (user == null) {
                    unknownUsers.add(peerId);
                } else {
                    usersLocal.put(peerId, user);
                }
            }

            if (unknownUsers.isEmpty()) {
                for (int i = 0; i < allPeers.size(); i++) {
                    peerIds.add(allPeers.get(i));
                    users.add(usersLocal.get(allPeers.get(i)));
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
                            }
                            for (int i = 0; i < allPeers.size(); i++) {
                                peerIds.add(allPeers.get(i));
                                this.users.add(usersLocal.get(allPeers.get(i)));
                            }
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
                            }
                            for (int i = 0; i < allPeers.size(); i++) {
                                peerIds.add(allPeers.get(i));
                                this.users.add(usersLocal.get(allPeers.get(i)));
                            }
                        }
                        updateView();
                    }));
                }
            }
        }
    }

}
