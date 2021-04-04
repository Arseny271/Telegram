package org.telegram.ui.AnimationSettings;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.util.Log;
import android.util.Property;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimationProperties;
import org.telegram.ui.Components.LayoutHelper;

public class ChatAnimationsOverlay extends FrameLayout {
    public static class OverlayProperty {
        private Property<OverlayView, Float> property;

        interface OnSetPropertyListener {
            void onSetProperty(float value);
        }

        private String name;
        private float value;
        private OnSetPropertyListener listener;

        public OverlayProperty(String name) {
            property = new AnimationProperties.FloatProperty<OverlayView>(name) {
                @Override
                public void setValue(OverlayView object, float val) {
                    value = val;
                    if (listener != null) {
                        listener.onSetProperty(value);
                    }
                }

                @Override
                public Float get(OverlayView object) {
                    return value;
                }
            };
            this.name = name;
        }

        public void setUpdateListener(OnSetPropertyListener listener) {
            this.listener = listener;
        }

        public float getValue() {
            return value;
        }

        public String getName() {
            return name;
        }

        public Property<OverlayView, Float> getProperty() {
            return property;
        }
    }

    public static class OverlayView extends FrameLayout {

        public static class OverlaySubView extends FrameLayout {
            public OverlaySubView (Context context) {
                super(context);
                setWillNotDraw(false);
            }
        }

        protected ChatActivity activity;
        protected AnimationsController controller;
        protected String animationParamGroupName;
        protected ChatActivity fragment;

        protected ChatMessageCell messageCell;
        protected MessageObject messageObject;

        protected RectF startPosition;
        protected RectF finishPosition;

        protected boolean hasReply;
        protected RectF replyStartPosition;
        protected float replyOffset;

        protected OverlaySubView backgroundSubView;

        protected final OverlayProperty POSITION_X = new OverlayProperty("X");
        protected final OverlayProperty POSITION_Y = new OverlayProperty("Y");
        protected final OverlayProperty BUBBLE_SHAPE = new OverlayProperty("BubbleShape");
        protected final OverlayProperty SCALE = new OverlayProperty("Scale");
        protected final OverlayProperty COLOR_CHANGE = new OverlayProperty("ColorChange");
        protected final OverlayProperty TIME_APPEARS = new OverlayProperty("TimeAppears");

        private float dynamicOffset;        // scroll offset
        protected float targetOffset;
        public AnimationParameter offsetY;  // add height animated

        private boolean DEBUG = false;

        public OverlayView(ChatActivity chatActivity, String paramName) {
            super(chatActivity.getParentActivity());
            activity = chatActivity;
            controller = chatActivity.getAnimationController();
            animationParamGroupName = paramName;
            fragment = chatActivity;
            dynamicOffset = 0;
            targetOffset = 0;
            offsetY = new AnimationParameter(0, 0);


            if (!DEBUG) {
                POSITION_X.setUpdateListener(this::onPositionXUpdated);
                POSITION_Y.setUpdateListener((v) -> {
                    offsetY.update(v);
                    onPositionYUpdated(v);
                });
                BUBBLE_SHAPE.setUpdateListener(this::onBubbleShapeUpdated);
                SCALE.setUpdateListener(this::onScaleUpdated);
                COLOR_CHANGE.setUpdateListener(this::onColorChangeUpdated);
                TIME_APPEARS.setUpdateListener(this::onTimeAppearsUpdated);
            } else {
                POSITION_X.setUpdateListener((v)->onPositionXUpdated(0));
                POSITION_Y.setUpdateListener((v)->onPositionYUpdated(0));
                BUBBLE_SHAPE.setUpdateListener((v)->onBubbleShapeUpdated(0));
                SCALE.setUpdateListener((v)-> onScaleUpdated(0));
                COLOR_CHANGE.setUpdateListener((v)->onColorChangeUpdated(0));
                TIME_APPEARS.setUpdateListener((v)->onTimeAppearsUpdated(0));
            }
        }

        public Animator getPropertyAnimator(OverlayProperty property) {
            final ObjectAnimator animator = ObjectAnimator.ofFloat(
                    this, property.getProperty(), 0f, 1f);

            return controller.setAnimationSettings(animator,
                    String.format("%s%s", animationParamGroupName, property.getName()));
        }

        private int oldHeight;
        public void setMessageCell(ChatMessageCell messageCell) {
            this.messageCell = messageCell;
            this.messageObject = messageCell.getMessageObject();
            this.messageCell.setOverlayView(this);
            oldHeight = messageCell.getMeasuredHeight();
            Log.i("step", "set overlay");
            Log.i("translationY", "set this");
        }

        public void setReplyStartPosition(RectF position, float offset) {
            int coords[] = new int[2];
            activity.getActionBar().getLocationOnScreen(coords);
            position.left -= coords[0];
            position.right -= coords[0];
            position.top -= coords[1];
            position.bottom -= coords[1];

            replyStartPosition = position;
            replyOffset = offset;
            hasReply = true;
        }

        public void onAnimationFinish() {
            messageCell.setOverlayAnimated(false);
            this.messageCell.setOverlayView(null);
            activity.onMessageCellAnimationFinish(messageCell);
        }

        public float calculateValue(float start, float end, float progress) {
            return start + (end - start) * progress;
        }

        public int calculateValue(int start, int end, float progress) {
            return start + (int)((end - start) * progress);
        }

        private boolean firstOffset = true;
        public void setDeltaOffset(float y, boolean isNew, boolean isTargetOffset) {
            if (isNew) {
                y += (oldHeight - messageCell.getMeasuredHeight());
            }

            if (!firstOffset && isNew) {
                float oldTarget = offsetY.finish();
                float newTarget = oldTarget + y;
                float currentValue = offsetY.get();
                float currentProgress = offsetY.progress();

                float newStart;
                if (currentProgress < 0.999) {
                    newStart = currentValue - ((newTarget - currentValue) / (1 - currentProgress) - (newTarget - currentValue));
                } else {
                    newStart = 0;
                }

                offsetY.start(newStart);
                offsetY.finish(newTarget);

                invalidate();
                return;
            }

            if (firstOffset && isNew) {
                firstOffset = false;
            }

            if (isNew || isTargetOffset) {
                targetOffset += y;
            } else {
                dynamicOffset += y;
            }
            invalidate();
        }

        protected void addOnAnimationEndListener(Animator animator) {
            OverlayView view = this;
            animator.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) {

                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    view.onAnimationFinish();
                    ((ViewGroup)(view.getParent())).removeView(view);

                    if (backgroundSubView != null) {
                        ((ViewGroup)(backgroundSubView.getParent())).removeView(backgroundSubView);
                    }

                }

                @Override
                public void onAnimationCancel(Animator animator) {

                }

                @Override
                public void onAnimationRepeat(Animator animator) {

                }
            });
        }

        public float getTotalOffset() {
            return dynamicOffset + offsetY.get();
        }

        public void setStartPosition(RectF position) {
            int coords[] = new int[2];
            activity.getActionBar().getLocationOnScreen(coords);
            position.left -= coords[0];
            position.right -= coords[0];
            position.top -= coords[1];
            position.bottom -= coords[1];
            startPosition = position;
        }

        public void setFinishPosition(RectF position) {
            int coords[] = new int[2];
            activity.getActionBar().getLocationOnScreen(coords);
            position.left -= coords[0];
            position.right -= coords[0];
            position.top -= coords[1];
            position.bottom -= coords[1];

            finishPosition = position;
        }

        public void updateFinishPosition(RectF rect) {
            setFinishPosition(rect);
        }

        public void startAnimation() {
            messageObject.wasAnimated = true;

            onPositionXUpdated(0);
            onPositionYUpdated(0);
            onBubbleShapeUpdated(0);
            onScaleUpdated(0);
            onColorChangeUpdated(0);
            onTimeAppearsUpdated(0);

            messageCell.setOverlayAnimated(true);
        }

        protected void drawReply(Canvas canvas, float x, float y, float extraBackground, float lineHeight, int background) {
            float replyStartX = messageCell.getReplyStartX();
            if (messageCell.getCurrentMessagesGroup() != null && messageCell.getCurrentMessagesGroup().transitionParams.backgroundChangeBounds) {
                replyStartX += messageCell.getCurrentMessagesGroup().transitionParams.offsetLeft;
            }
            if (messageCell.getTransitionParams().animateBackgroundBoundsInner) {
                replyStartX += messageCell.getTransitionParams().deltaLeft;
            }

            canvas.save();
            canvas.translate(
                x - replyStartX,
                y - messageCell.getReplyStartY());
            messageCell.drawNamesLayout(canvas, 1f, extraBackground, lineHeight, background);
            canvas.restore();
        }


        public void onPositionXUpdated(float x) {};
        public void onPositionYUpdated(float y) {};
        public void onBubbleShapeUpdated(float bubbleShape) {};
        public void onScaleUpdated(float scale) {};
        public void onColorChangeUpdated(float colorChande) {};
        public void onTimeAppearsUpdated(float timeAppears) {};

        public static class AnimationParameter {
            private float start;
            private float finish;
            private float current;
            private float progress;

            public AnimationParameter(float start, float finish) {
                this.start = start;
                this.finish = finish;
                this.current = start;
            }

            public void update(float progress) {
                current = start + (finish - start) * progress;
                this.progress = progress;
            }

            public void update(float progress, float targetOffset, float offset) {
                current = start + (finish + targetOffset - start) * progress + offset;
                this.progress = progress;
            }

            public float start() {
                return start;
            }

            public float finish() {
                return finish;
            }

            public void start(float start) {
                this.start = start;
            }

            public void finish(float finish) {
                this.finish = finish;
            }

            public float get() {
                return current;
            }

            public float progress() {
                return progress;
            }
        }
    }

    private ViewGroup backgroundSubView;

    public ChatAnimationsOverlay(Context context) {
        super(context);
        setWillNotDraw(false);
    }

    public void setBackgroundSubView(ViewGroup backgroundSubView) {
        this.backgroundSubView = backgroundSubView;
    }

    public void addLayer(OverlayView overlayView) {
        addView(overlayView, new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        if (overlayView.backgroundSubView != null) {
            backgroundSubView.addView(overlayView.backgroundSubView,
                new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }

        overlayView.startAnimation();
    }

    public void updateDeltaOffset(float dy, boolean isNew, boolean isTargetOffset) {
        final int N = getChildCount();
        for (int i = 0; i < N; i++) {
            View view = getChildAt(i);
            if (view instanceof OverlayView) {
                OverlayView offsetView = (OverlayView) view;
                offsetView.setDeltaOffset(dy, isNew, isTargetOffset);
            }
        }
    }

    private int keyboardHeight = 0;
    public void setKeyboardHeight(int keyboardHeight) {
        if (keyboardHeight != 0) {
            this.keyboardHeight = keyboardHeight;
        }
    }

    public void onScroll(float dy, boolean isNew) {
        Log.i("anmessage offset", "" + dy);

        boolean newIsTargetOffset = (Math.abs(dy) < (keyboardHeight - AndroidUtilities.dp(10)));
        if (isNew) newIsTargetOffset = false;

        updateDeltaOffset(dy, isNew, newIsTargetOffset);
    }

    public void onScroll(float dy) {
        onScroll(dy, false);
    }

}
