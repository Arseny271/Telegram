package org.telegram.ui.Components.quickforward;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.Bulletin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class QuickShareSelectorOverlayLayout extends View {
    private final HashMap<String, QuickShareSelectorDrawable> drawableHashMap = new HashMap<>();
    private final ArrayList<String> drawablesForRemove = new ArrayList<>();

    public QuickShareSelectorOverlayLayout(Context context) {
        super(context);
    }

    public void open(ChatMessageCell cell) {
        fetchDialogs();

        final String key = key(cell);
        if (key == null) {
            return;
        }

        final QuickShareSelectorDrawable drawable = new QuickShareSelectorDrawable(this, cell, dialogs, key, () -> {
            drawablesForRemove.add(key);
            invalidate();
        });
        drawable.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
        drawable.setCallback(this);

        if (!drawableHashMap.containsKey(key)) {
            drawableHashMap.put(key, drawable);
        }
    }

    public boolean isActive() {
        for (Map.Entry<String, QuickShareSelectorDrawable> e : drawableHashMap.entrySet()) {
            if (e.getValue().isActive()) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void invalidateDrawable(@NonNull Drawable drawable) {
        super.invalidateDrawable(drawable);
        if (drawable instanceof QuickShareSelectorDrawable) {
            invalidate();
        }
    }

    public void onTouchMoveEvent (ChatMessageCell cell, float x, float y) {
        final QuickShareSelectorDrawable drawable = drawableHashMap.get(key(cell));
        if (drawable != null) {
            drawable.onTouchMoveEvent(x, y);
        }
    }

    public long getSelectedDialogId(ChatMessageCell cell) {
        final QuickShareSelectorDrawable drawable = drawableHashMap.get(key(cell));
        if (drawable != null) {
            return drawable.getSelectedDialogId();
        }

        return 0;
    }

    public MessageObject getSelectedMessageObject(ChatMessageCell cell) {
        final QuickShareSelectorDrawable drawable = drawableHashMap.get(key(cell));
        if (drawable != null) {
            return drawable.messageObject;
        }

        return null;
    }

    public void close(ChatMessageCell cell, Bulletin bulletin) {
        final QuickShareSelectorDrawable drawable = drawableHashMap.get(key(cell));
        if (drawable != null) {
            drawable.close(bulletin);
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        for (Map.Entry<String, QuickShareSelectorDrawable> e : drawableHashMap.entrySet()) {
            e.getValue().destroy();
        }

        drawableHashMap.clear();
        drawablesForRemove.clear();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        for (Map.Entry<String, QuickShareSelectorDrawable> e : drawableHashMap.entrySet()) {
            e.getValue().setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        for (Map.Entry<String, QuickShareSelectorDrawable> e : drawableHashMap.entrySet()) {
            e.getValue().draw(canvas);
        }

        if (!drawablesForRemove.isEmpty()) {
            for (String key : drawablesForRemove) {
                final QuickShareSelectorDrawable d = drawableHashMap.remove(key);
                if (d != null) {
                    d.destroy();
                }
            }
            drawablesForRemove.clear();
        }
    }

    private final ArrayList<TLRPC.Dialog> dialogs = new ArrayList<>();
    private final int currentAccount = UserConfig.selectedAccount;

    private void fetchDialogs() {
        dialogs.clear();
        long selfUserId = UserConfig.getInstance(currentAccount).clientUserId;
        if (!MessagesController.getInstance(currentAccount).dialogsForward.isEmpty()) {
            TLRPC.Dialog dialog = MessagesController.getInstance(currentAccount).dialogsForward.get(0);
            dialogs.add(dialog);
        }
        ArrayList<TLRPC.Dialog> archivedDialogs = new ArrayList<>();
        ArrayList<TLRPC.Dialog> allDialogs = MessagesController.getInstance(currentAccount).getAllDialogs();
        for (int a = 0; a < allDialogs.size(); a++) {
            TLRPC.Dialog dialog = allDialogs.get(a);
            if (!(dialog instanceof TLRPC.TL_dialog)) {
                continue;
            }
            if (dialog.id == selfUserId) {
                continue;
            }
            if (!DialogObject.isEncryptedDialog(dialog.id)) {
                if (DialogObject.isUserDialog(dialog.id)) {
                    if (dialog.folder_id == 1) {
                        archivedDialogs.add(dialog);
                    } else {
                        dialogs.add(dialog);
                    }
                } else {
                    TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialog.id);
                    if (!(chat == null || chat.forum || ChatObject.isNotInChat(chat) || chat.gigagroup && !ChatObject.hasAdminRights(chat) || ChatObject.isChannel(chat) && !chat.creator && (chat.admin_rights == null || !chat.admin_rights.post_messages) && !chat.megagroup)) {
                        if (dialog.folder_id == 1) {
                            archivedDialogs.add(dialog);
                        } else {
                            dialogs.add(dialog);
                        }
                    }
                }
            }
        }
        dialogs.addAll(archivedDialogs);
    }

    private static String key (ChatMessageCell cell) {
        MessageObject object = cell.getMessageObject();
        if (object == null) {
            return null;
        }

        return object.getChatId() + "_" + object.getId();
    }
}
