package org.telegram.ui.ActionBar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.PopupWindow;

import androidx.annotation.Keep;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.GroupCallPip;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public class ExtendedActionBarPopupWindowFragmentsLayout extends FrameLayout {

    public interface ExtendedActionBarPopupWindowFragmentsLayoutDelegate {
        boolean onPreIme();

        boolean needPresentFragment(ExtendedActionBarPopupWindowBaseFragment fragment, boolean removeLast, boolean forceWithoutAnimation, ExtendedActionBarPopupWindowFragmentsLayout layout);

        boolean needAddFragmentToStack(ExtendedActionBarPopupWindowBaseFragment fragment, ExtendedActionBarPopupWindowFragmentsLayout layout);

        boolean needCloseLastFragment(ExtendedActionBarPopupWindowFragmentsLayout layout);

        void onRebuildAllFragments(ExtendedActionBarPopupWindowFragmentsLayout layout, boolean last);
    }

    public interface ExtendedActionBarPopupDelegate {
        void closePopupWindow();
    }

    private static Drawable layerShadowDrawable;
    private static Paint scrimPaint;

    //private Runnable waitingForKeyboardCloseRunnable;
    private Runnable delayedOpenAnimationRunnable;

    private FrameLayout containerView;
    private FrameLayout containerViewBack;

    private ExtendedActionBarPopupWindowBaseFragment newFragment;
    private ExtendedActionBarPopupWindowBaseFragment oldFragment;

    private AnimatorSet currentAnimation;
    private DecelerateInterpolator decelerateInterpolator = new DecelerateInterpolator(1.5f);
    private AccelerateDecelerateInterpolator accelerateDecelerateInterpolator = new AccelerateDecelerateInterpolator();

    public float innerTranslationX;

    private boolean maybeStartTracking;
    protected boolean startedTracking;
    private int startedTrackingX;
    private int startedTrackingY;
    protected boolean animationInProgress;
    private VelocityTracker velocityTracker;
    private View layoutToIgnore;
    private boolean beginTrackingSent;
    private boolean transitionAnimationInProgress;
    private ArrayList<int[]> animateStartColors = new ArrayList<>();
    private ArrayList<int[]> animateEndColors = new ArrayList<>();

    public Theme.MessageDrawable messageDrawableOutStart;
    public Theme.MessageDrawable messageDrawableOutMediaStart;

    private boolean rebuildAfterAnimation;
    private boolean rebuildLastAfterAnimation;
    private boolean showLastAfterAnimation;
    private long transitionAnimationStartTime;
    private int startedTrackingPointerId;
    private Runnable onCloseAnimationEndRunnable;
    private Runnable onOpenAnimationEndRunnable;
    private boolean useAlphaAnimations;
    private View backgroundView;
    private Runnable animationRunnable;

    private float animationProgress;
    private long lastFrameTime;

    private ExtendedActionBarPopupWindowFragmentsLayoutDelegate delegate;
    private ExtendedActionBarPopupDelegate popupDelegate;
    protected Activity parentActivity;

    public ArrayList<ExtendedActionBarPopupWindowBaseFragment> fragmentsStack;
    private Rect rect = new Rect();
    private boolean delayedAnimationResumed;
    private PopupWindow parentPopupWindow;

    private int backgroundColor = Color.WHITE;
    protected Drawable backgroundDrawable;

    private float subtractedHeight = 0;

    public ExtendedActionBarPopupWindowFragmentsLayout(Context context) {
        super(context);
        parentActivity = (Activity) context;

        if (layerShadowDrawable == null) {
            layerShadowDrawable = getResources().getDrawable(R.drawable.layer_shadow);
            scrimPaint = new Paint();
        }

        backgroundDrawable = getResources().getDrawable(R.drawable.popup_fixed_alert).mutate();
        setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        setWillNotDraw(false);

        setMinimumWidth(AndroidUtilities.dp(200));
        setBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground));
    }

    public void setParentPopupWindow(PopupWindow popupWindow) {
        parentPopupWindow = popupWindow;
    }

    public void setBackgroundColor(int color) {
        if (backgroundColor != color) {
            backgroundDrawable.setColorFilter(new PorterDuffColorFilter(backgroundColor = color, PorterDuff.Mode.MULTIPLY));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (backgroundDrawable == null) {
            return;
        }

        backgroundDrawable.setBounds(0, 0, getMeasuredWidth(), (int) (getMeasuredHeight() - subtractedHeight));
        backgroundDrawable.draw(canvas);
    }

    public void init(ArrayList<ExtendedActionBarPopupWindowBaseFragment> stack) {
        fragmentsStack = stack;
        containerViewBack = new FrameLayout(parentActivity);
        addView(containerViewBack);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) containerViewBack.getLayoutParams();
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.height = LayoutHelper.WRAP_CONTENT;
        layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        containerViewBack.setLayoutParams(layoutParams);

        containerView = new FrameLayout(parentActivity);
        addView(containerView);
        layoutParams = (FrameLayout.LayoutParams) containerView.getLayoutParams();
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.height = LayoutHelper.WRAP_CONTENT;
        layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
        containerView.setLayoutParams(layoutParams);

        for (ExtendedActionBarPopupWindowBaseFragment fragment : fragmentsStack) {
            fragment.setParentLayout(this);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        setInnerTranslationX(containerView.getTranslationX());

        if (changed) {
            float progress = Math.min(1f, Math.max(0f, containerView.getTranslationX() / containerView.getMeasuredWidth()));
            int fragmentHeight = containerView.getMeasuredHeight();
            int backFragmentHeight = containerViewBack.getMeasuredHeight();
            if (fragmentHeight > backFragmentHeight) {
                subtractedHeight = (fragmentHeight - backFragmentHeight) * progress;
            } else {
                subtractedHeight = (backFragmentHeight - fragmentHeight) * (1f - progress);
            }
        }
    }


    @Keep
    public void setSubtractedHeight(float value) {
        subtractedHeight = value;
        invalidate();
    }

    @Keep
    public float getSubtractedHeight() {
        return subtractedHeight;
    }

    @Keep
    public void setInnerTranslationX(float value) {
        innerTranslationX = value;
        invalidate();

        if (fragmentsStack.size() >= 2 && containerView.getMeasuredWidth() > 0) {
            float progress = Math.min(1f, Math.max(0f, value / containerView.getMeasuredWidth()));

            ExtendedActionBarPopupWindowBaseFragment prevFragment = fragmentsStack.get(fragmentsStack.size() - 2);
            prevFragment.onSlideProgress(false, progress);
        }
    }

    @Keep
    public float getInnerTranslationX() {
        return innerTranslationX;
    }

    public void onResume() {
        if (transitionAnimationInProgress) {
            if (currentAnimation != null) {
                currentAnimation.cancel();
                currentAnimation = null;
            }
            if (animationRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(animationRunnable);
                animationRunnable = null;
            }
            /*if (waitingForKeyboardCloseRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(waitingForKeyboardCloseRunnable);
                waitingForKeyboardCloseRunnable = null;
            }*/
            if (onCloseAnimationEndRunnable != null) {
                onCloseAnimationEnd();
            } else if (onOpenAnimationEndRunnable != null) {
                onOpenAnimationEnd();
            }
        }
        if (!fragmentsStack.isEmpty()) {
            ExtendedActionBarPopupWindowBaseFragment lastFragment = fragmentsStack.get(fragmentsStack.size() - 1);
            lastFragment.onResume();
        }
    }

    public void onPause() {
        if (!fragmentsStack.isEmpty()) {
            ExtendedActionBarPopupWindowBaseFragment lastFragment = fragmentsStack.get(fragmentsStack.size() - 1);
            lastFragment.onPause();
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return animationInProgress || checkTransitionAnimation() || onTouchEvent(ev);
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        onTouchEvent(null);
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    @Override
    public boolean dispatchKeyEventPreIme(KeyEvent event) {
        if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
            return delegate != null && delegate.onPreIme() || super.dispatchKeyEventPreIme(event);
        }
        return super.dispatchKeyEventPreIme(event);
    }

    Path roundRectPath = new Path();
    RectF roundRectF = new RectF();

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        int width = getWidth() - getPaddingLeft() - getPaddingRight();
        int height = getHeight() - getPaddingTop() - getPaddingBottom();

        int translationX = (int) innerTranslationX + getPaddingRight();
        int clipLeft = getPaddingLeft();
        int clipTop = getPaddingTop();
        int clipRight = width + getPaddingLeft();
        int clipBottom = height + getPaddingBottom();

        if (child == containerViewBack) {
            clipRight = translationX + AndroidUtilities.dp(1);
        } else if (child == containerView) {
            clipLeft = translationX;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            roundRectF.set(getPaddingLeft(), getPaddingTop(), getPaddingLeft() + width, getPaddingTop() + height - subtractedHeight);
            roundRectPath.reset();
            roundRectPath.addRoundRect(roundRectF, AndroidUtilities.dp(5), AndroidUtilities.dp(5), Path.Direction.CW);
            roundRectPath.close();

            canvas.save();
            canvas.clipPath(roundRectPath);
        }

        final int restoreCount = canvas.save();
        if (!transitionAnimationInProgress) {
            canvas.clipRect(clipLeft, 0, clipRight, getHeight());
        }
        final boolean result = super.drawChild(canvas, child, drawingTime);
        canvas.restoreToCount(restoreCount);

        if (innerTranslationX != 0) {
            if (child == containerView) {
                final float alpha = Math.max(0, Math.min((width - translationX) / (float) AndroidUtilities.dp(20), 1.0f));
                layerShadowDrawable.setBounds(translationX - layerShadowDrawable.getIntrinsicWidth(), getPaddingTop(), translationX, (int) (getPaddingTop() + height - subtractedHeight));
                layerShadowDrawable.setAlpha((int) (0xff * alpha));
                layerShadowDrawable.draw(canvas);
            } else if (child == containerViewBack) {
                float opacity = Math.min(0.8f, (width - translationX) / (float) width);
                if (opacity < 0) {
                    opacity = 0;
                }
                scrimPaint.setColor((int) (((0x99000000 & 0xff000000) >>> 24) * opacity) << 24);
                canvas.drawRect(clipLeft, 0, clipRight, getHeight(), scrimPaint);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            canvas.restore();
        }

        return result;
    }

    public void setDelegate(ExtendedActionBarPopupWindowFragmentsLayoutDelegate actionBarLayoutDelegate) {
        delegate = actionBarLayoutDelegate;
    }

    public void setPopupDelegate(ExtendedActionBarPopupDelegate popupDelegate) {
        this.popupDelegate = popupDelegate;
    }

    private void onSlideAnimationEnd(final boolean backAnimation) {
        if (!backAnimation) {
            if (fragmentsStack.size() < 2) {
                return;
            }
            ExtendedActionBarPopupWindowBaseFragment lastFragment = fragmentsStack.get(fragmentsStack.size() - 1);
            lastFragment.prepareFragmentToSlide(true, false);
            lastFragment.onPause();
            lastFragment.onFragmentDestroy();
            lastFragment.setParentLayout(null);

            fragmentsStack.remove(fragmentsStack.size() - 1);

            FrameLayout temp = containerView;
            containerView = containerViewBack;
            containerViewBack = temp;
            bringChildToFront(containerView);

            lastFragment = fragmentsStack.get(fragmentsStack.size() - 1);
            lastFragment.onResume();
            lastFragment.prepareFragmentToSlide(false, false);

            layoutToIgnore = containerView;
        } else {
            if (fragmentsStack.size() >= 2) {
                ExtendedActionBarPopupWindowBaseFragment lastFragment = fragmentsStack.get(fragmentsStack.size() - 1);
                lastFragment.prepareFragmentToSlide(true, false);

                lastFragment = fragmentsStack.get(fragmentsStack.size() - 2);
                lastFragment.prepareFragmentToSlide(false, false);
                lastFragment.onPause();
                if (lastFragment.fragmentView != null) {
                    ViewGroup parent = (ViewGroup) lastFragment.fragmentView.getParent();
                    if (parent != null) {
                        lastFragment.onRemoveFromParent();
                        parent.removeViewInLayout(lastFragment.fragmentView);
                    }
                }
            }
            layoutToIgnore = null;
        }
        containerViewBack.setVisibility(View.INVISIBLE);
        startedTracking = false;
        animationInProgress = false;
        containerView.setTranslationX(0);
        containerViewBack.setTranslationX(0);
        setInnerTranslationX(0);
    }

    private void prepareForMoving(MotionEvent ev) {
        maybeStartTracking = false;
        startedTracking = true;
        layoutToIgnore = containerViewBack;
        startedTrackingX = (int) ev.getX();
        containerViewBack.setVisibility(View.VISIBLE);
        beginTrackingSent = false;

        ExtendedActionBarPopupWindowBaseFragment lastFragment = fragmentsStack.get(fragmentsStack.size() - 2);
        View fragmentView = lastFragment.fragmentView;
        if (fragmentView == null) {
            fragmentView = lastFragment.createView(parentActivity);
        }
        ViewGroup parent = (ViewGroup) fragmentView.getParent();
        if (parent != null) {
            lastFragment.onRemoveFromParent();
            parent.removeView(fragmentView);
        }
        containerViewBack.addView(fragmentView);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) fragmentView.getLayoutParams();
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.height = LayoutHelper.WRAP_CONTENT;
        layoutParams.topMargin = layoutParams.bottomMargin = layoutParams.rightMargin = layoutParams.leftMargin = 0;
        fragmentView.setLayoutParams(layoutParams);
        /*if (!lastFragment.hasOwnBackground && fragmentView.getBackground() == null) {
            fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        }*/
        lastFragment.onResume();

        ExtendedActionBarPopupWindowBaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
        currentFragment.prepareFragmentToSlide(true, true);
        lastFragment.prepareFragmentToSlide(false, true);
    }

    public boolean onTouchEvent(MotionEvent ev) {
        int fragmentHeight = containerView.getMeasuredHeight();
        int backFragmentHeight = containerViewBack.getMeasuredHeight();

        if (ev != null && getMeasuredHeight() - subtractedHeight < ev.getY() && popupDelegate != null && ev.getAction() == MotionEvent.ACTION_DOWN) {
            popupDelegate.closePopupWindow();
            return true;
        }

        if (!checkTransitionAnimation() && !animationInProgress) {
            if (fragmentsStack.size() > 1) {
                if (ev != null && ev.getAction() == MotionEvent.ACTION_DOWN) {
                    ExtendedActionBarPopupWindowBaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
                    if (!currentFragment.isSwipeBackEnabled(ev)) {
                        maybeStartTracking = false;
                        startedTracking = false;
                        return false;
                    }
                    startedTrackingPointerId = ev.getPointerId(0);
                    maybeStartTracking = true;
                    startedTrackingX = (int) ev.getX();
                    startedTrackingY = (int) ev.getY();
                    if (velocityTracker != null) {
                        velocityTracker.clear();
                    }
                } else if (ev != null && ev.getAction() == MotionEvent.ACTION_MOVE && ev.getPointerId(0) == startedTrackingPointerId) {
                    if (velocityTracker == null) {
                        velocityTracker = VelocityTracker.obtain();
                    }
                    int dx = Math.max(0, (int) (ev.getX() - startedTrackingX));
                    int dy = Math.abs((int) ev.getY() - startedTrackingY);
                    velocityTracker.addMovement(ev);
                    if (!transitionAnimationInProgress && maybeStartTracking && !startedTracking && dx >= AndroidUtilities.getPixelsInCM(0.4f, true) && Math.abs(dx) / 3 > dy) {
                        ExtendedActionBarPopupWindowBaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
                        if (currentFragment.canBeginSlide() && findScrollingChild(this, ev.getX(), ev.getY()) == null) {
                            prepareForMoving(ev);
                        } else {
                            maybeStartTracking = false;
                        }
                    } else if (startedTracking) {
                        if (!beginTrackingSent) {
                            /*if (parentActivity.getCurrentFocus() != null) {
                                AndroidUtilities.hideKeyboard(parentActivity.getCurrentFocus());
                            }*/
                            ExtendedActionBarPopupWindowBaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
                            currentFragment.onBeginSlide();
                            beginTrackingSent = true;
                        }
                        containerView.setTranslationX(dx);
                        containerViewBack.setTranslationX(Math.min(0f, (dx - containerView.getMeasuredWidth()) / 4f));
                        setInnerTranslationX(dx);

                        float progress = Math.max(0, Math.min(1f, (float) dx / containerView.getMeasuredWidth()));
                        if (fragmentHeight > backFragmentHeight) {
                            subtractedHeight = (fragmentHeight - backFragmentHeight) * (progress);
                        } else {
                            subtractedHeight = (backFragmentHeight - fragmentHeight) * (1f - progress);
                        }
                    }
                } else if (ev != null && ev.getPointerId(0) == startedTrackingPointerId && (ev.getAction() == MotionEvent.ACTION_CANCEL || ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_POINTER_UP)) {
                    if (velocityTracker == null) {
                        velocityTracker = VelocityTracker.obtain();
                    }
                    velocityTracker.computeCurrentVelocity(1000);
                    ExtendedActionBarPopupWindowBaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
                    if (!startedTracking && currentFragment.isSwipeBackEnabled(ev)) {
                        float velX = velocityTracker.getXVelocity();
                        float velY = velocityTracker.getYVelocity();
                        if (velX >= 3500 && velX > Math.abs(velY) && currentFragment.canBeginSlide()) {
                            prepareForMoving(ev);
                            if (!beginTrackingSent) {
                                /*if (((Activity) getContext()).getCurrentFocus() != null) {
                                    AndroidUtilities.hideKeyboard(((Activity) getContext()).getCurrentFocus());
                                }*/
                                beginTrackingSent = true;
                            }
                        }
                    }
                    if (startedTracking) {
                        float x = containerView.getX();
                        AnimatorSet animatorSet = new AnimatorSet();
                        float velX = velocityTracker.getXVelocity();
                        float velY = velocityTracker.getYVelocity();
                        final boolean backAnimation = x < containerView.getMeasuredWidth() / 3.0f && (velX < 3500 || velX < velY);
                        float distToMove;
                        if (!backAnimation) {
                            distToMove = containerView.getMeasuredWidth() - x;
                            int duration = Math.max((int) (200.0f / containerView.getMeasuredWidth() * distToMove), 50);
                            animatorSet.playTogether(
                                    ObjectAnimator.ofFloat(containerView, View.TRANSLATION_X, containerView.getMeasuredWidth()).setDuration(duration),
                                    ObjectAnimator.ofFloat(containerViewBack, View.TRANSLATION_X, 0).setDuration(duration),
                                    ObjectAnimator.ofFloat(this, "innerTranslationX", (float) containerView.getMeasuredWidth()).setDuration(duration),
                                    ObjectAnimator.ofFloat(this, "subtractedHeight", Math.max(fragmentHeight - backFragmentHeight, 0)).setDuration(duration)

                            );
                        } else {
                            distToMove = x;
                            int duration = Math.max((int) (200.0f / containerView.getMeasuredWidth() * distToMove), 50);
                            animatorSet.playTogether(
                                    ObjectAnimator.ofFloat(containerView, View.TRANSLATION_X, 0).setDuration(duration),
                                    ObjectAnimator.ofFloat(containerViewBack, View.TRANSLATION_X, -containerView.getMeasuredWidth() / 4f).setDuration(duration),
                                    ObjectAnimator.ofFloat(this, "innerTranslationX", 0.0f).setDuration(duration),
                                    ObjectAnimator.ofFloat(this, "subtractedHeight", Math.max(backFragmentHeight - fragmentHeight , 0)).setDuration(duration)
                            );
                        }

                        Animator customTransition = currentFragment.getCustomSlideTransition(false, backAnimation, distToMove);
                        if (customTransition != null) {
                            animatorSet.playTogether(customTransition);
                        }

                        ExtendedActionBarPopupWindowBaseFragment lastFragment = fragmentsStack.get(fragmentsStack.size() - 2);
                        if (lastFragment != null) {
                            customTransition = lastFragment.getCustomSlideTransition(false, backAnimation, distToMove);
                            if (customTransition != null) {
                                animatorSet.playTogether(customTransition);
                            }
                        }

                        animatorSet.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animator) {
                                onSlideAnimationEnd(backAnimation);
                            }
                        });
                        animatorSet.start();
                        animationInProgress = true;
                        layoutToIgnore = containerViewBack;
                    } else {
                        maybeStartTracking = false;
                        startedTracking = false;
                        layoutToIgnore = null;
                    }
                    if (velocityTracker != null) {
                        velocityTracker.recycle();
                        velocityTracker = null;
                    }
                } else if (ev == null) {
                    maybeStartTracking = false;
                    startedTracking = false;
                    layoutToIgnore = null;
                    if (velocityTracker != null) {
                        velocityTracker.recycle();
                        velocityTracker = null;
                    }
                }
            }
            return startedTracking;
        }



        return false;
    }

    public void onBackPressed() {
        if (startedTracking || checkTransitionAnimation() || fragmentsStack.isEmpty()) {
            return;
        }
        if (GroupCallPip.onBackPressed()) {
            return;
        }
        ExtendedActionBarPopupWindowBaseFragment lastFragment = fragmentsStack.get(fragmentsStack.size() - 1);
        if (lastFragment.onBackPressed()) {
            if (!fragmentsStack.isEmpty()) {
                closeLastFragment(true);
            }
        }
    }

    private void onAnimationEndCheck(boolean byCheck) {
        onCloseAnimationEnd();
        onOpenAnimationEnd();
        /*if (waitingForKeyboardCloseRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(waitingForKeyboardCloseRunnable);
            waitingForKeyboardCloseRunnable = null;
        }*/
        if (currentAnimation != null) {
            if (byCheck) {
                currentAnimation.cancel();
            }
            currentAnimation = null;
        }
        /*if (animationRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(animationRunnable);
            animationRunnable = null;
        }*/
        setAlpha(1.0f);
        containerView.setAlpha(1.0f);
        containerView.setScaleX(1.0f);
        containerView.setScaleY(1.0f);
        containerViewBack.setAlpha(1.0f);
        containerViewBack.setScaleX(1.0f);
        containerViewBack.setScaleY(1.0f);
    }

    public ExtendedActionBarPopupWindowBaseFragment getLastFragment() {
        if (fragmentsStack.isEmpty()) {
            return null;
        }
        return fragmentsStack.get(fragmentsStack.size() - 1);
    }

    public boolean checkTransitionAnimation() {
        if (transitionAnimationInProgress && transitionAnimationStartTime < System.currentTimeMillis() - 1500) {
            onAnimationEndCheck(true);
        }
        return transitionAnimationInProgress;
    }

    public boolean isTransitionAnimationInProgress() {
        return transitionAnimationInProgress || animationInProgress;
    }

    private void presentFragmentInternalRemoveOld(boolean removeLast, final ExtendedActionBarPopupWindowBaseFragment fragment) {
        if (fragment == null) {
            return;
        }
        fragment.onPause();
        if (removeLast) {
            fragment.onFragmentDestroy();
            fragment.setParentLayout(null);
            fragmentsStack.remove(fragment);
        } else {
            if (fragment.fragmentView != null) {
                ViewGroup parent = (ViewGroup) fragment.fragmentView.getParent();
                if (parent != null) {
                    fragment.onRemoveFromParent();
                    try {
                        parent.removeViewInLayout(fragment.fragmentView);
                    } catch (Exception e) {
                        FileLog.e(e);
                        try {
                            parent.removeView(fragment.fragmentView);
                        } catch (Exception e2) {
                            FileLog.e(e2);
                        }
                    }
                }
            }
        }
        containerViewBack.setVisibility(View.INVISIBLE);
    }

    public boolean presentFragmentAsPreview(ExtendedActionBarPopupWindowBaseFragment fragment) {
        return presentFragment(fragment, false, false, true);
    }

    public boolean presentFragment(ExtendedActionBarPopupWindowBaseFragment fragment) {
        return presentFragment(fragment, false, false, true);
    }

    public boolean presentFragment(ExtendedActionBarPopupWindowBaseFragment fragment, boolean removeLast) {
        return presentFragment(fragment, removeLast, false, true);
    }

    private void startLayoutAnimation(final boolean open, final boolean first) {
        if (first) {
            animationProgress = 0.0f;
            lastFrameTime = System.nanoTime() / 1000000;
        }
        AndroidUtilities.runOnUIThread(animationRunnable = new Runnable() {
            @Override
            public void run() {
                if (animationRunnable != this) {
                    return;
                }
                animationRunnable = null;
                if (first) {
                    transitionAnimationStartTime = System.currentTimeMillis();
                }
                long newTime = System.nanoTime() / 1000000;
                long dt = newTime - lastFrameTime;
                if (dt > 18) {
                    dt = 18;
                }
                lastFrameTime = newTime;
                animationProgress += dt / 320.0f;
                if (animationProgress > 1.0f) {
                    animationProgress = 1.0f;
                }
                if (newFragment != null) {
                    newFragment.onTransitionAnimationProgress(true, animationProgress);
                }
                if (oldFragment != null) {
                    oldFragment.onTransitionAnimationProgress(false, animationProgress);
                }
                float interpolated = decelerateInterpolator.getInterpolation(animationProgress);
                int fragmentHeight = containerView.getMeasuredHeight();
                int backFragmentHeight = containerViewBack.getMeasuredHeight();

                if (open) {
                    containerView.setAlpha(interpolated);
                    containerView.setTranslationX(containerView.getMeasuredWidth() * (1.0f - interpolated));
                    containerViewBack.setTranslationX((interpolated * containerView.getMeasuredWidth()) / -4f);
                    setInnerTranslationX(containerView.getMeasuredWidth() * (1.0f - interpolated));
                    if (fragmentHeight > backFragmentHeight) {
                        subtractedHeight = (fragmentHeight - backFragmentHeight) * (1f - interpolated);
                    } else {
                        subtractedHeight = (backFragmentHeight - fragmentHeight) * (interpolated);
                    }

                } else {
                    containerView.setTranslationX(((1f - interpolated) * containerView.getMeasuredWidth()) / -4f);
                    containerViewBack.setAlpha(1.0f - interpolated);
                    containerViewBack.setTranslationX(containerView.getMeasuredWidth() * interpolated);
                    setInnerTranslationX(containerView.getMeasuredWidth() * interpolated);
                    if (fragmentHeight > backFragmentHeight) {
                        subtractedHeight = (fragmentHeight - backFragmentHeight) * (1f - interpolated);
                    } else {
                        subtractedHeight = (backFragmentHeight - fragmentHeight) * (interpolated);
                    }
                }
                if (animationProgress < 1) {
                    startLayoutAnimation(open, false);
                } else {
                    onAnimationEndCheck(false);
                }
            }
        });
    }

    public void resumeDelayedFragmentAnimation() {
        delayedAnimationResumed = true;
        if (delayedOpenAnimationRunnable == null/* || waitingForKeyboardCloseRunnable != null*/) {
            return;
        }
        AndroidUtilities.cancelRunOnUIThread(delayedOpenAnimationRunnable);
        delayedOpenAnimationRunnable.run();
        delayedOpenAnimationRunnable = null;
    }

    public boolean presentFragment(final ExtendedActionBarPopupWindowBaseFragment fragment, final boolean removeLast, boolean forceWithoutAnimation, boolean check) {
        if (fragment == null || checkTransitionAnimation() || delegate != null && check && !delegate.needPresentFragment(fragment, removeLast, forceWithoutAnimation, this) || !fragment.onFragmentCreate()) {
            return false;
        }
        /*if (parentActivity.getCurrentFocus() != null && fragment.hideKeyboardOnShow()) {
            AndroidUtilities.hideKeyboard(parentActivity.getCurrentFocus());
        }*/
        boolean needAnimation = !forceWithoutAnimation && MessagesController.getGlobalMainSettings().getBoolean("view_animations", true);

        final ExtendedActionBarPopupWindowBaseFragment currentFragment = !fragmentsStack.isEmpty() ? fragmentsStack.get(fragmentsStack.size() - 1) : null;

        fragment.setParentLayout(this);
        View fragmentView = fragment.fragmentView;
        if (fragmentView == null) {
            fragmentView = fragment.createView(parentActivity);
        } else {
            ViewGroup parent = (ViewGroup) fragmentView.getParent();
            if (parent != null) {
                fragment.onRemoveFromParent();
                parent.removeView(fragmentView);
            }
        }
        containerViewBack.addView(fragmentView);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) fragmentView.getLayoutParams();
        layoutParams.width = LayoutHelper.MATCH_PARENT;
        layoutParams.height = LayoutHelper.WRAP_CONTENT;
        layoutParams.topMargin = layoutParams.bottomMargin = layoutParams.rightMargin = layoutParams.leftMargin = 0;

        fragmentView.setLayoutParams(layoutParams);
        fragmentsStack.add(fragment);
        fragment.onResume();
        /*if (!fragment.hasOwnBackground && fragmentView.getBackground() == null) {
            fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        }*/

        FrameLayout temp = containerView;
        containerView = containerViewBack;
        containerViewBack = temp;
        containerView.setVisibility(View.VISIBLE);
        setInnerTranslationX(0);
        containerView.setTranslationY(0);

        bringChildToFront(containerView);
        if (!needAnimation) {
            presentFragmentInternalRemoveOld(removeLast, currentFragment);
            if (backgroundView != null) {
                backgroundView.setVisibility(VISIBLE);
            }
        }

        if (needAnimation) {
            if (useAlphaAnimations && fragmentsStack.size() == 1) {
                presentFragmentInternalRemoveOld(removeLast, currentFragment);

                transitionAnimationStartTime = System.currentTimeMillis();
                transitionAnimationInProgress = true;
                layoutToIgnore = containerView;
                onOpenAnimationEndRunnable = () -> {
                    if (currentFragment != null) {
                        currentFragment.onTransitionAnimationEnd(false, false);
                    }
                    fragment.onTransitionAnimationEnd(true, false);
                };
                ArrayList<Animator> animators = new ArrayList<>();
                animators.add(ObjectAnimator.ofFloat(this, View.ALPHA, 0.0f, 1.0f));
                if (backgroundView != null) {
                    backgroundView.setVisibility(VISIBLE);
                    animators.add(ObjectAnimator.ofFloat(backgroundView, View.ALPHA, 0.0f, 1.0f));
                }
                if (currentFragment != null) {
                    currentFragment.onTransitionAnimationStart(false, false);
                }
                fragment.onTransitionAnimationStart(true, false);
                currentAnimation = new AnimatorSet();
                currentAnimation.playTogether(animators);
                currentAnimation.setInterpolator(accelerateDecelerateInterpolator);
                currentAnimation.setDuration(200);
                currentAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        onAnimationEndCheck(false);
                    }
                });
                currentAnimation.start();
            } else {
                transitionAnimationStartTime = System.currentTimeMillis();
                transitionAnimationInProgress = true;
                layoutToIgnore = containerView;
                onOpenAnimationEndRunnable = () -> {
                    presentFragmentInternalRemoveOld(removeLast, currentFragment);
                    containerView.setTranslationX(0);
                    if (currentFragment != null) {
                        currentFragment.onTransitionAnimationEnd(false, false);
                    }
                    fragment.onTransitionAnimationEnd(true, false);
                };
                boolean noDelay;
                if (noDelay = !fragment.needDelayOpenAnimation()) {
                    if (currentFragment != null) {
                        currentFragment.onTransitionAnimationStart(false, false);
                    }
                    fragment.onTransitionAnimationStart(true, false);
                }

                delayedAnimationResumed = false;
                oldFragment = currentFragment;
                newFragment = fragment;
                AnimatorSet animation = null;
                animation = fragment.onCustomTransitionAnimation(true, () -> onAnimationEndCheck(false));
                if (animation == null) {
                    containerView.setAlpha(0.0f);
                    containerView.setTranslationX(containerView.getMeasuredWidth());
                    containerView.setScaleX(1.0f);
                    containerView.setScaleY(1.0f);

                    if (fragment.needDelayOpenAnimation()) {
                        delayedOpenAnimationRunnable = new Runnable() {
                            @Override
                            public void run() {
                                if (delayedOpenAnimationRunnable != this) {
                                    return;
                                }
                                delayedOpenAnimationRunnable = null;
                                fragment.onTransitionAnimationStart(true, false);
                                startLayoutAnimation(true, true);
                            }
                        };
                        AndroidUtilities.runOnUIThread(delayedOpenAnimationRunnable, 200);
                    } else {
                        startLayoutAnimation(true, true);
                    }
                } else {
                    /*if (containerView.isKeyboardVisible || containerViewBack.isKeyboardVisible && currentFragment != null) {
                        currentFragment.saveKeyboardPositionBeforeTransition();
                    }*/
                    currentAnimation = animation;
                }
            }
        } else {
            if (backgroundView != null) {
                backgroundView.setAlpha(1.0f);
                backgroundView.setVisibility(VISIBLE);
            }
            if (currentFragment != null) {
                currentFragment.onTransitionAnimationStart(false, false);
                currentFragment.onTransitionAnimationEnd(false, false);
            }
            fragment.onTransitionAnimationStart(true, false);
            fragment.onTransitionAnimationEnd(true, false);
        }
        return true;
    }

    public boolean addFragmentToStack(ExtendedActionBarPopupWindowBaseFragment fragment) {
        return addFragmentToStack(fragment, -1);
    }

    public boolean addFragmentToStack(ExtendedActionBarPopupWindowBaseFragment fragment, int position) {
        if (delegate != null && !delegate.needAddFragmentToStack(fragment, this) || !fragment.onFragmentCreate()) {
            return false;
        }
        fragment.setParentLayout(this);
        if (position == -1) {
            if (!fragmentsStack.isEmpty()) {
                ExtendedActionBarPopupWindowBaseFragment previousFragment = fragmentsStack.get(fragmentsStack.size() - 1);
                previousFragment.onPause();
                if (previousFragment.fragmentView != null) {
                    ViewGroup parent = (ViewGroup) previousFragment.fragmentView.getParent();
                    if (parent != null) {
                        previousFragment.onRemoveFromParent();
                        parent.removeView(previousFragment.fragmentView);
                    }
                }
            }
            fragmentsStack.add(fragment);
        } else {
            fragmentsStack.add(position, fragment);
        }
        return true;
    }

    private void closeLastFragmentInternalRemoveOld(ExtendedActionBarPopupWindowBaseFragment fragment) {
        fragment.onPause();
        fragment.onFragmentDestroy();
        fragment.setParentLayout(null);
        fragmentsStack.remove(fragment);
        containerViewBack.setVisibility(View.INVISIBLE);
        containerViewBack.setTranslationY(0);
        bringChildToFront(containerView);
    }

    public void closeLastFragment(boolean animated) {
        if (delegate != null && !delegate.needCloseLastFragment(this) || checkTransitionAnimation() || fragmentsStack.isEmpty()) {
            return;
        }
        /*if (parentActivity.getCurrentFocus() != null) {
            AndroidUtilities.hideKeyboard(parentActivity.getCurrentFocus());
        }*/
        setInnerTranslationX(0);
        boolean needAnimation = animated && MessagesController.getGlobalMainSettings().getBoolean("view_animations", true);
        final ExtendedActionBarPopupWindowBaseFragment currentFragment = fragmentsStack.get(fragmentsStack.size() - 1);
        ExtendedActionBarPopupWindowBaseFragment previousFragment = null;
        if (fragmentsStack.size() > 1) {
            previousFragment = fragmentsStack.get(fragmentsStack.size() - 2);
        }

        if (previousFragment != null) {
            FrameLayout temp = containerView;
            containerView = containerViewBack;
            containerViewBack = temp;

            previousFragment.setParentLayout(this);
            View fragmentView = previousFragment.fragmentView;
            if (fragmentView == null) {
                fragmentView = previousFragment.createView(parentActivity);
            }

            containerView.setVisibility(View.VISIBLE);
            containerViewBack.setVisibility(View.VISIBLE);
            ViewGroup parent = (ViewGroup) fragmentView.getParent();
            if (parent != null) {
                previousFragment.onRemoveFromParent();
                try {
                    parent.removeView(fragmentView);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            containerView.addView(fragmentView);
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) fragmentView.getLayoutParams();
            layoutParams.width = LayoutHelper.MATCH_PARENT;
            layoutParams.height = LayoutHelper.WRAP_CONTENT;
            layoutParams.topMargin = layoutParams.bottomMargin = layoutParams.rightMargin = layoutParams.leftMargin = 0;
            fragmentView.setLayoutParams(layoutParams);

            newFragment = previousFragment;
            oldFragment = currentFragment;
            previousFragment.onTransitionAnimationStart(true, true);
            currentFragment.onTransitionAnimationStart(false, true);
            previousFragment.onResume();
            /*if (!previousFragment.hasOwnBackground && fragmentView.getBackground() == null) {
                fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            }*/

            if (!needAnimation) {
                closeLastFragmentInternalRemoveOld(currentFragment);
            }

            if (needAnimation) {
                transitionAnimationStartTime = System.currentTimeMillis();
                transitionAnimationInProgress = true;
                layoutToIgnore = containerView;
                final ExtendedActionBarPopupWindowBaseFragment previousFragmentFinal = previousFragment;
                onCloseAnimationEndRunnable = () -> {
                    containerViewBack.setTranslationX(0);
                    closeLastFragmentInternalRemoveOld(currentFragment);
                    currentFragment.onTransitionAnimationEnd(false, true);
                    previousFragmentFinal.onTransitionAnimationEnd(true, true);
                };
                AnimatorSet animation = null;
                animation = currentFragment.onCustomTransitionAnimation(false, () -> onAnimationEndCheck(false));
                if (animation == null) {

                    containerViewBack.setAlpha(1f);
                    containerViewBack.setTranslationX(0);
                    containerViewBack.setScaleX(1.0f);
                    containerViewBack.setScaleY(1.0f);

                    startLayoutAnimation(false, true);
                } else {
                    currentAnimation = animation;
                    if (Bulletin.getVisibleBulletin() != null && Bulletin.getVisibleBulletin().isShowing()) {
                        Bulletin.getVisibleBulletin().hide();
                    }
                }
            } else {
                currentFragment.onTransitionAnimationEnd(false, true);
                previousFragment.onTransitionAnimationEnd(true, true);
            }
        } else {
            if (useAlphaAnimations) {
                transitionAnimationStartTime = System.currentTimeMillis();
                transitionAnimationInProgress = true;
                layoutToIgnore = containerView;

                onCloseAnimationEndRunnable = () -> {
                    removeFragmentFromStackInternal(currentFragment);
                    setVisibility(GONE);
                    if (backgroundView != null) {
                        backgroundView.setVisibility(GONE);
                    }
                };

                ArrayList<Animator> animators = new ArrayList<>();
                animators.add(ObjectAnimator.ofFloat(this, View.ALPHA, 1.0f, 0.0f));
                if (backgroundView != null) {
                    animators.add(ObjectAnimator.ofFloat(backgroundView, View.ALPHA, 1.0f, 0.0f));
                }

                currentAnimation = new AnimatorSet();
                currentAnimation.playTogether(animators);
                currentAnimation.setInterpolator(accelerateDecelerateInterpolator);
                currentAnimation.setDuration(200);
                currentAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        transitionAnimationStartTime = System.currentTimeMillis();
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        onAnimationEndCheck(false);
                    }
                });
                currentAnimation.start();
            } else {
                removeFragmentFromStackInternal(currentFragment);
                setVisibility(GONE);
                if (backgroundView != null) {
                    backgroundView.setVisibility(GONE);
                }
            }
        }
    }

    public void showLastFragment() {
        if (fragmentsStack.isEmpty()) {
            return;
        }
        for (int a = 0; a < fragmentsStack.size() - 1; a++) {
            ExtendedActionBarPopupWindowBaseFragment previousFragment = fragmentsStack.get(a);
            if (previousFragment.fragmentView != null) {
                ViewGroup parent = (ViewGroup) previousFragment.fragmentView.getParent();
                if (parent != null) {
                    previousFragment.onPause();
                    previousFragment.onRemoveFromParent();
                    parent.removeView(previousFragment.fragmentView);
                }
            }
        }
        ExtendedActionBarPopupWindowBaseFragment previousFragment = fragmentsStack.get(fragmentsStack.size() - 1);
        previousFragment.setParentLayout(this);
        View fragmentView = previousFragment.fragmentView;
        if (fragmentView == null) {
            fragmentView = previousFragment.createView(parentActivity);
        } else {
            ViewGroup parent = (ViewGroup) fragmentView.getParent();
            if (parent != null) {
                previousFragment.onRemoveFromParent();
                parent.removeView(fragmentView);
            }
        }
        containerView.addView(fragmentView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        previousFragment.onResume();
        /*if (!previousFragment.hasOwnBackground && fragmentView.getBackground() == null) {
            fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        }*/
    }

    private void removeFragmentFromStackInternal(ExtendedActionBarPopupWindowBaseFragment fragment) {
        fragment.onPause();
        fragment.onFragmentDestroy();
        fragment.setParentLayout(null);
        fragmentsStack.remove(fragment);
    }

    public void removeFragmentFromStack(int num) {
        if (num >= fragmentsStack.size()) {
            return;
        }
        removeFragmentFromStackInternal(fragmentsStack.get(num));
    }

    public void removeFragmentFromStack(ExtendedActionBarPopupWindowBaseFragment fragment) {
        if (useAlphaAnimations && fragmentsStack.size() == 1 && AndroidUtilities.isTablet()) {
            closeLastFragment(true);
        } else {
            if (delegate != null && fragmentsStack.size() == 1 && AndroidUtilities.isTablet()) {
                delegate.needCloseLastFragment(this);
            }
            removeFragmentFromStackInternal(fragment);
        }
    }

    public void removeAllFragments() {
        for (int a = 0; a < fragmentsStack.size(); a++) {
            removeFragmentFromStackInternal(fragmentsStack.get(a));
            a--;
        }
    }

    public void rebuildAllFragmentViews(boolean last, boolean showLastAfter) {
        if (transitionAnimationInProgress || startedTracking) {
            rebuildAfterAnimation = true;
            rebuildLastAfterAnimation = last;
            showLastAfterAnimation = showLastAfter;
            return;
        }
        int size = fragmentsStack.size();
        if (!last) {
            size--;
        }
        for (int a = 0; a < size; a++) {
            fragmentsStack.get(a).clearViews();
            fragmentsStack.get(a).setParentLayout(this);
        }
        if (delegate != null) {
            delegate.onRebuildAllFragments(this, last);
        }
        if (showLastAfter) {
            showLastFragment();
        }
    }

    private void onCloseAnimationEnd() {
        if (transitionAnimationInProgress && onCloseAnimationEndRunnable != null) {
            transitionAnimationInProgress = false;
            layoutToIgnore = null;
            transitionAnimationStartTime = 0;
            newFragment = null;
            oldFragment = null;
            Runnable endRunnable = onCloseAnimationEndRunnable;
            onCloseAnimationEndRunnable = null;
            endRunnable.run();
            checkNeedRebuild();
            checkNeedRebuild();
        }
    }

    private void checkNeedRebuild() {
        if (rebuildAfterAnimation) {
            rebuildAllFragmentViews(rebuildLastAfterAnimation, showLastAfterAnimation);
            rebuildAfterAnimation = false;
        }
    }

    private void onOpenAnimationEnd() {
        if (transitionAnimationInProgress && onOpenAnimationEndRunnable != null) {
            transitionAnimationInProgress = false;
            layoutToIgnore = null;
            transitionAnimationStartTime = 0;
            newFragment = null;
            oldFragment = null;
            Runnable endRunnable = onOpenAnimationEndRunnable;
            onOpenAnimationEndRunnable = null;
            endRunnable.run();
            checkNeedRebuild();
        }
    }

    public void startActivityForResult(final Intent intent, final int requestCode) {
        if (parentActivity == null) {
            return;
        }
        if (transitionAnimationInProgress) {
            if (currentAnimation != null) {
                currentAnimation.cancel();
                currentAnimation = null;
            }
            if (onCloseAnimationEndRunnable != null) {
                onCloseAnimationEnd();
            } else if (onOpenAnimationEndRunnable != null) {
                onOpenAnimationEnd();
            }
            containerView.invalidate();
        }
        if (intent != null) {
            parentActivity.startActivityForResult(intent, requestCode);
        }
    }

    public void setUseAlphaAnimations(boolean value) {
        useAlphaAnimations = value;
    }

    public void setBackgroundView(View view) {
        backgroundView = view;
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private View findScrollingChild(ViewGroup parent, float x, float y) {
        int n = parent.getChildCount();
        for (int i = 0; i < n; i++) {
            View child = parent.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) {
                continue;
            }
            child.getHitRect(rect);
            if (rect.contains((int) x, (int) y)) {
                if (child.canScrollHorizontally(-1)) {
                    return child;
                } else if (child instanceof ViewGroup) {
                    View v = findScrollingChild((ViewGroup) child, x - rect.left, y - rect.top);
                    if (v != null) {
                        return v;
                    }
                }
            }
        }
        return null;
    }
}
