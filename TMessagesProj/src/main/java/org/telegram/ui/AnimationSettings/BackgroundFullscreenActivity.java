package org.telegram.ui.AnimationSettings;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.widget.FrameLayout;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.FilterTabsView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SizeNotifierFrameLayout;

public class BackgroundFullscreenActivity extends BaseFragment {
    private ChatBackgroundView chatBackgroundView;
    private FilterTabsView filterTabsView;
    private BottomButton bottomButton;
    private Paint actionBarDefaultPaint = new Paint();

    private String currentParamName = "SendMessage";

    @Override
    public View createView(final Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);

        actionBar.setTitle("Background Preview");
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefault));
        actionBar.setAddToContainer(false);
        actionBar.setCastShadows(false);
        actionBar.setClipContent(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        actionBarDefaultPaint.setColor(Theme.getColor(Theme.key_actionBarDefault));

        ContentView contentView = new ContentView(context);
        fragmentView = contentView;
        filterTabsView = new FilterTabsView(context);
        filterTabsView.setNeedRenameFirstTab(false);
        filterTabsView.setListViewPadding(0,0,0,0);
        filterTabsView.setDelegate(new FilterTabsView.FilterTabsViewDelegate() {
            @Override
            public void onSamePageSelected() {}

            @Override
            public void onPageReorder(int fromId, int toId) {}

            @Override
            public void onPageSelected(int id, boolean forward) {
                if (id == Integer.MAX_VALUE) {
                    currentParamName = "SendMessage";
                } else if (id == 1) {
                    currentParamName = "OpenChat";
                } else if (id == 2) {
                    currentParamName = "JumpToMessage";
                }
            }

            @Override
            public boolean canPerformActions() {
                return true;
            }

            @Override
            public void onPageScrolled(float progress) {}

            @Override
            public int getTabCounter(int tabId) {
                return 0;
            }

            @Override
            public boolean didSelectTab(FilterTabsView.TabView tabView, boolean selected) {
                if (actionBar.isActionModeShowed()) {
                    return false;
                }

                return true;
            }

            @Override
            public boolean isTabMenuVisible() {
                return false;
            }

            @Override
            public void onDeletePressed(int id) {}
        });
        filterTabsView.addTab(Integer.MAX_VALUE, 20, "Send Message");
        filterTabsView.addTab(1, 21, "Open Chat");
        filterTabsView.addTab(2, 22, "Jump to Message");
        filterTabsView.finishAddingTabs(false);
        filterTabsView.selectFirstTab();

        chatBackgroundView = new ChatBackgroundView(this);

        int colors[] = new int[4];
        getAnimationController().getColors(colors);
        chatBackgroundView.setColors(colors[0], colors[1], colors[2], colors[3]);
        chatBackgroundView.redraw();

        bottomButton = new BottomButton(context);
        bottomButton.setText("Animate");
        bottomButton.setBackgroundColor(Theme.getColor(Theme.key_chat_messagePanelBackground));
        bottomButton.setOnClickListener((ev) -> {
            chatBackgroundView.rotate(currentParamName);
        });

        contentView.addView(filterTabsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44));
        contentView.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        contentView.addView(chatBackgroundView);
        contentView.addView(bottomButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50, Gravity.BOTTOM));

        return fragmentView;
    }

    private class ContentView extends SizeNotifierFrameLayout {
        public ContentView(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        private int startedTrackingPointerId;
        private int startedTrackingX;
        private int startedTrackingY;
        private VelocityTracker velocityTracker;
        private boolean globalIgnoreLayout;
        private int[] pos = new int[2];

        public int getActionBarFullHeight() {
            float h = actionBar.getHeight();
            float filtersTabsHeight = 0;
            if (filterTabsView != null && filterTabsView.getVisibility() != GONE) {
                filtersTabsHeight = filterTabsView.getMeasuredHeight();
            }
            h += filtersTabsHeight;
            return (int) h;
        }

        @Override
        protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
            boolean result = super.drawChild(canvas, child, drawingTime);
            if (child == actionBar && parentLayout != null) {
                int y = (int) (actionBar.getY() + getActionBarFullHeight());
                parentLayout.drawHeaderShadow(canvas, 255, y);
            }
            return result;
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            int actionBarHeight = getActionBarFullHeight();
            int top = (int) (-getY() + actionBar.getY());

            canvas.drawRect(0, top, getMeasuredWidth(), top + actionBarHeight, actionBarDefaultPaint);

            if (filterTabsView != null && filterTabsView.getVisibility() == View.VISIBLE) {
                filterTabsView.setTranslationY(actionBar.getTranslationY());
                filterTabsView.setAlpha(1f);
            }
            super.dispatchDraw(canvas);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int widthSize = View.MeasureSpec.getSize(widthMeasureSpec);
            int heightSize = View.MeasureSpec.getSize(heightMeasureSpec);

            setMeasuredDimension(widthSize, heightSize);
            heightSize -= getPaddingTop();

            measureChildWithMargins(actionBar, widthMeasureSpec, 0, heightMeasureSpec, 0);

            int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = getChildAt(i);
                if (child == null || child.getVisibility() == GONE || child == actionBar) {
                    continue;
                }

                if (child == chatBackgroundView) {
                    int contentWidthSpec = View.MeasureSpec.makeMeasureSpec(widthSize, View.MeasureSpec.EXACTLY);
                    int h = heightSize + AndroidUtilities.dp(2) - AndroidUtilities.dp(94) - actionBar.getMeasuredHeight();

                    child.setTranslationY(0);
                    child.measure(contentWidthSpec, View.MeasureSpec.makeMeasureSpec(Math.max(AndroidUtilities.dp(10), h), View.MeasureSpec.EXACTLY));
                    child.setPivotX(child.getMeasuredWidth() / 2);
                } else {
                    measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                }
            }
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            final int count = getChildCount();

            int paddingBottom;
            paddingBottom = 0;
            setBottomClip(paddingBottom);

            for (int i = 0; i < count; i++) {
                final View child = getChildAt(i);
                if (child.getVisibility() == GONE) {
                    continue;
                }
                final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) child.getLayoutParams();

                final int width = child.getMeasuredWidth();
                final int height = child.getMeasuredHeight();

                int childLeft;
                int childTop;

                int gravity = lp.gravity;
                if (gravity == -1) {
                    gravity = Gravity.TOP | Gravity.LEFT;
                }

                final int absoluteGravity = gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
                final int verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;

                switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                    case Gravity.CENTER_HORIZONTAL:
                        childLeft = (r - l - width) / 2 + lp.leftMargin - lp.rightMargin;
                        break;
                    case Gravity.RIGHT:
                        childLeft = r - width - lp.rightMargin;
                        break;
                    case Gravity.LEFT:
                    default:
                        childLeft = lp.leftMargin;
                }

                switch (verticalGravity) {
                    case Gravity.TOP:
                        childTop = lp.topMargin + getPaddingTop();
                        break;
                    case Gravity.CENTER_VERTICAL:
                        childTop = ((b - paddingBottom) - t - height) / 2 + lp.topMargin - lp.bottomMargin;
                        break;
                    case Gravity.BOTTOM:
                        childTop = ((b - paddingBottom) - t) - height - lp.bottomMargin;
                        break;
                    default:
                        childTop = lp.topMargin;
                }

                if (child == filterTabsView) {
                    childTop = actionBar.getMeasuredHeight();
                } else if (child == chatBackgroundView) {
                    childTop = actionBar.getMeasuredHeight() + AndroidUtilities.dp(44);
                }
                child.layout(childLeft, childTop, childLeft + width, childTop + height);
            }

            notifyHeightChanged();
        }

        @Override
        public boolean hasOverlappingRendering() {
            return false;
        }
    }

    private class BottomButton extends View {

        private int currentCounter;
        private String currentCounterString;
        private int textWidth;
        private TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private RectF rect = new RectF();
        private int circleWidth;
        private int rippleColor;

        private StaticLayout textLayout;
        private StaticLayout textLayoutOut;
        private int layoutTextWidth;
        private TextPaint layoutPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

        Drawable selectableBackground;

        ValueAnimator replaceAnimator;
        float replaceProgress = 1f;
        boolean animatedFromBottom;
        int textColor;
        int panelBackgroundColor;
        int counterColor;

        public BottomButton(Context context) {
            super(context);
            textPaint.setTextSize(AndroidUtilities.dp(13));
            textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

            layoutPaint.setTextSize(AndroidUtilities.dp(15));
            layoutPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        }

        public void setText(CharSequence text, boolean animatedFromBottom) {
            this.animatedFromBottom = animatedFromBottom;
            textLayoutOut = textLayout;
            layoutTextWidth = (int) Math.ceil(layoutPaint.measureText(text, 0, text.length()));
            textLayout = new StaticLayout(text, layoutPaint, layoutTextWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, true);
            setContentDescription(text);
            invalidate();

            if (textLayoutOut != null) {
                if (replaceAnimator != null) {
                    replaceAnimator.cancel();
                }
                replaceProgress = 0;
                replaceAnimator = ValueAnimator.ofFloat(0,1f);
                replaceAnimator.addUpdateListener(animation -> {
                    replaceProgress = (float) animation.getAnimatedValue();
                    invalidate();
                });
                replaceAnimator.setDuration(150);
                replaceAnimator.start();
            }
        }

        public void setText(CharSequence text) {
            layoutTextWidth = (int) Math.ceil(layoutPaint.measureText(text, 0, text.length()));
            textLayout = new StaticLayout(text, layoutPaint, layoutTextWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, true);
            setContentDescription(text);
            invalidate();
        }

        @Override
        protected void drawableStateChanged() {
            super.drawableStateChanged();
            if (selectableBackground != null) {
                selectableBackground.setState(getDrawableState());
            }
        }

        @Override
        public boolean verifyDrawable(Drawable drawable) {
            if (selectableBackground != null) {
                return selectableBackground == drawable || super.verifyDrawable(drawable);
            }
            return super.verifyDrawable(drawable);
        }

        @Override
        public void jumpDrawablesToCurrentState() {
            super.jumpDrawablesToCurrentState();
            if (selectableBackground != null) {
                selectableBackground.jumpToCurrentState();
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (textLayout != null) {
                    int lineWidth = (int) Math.ceil(textLayout.getLineWidth(0));
                    int contentWidth;
                    if (getMeasuredWidth() == ((View)getParent()).getMeasuredWidth()) {
                        contentWidth = getMeasuredWidth() - AndroidUtilities.dp(96);
                    } else {
                        contentWidth = lineWidth + (circleWidth > 0 ? circleWidth + AndroidUtilities.dp(8) : 0);
                        contentWidth += AndroidUtilities.dp(48);
                    }
                    int x = (getMeasuredWidth() - contentWidth) / 2;
                    rect.set(
                            x, getMeasuredHeight() / 2f - contentWidth / 2f,
                            x + contentWidth, getMeasuredHeight() / 2f + contentWidth / 2f
                    );
                    if (!rect.contains(event.getX(), event.getY())) {
                        setPressed(false);
                        return false;
                    }
                }
            }
            return super.onTouchEvent(event);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            Layout layout = textLayout;
            int color = Theme.getColor(Theme.key_chat_fieldOverlayText);
            if (textColor != color) {
                layoutPaint.setColor(textColor = color);
            }
            color = Theme.getColor(Theme.key_chat_messagePanelBackground);
            if (panelBackgroundColor != color) {
                textPaint.setColor(panelBackgroundColor = color);
            }
            color = Theme.getColor(Theme.key_chat_goDownButtonCounterBackground);
            if (counterColor != color) {
                paint.setColor(counterColor = color);
            }

            if (getParent() != null) {
                int contentWidth = getMeasuredWidth();
                int x = (getMeasuredWidth() - contentWidth) / 2;
                if (rippleColor != Theme.getColor(Theme.key_chat_fieldOverlayText) || selectableBackground == null) {
                    selectableBackground = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(60), 0, ColorUtils.setAlphaComponent(rippleColor = Theme.getColor(Theme.key_chat_fieldOverlayText), 26));
                    selectableBackground.setCallback(this);
                }
                int start = (getLeft() + x) <= 0 ? x - AndroidUtilities.dp(20) : x;
                int end = x + contentWidth > ((View) getParent()).getMeasuredWidth() ? x + contentWidth + AndroidUtilities.dp(20) : x + contentWidth;
                selectableBackground.setBounds(
                        start, getMeasuredHeight() / 2 - contentWidth / 2,
                        end, getMeasuredHeight() / 2 + contentWidth / 2
                );
                selectableBackground.draw(canvas);
            }
            if (textLayout != null) {
                canvas.save();
                if (replaceProgress != 1f && textLayoutOut != null) {
                    int oldAlpha = layoutPaint.getAlpha();

                    canvas.save();
                    canvas.translate((getMeasuredWidth() - textLayoutOut.getWidth()) / 2 - circleWidth / 2, (getMeasuredHeight() - textLayout.getHeight()) / 2);
                    canvas.translate(0, (animatedFromBottom ? -1f : 1f) * AndroidUtilities.dp(18) * replaceProgress);
                    layoutPaint.setAlpha((int) (oldAlpha * (1f - replaceProgress)));
                    textLayoutOut.draw(canvas);
                    canvas.restore();

                    canvas.save();
                    canvas.translate((getMeasuredWidth() - layoutTextWidth) / 2 - circleWidth / 2, (getMeasuredHeight() - textLayout.getHeight()) / 2);
                    canvas.translate(0, (animatedFromBottom ? 1f : -1f) * AndroidUtilities.dp(18) * (1f - replaceProgress));
                    layoutPaint.setAlpha((int) (oldAlpha * (replaceProgress)));
                    textLayout.draw(canvas);
                    canvas.restore();

                    layoutPaint.setAlpha(oldAlpha);
                } else {
                    canvas.translate((getMeasuredWidth() - layoutTextWidth) / 2 - circleWidth / 2, (getMeasuredHeight() - textLayout.getHeight()) / 2);
                    textLayout.draw(canvas);
                }

                canvas.restore();
            }

            if (currentCounterString != null) {
                if (layout != null) {
                    int lineWidth = (int) Math.ceil(layout.getLineWidth(0));
                    int x = (getMeasuredWidth() - lineWidth) / 2 + lineWidth - circleWidth / 2 + AndroidUtilities.dp(6);
                    rect.set(x, getMeasuredHeight() / 2 - AndroidUtilities.dp(10), x + circleWidth, getMeasuredHeight() / 2 + AndroidUtilities.dp(10));
                    canvas.drawRoundRect(rect, AndroidUtilities.dp(10), AndroidUtilities.dp(10), paint);
                    canvas.drawText(currentCounterString, rect.centerX() - textWidth / 2.0f, rect.top + AndroidUtilities.dp(14.5f), textPaint);
                }
            }
        }
    }
}
