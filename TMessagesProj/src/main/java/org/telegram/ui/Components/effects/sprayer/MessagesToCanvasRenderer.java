package org.telegram.ui.Components.effects.sprayer;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class MessagesToCanvasRenderer {
    private final ChatActivity activity;
    private final Runnable applyRunnable;
    private final ArrayList<View> cells = new ArrayList<>();

    public MessagesToCanvasRenderer (ChatActivity activity) {
        this.applyRunnable = this::apply;
        this.activity = activity;
    }

    public void onMessageRemoved (View view) {
        cells.add(view);

        AndroidUtilities.cancelRunOnUIThread(applyRunnable);
        AndroidUtilities.runOnUIThread(applyRunnable);
    }

    private final Rect tmpSizeRect = new Rect();
    private final int[] cords = new int[2];

    private void apply () {
        final Set<View> views = new HashSet<>(cells);
        final View contentView = activity.getContentView();
        final int width = contentView.getMeasuredWidth();
        final int height = contentView.getMeasuredHeight();

        contentView.getLocationOnScreen(cords);
        final int contentX = cords[0];
        final int contentY = cords[1];


        final View actionBar = activity.getActionBar();
        actionBar.getLocationOnScreen(cords);
        final int actionBarTop = cords[1];
        final int actionBatBottom = actionBarTop + actionBar.getMeasuredHeight();

        final View chatListView = activity.getChatListView();
        chatListView.getLocationOnScreen(cords);
        final int keyboardCompensation = actionBatBottom - cords[1];
        final int chatListViewRealTop = cords[1];
        final int chatListViewFakeTop = cords[1] + keyboardCompensation;
        final int chatListViewBottom = chatListViewRealTop + chatListView.getMeasuredHeight();
        final int chatListViewHeight = chatListViewBottom - chatListViewFakeTop;
        final int offsetX = cords[0] - contentX;
        final int offsetY = chatListViewFakeTop - contentY;

        tmpSizeRect.set(0, 0, 0, 0);
        for (View view : cells) {
            final int x = (int) view.getX();
            final int y = (int) view.getY() - keyboardCompensation;
            final int left, right;
            final int top = offsetY + y;
            final int bottom = offsetY + y + view.getMeasuredHeight();
            if (view instanceof ChatMessageCell) {
                ChatMessageCell messageCell = (ChatMessageCell) view;
                left = offsetX + x + messageCell.getCurrentBackgroundLeft();
                right = offsetX + x + messageCell.getCurrentBackgroundRight();
            } else {
                left = offsetX + x;
                right = offsetX + x + view.getMeasuredWidth();
            }
            tmpSizeRect.union(left, top, right, bottom);
        }
        if (!tmpSizeRect.intersect(offsetX, offsetY, offsetX + chatListView.getMeasuredWidth(), offsetY + chatListViewHeight)) {
            cells.clear();
            return;
        }

        Bitmap bitmap2 = Bitmap.createBitmap(
            tmpSizeRect.width() + tmpSizeRect.width() % 2,
            tmpSizeRect.height() + tmpSizeRect.height() % 2, Bitmap.Config.ARGB_8888);
        Canvas canvas2 = new Canvas(bitmap2);

        canvas2.save();
        canvas2.translate(offsetX - tmpSizeRect.left, offsetY - tmpSizeRect.top - keyboardCompensation);
        activity.drawChatBackgroundElements(canvas2, views);
        for (View view : cells) {
            activity.getChatListView().drawChild(canvas2, view, 0);
        }
        activity.drawChatForegroundElements(canvas2);
        canvas2.restore();

        activity.onMessagesRemoved(new SprayerBitmapState(bitmap2, tmpSizeRect, width, height));
        cells.clear();
    }
}
