package org.telegram.ui;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.Point;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.HashMap;

public class ReactionAnimationsOverlay {
    int currentAccount;

    HashMap<String, TLRPC.Document> emojiStickersMap = new HashMap<>();
    HashMap<String, TLRPC.Document> emojiEffectsStickersMap = new HashMap<>();

    HashMap<Long, Integer> lastAnimationIndex = new HashMap<>();

    private boolean attached;

    ArrayList<DrawingObject> drawingObjects = new ArrayList<>();

    FrameLayout contentLayout;
    RecyclerListView listView;
    boolean inited = false;

    public ReactionAnimationsOverlay(ChatActivity chatActivity, FrameLayout frameLayout, RecyclerListView chatListView, int currentAccount, long dialogId, int threadMsgId) {
        this.contentLayout = frameLayout;
        this.listView = chatListView;
        this.currentAccount = currentAccount;
    }

    protected void onAttachedToWindow() {
        attached = true;
        checkStickerPack();
    }

    protected void onDetachedFromWindow() {
        attached = false;
    }

    public void checkStickerPack() {
        if (inited) {
            return;
        }

        ArrayList<TLRPC.TL_availableReaction> reactionsList = MessagesController.getInstance(currentAccount).getAvailableReactionsList();

        for (TLRPC.TL_availableReaction availableReaction : reactionsList) {
            emojiStickersMap.put(availableReaction.reaction, availableReaction.activate_animation);
            emojiEffectsStickersMap.put(availableReaction.reaction, availableReaction.effect_animation);
        }

        inited = true;
    }

    public void draw(Canvas canvas) {
        if (!drawingObjects.isEmpty()) {
            for (int i = 0; i < drawingObjects.size(); i++) {
                DrawingObject drawingObject = drawingObjects.get(i);

                float size = drawingObject.startSize + (drawingObject.finishSize - drawingObject.startSize) * drawingObject.progress;
                float x = drawingObject.startX + (drawingObject.finishX - drawingObject.startX) * drawingObject.progress;
                float y = drawingObject.startY + (drawingObject.finishY - drawingObject.startY) * drawingObject.progress;

                if (drawingObject.removed) {
                    drawingObjects.remove(i);
                    i--;
                    continue;
                }

                drawingObject.imageReceiver.setImageCoords(x - size / 2, y - size / 2, size, size);
                drawingObject.effectImageReceiver.setImageCoords(x - size * 1.85f, y - size * 1.1f, size * 2.5f, size * 2.5f);

                drawingObject.imageReceiver.draw(canvas);
                drawingObject.effectImageReceiver.draw(canvas);

                if (drawingObject.wasPlayed && drawingObject.imageReceiver.getLottieAnimation() != null && drawingObject.imageReceiver.getLottieAnimation().getCurrentFrame() == drawingObject.imageReceiver.getLottieAnimation().getFramesCount() - 2) {
                    drawingObject.animator = ValueAnimator.ofFloat(1f, 0f);
                    drawingObject.animator.addListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(Animator animation) {

                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            drawingObject.removed = true;
                            contentLayout.invalidate();
                        }

                        @Override
                        public void onAnimationCancel(Animator animation) {
                            drawingObject.removed = true;
                            contentLayout.invalidate();
                        }

                        @Override
                        public void onAnimationRepeat(Animator animation) {

                        }
                    });
                    drawingObject.animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                        @Override
                        public void onAnimationUpdate(ValueAnimator animation) {
                            drawingObject.progress = (Float) animation.getAnimatedValue();
                            contentLayout.invalidate();
                        }
                    });

                    Point position = drawingObject.cell.getReactionButtonPosition(drawingObject.reaction);
                    if (position != null) {
                        drawingObject.startX = position.x + listView.getX() + drawingObject.cell.getX();
                        drawingObject.startY = position.y + listView.getY() + drawingObject.cell.getY();
                    }

                    drawingObject.animator.setDuration(220);
                    drawingObject.animator.start();
                    drawingObject.finished = true;
                }  else if (drawingObject.imageReceiver.getLottieAnimation() != null && drawingObject.imageReceiver.getLottieAnimation().isRunning()) {
                    drawingObject.wasPlayed = true;
                } else if (drawingObject.imageReceiver.getLottieAnimation() != null && !drawingObject.imageReceiver.getLottieAnimation().isRunning()) {
                    drawingObject.imageReceiver.getLottieAnimation().setCurrentFrame(0, true);
                    drawingObject.imageReceiver.getLottieAnimation().start();
                }
            }
            contentLayout.invalidate();
        }
    }

    public void onTapReaction(ChatMessageCell view, String reaction) {
        if (view.getMessageObject() == null || view.getMessageObject().getId() < 0) {
            return;
        }

        showAnimationForCell(view, reaction, -1, -1);
    }

    public void onTapReaction(ChatMessageCell view, String reaction, int x, int y) {
        if (view.getMessageObject() == null || view.getMessageObject().getId() < 0) {
            return;
        }

        showAnimationForCell(view, reaction, x, y);
    }

    private void showAnimationForCell(ChatMessageCell view, String reaction, int x, int y) {
        if (!attached) {
            return;
        }

        if (drawingObjects.size() > 12) {
            return;
        }

        TLRPC.Document activateAnimation = emojiStickersMap.get(reaction);
        TLRPC.Document effectAnimation = emojiEffectsStickersMap.get(reaction);
        if (activateAnimation == null || effectAnimation == null) {
            return;
        }

        float startX = (float) x;
        float startY = (float) y;

        Point position = view.getReactionButtonPosition(reaction);
        if (position != null && x < 0 && y < 0 ) {
            startX = position.x + listView.getX() + view.getX();
            startY = position.y + listView.getY() + view.getY();
        }


        int sameAnimationsCount = 0;
        for (int i = 0; i < drawingObjects.size(); i++) {
            if (drawingObjects.get(i).messageId == view.getMessageObject().getId()) {
                sameAnimationsCount++;
                if (drawingObjects.get(i).imageReceiver.getLottieAnimation() == null || drawingObjects.get(i).imageReceiver.getLottieAnimation().isGeneratingCache()) {
                    return;
                }
            }
        }
        if (sameAnimationsCount >= 4) {
            return;
        }

        DrawingObject drawingObject = new DrawingObject();
        drawingObject.reaction = reaction;
        drawingObject.cell = view;
        drawingObject.messageId = view.getMessageObject().getId();
        drawingObject.document = activateAnimation;
        drawingObject.effectDocument = effectAnimation;

        /**/

        Integer lastIndex = lastAnimationIndex.get(activateAnimation.id);
        int currentIndex = lastIndex == null ? 0 : lastIndex;
        lastAnimationIndex.put(activateAnimation.id, (currentIndex + 1) % 4);

        ImageLocation imageLocation = ImageLocation.getForDocument(activateAnimation);
        drawingObject.imageReceiver.setUniqKeyPrefix(currentIndex + "_" + drawingObject.messageId + "_" + activateAnimation.id + "_");

        int w = 512;
        drawingObject.imageReceiver.setImage(imageLocation, w + "_" + w + "_pcache", null, "tgs", emojiStickersMap, 1);
        drawingObject.imageReceiver.setLayerNum(Integer.MAX_VALUE);
        drawingObject.imageReceiver.setAllowStartAnimation(true);
        drawingObject.imageReceiver.setAutoRepeat(0);
        if (drawingObject.imageReceiver.getLottieAnimation() != null) {
            drawingObject.imageReceiver.getLottieAnimation().start();
        }

        drawingObject.imageReceiver.onAttachedToWindow();
        drawingObject.imageReceiver.setParentView(contentLayout);

        /**/

        Integer lastIndex2 = lastAnimationIndex.get(effectAnimation.id);
        int currentIndex2 = lastIndex2 == null ? 0 : lastIndex2;
        lastAnimationIndex.put(effectAnimation.id, (currentIndex2 + 1) % 4);

        ImageLocation effectImageLocation = ImageLocation.getForDocument(effectAnimation);
        drawingObject.effectImageReceiver.setUniqKeyPrefix(currentIndex2 + "_" + drawingObject.messageId + "_" + effectAnimation.id + "_");

        int w2 = 512;
        drawingObject.effectImageReceiver.setImage(effectImageLocation, w2 + "_" + w2 + "_pcache", null, "tgs", emojiStickersMap, 1);
        drawingObject.effectImageReceiver.setLayerNum(Integer.MAX_VALUE);
        drawingObject.effectImageReceiver.setAllowStartAnimation(true);
        drawingObject.effectImageReceiver.setAutoRepeat(0);
        if (drawingObject.effectImageReceiver.getLottieAnimation() != null) {
            drawingObject.effectImageReceiver.getLottieAnimation().start();
        }

        drawingObject.effectImageReceiver.onAttachedToWindow();
        drawingObject.effectImageReceiver.setParentView(contentLayout);

        /**/

        drawingObject.startX = startX;
        drawingObject.startY = startY;
        drawingObject.finishX = contentLayout.getWidth() / 2f;
        drawingObject.finishY = contentLayout.getHeight() / 2f;
        drawingObject.startSize = AndroidUtilities.dp(20);
        drawingObject.finishSize = AndroidUtilities.dp(180);
        drawingObject.progress = 0f;
        drawingObject.animator = ValueAnimator.ofFloat(0, 1);
        drawingObject.animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                drawingObject.progress = (Float) animation.getAnimatedValue();
                contentLayout.invalidate();
            }
        });

        drawingObject.animator.setDuration(220);
        drawingObject.animator.start();

        drawingObjects.add(drawingObject);
        contentLayout.invalidate();

    }

    public void onScrolled(int dy) {
        for (int i = 0; i < drawingObjects.size(); i++) {
            drawingObjects.get(i).startY -= dy;
        }
    }

    private class DrawingObject {
        ChatMessageCell cell;
        public String reaction;

        public float startX;
        public float startY;
        public float finishX;
        public float finishY;
        public float startSize;
        public float finishSize;
        public float progress;

        public boolean removed;
        public boolean finished;
        boolean wasPlayed;
        int messageId;

        TLRPC.Document document;
        TLRPC.Document effectDocument;

        ImageReceiver imageReceiver = new ImageReceiver();
        ImageReceiver effectImageReceiver = new ImageReceiver();

        public ValueAnimator animator;
    }
}
