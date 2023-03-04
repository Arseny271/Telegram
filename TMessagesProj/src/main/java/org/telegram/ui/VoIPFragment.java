package org.telegram.ui;

import static org.telegram.ui.GroupCallActivity.TRANSITION_DURATION;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.utils.AnimationUtilities;
import org.telegram.messenger.voip.Instance;
import org.telegram.messenger.voip.VideoCapturerDevice;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.DarkAlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.BackgroundGradientDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.voip.PrivateVideoPreviewDialog;
import org.telegram.ui.Components.voip.VoIPAvatarWithWavesView;
import org.telegram.ui.Components.voip.VoIPBackground;
import org.telegram.ui.Components.voip.VoIPButtonsLayout2;
import org.telegram.ui.Components.voip.VoIPFloatingLayout;
import org.telegram.ui.Components.voip.VoIPHelper;
import org.telegram.ui.Components.voip.VoIPHintView;
import org.telegram.ui.Components.voip.VoIPNotificationsLayout;
import org.telegram.ui.Components.voip.VoIPPiPView;
import org.telegram.ui.Components.voip.VoIPRateView;
import org.telegram.ui.Components.voip.VoIPStatusTextView;
import org.telegram.ui.Components.voip.VoIPTextureView;
import org.telegram.ui.Components.voip.VoIPToggleButton2;
import org.telegram.ui.Components.voip.VoIPWindowView;
import org.telegram.ui.Components.voip.VoipEmojiLayout;
import org.webrtc.EglBase;
import org.webrtc.GlRectDrawer;
import org.webrtc.RendererCommon;
import org.webrtc.TextureViewRenderer;

public class VoIPFragment implements VoIPService.StateListener, NotificationCenter.NotificationCenterDelegate {
    public final static int STATUS_LAYOUT_EMOJI_EXPAND_OFFSET = 23;

    private final BooleanAnimation callOutButtonsPresent = new BooleanAnimation(this::checkLayout, 350, 350)
        .setStartDelay(250).setSetInterpolator(CubicBezierInterpolator.EASE_IN);
    private final BooleanAnimation ratingVisible = new BooleanAnimation(this::checkLayout, 360,360);
    private final BooleanAnimation emojiExpand = new BooleanAnimation(this::checkLayout, 520, 420);
    private final BooleanAnimation uiVisibility = new BooleanAnimation(this::checkLayout, 400, 400);
    private final BooleanAnimation hasAnyVideo = new BooleanAnimation(this::checkLayout, 300, 300);
    private final BooleanAnimation acceptVisible = new BooleanAnimation(this::checkLayout, 350, 350);
    private final BooleanAnimation callingUserTextureViewVisible = new BooleanAnimation(this::checkLayout, 350, 350);
    private final BooleanAnimation videoPreviewAppear = new BooleanAnimation(this::checkVideoPreviewLayout, 380, 380);

    private final RectF videoPreviewButtonRect = new RectF();
    private final RectF videoPreviewClipRect = new RectF();
    private final Path videoPreviewClipPath = new Path();

    private final static int STATE_GONE = 0;
    private final static int STATE_FULLSCREEN = 1;
    private final static int STATE_FLOATING = 2;

    private final int currentAccount;

    Activity activity;

    TLRPC.User currentUser;
    TLRPC.User callingUser;

    VoIPToggleButton2[] bottomButtons = new VoIPToggleButton2[3];

    private ViewGroup fragmentView;
    private VoIPBackground callGradientBackground;
    private VoIPAvatarWithWavesView callingUserAvatarView;

    private TextView callingUserTitle;

    private VoIPStatusTextView statusTextView;
    private ImageView backIcon;
    private ImageView speakerPhoneIcon;

    VoipEmojiLayout emojiLayout;
    LinearLayout statusLayout;
    private VoIPFloatingLayout currentUserCameraFloatingLayout;
    private VoIPFloatingLayout callingUserMiniFloatingLayout;
    private boolean currentUserCameraIsFullscreen;

    private TextureViewRenderer callingUserMiniTextureRenderer;
    private VoIPTextureView callingUserTextureView;
    private VoIPTextureView currentUserTextureView;

    View bottomShadow;
    View topShadow;

    private VoIPButtonsLayout2 buttonsLayout;
    private VoIPRateView rateView;
    Paint overlayPaint = new Paint();
    Paint overlayBottomPaint = new Paint();

    boolean isOutgoing;
    boolean callingUserIsVideo;
    boolean currentUserIsVideo;

    private PrivateVideoPreviewDialog previewDialog;

    private int currentState;
    private int previousState;
    private WindowInsets lastInsets;

    float touchSlop;

    private static VoIPFragment instance;
    private VoIPWindowView windowView;

    private AccessibilityManager accessibilityManager;

    private boolean uiVisible = true;
    private boolean canHideUI;
    private Animator cameraShowingAnimator;

    private boolean canSwitchToPip;
    private boolean switchingToPip;

    private boolean isFinished;
    boolean cameraForceExpanded;
    boolean enterFromPiP;
    private boolean deviceIsLocked;

    long lastContentTapTime;
    int animationIndex = -1;
    VoIPNotificationsLayout notificationsLayout;

    VoIPHintView videoUnavailableHint;
    VoIPHintView tapToVideoTooltip;
    VoIPHintView tapToEmojiTooltip;

    private boolean lockOnScreen;
    private boolean isVideoCall;

    /* === pinch to zoom === */
    private float pinchStartCenterX;
    private float pinchStartCenterY;
    private float pinchStartDistance;
    private float pinchTranslationX;
    private float pinchTranslationY;
    private boolean isInPinchToZoomTouchMode;

    private float pinchCenterX;
    private float pinchCenterY;

    private int pointerId1, pointerId2;

    float pinchScale = 1f;
    private boolean zoomStarted;
    private boolean canZoomGesture;
    ValueAnimator zoomBackAnimator;
    /* === pinch to zoom === */

    public TLRPC.PhoneCall privateCallToRate;

    public static void show(Activity activity, int account) {
        show(activity, false, account);
    }

    public static void show(Activity activity, boolean overlay, int account) {
        if (instance != null && instance.windowView.getParent() == null) {
            if (instance != null) {
                instance.callingUserTextureView.renderer.release();
                instance.currentUserTextureView.renderer.release();
                instance.callingUserMiniTextureRenderer.release();
                instance.destroy();
            }
            instance = null;
        }
        if (instance != null || activity.isFinishing()) {
            return;
        }
        boolean transitionFromPip = VoIPPiPView.getInstance() != null;
        if (VoIPService.getSharedInstance() == null || VoIPService.getSharedInstance().getUser() == null) {
            return;
        }
        VoIPFragment fragment = new VoIPFragment(account);
        fragment.activity = activity;
        instance = fragment;
        VoIPWindowView windowView = new VoIPWindowView(activity, !transitionFromPip) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (fragment.isFinished || fragment.switchingToPip) {
                    return false;
                }
                final int keyCode = event.getKeyCode();
                if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP && (!fragment.lockOnScreen || fragment.previewDialog != null && fragment.videoPreviewAppear.getValue() )) {
                    fragment.onBackPressed();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                    if (fragment.currentState == VoIPService.STATE_WAITING_INCOMING) {
                        final VoIPService service = VoIPService.getSharedInstance();
                        if (service != null) {
                            service.stopRinging();
                            return true;
                        }
                    }
                }
                return super.dispatchKeyEvent(event);
            }
        };
        instance.deviceIsLocked = ((KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE)).inKeyguardRestrictedInputMode();

        windowView.setLockOnScreen(instance.deviceIsLocked);
        fragment.windowView = windowView;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            windowView.setOnApplyWindowInsetsListener((view, windowInsets) -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    fragment.setInsets(windowInsets);
                }
                if (Build.VERSION.SDK_INT >= 30) {
                    return WindowInsets.CONSUMED;
                } else {
                    return windowInsets.consumeSystemWindowInsets();
                }
            });
        }

        WindowManager wm = (WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
        WindowManager.LayoutParams layoutParams = windowView.createWindowLayoutParams();
        if (overlay) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                layoutParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
            }
        }
        wm.addView(windowView, layoutParams);
        View view = fragment.createView(activity);
        windowView.addView(view);

        if (transitionFromPip) {
            fragment.startTransitionFromPiP();
        } else {
            fragment.updateSystemBarColors();
        }
    }

    private void onBackPressed() {
        if (isFinished || switchingToPip) {
            return;
        }
        if (previewDialog != null) {
            previewDialog.dismiss(false, false);
            return;
        }
        if (callingUserIsVideo && currentUserIsVideo && cameraForceExpanded) {
            cameraForceExpanded = false;
            currentUserCameraFloatingLayout.setRelativePosition(callingUserMiniFloatingLayout);
            currentUserCameraIsFullscreen = false;
            previousState = currentState;
            updateViewState();
            return;
        }
        if (emojiExpand.getValue()) {
            expandEmoji(false);
        } else {
            if (emojiExpand.get() > 0f) {
                return;
            }
            if (canSwitchToPip && !lockOnScreen) {
                if (AndroidUtilities.checkInlinePermissions(activity)) {
                    switchToPip();
                } else {
                    requestInlinePermissions();
                }
            } else {
                windowView.finish();
            }
        }
    }

    public static void clearInstance() {
        if (instance != null) {
            if (VoIPService.getSharedInstance() != null) {
                int h = instance.windowView.getMeasuredHeight();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH && instance.lastInsets != null) {
                    h -= instance.lastInsets.getSystemWindowInsetBottom();
                }
                if (instance.canSwitchToPip) {
                    VoIPPiPView.show(instance.activity, instance.currentAccount, instance.windowView.getMeasuredWidth(), h, VoIPPiPView.ANIMATION_ENTER_TYPE_SCALE);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH && instance.lastInsets != null) {
                        VoIPPiPView.topInset = instance.lastInsets.getSystemWindowInsetTop();
                        VoIPPiPView.bottomInset = instance.lastInsets.getSystemWindowInsetBottom();
                    }
                }
            }
            instance.callingUserTextureView.renderer.release();
            instance.currentUserTextureView.renderer.release();
            instance.callingUserMiniTextureRenderer.release();
            instance.destroy();
        }
        instance = null;
    }

    public static VoIPFragment getInstance() {
        return instance;
    }


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void setInsets(WindowInsets windowInsets) {
        lastInsets = windowInsets;
        ((FrameLayout.LayoutParams) rateView.getLayoutParams()).bottomMargin = lastInsets.getSystemWindowInsetBottom();
        ((FrameLayout.LayoutParams) buttonsLayout.getLayoutParams()).bottomMargin = lastInsets.getSystemWindowInsetBottom();
        ((FrameLayout.LayoutParams) rateView.getLayoutParams()).topMargin = lastInsets.getSystemWindowInsetTop();
        ((FrameLayout.LayoutParams) backIcon.getLayoutParams()).topMargin = lastInsets.getSystemWindowInsetTop();
        ((FrameLayout.LayoutParams) speakerPhoneIcon.getLayoutParams()).topMargin = lastInsets.getSystemWindowInsetTop();
        ((FrameLayout.LayoutParams) topShadow.getLayoutParams()).topMargin = lastInsets.getSystemWindowInsetTop();
        ((FrameLayout.LayoutParams) statusLayout.getLayoutParams()).topMargin = AndroidUtilities.dp(303) + lastInsets.getSystemWindowInsetTop();
        ((FrameLayout.LayoutParams) emojiLayout.getLayoutParams()).topMargin = lastInsets.getSystemWindowInsetTop();
        ((FrameLayout.LayoutParams) callingUserAvatarView.getLayoutParams()).topMargin = AndroidUtilities.dp(88) + lastInsets.getSystemWindowInsetTop();

        // ((FrameLayout.LayoutParams) currentUserCameraFloatingLayout.getLayoutParams()).bottomMargin = lastInsets.getSystemWindowInsetBottom();
        ((FrameLayout.LayoutParams) callingUserMiniFloatingLayout.getLayoutParams()).bottomMargin = lastInsets.getSystemWindowInsetBottom();
        // ((FrameLayout.LayoutParams) callingUserTextureView.getLayoutParams()).bottomMargin = lastInsets.getSystemWindowInsetBottom();
        ((FrameLayout.LayoutParams) notificationsLayout.getLayoutParams()).bottomMargin = lastInsets.getSystemWindowInsetBottom();

        ((FrameLayout.LayoutParams) bottomShadow.getLayoutParams()).bottomMargin = lastInsets.getSystemWindowInsetBottom();
        currentUserCameraFloatingLayout.setInsets(lastInsets);
        callingUserMiniFloatingLayout.setInsets(lastInsets);
        fragmentView.requestLayout();
        if (previewDialog != null) {
            previewDialog.setBottomPadding(lastInsets.getSystemWindowInsetBottom());
        }
    }

    public VoIPFragment(int account) {
        currentAccount = account;
        currentUser = MessagesController.getInstance(currentAccount).getUser(UserConfig.getInstance(currentAccount).getClientUserId());
        callingUser = VoIPService.getSharedInstance().getUser();
        VoIPService.getSharedInstance().registerStateListener(this);
        isOutgoing = VoIPService.getSharedInstance().isOutgoing();
        previousState = -1;
        currentState = VoIPService.getSharedInstance().getCallState();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.voipServiceCreated);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.closeInCallActivity);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.webRtcSpeakerAmplitudeEvent);
    }

    private void destroy() {
        final VoIPService service = VoIPService.getSharedInstance();
        if (service != null) {
            service.unregisterStateListener(this);
        }
        if (emojiLayout != null) {
            emojiLayout.destroy();
        }
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.voipServiceCreated);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.closeInCallActivity);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.webRtcSpeakerAmplitudeEvent);
    }

    @Override
    public void onStateChanged(int state) {
        if (currentState != state) {
            previousState = currentState;
            currentState = state;
            if (windowView != null) {
                updateViewState();
            }
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.voipServiceCreated) {
            if (currentState == VoIPService.STATE_BUSY && VoIPService.getSharedInstance() != null) {
                currentUserTextureView.renderer.release();
                callingUserTextureView.renderer.release();
                callingUserMiniTextureRenderer.release();
                initRenderers();
                VoIPService.getSharedInstance().registerStateListener(this);
            }
        } else if (id == NotificationCenter.closeInCallActivity) {
            windowView.finish();
        } else if (id == NotificationCenter.webRtcSpeakerAmplitudeEvent) {
            callingUserAvatarView.setAmplitude((float) args[0] * 15f);
        }
    }

    @Override
    public void onSignalBarsCountChanged(int count) {
        if (statusTextView != null) {
            statusTextView.setSignalBarCount(count);
        }
        bgWeakSignal = count <= 1;
        checkProblems();
    }

    boolean bgWeakSignal = false;
    boolean bgNoSignal = false;

    private void checkProblems () {
        if (callGradientBackground != null) {
            callGradientBackground.renderer.showProblems(bgNoSignal || bgWeakSignal);
        }
        if (statusTextView != null) {
            statusTextView.showReconnect(bgNoSignal && !ratingVisible.getValue(), true);
        }
    }






    @Override
    public void onAudioSettingsChanged() {
        checkHeadsetConnected();
        updateButtons(true);
    }

    @Override
    public void onMediaStateUpdated(int audioState, int videoState) {
        previousState = currentState;
        if (videoState == Instance.VIDEO_STATE_ACTIVE && !isVideoCall) {
            isVideoCall = true;
        }
        updateViewState();
    }

    @Override
    public void onCameraSwitch(boolean isFrontFace) {
        previousState = currentState;
        updateViewState();
    }

    @Override
    public void onVideoAvailableChange(boolean isAvailable) {
        previousState = currentState;
        if (isAvailable && !isVideoCall) {
            isVideoCall = true;
        }
        updateViewState();
    }

    @Override
    public void onScreenOnChange(boolean screenOn) {

    }

    public View createView(Context context) {
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        accessibilityManager = ContextCompat.getSystemService(context, AccessibilityManager.class);

        FrameLayout frameLayout = new FrameLayout(context) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH && lastInsets != null) {
                    canvas.drawRect(0, 0, getMeasuredWidth(), lastInsets.getSystemWindowInsetTop(), overlayPaint);
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH && lastInsets != null) {
                    canvas.drawRect(0, getMeasuredHeight() - lastInsets.getSystemWindowInsetBottom(), getMeasuredWidth(), getMeasuredHeight(), overlayBottomPaint);
                }
            }

            float pressedX;
            float pressedY;
            boolean check;
            long pressedTime;

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                scheduleStopInterfaceUpdates();
                return super.dispatchTouchEvent(ev);
            }

            @Override
            public boolean onTouchEvent(MotionEvent ev) {
                /* === pinch to zoom === */
                if (!canZoomGesture && !isInPinchToZoomTouchMode && !zoomStarted && ev.getActionMasked() != MotionEvent.ACTION_DOWN) {
                    finishZoom();
                    return false;
                }
                if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    canZoomGesture = false;
                    isInPinchToZoomTouchMode = false;
                    zoomStarted = false;
                }
                VoIPTextureView currentTextureView = getFullscreenTextureView();

                if (ev.getActionMasked() == MotionEvent.ACTION_DOWN || ev.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
                    if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        AndroidUtilities.rectTmp.set(currentTextureView.getX(), currentTextureView.getY(), currentTextureView.getX() + currentTextureView.getMeasuredWidth(), currentTextureView.getY() + currentTextureView.getMeasuredHeight());
                        AndroidUtilities.rectTmp.inset((currentTextureView.getMeasuredHeight() * currentTextureView.scaleTextureToFill - currentTextureView.getMeasuredHeight()) / 2, (currentTextureView.getMeasuredWidth() * currentTextureView.scaleTextureToFill - currentTextureView.getMeasuredWidth()) / 2);
                        if (!GroupCallActivity.isLandscapeMode) {
                            AndroidUtilities.rectTmp.top = Math.max(AndroidUtilities.rectTmp.top, ActionBar.getCurrentActionBarHeight());
                            AndroidUtilities.rectTmp.bottom = Math.min(AndroidUtilities.rectTmp.bottom, currentTextureView.getMeasuredHeight() - AndroidUtilities.dp(90));
                        } else {
                            AndroidUtilities.rectTmp.top = Math.max(AndroidUtilities.rectTmp.top, ActionBar.getCurrentActionBarHeight());
                            AndroidUtilities.rectTmp.right = Math.min(AndroidUtilities.rectTmp.right, currentTextureView.getMeasuredWidth() - AndroidUtilities.dp(90));
                        }
                        canZoomGesture = AndroidUtilities.rectTmp.contains(ev.getX(), ev.getY());
                        if (!canZoomGesture) {
                            finishZoom();
                        }
                    }
                    if (canZoomGesture && !isInPinchToZoomTouchMode && ev.getPointerCount() == 2) {
                        pinchStartDistance = (float) Math.hypot(ev.getX(1) - ev.getX(0), ev.getY(1) - ev.getY(0));
                        pinchStartCenterX = pinchCenterX = (ev.getX(0) + ev.getX(1)) / 2.0f;
                        pinchStartCenterY = pinchCenterY = (ev.getY(0) + ev.getY(1)) / 2.0f;
                        pinchScale = 1f;

                        pointerId1 = ev.getPointerId(0);
                        pointerId2 = ev.getPointerId(1);
                        isInPinchToZoomTouchMode = true;
                    }
                } else if (ev.getActionMasked() == MotionEvent.ACTION_MOVE && isInPinchToZoomTouchMode) {
                    int index1 = -1;
                    int index2 = -1;
                    for (int i = 0; i < ev.getPointerCount(); i++) {
                        if (pointerId1 == ev.getPointerId(i)) {
                            index1 = i;
                        }
                        if (pointerId2 == ev.getPointerId(i)) {
                            index2 = i;
                        }
                    }
                    if (index1 == -1 || index2 == -1) {
                        getParent().requestDisallowInterceptTouchEvent(false);
                        finishZoom();
                    } else {
                        pinchScale = (float) Math.hypot(ev.getX(index2) - ev.getX(index1), ev.getY(index2) - ev.getY(index1)) / pinchStartDistance;
                        if (pinchScale > 1.005f && !zoomStarted) {
                            pinchStartDistance = (float) Math.hypot(ev.getX(index2) - ev.getX(index1), ev.getY(index2) - ev.getY(index1));
                            pinchStartCenterX = pinchCenterX = (ev.getX(index1) + ev.getX(index2)) / 2.0f;
                            pinchStartCenterY = pinchCenterY = (ev.getY(index1) + ev.getY(index2)) / 2.0f;
                            pinchScale = 1f;
                            pinchTranslationX = 0f;
                            pinchTranslationY = 0f;
                            getParent().requestDisallowInterceptTouchEvent(true);
                            zoomStarted = true;
                            isInPinchToZoomTouchMode = true;
                        }

                        float newPinchCenterX = (ev.getX(index1) + ev.getX(index2)) / 2.0f;
                        float newPinchCenterY = (ev.getY(index1) + ev.getY(index2)) / 2.0f;

                        float moveDx = pinchStartCenterX - newPinchCenterX;
                        float moveDy = pinchStartCenterY - newPinchCenterY;
                        pinchTranslationX = -moveDx / pinchScale;
                        pinchTranslationY = -moveDy / pinchScale;
                        invalidate();
                    }
                } else if ((ev.getActionMasked() == MotionEvent.ACTION_UP || (ev.getActionMasked() == MotionEvent.ACTION_POINTER_UP && checkPointerIds(ev)) || ev.getActionMasked() == MotionEvent.ACTION_CANCEL)) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                    finishZoom();
                }
                fragmentView.invalidate();

                switch (ev.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        pressedX = ev.getX();
                        pressedY = ev.getY();
                        check = true;
                        pressedTime = System.currentTimeMillis();
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        check = false;
                        break;
                    case MotionEvent.ACTION_UP:
                        if (check) {
                            float dx = ev.getX() - pressedX;
                            float dy = ev.getY() - pressedY;
                            long currentTime = System.currentTimeMillis();
                            if (dx * dx + dy * dy < touchSlop * touchSlop && currentTime - pressedTime < 300 && currentTime - lastContentTapTime > 300) {
                                lastContentTapTime = System.currentTimeMillis();
                                if (emojiExpand.getValue()) {
                                    expandEmoji(false);
                                } else if (canHideUI) {
                                    showUi(!uiVisible);
                                    previousState = currentState;
                                    updateViewState();
                                }
                            }
                            check = false;
                        }
                        break;
                }
                return canZoomGesture || check;
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                final float previewAppearValue = videoPreviewAppear.get();
                if (child == previewDialog && (videoPreviewAppear.getValue() && previewAppearValue != 1f)) {
                    canvas.save();
                    canvas.clipPath(videoPreviewClipPath);
                    boolean b = super.drawChild(canvas, child, drawingTime);
                    canvas.restore();

                    float radius = AndroidUtilities.dp(AnimationUtilities.fromTo(30, 6, previewAppearValue));
                    canvas.save();
                    canvas.translate(videoPreviewButtonRect.left, videoPreviewButtonRect.top);
                    previewDialog.drawButton(canvas, 0, 0, videoPreviewButtonRect.width(), videoPreviewButtonRect.height(), radius, previewAppearValue);
                    canvas.restore();
                    return b;
                }

                if (child == previewDialog && currentUserIsVideo && !videoPreviewAppear.getValue()) {
                    canvas.save();
                    canvas.clipPath(videoPreviewClipPath);
                    canvas.translate(videoPreviewClipRect.left, videoPreviewClipRect.top);
                    canvas.scale(
                        videoPreviewClipRect.width() / fragmentView.getMeasuredWidth(),
                        videoPreviewClipRect.height() / fragmentView.getMeasuredHeight(), 0, 0
                    );

                    boolean b = super.drawChild(canvas, child, drawingTime);
                    canvas.restore();
                    return b;
                }


                if (
                        child == callGradientBackground ||
                                child == callingUserTextureView ||
                                (child == currentUserCameraFloatingLayout && currentUserCameraIsFullscreen)
                ) {
                    if (zoomStarted || zoomBackAnimator != null) {
                        canvas.save();
                        canvas.scale(pinchScale, pinchScale, pinchCenterX, pinchCenterY);
                        canvas.translate(pinchTranslationX, pinchTranslationY);
                        boolean b = super.drawChild(canvas, child, drawingTime);
                        canvas.restore();
                        return b;
                    }
                }
                return super.drawChild(canvas, child, drawingTime);
            }
        };
        frameLayout.setClipToPadding(false);
        frameLayout.setClipChildren(false);
        frameLayout.setBackgroundColor(0xff000000);
        fragmentView = frameLayout;
        frameLayout.setFitsSystemWindows(true);
        callingUserTextureView = new VoIPTextureView(context, false, true, false, false);
        callingUserTextureView.renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        callingUserTextureView.renderer.setEnableHardwareScaler(true);
        callingUserTextureView.renderer.setRotateTextureWithScreen(true);
        callingUserTextureView.scaleType = VoIPTextureView.SCALE_TYPE_FIT;
        //     callingUserTextureView.attachBackgroundRenderer();

        callGradientBackground = new VoIPBackground(context);
        callGradientBackground.renderer.setOnUpdateAvatarScaleListener(this::checkAvatarLayout);
        frameLayout.addView(callGradientBackground);

        callingUserAvatarView = new VoIPAvatarWithWavesView(context);
        callingUserAvatarView.setUser(callingUser);
        callingUserAvatarView.setAmplitude(0f);
        frameLayout.addView(callingUserAvatarView, LayoutHelper.createFrame(250, 250, Gravity.CENTER_HORIZONTAL, 0, 88, 0, 0));

        frameLayout.addView(callingUserTextureView);


        final BackgroundGradientDrawable gradientDrawable = new BackgroundGradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF1b354e, 0xFF255b7d});
        final BackgroundGradientDrawable.Sizes sizes = BackgroundGradientDrawable.Sizes.ofDeviceScreen(BackgroundGradientDrawable.Sizes.Orientation.PORTRAIT);
        gradientDrawable.startDithering(sizes, new BackgroundGradientDrawable.ListenerAdapter());

        currentUserCameraFloatingLayout = new VoIPFloatingLayout(context);
        currentUserCameraFloatingLayout.setDelegate((progress, value) -> currentUserTextureView.setScreenshareMiniProgress(progress, value));
        currentUserCameraFloatingLayout.setRelativePosition(1f, 1f);
        currentUserCameraIsFullscreen = true;
        currentUserTextureView = new VoIPTextureView(context, true, false);
        currentUserTextureView.renderer.setIsCamera(true);
        currentUserTextureView.renderer.setUseCameraRotation(true);
        currentUserCameraFloatingLayout.setOnTapListener(view -> {
            if (currentUserIsVideo && callingUserIsVideo && System.currentTimeMillis() - lastContentTapTime > 500) {
                cancelHideUserInterface();
                lastContentTapTime = System.currentTimeMillis();
                callingUserMiniFloatingLayout.setRelativePosition(currentUserCameraFloatingLayout);
                currentUserCameraIsFullscreen = true;
                cameraForceExpanded = true;
                previousState = currentState;
                updateViewState();
            }
        });
        currentUserTextureView.renderer.setMirror(true);
        currentUserCameraFloatingLayout.addView(currentUserTextureView);

        callingUserMiniFloatingLayout = new VoIPFloatingLayout(context);
        callingUserMiniFloatingLayout.alwaysFloating = true;
        callingUserMiniFloatingLayout.setFloatingMode(true, false);
        callingUserMiniTextureRenderer = new TextureViewRenderer(context);
        callingUserMiniTextureRenderer.setEnableHardwareScaler(true);
        callingUserMiniTextureRenderer.setIsCamera(false);
        callingUserMiniTextureRenderer.setFpsReduction(30);
        callingUserMiniTextureRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);

        View backgroundView = new View(context);
        backgroundView.setBackgroundColor(0xff1b1f23);
        callingUserMiniFloatingLayout.addView(backgroundView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        callingUserMiniFloatingLayout.addView(callingUserMiniTextureRenderer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        callingUserMiniFloatingLayout.setOnTapListener(view -> {
            if (cameraForceExpanded && System.currentTimeMillis() - lastContentTapTime > 500) {
                cancelHideUserInterface();
                lastContentTapTime = System.currentTimeMillis();
                currentUserCameraFloatingLayout.setRelativePosition(callingUserMiniFloatingLayout);
                currentUserCameraIsFullscreen = false;
                cameraForceExpanded = false;
                previousState = currentState;
                updateViewState();
            }
        });
        callingUserMiniFloatingLayout.setVisibility(View.GONE);

        frameLayout.addView(currentUserCameraFloatingLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        frameLayout.addView(callingUserMiniFloatingLayout);


        bottomShadow = new View(context);
        bottomShadow.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{Color.TRANSPARENT, ColorUtils.setAlphaComponent(Color.BLACK, 100)}));
        bottomShadow.setAlpha(0);
        frameLayout.addView(bottomShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 140, Gravity.BOTTOM));

        topShadow = new View(context);
        topShadow.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{ColorUtils.setAlphaComponent(Color.BLACK, 100), Color.TRANSPARENT}));
        topShadow.setAlpha(0);
        frameLayout.addView(topShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 140, Gravity.TOP));

        emojiLayout = new VoipEmojiLayout(context, callGradientBackground, currentAccount, callingUser);
        emojiLayout.setOnEmojiLoadedListener(this::showEmojiKeyHint);
        emojiLayout.hideEmojiTextView.setOnClickListener(view -> {
            if (System.currentTimeMillis() - lastContentTapTime < 500) {
                return;
            }
            lastContentTapTime = System.currentTimeMillis();
            if (emojiLayout.emojiLoaded) {
                expandEmoji(!emojiExpand.getValue());
            }
        });

        emojiLayout.emojiLayout.setOnClickListener(view -> {
            if (System.currentTimeMillis() - lastContentTapTime < 500) {
                return;
            }
            lastContentTapTime = System.currentTimeMillis();
            if (emojiLayout.emojiLoaded) {
                expandEmoji(!emojiExpand.getValue());
            }
        });

        statusLayout = new LinearLayout(context) {
            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                final VoIPService service = VoIPService.getSharedInstance();
                final CharSequence callingUserTitleText = callingUserTitle.getText();
                if (service != null && !TextUtils.isEmpty(callingUserTitleText)) {
                    final StringBuilder builder = new StringBuilder(callingUserTitleText);

                    builder.append(", ");
                    if (service.privateCall != null && service.privateCall.video) {
                        builder.append(LocaleController.getString("VoipInVideoCallBranding", R.string.VoipInVideoCallBranding));
                    } else {
                        builder.append(LocaleController.getString("VoipInCallBranding", R.string.VoipInCallBranding));
                    }

                    final long callDuration = service.getCallDuration();
                    if (callDuration > 0) {
                        builder.append(", ");
                        builder.append(LocaleController.formatDuration((int) (callDuration / 1000)));
                    }

                    info.setText(builder);
                }
            }
        };
        statusLayout.setOrientation(LinearLayout.VERTICAL);
        statusLayout.setFocusable(true);
        statusLayout.setFocusableInTouchMode(true);

        callingUserTitle = new TextView(context);
        callingUserTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 28);
        CharSequence name = ContactsController.formatName(callingUser.first_name, callingUser.last_name);
        name = Emoji.replaceEmoji(name, callingUserTitle.getPaint().getFontMetricsInt(), AndroidUtilities.dp(20), false);
        callingUserTitle.setText(name);
        callingUserTitle.setTextColor(Color.WHITE);
        callingUserTitle.setGravity(Gravity.CENTER_HORIZONTAL);
        callingUserTitle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        statusLayout.addView(callingUserTitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 3));

        statusTextView = new VoIPStatusTextView(context, callGradientBackground) {
            @Override
            public float getOffsetX() {
                return statusLayout.getX() + super.getOffsetX();
            }

            @Override
            public float getOffsetY() {
                return statusLayout.getY() + super.getOffsetY();
            }
        };
        ViewCompat.setImportantForAccessibility(statusTextView, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        statusLayout.addView(statusTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 6));

        statusLayout.setClipChildren(false);
        statusLayout.setClipToPadding(false);
        statusLayout.setPadding(0, 0, 0, AndroidUtilities.dp(15));

        frameLayout.addView(statusLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 303, 0, 0));
        frameLayout.addView(emojiLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        rateView = new VoIPRateView(context, callGradientBackground);
        rateView.setVisibility(View.GONE);
        frameLayout.addView(rateView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        buttonsLayout = new VoIPButtonsLayout2(context, callGradientBackground);
        for (int i = 0; i < 3; i++) {
            bottomButtons[i] = new VoIPToggleButton2(context, callGradientBackground) {
                @Override
                public float getOffsetX() {
                    return buttonsLayout.getOffsetX() + super.getOffsetX();
                }

                @Override
                public float getOffsetY() {
                    return buttonsLayout.getOffsetY() + super.getOffsetY();
                }
            };
            buttonsLayout.addOptionButton(bottomButtons[i]);
        }
        buttonsLayout.setListener(buttonsListener);

        frameLayout.addView(buttonsLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        backIcon = new ImageView(context);
        backIcon.setBackground(Theme.createSelectorDrawable(ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.3f))));
        backIcon.setImageResource(R.drawable.msg_call_minimize);
        backIcon.setPadding(AndroidUtilities.dp(15), AndroidUtilities.dp(15), AndroidUtilities.dp(15), AndroidUtilities.dp(15));
        backIcon.setContentDescription(LocaleController.getString("Back", R.string.Back));
        frameLayout.addView(backIcon, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.LEFT, 8, 0, 0, 0));

        speakerPhoneIcon = new ImageView(context) {
            @Override
            public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(info);
                info.setClassName(ToggleButton.class.getName());
                info.setCheckable(true);
                VoIPService service = VoIPService.getSharedInstance();
                if (service != null) {
                    info.setChecked(service.isSpeakerphoneOn());
                }
            }
        };
        speakerPhoneIcon.setContentDescription(LocaleController.getString("VoipSpeaker", R.string.VoipSpeaker));
        speakerPhoneIcon.setBackground(Theme.createSelectorDrawable(ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.3f))));
        speakerPhoneIcon.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12));
        frameLayout.addView(speakerPhoneIcon, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.RIGHT));
        speakerPhoneIcon.setOnClickListener(view -> {
            if (speakerPhoneIcon.getTag() == null) {
                return;
            }
            if (VoIPService.getSharedInstance() != null) {
                VoIPService.getSharedInstance().toggleSpeakerphoneOrShowRouteSheet(activity, false);
            }
        });

        backIcon.setOnClickListener(view -> {
            if (!lockOnScreen) {
                onBackPressed();
            }
        });
        if (windowView.isLockOnScreen()) {
            backIcon.setVisibility(View.GONE);
        }

        notificationsLayout = new VoIPNotificationsLayout(context, callGradientBackground);
        notificationsLayout.setGravity(Gravity.BOTTOM);
        notificationsLayout.setOnViewsUpdated(() -> {
            previousState = currentState;
            updateViewState();
        });
        frameLayout.addView(notificationsLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 200, Gravity.BOTTOM, 16, 0, 16, 0));

        tapToVideoTooltip = new VoIPHintView(context, callGradientBackground, false);
        tapToVideoTooltip.setText(LocaleController.getString("TapToTurnCamera", R.string.TapToTurnCamera));
        frameLayout.addView(tapToVideoTooltip, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 19, 0, 19, 8));
        tapToVideoTooltip.setBottomOffset(AndroidUtilities.dp(4));
        tapToVideoTooltip.setVisibility(View.GONE);

        tapToEmojiTooltip = new VoIPHintView(context, callGradientBackground, true);
        tapToEmojiTooltip.setText(LocaleController.getString("VoipEmojiKeyHint", R.string.VoipEmojiKeyHint));
        frameLayout.addView(tapToEmojiTooltip, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 19, 0, 19, 8));
        tapToEmojiTooltip.setVisibility(View.GONE);

        videoUnavailableHint = new VoIPHintView(context, callGradientBackground, false);
        videoUnavailableHint.setText(LocaleController.getString("VoipVideoUnavailableHint", R.string.VoipVideoUnavailableHint));
        frameLayout.addView(videoUnavailableHint, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 19, 0, 19, 8));
        videoUnavailableHint.setBottomOffset(AndroidUtilities.dp(4));
        videoUnavailableHint.setVisibility(View.GONE);

        updateViewState();

        VoIPService service = VoIPService.getSharedInstance();
        if (service != null) {
            currentBluetoothConnected = service.isBluetoothHeadsetConnected();

            if (!isVideoCall) {
                isVideoCall = service.privateCall != null && service.privateCall.video;
            }
            initRenderers();
        }

        uiVisibility.set(true, false);
        scheduleStopInterfaceUpdates();
        checkLayout();

        return frameLayout;
    }

    private boolean checkPointerIds(MotionEvent ev) {
        if (ev.getPointerCount() < 2) {
            return false;
        }
        if (pointerId1 == ev.getPointerId(0) && pointerId2 == ev.getPointerId(1)) {
            return true;
        }
        if (pointerId1 == ev.getPointerId(1) && pointerId2 == ev.getPointerId(0)) {
            return true;
        }
        return false;
    }

    private VoIPTextureView getFullscreenTextureView() {
        if (callingUserIsVideo) {
            return callingUserTextureView;
        }
        return currentUserTextureView;
    }

    private void finishZoom() {
        if (zoomStarted) {
            zoomStarted = false;
            zoomBackAnimator = ValueAnimator.ofFloat(1f, 0);

            float fromScale = pinchScale;
            float fromTranslateX = pinchTranslationX;
            float fromTranslateY = pinchTranslationY;
            zoomBackAnimator.addUpdateListener(valueAnimator -> {
                float v = (float) valueAnimator.getAnimatedValue();
                pinchScale = fromScale * v + (1f - v);
                pinchTranslationX = fromTranslateX * v;
                pinchTranslationY = fromTranslateY * v;
                fragmentView.invalidate();
            });

            zoomBackAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    zoomBackAnimator = null;
                    pinchScale = 1f;
                    pinchTranslationX = 0;
                    pinchTranslationY = 0;
                    fragmentView.invalidate();
                }
            });
            zoomBackAnimator.setDuration(TRANSITION_DURATION);
            zoomBackAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            zoomBackAnimator.start();
        }
        canZoomGesture = false;
        isInPinchToZoomTouchMode = false;
    }

    private void initRenderers() {
        currentUserTextureView.renderer.init(VideoCapturerDevice.getEglBase().getEglBaseContext(), new RendererCommon.RendererEvents() {
            @Override
            public void onFirstFrameRendered() {
                AndroidUtilities.runOnUIThread(() -> updateViewState());
            }

            @Override
            public void onFrameResolutionChanged(int videoWidth, int videoHeight, int rotation) {

            }

        });
        callingUserTextureView.renderer.init(VideoCapturerDevice.getEglBase().getEglBaseContext(), new RendererCommon.RendererEvents() {
            @Override
            public void onFirstFrameRendered() {
                AndroidUtilities.runOnUIThread(() -> updateViewState());
            }

            @Override
            public void onFrameResolutionChanged(int videoWidth, int videoHeight, int rotation) {

            }

        }, EglBase.CONFIG_PLAIN, new GlRectDrawer());

        callingUserMiniTextureRenderer.init(VideoCapturerDevice.getEglBase().getEglBaseContext(), null);
    }

    public void switchToPip() {
        if (isFinished || !AndroidUtilities.checkInlinePermissions(activity) || instance == null) {
            return;
        }
        isFinished = true;
        if (VoIPService.getSharedInstance() != null) {
            int h = instance.windowView.getMeasuredHeight();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH && instance.lastInsets != null) {
                h -= instance.lastInsets.getSystemWindowInsetBottom();
            }
            VoIPPiPView.show(instance.activity, instance.currentAccount, instance.windowView.getMeasuredWidth(), h, VoIPPiPView.ANIMATION_ENTER_TYPE_TRANSITION);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH && instance.lastInsets != null) {
                VoIPPiPView.topInset = instance.lastInsets.getSystemWindowInsetTop();
                VoIPPiPView.bottomInset = instance.lastInsets.getSystemWindowInsetBottom();
            }
        }
        if (VoIPPiPView.getInstance() == null) {
            return;
        }

        speakerPhoneIcon.animate().alpha(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        backIcon.animate().alpha(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        // buttonsLayout2.animate().alpha(0).setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        callingUserMiniFloatingLayout.animate().alpha(0).setDuration(350).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
        uiVisibility.set(false, true);

        VoIPPiPView.switchingToPip = true;
        switchingToPip = true;
        Animator animator = createPiPTransition(false);
        animationIndex = NotificationCenter.getInstance(currentAccount).setAnimationInProgress(animationIndex, null);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                VoIPPiPView.getInstance().windowView.setAlpha(1f);
                AndroidUtilities.runOnUIThread(() -> {
                    NotificationCenter.getInstance(currentAccount).onAnimationFinish(animationIndex);
                    VoIPPiPView.getInstance().onTransitionEnd();
                    currentUserCameraFloatingLayout.setCornerRadius(-1f);
                    callingUserTextureView.renderer.release();
                    currentUserTextureView.renderer.release();
                    callingUserMiniTextureRenderer.release();
                    destroy();
                    windowView.finishImmediate();
                    VoIPPiPView.switchingToPip = false;
                    switchingToPip = false;
                    instance = null;
                }, 200);
            }
        });
        animator.setDuration(350);
        animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        animator.start();
    }

    public void startTransitionFromPiP() {
        enterFromPiP = true;
        VoIPService service = VoIPService.getSharedInstance();
        if (service != null && service.getVideoState(false) == Instance.VIDEO_STATE_ACTIVE) {
            callingUserTextureView.setStub(VoIPPiPView.getInstance().callingUserTextureView);
            currentUserTextureView.setStub(VoIPPiPView.getInstance().currentUserTextureView);
        }
        windowView.setAlpha(0f);
        updateViewState();
        switchingToPip = true;
        VoIPPiPView.switchingToPip = true;
        VoIPPiPView.prepareForTransition();
        animationIndex = NotificationCenter.getInstance(currentAccount).setAnimationInProgress(animationIndex, null);
        AndroidUtilities.runOnUIThread(() -> {
            windowView.setAlpha(1f);
            Animator animator = createPiPTransition(true);

            backIcon.setAlpha(0f);
            // buttonsLayout2.setAlpha(0f);
            uiVisibility.set(false, false);
            speakerPhoneIcon.setAlpha(0f);
            notificationsLayout.setAlpha(0f);

            currentUserCameraFloatingLayout.switchingToPip = true;
            AndroidUtilities.runOnUIThread(() -> {
                VoIPPiPView.switchingToPip = false;
                VoIPPiPView.finish();

                speakerPhoneIcon.animate().setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                backIcon.animate().alpha(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                // buttonsLayout2.animate().alpha(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                uiVisibility.set(true, true);

                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        NotificationCenter.getInstance(currentAccount).onAnimationFinish(animationIndex);
                        currentUserCameraFloatingLayout.setCornerRadius(-1f);
                        switchingToPip = false;
                        currentUserCameraFloatingLayout.switchingToPip = false;
                        previousState = currentState;
                        updateViewState();
                    }
                });
                animator.setDuration(350);
                animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                animator.start();
            }, 32);
        }, 32);

    }

    public Animator createPiPTransition(boolean enter) {
        currentUserCameraFloatingLayout.animate().cancel();
        float toX = VoIPPiPView.getInstance().windowLayoutParams.x + VoIPPiPView.getInstance().xOffset;
        float toY = VoIPPiPView.getInstance().windowLayoutParams.y + VoIPPiPView.getInstance().yOffset;

        float cameraFromX = currentUserCameraFloatingLayout.getX();
        float cameraFromY = currentUserCameraFloatingLayout.getY();
        float cameraFromScale = currentUserCameraFloatingLayout.getScaleX();
        boolean animateCamera = true;

        float callingUserFromX = 0;
        float callingUserFromY = 0;
        float callingUserFromScale = 1f;
        float callingUserToScale, callingUserToX, callingUserToY;
        float cameraToScale, cameraToX, cameraToY;

        float pipScale = VoIPPiPView.isExpanding() ? 0.4f : 0.25f;
        callingUserToScale = pipScale;
        callingUserToX = toX - (callingUserTextureView.getMeasuredWidth() - callingUserTextureView.getMeasuredWidth() * callingUserToScale) / 2f;
        callingUserToY = toY - (callingUserTextureView.getMeasuredHeight() - callingUserTextureView.getMeasuredHeight() * callingUserToScale) / 2f;
        if (callingUserIsVideo) {
            int currentW = currentUserCameraFloatingLayout.getMeasuredWidth();
            if (currentUserIsVideo && currentW != 0) {
                cameraToScale = (windowView.getMeasuredWidth() / (float) currentW) * pipScale * 0.4f;
                cameraToX = toX - (currentUserCameraFloatingLayout.getMeasuredWidth() - currentUserCameraFloatingLayout.getMeasuredWidth() * cameraToScale) / 2f +
                        VoIPPiPView.getInstance().parentWidth * pipScale - VoIPPiPView.getInstance().parentWidth * pipScale * 0.4f - AndroidUtilities.dp(4);
                cameraToY = toY - (currentUserCameraFloatingLayout.getMeasuredHeight() - currentUserCameraFloatingLayout.getMeasuredHeight() * cameraToScale) / 2f +
                        VoIPPiPView.getInstance().parentHeight * pipScale - VoIPPiPView.getInstance().parentHeight * pipScale * 0.4f - AndroidUtilities.dp(4);
            } else {
                cameraToScale = 0;
                cameraToX = 1f;
                cameraToY = 1f;
                animateCamera = false;
            }
        } else {
            cameraToScale = pipScale;
            cameraToX = toX - (currentUserCameraFloatingLayout.getMeasuredWidth() - currentUserCameraFloatingLayout.getMeasuredWidth() * cameraToScale) / 2f;
            cameraToY = toY - (currentUserCameraFloatingLayout.getMeasuredHeight() - currentUserCameraFloatingLayout.getMeasuredHeight() * cameraToScale) / 2f;
        }

        float cameraCornerRadiusFrom = callingUserIsVideo ? AndroidUtilities.dp(4) : 0;
        float cameraCornerRadiusTo = AndroidUtilities.dp(4) * 1f / cameraToScale;

        float fromCameraAlpha = 1f;
        float toCameraAlpha = 1f;
        if (callingUserIsVideo) {
            fromCameraAlpha = VoIPPiPView.isExpanding() ? 1f : 0f;
        }

        if (enter) {
            if (animateCamera) {
                currentUserCameraFloatingLayout.setScaleX(cameraToScale);
                currentUserCameraFloatingLayout.setScaleY(cameraToScale);
                currentUserCameraFloatingLayout.setTranslationX(cameraToX);
                currentUserCameraFloatingLayout.setTranslationY(cameraToY);
                currentUserCameraFloatingLayout.setCornerRadius(cameraCornerRadiusTo);
                currentUserCameraFloatingLayout.setAlpha(fromCameraAlpha);
            }
            callingUserTextureView.setScaleX(callingUserToScale);
            callingUserTextureView.setScaleY(callingUserToScale);
            callingUserTextureView.setTranslationX(callingUserToX);
            callingUserTextureView.setTranslationY(callingUserToY);
            callingUserTextureView.setRoundCorners(AndroidUtilities.dp(6) * 1f / callingUserToScale);
        }
        ValueAnimator animator = ValueAnimator.ofFloat(enter ? 1f : 0, enter ? 0 : 1f);

        updateSystemBarColors();

        boolean finalAnimateCamera = animateCamera;
        float finalFromCameraAlpha = fromCameraAlpha;
        animator.addUpdateListener(valueAnimator -> {
            float v = (float) valueAnimator.getAnimatedValue();
            updateSystemBarColors();

            if (finalAnimateCamera) {
                float cameraScale = cameraFromScale * (1f - v) + cameraToScale * v;
                currentUserCameraFloatingLayout.setScaleX(cameraScale);
                currentUserCameraFloatingLayout.setScaleY(cameraScale);
                currentUserCameraFloatingLayout.setTranslationX(cameraFromX * (1f - v) + cameraToX * v);
                currentUserCameraFloatingLayout.setTranslationY(cameraFromY * (1f - v) + cameraToY * v);
                currentUserCameraFloatingLayout.setCornerRadius(cameraCornerRadiusFrom * (1f - v) + cameraCornerRadiusTo * v);
                currentUserCameraFloatingLayout.setAlpha(toCameraAlpha * (1f - v) + finalFromCameraAlpha * v);
            }

            float callingUserScale = callingUserFromScale * (1f - v) + callingUserToScale * v;
            callingUserTextureView.setScaleX(callingUserScale);
            callingUserTextureView.setScaleY(callingUserScale);
            float tx = callingUserFromX * (1f - v) + callingUserToX * v;
            float ty = callingUserFromY * (1f - v) + callingUserToY * v;

            callingUserTextureView.setTranslationX(tx);
            callingUserTextureView.setTranslationY(ty);
            callingUserTextureView.setRoundCorners(v * AndroidUtilities.dp(4) * 1 / callingUserScale);
            if (!currentUserCameraFloatingLayout.measuredAsFloatingMode) {
                currentUserTextureView.setScreenshareMiniProgress(v, false);
            }
        });
        return animator;
    }

    private boolean wasConnected = false;
    
    private void updateViewState() {
        if (isFinished || switchingToPip) {
            return;
        }
        lockOnScreen = false;
        boolean animated = previousState != -1;
        boolean showAcceptDeclineView = false;
        boolean showTimer = false;
        boolean showReconnecting = false;
        boolean showProblems = false;
        boolean showUserVideo = false;
        VoIPService service = VoIPService.getSharedInstance();

        switch (currentState) {
            case VoIPService.STATE_WAITING_INCOMING:
                showAcceptDeclineView = true;
                lockOnScreen = true;
                buttonsLayout.setRetryMod(false);
                if (service != null && service.privateCall.video) {
                    statusTextView.setText(LocaleController.getString("VoipInVideoCallBranding", R.string.VoipInVideoCallBranding), true, animated);
                    // showUserVideo = true;
                } else {
                    statusTextView.setText(LocaleController.getString("VoipInCallBranding", R.string.VoipInCallBranding), true, animated);
                }
                break;
            case VoIPService.STATE_WAIT_INIT:
            case VoIPService.STATE_WAIT_INIT_ACK:
                statusTextView.setText(LocaleController.getString("VoipConnecting", R.string.VoipConnecting), true, animated);
                break;
            case VoIPService.STATE_EXCHANGING_KEYS:
                statusTextView.setText(LocaleController.getString("VoipExchangingKeys", R.string.VoipExchangingKeys), true, animated);
                break;
            case VoIPService.STATE_WAITING:
                statusTextView.setText(LocaleController.getString("VoipWaiting", R.string.VoipWaiting), true, animated);
                break;
            case VoIPService.STATE_RINGING:
                statusTextView.setText(LocaleController.getString("VoipRinging", R.string.VoipRinging), true, animated);
                break;
            case VoIPService.STATE_REQUESTING:
                statusTextView.setText(LocaleController.getString("VoipRequesting", R.string.VoipRequesting), true, animated);
                break;
            case VoIPService.STATE_HANGING_UP:
                break;
            case VoIPService.STATE_BUSY:
                showAcceptDeclineView = true;
                // showProblems = true;
                statusTextView.setText(LocaleController.getString("VoipBusy", R.string.VoipBusy), false, animated);
                buttonsLayout.setRetryMod(true);
                currentUserIsVideo = false;
                callingUserIsVideo = false;
                break;
            case VoIPService.STATE_ESTABLISHED:
            case VoIPService.STATE_RECONNECTING:
                emojiLayout.updateKeyView(animated);
                showTimer = true;
                if (currentState == VoIPService.STATE_RECONNECTING) {
                    showReconnecting = true;
                }
                break;
            case VoIPService.STATE_ENDED:
                currentUserTextureView.saveCameraLastBitmap();
                scheduleFinish(200);
                break;
            case VoIPService.STATE_FAILED:
                statusTextView.setText(LocaleController.getString("VoipFailed", R.string.VoipFailed), false, animated);
                final VoIPService voipService = VoIPService.getSharedInstance();
                final String lastError = voipService != null ? voipService.getLastError() : Instance.ERROR_UNKNOWN;
                if (!TextUtils.equals(lastError, Instance.ERROR_UNKNOWN)) {
                    if (TextUtils.equals(lastError, Instance.ERROR_INCOMPATIBLE)) {
                        final String name = ContactsController.formatName(callingUser.first_name, callingUser.last_name);
                        final String message = LocaleController.formatString("VoipPeerIncompatible", R.string.VoipPeerIncompatible, name);
                        showErrorDialog(AndroidUtilities.replaceTags(message));
                    } else if (TextUtils.equals(lastError, Instance.ERROR_PEER_OUTDATED)) {
                        if (isVideoCall) {
                            final String name = UserObject.getFirstName(callingUser);
                            final String message = LocaleController.formatString("VoipPeerVideoOutdated", R.string.VoipPeerVideoOutdated, name);
                            boolean[] callAgain = new boolean[1];
                            AlertDialog dlg = new DarkAlertDialog.Builder(activity)
                                    .setTitle(LocaleController.getString("VoipFailed", R.string.VoipFailed))
                                    .setMessage(AndroidUtilities.replaceTags(message))
                                    .setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), (dialogInterface, i) -> windowView.finish())
                                    .setPositiveButton(LocaleController.getString("VoipPeerVideoOutdatedMakeVoice", R.string.VoipPeerVideoOutdatedMakeVoice), (dialogInterface, i) -> {
                                        callAgain[0] = true;
                                        currentState = VoIPService.STATE_BUSY;
                                        Intent intent = new Intent(activity, VoIPService.class);
                                        intent.putExtra("user_id", callingUser.id);
                                        intent.putExtra("is_outgoing", true);
                                        intent.putExtra("start_incall_activity", false);
                                        intent.putExtra("video_call", false);
                                        intent.putExtra("can_video_call", false);
                                        intent.putExtra("account", currentAccount);
                                        try {
                                            activity.startService(intent);
                                        } catch (Throwable e) {
                                            FileLog.e(e);
                                        }
                                    })
                                    .show();
                            dlg.setCanceledOnTouchOutside(true);
                            dlg.setOnDismissListener(dialog -> {
                                if (!callAgain[0]) {
                                    windowView.finish();
                                }
                            });
                        } else {
                            final String name = UserObject.getFirstName(callingUser);
                            final String message = LocaleController.formatString("VoipPeerOutdated", R.string.VoipPeerOutdated, name);
                            showErrorDialog(AndroidUtilities.replaceTags(message));
                        }
                    } else if (TextUtils.equals(lastError, Instance.ERROR_PRIVACY)) {
                        final String name = ContactsController.formatName(callingUser.first_name, callingUser.last_name);
                        final String message = LocaleController.formatString("CallNotAvailable", R.string.CallNotAvailable, name);
                        showErrorDialog(AndroidUtilities.replaceTags(message));
                    } else if (TextUtils.equals(lastError, Instance.ERROR_AUDIO_IO)) {
                        showErrorDialog("Error initializing audio hardware");
                    } else if (TextUtils.equals(lastError, Instance.ERROR_LOCALIZED)) {
                        windowView.finish();
                    } else if (TextUtils.equals(lastError, Instance.ERROR_CONNECTION_SERVICE)) {
                        showErrorDialog(LocaleController.getString("VoipErrorUnknown", R.string.VoipErrorUnknown));
                    } else {
                        scheduleFinish(1000);
                    }
                } else {
                    scheduleFinish(1000);
                }
                break;
            case VoIPService.STATE_ASK_RATING:
                if (service != null && service.privateCall != null) {
                    privateCallToRate = service.privateCall;
                    cancelFinish();
                    rateView.setVisibility(View.VISIBLE);
                    ratingVisible.set(true);
                    callingUserTitle.setText(LocaleController.getString(R.string.VoipCallEnded));
                    callingUserMiniFloatingLayout.setVisibility(View.GONE);
                    currentUserCameraFloatingLayout.setVisibility(View.GONE);
                    callingUserAvatarView.setShowWaves(false);
                }
                break;
        }

        boolean finalShowReconnecting = showReconnecting;
        boolean finalShowTimer = showTimer;
        boolean finalShowProblems = showProblems;
        AndroidUtilities.runOnUIThread(() -> {
            bgNoSignal = (finalShowReconnecting && wasConnected) || finalShowProblems;
            if (finalShowTimer && !wasConnected) {
                wasConnected = true;
                int offset = 0;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT_WATCH) {
                    offset = lastInsets.getSystemWindowInsetTop();
                }
                callGradientBackground.renderer.showConnected(fragmentView.getMeasuredWidth() / 2f, AndroidUtilities.dp(125 + 90) + offset);
            }
            checkProblems();
        });

        if (previewDialog != null && videoPreviewAppear.getValue()) {
            return;
        }

        if (service != null) {
            callingUserIsVideo = service.getRemoteVideoState() == Instance.VIDEO_STATE_ACTIVE;
            currentUserIsVideo = service.getVideoState(false) == Instance.VIDEO_STATE_ACTIVE || service.getVideoState(false) == Instance.VIDEO_STATE_PAUSED;

            if (!currentUserIsVideo && showUserVideo && !buttonsLayout.isVideoCallMode()) {
                buttonsLayout.setVideoCallMode(true);
                currentUserIsVideo = true;
                service.requestVideoCall(false);
                service.setVideoState(false, Instance.VIDEO_STATE_ACTIVE);
            }

            if (showUserVideo) {
                service.setNeedStartVideoOnInit(currentUserIsVideo);
            }

            if (currentUserIsVideo && !isVideoCall) {
                isVideoCall = true;
            }
        }

        if (animated) {
            currentUserCameraFloatingLayout.saveRelativePosition();
            callingUserMiniFloatingLayout.saveRelativePosition();
        }

        if (callingUserIsVideo) {
            if (!switchingToPip) {
                callingUserAvatarView.setAlpha(1f);
            }
            callingUserTextureViewVisible.set(true, animated);
            if (!callingUserTextureView.renderer.isFirstFrameRendered() && !enterFromPiP) {
                callingUserIsVideo = false;
            }
        }

        if (!(currentUserIsVideo || callingUserIsVideo)) {
            callingUserTextureViewVisible.set(false, animated);
        }

        if (!currentUserIsVideo || !callingUserIsVideo) {
            cameraForceExpanded = false;
        }

        boolean showCallingUserVideoMini = currentUserIsVideo && cameraForceExpanded;

        acceptVisible.set(showAcceptDeclineView, animated /*&& currentState != VoIPService.STATE_BUSY*/ );

        if (!animated) {
            callOutButtonsPresent.set(true, !showAcceptDeclineView);
        }

        windowView.setLockOnScreen(lockOnScreen || deviceIsLocked);
        canHideUI = (currentState == VoIPService.STATE_ESTABLISHED) && (currentUserIsVideo || callingUserIsVideo);
        if (!canHideUI && !uiVisible) {
            showUi(true);
        }

        if (uiVisible && canHideUI) {
            scheduleHideUserInterface();
        }

        if (animated) {
            if (lockOnScreen || !uiVisible) {
                if (backIcon.getVisibility() != View.VISIBLE) {
                    backIcon.setVisibility(View.VISIBLE);
                    backIcon.setAlpha(0f);
                }
                backIcon.animate().alpha(0f).start();
            } else {
                backIcon.animate().alpha(1f).start();
            }
        } else {
            if (!lockOnScreen) {
                backIcon.setVisibility(View.VISIBLE);
            }
            backIcon.setAlpha(lockOnScreen ? 0 : 1f);
            notificationsLayout.setTranslationY(-AndroidUtilities.dp(16) - (uiVisible ? AndroidUtilities.dp(96) : 0));
        }

        if (currentState != VoIPService.STATE_HANGING_UP && currentState != VoIPService.STATE_ENDED) {
            updateButtons(animated);
        }

        if (showTimer) {
            statusTextView.showTimer(animated);
        }

        canSwitchToPip = (currentState != VoIPService.STATE_ENDED && currentState != VoIPService.STATE_BUSY) && (currentUserIsVideo || callingUserIsVideo);

        int floatingViewsOffset;
        if (service != null) {
            if (currentUserIsVideo) {
                service.sharedUIParams.tapToVideoTooltipWasShowed = true;
            }
            currentUserTextureView.setIsScreencast(service.isScreencast());
            currentUserTextureView.renderer.setMirror(service.isFrontFaceCamera());
            service.setSinks(currentUserIsVideo && !service.isScreencast() ? currentUserTextureView.renderer : null, showCallingUserVideoMini ? callingUserMiniTextureRenderer : callingUserTextureView.renderer);

            if (animated) {
                notificationsLayout.beforeLayoutChanges();
            }
            if (service.isMicMute()) {
                notificationsLayout.addNotification(0, LocaleController.getString(R.string.VoipSelfMicrophoneIsOff), "self-muted", animated);
            } else {
                notificationsLayout.removeNotification("self-muted");
            }
            if ((currentUserIsVideo || callingUserIsVideo) && (currentState == VoIPService.STATE_ESTABLISHED || currentState == VoIPService.STATE_RECONNECTING) && service.getCallDuration() > 500) {
                if (service.getRemoteAudioState() == Instance.AUDIO_STATE_MUTED) {
                    notificationsLayout.addNotification(0, LocaleController.formatString("VoipUserMicrophoneIsOff", R.string.VoipUserMicrophoneIsOff, UserObject.getFirstName(callingUser)), "muted", animated);
                } else {
                    notificationsLayout.removeNotification("muted");
                }
                if (service.getRemoteVideoState() == Instance.VIDEO_STATE_INACTIVE) {
                    notificationsLayout.addNotification(0, LocaleController.formatString("VoipUserCameraIsOff", R.string.VoipUserCameraIsOff, UserObject.getFirstName(callingUser)), "video", animated);
                } else {
                    notificationsLayout.removeNotification("video");
                }
            } else {
                if (service.getRemoteAudioState() == Instance.AUDIO_STATE_MUTED) {
                    notificationsLayout.addNotification(0, LocaleController.formatString("VoipUserMicrophoneIsOff", R.string.VoipUserMicrophoneIsOff, UserObject.getFirstName(callingUser)), "muted", animated);
                } else {
                    notificationsLayout.removeNotification("muted");
                }
                notificationsLayout.removeNotification("video");
            }

            if (notificationsLayout.getChildCount() == 0 && callingUserIsVideo && service.privateCall != null && !service.privateCall.video && !service.sharedUIParams.tapToVideoTooltipWasShowed) {
                service.sharedUIParams.tapToVideoTooltipWasShowed = true;
                tapToVideoTooltip.showForView(bottomButtons[1], true);
            } else if (notificationsLayout.getChildCount() != 0) {
                tapToVideoTooltip.hide();
            }

            if (animated) {
                notificationsLayout.animateLayoutChanges();
            }
        }

        floatingViewsOffset = notificationsLayout.getChildsHeight();

        callingUserMiniFloatingLayout.setBottomOffset(floatingViewsOffset, animated);
        currentUserCameraFloatingLayout.setBottomOffset(floatingViewsOffset, animated);
        currentUserCameraFloatingLayout.setUiVisible(uiVisible);
        callingUserMiniFloatingLayout.setUiVisible(uiVisible);

        if (currentUserIsVideo) {
            if ((!callingUserIsVideo || cameraForceExpanded) && !showUserVideo) {
                showFloatingLayout(STATE_FULLSCREEN, animated);
            } else {
                showFloatingLayout(STATE_FLOATING, animated);
            }
        } else {
            showFloatingLayout(STATE_GONE, animated);
        }

        if (showCallingUserVideoMini && callingUserMiniFloatingLayout.getTag() == null) {
            callingUserMiniFloatingLayout.setIsActive(true);
            if (callingUserMiniFloatingLayout.getVisibility() != View.VISIBLE) {
                callingUserMiniFloatingLayout.setVisibility(View.VISIBLE);
                callingUserMiniFloatingLayout.setAlpha(0f);
                callingUserMiniFloatingLayout.setScaleX(0.5f);
                callingUserMiniFloatingLayout.setScaleY(0.5f);
            }
            callingUserMiniFloatingLayout.animate().setListener(null).cancel();
            callingUserMiniFloatingLayout.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).setStartDelay(150).start();
            callingUserMiniFloatingLayout.setTag(1);
        } else if (!showCallingUserVideoMini && callingUserMiniFloatingLayout.getTag() != null) {
            callingUserMiniFloatingLayout.setIsActive(false);
            callingUserMiniFloatingLayout.animate().alpha(0).scaleX(0.5f).scaleY(0.5f).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (callingUserMiniFloatingLayout.getTag() == null) {
                        callingUserMiniFloatingLayout.setVisibility(View.GONE);
                    }
                }
            }).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            callingUserMiniFloatingLayout.setTag(null);
        }

        currentUserCameraFloatingLayout.restoreRelativePosition();
        callingUserMiniFloatingLayout.restoreRelativePosition();

        updateSpeakerPhoneIcon();
        callGradientBackground.setHardStop((callingUserIsVideo || currentUserIsVideo || ratingVisible.getValue()) && !showUserVideo);
        callingUserAvatarView.setVisibility(((callingUserIsVideo || currentUserIsVideo) && !ratingVisible.getValue() && !showUserVideo) ? View.GONE: View.VISIBLE);
        hasAnyVideo.set((callingUserIsVideo || currentUserIsVideo) && !showUserVideo);
    }

    private void showUi(boolean show) {
        if (!show && uiVisible) {
            speakerPhoneIcon.animate().alpha(0).translationY(-AndroidUtilities.dp(50)).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            backIcon.animate().alpha(0).translationY(-AndroidUtilities.dp(50)).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            cancelHideUserInterface();
            buttonsLayout.setEnabled(false);
        } else if (show && !uiVisible) {
            tapToVideoTooltip.hide();
            tapToEmojiTooltip.hide();
            videoUnavailableHint.hide();
            speakerPhoneIcon.animate().alpha(1f).translationY(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            backIcon.animate().alpha(1f).translationY(0).setDuration(150).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            buttonsLayout.setEnabled(true);
        }

        uiVisible = show;
        uiVisibility.set(show, true);
        windowView.requestFullscreen(!show);
    }

    private void showFloatingLayout(int state, boolean animated) {
        if (currentUserCameraFloatingLayout.getTag() == null || (int) currentUserCameraFloatingLayout.getTag() != STATE_FLOATING) {
            currentUserCameraFloatingLayout.setUiVisible(uiVisible);
        }
        if (!animated && cameraShowingAnimator != null) {
            cameraShowingAnimator.removeAllListeners();
            cameraShowingAnimator.cancel();
        }
        if (state == STATE_GONE) {
            if (animated) {
                if (currentUserCameraFloatingLayout.getTag() != null && (int) currentUserCameraFloatingLayout.getTag() != STATE_GONE) {
                    if (cameraShowingAnimator != null) {
                        cameraShowingAnimator.removeAllListeners();
                        cameraShowingAnimator.cancel();
                    }
                    AnimatorSet animatorSet = new AnimatorSet();
                    animatorSet.playTogether(
                            ObjectAnimator.ofFloat(currentUserCameraFloatingLayout, View.ALPHA, currentUserCameraFloatingLayout.getAlpha(), 0)
                    );
                    if (currentUserCameraFloatingLayout.getTag() != null && (int) currentUserCameraFloatingLayout.getTag() == STATE_FLOATING) {
                        animatorSet.playTogether(
                                ObjectAnimator.ofFloat(currentUserCameraFloatingLayout, View.SCALE_X, currentUserCameraFloatingLayout.getScaleX(), 0.7f),
                                ObjectAnimator.ofFloat(currentUserCameraFloatingLayout, View.SCALE_Y, currentUserCameraFloatingLayout.getScaleX(), 0.7f)
                        );
                    }
                    cameraShowingAnimator = animatorSet;
                    cameraShowingAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            currentUserCameraFloatingLayout.setTranslationX(0);
                            currentUserCameraFloatingLayout.setTranslationY(0);
                            currentUserCameraFloatingLayout.setScaleY(1f);
                            currentUserCameraFloatingLayout.setScaleX(1f);
                            currentUserCameraFloatingLayout.setVisibility(View.GONE);
                        }
                    });
                    cameraShowingAnimator.setDuration(250).setInterpolator(CubicBezierInterpolator.DEFAULT);
                    cameraShowingAnimator.setStartDelay(50);
                    cameraShowingAnimator.start();
                }
            } else {
                currentUserCameraFloatingLayout.setVisibility(View.GONE);
            }
        } else {
            boolean switchToFloatAnimated = animated;
            if (currentUserCameraFloatingLayout.getTag() == null || (int) currentUserCameraFloatingLayout.getTag() == STATE_GONE) {
                switchToFloatAnimated = false;
            }
            if (animated) {
                if (currentUserCameraFloatingLayout.getTag() != null && (int) currentUserCameraFloatingLayout.getTag() == STATE_GONE) {
                    if (currentUserCameraFloatingLayout.getVisibility() == View.GONE) {
                        currentUserCameraFloatingLayout.setAlpha(0f);
                        currentUserCameraFloatingLayout.setScaleX(0.7f);
                        currentUserCameraFloatingLayout.setScaleY(0.7f);
                        currentUserCameraFloatingLayout.setVisibility(View.VISIBLE);
                    }
                    if (cameraShowingAnimator != null) {
                        cameraShowingAnimator.removeAllListeners();
                        cameraShowingAnimator.cancel();
                    }
                    AnimatorSet animatorSet = new AnimatorSet();
                    animatorSet.playTogether(
                            ObjectAnimator.ofFloat(currentUserCameraFloatingLayout, View.ALPHA, 0.0f, 1f),
                            ObjectAnimator.ofFloat(currentUserCameraFloatingLayout, View.SCALE_X, 0.7f, 1f),
                            ObjectAnimator.ofFloat(currentUserCameraFloatingLayout, View.SCALE_Y, 0.7f, 1f)
                    );
                    cameraShowingAnimator = animatorSet;
                    cameraShowingAnimator.setDuration(150).start();
                }
            } else {
                currentUserCameraFloatingLayout.setVisibility(View.VISIBLE);
            }
            if ((currentUserCameraFloatingLayout.getTag() == null || (int) currentUserCameraFloatingLayout.getTag() != STATE_FLOATING) && currentUserCameraFloatingLayout.relativePositionToSetX < 0) {
                currentUserCameraFloatingLayout.setRelativePosition(1f, 1f);
                currentUserCameraIsFullscreen = true;
            }
            currentUserCameraFloatingLayout.setFloatingMode(state == STATE_FLOATING, switchToFloatAnimated);
            currentUserCameraIsFullscreen = state != STATE_FLOATING;
        }
        currentUserCameraFloatingLayout.setTag(state);
    }

    private void updateButtons(boolean animated) {
        VoIPService service = VoIPService.getSharedInstance();
        if (service == null) {
            return;
        }
        if (currentState == VoIPService.STATE_WAITING_INCOMING || currentState == VoIPService.STATE_BUSY) {
            if (service.privateCall != null && service.privateCall.video && currentState == VoIPService.STATE_WAITING_INCOMING) {
                if (!service.isScreencast() && (currentUserIsVideo || callingUserIsVideo)) {
                    setFrontalCameraAction(bottomButtons[0], service, animated);
                    if (uiVisible) {
                        speakerPhoneIcon.animate().alpha(1f).start();
                    }
                } else {
                    setSpeakerPhoneAction(bottomButtons[0], service, animated);
                    speakerPhoneIcon.animate().alpha(0).start();
                }
                setVideoAction(bottomButtons[1], service, animated);
                setMicrophoneAction(bottomButtons[2], service, animated);
            } else if (currentState != VoIPService.STATE_BUSY) {
                bottomButtons[0].setVisibility(View.GONE);
                bottomButtons[1].setVisibility(View.GONE);
                bottomButtons[2].setVisibility(View.GONE);
            }
        } else {
            if (instance == null) {
                return;
            }
            if (!service.isScreencast() && currentUserIsVideo) {
                setFrontalCameraAction(bottomButtons[0], service, animated);
                if (uiVisible) {
                    speakerPhoneIcon.setTag(1);
                    speakerPhoneIcon.animate().alpha(1f).start();
                }
            } else {
                setSpeakerPhoneAction(bottomButtons[0], service, animated);
                speakerPhoneIcon.setTag(null);
                speakerPhoneIcon.animate().alpha(0f).start();
            }
            setVideoAction(bottomButtons[1], service, animated);
            setMicrophoneAction(bottomButtons[2], service, animated);
        }

        updateSpeakerPhoneIcon();
    }

    private void setMicrophoneAction(VoIPToggleButton2 bottomButton, VoIPService service, boolean animated) {
        if (service.isMicMute()) {
            bottomButton.setText(LocaleController.getString("VoipUnmute", R.string.VoipUnmute), animated);
            bottomButton.setChecked(true, animated);
            bottomButton.startLottieAnimation(R.raw.call_mute, animated);
        } else {
            bottomButton.setText(LocaleController.getString("VoipMute", R.string.VoipMute), animated);
            bottomButton.setChecked(false, animated);
            bottomButton.startLottieAnimation(R.raw.call_unmute, animated);
        }
        currentUserCameraFloatingLayout.setMuted(service.isMicMute(), animated);
        bottomButton.setOnClickListener(view -> {
            final VoIPService serviceInstance = VoIPService.getSharedInstance();
            if (serviceInstance != null) {
                final boolean micMute = !serviceInstance.isMicMute();
                if (accessibilityManager.isTouchExplorationEnabled()) {
                    final String text;
                    if (micMute) {
                        text = LocaleController.getString("AccDescrVoipMicOff", R.string.AccDescrVoipMicOff);
                    } else {
                        text = LocaleController.getString("AccDescrVoipMicOn", R.string.AccDescrVoipMicOn);
                    }
                    view.announceForAccessibility(text);
                }
                serviceInstance.setMicMute(micMute, false, true);
                previousState = currentState;
                updateViewState();
                bottomButton.playClickAnimation();
            }
        });
    }

    private void setVideoAction(VoIPToggleButton2 bottomButton, VoIPService service, boolean animated) {
        boolean isVideoAvailable;
        if (currentUserIsVideo || callingUserIsVideo) {
            isVideoAvailable = true;
        } else {
            isVideoAvailable = service.isVideoAvailable();
        }
        if (isVideoAvailable) {
            if (currentUserIsVideo) {
                bottomButton.setText(LocaleController.getString("VoipStopVideo", R.string.VoipStopVideo), animated);
                bottomButton.setChecked(false, animated);
                bottomButton.startLottieAnimation(R.raw.video_start, animated);
            } else {
                bottomButton.setText(LocaleController.getString("VoipStartVideo", R.string.VoipStartVideo), animated);
                bottomButton.setChecked(true, animated);
                bottomButton.startLottieAnimation(R.raw.video_stop, animated);
            }
            bottomButton.setOnClickListener(view -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && activity.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    activity.requestPermissions(new String[]{Manifest.permission.CAMERA}, 102);
                } else {
                    if (Build.VERSION.SDK_INT < 21 && service.privateCall != null && !service.privateCall.video && !callingUserIsVideo && !service.sharedUIParams.cameraAlertWasShowed) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                        builder.setMessage(LocaleController.getString("VoipSwitchToVideoCall", R.string.VoipSwitchToVideoCall));
                        builder.setPositiveButton(LocaleController.getString("VoipSwitch", R.string.VoipSwitch), (dialogInterface, i) -> {
                            service.sharedUIParams.cameraAlertWasShowed = true;
                            toggleCameraInput();
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        builder.create().show();
                    } else {
                        toggleCameraInput();
                    }
                }
                bottomButton.playClickAnimation();
            });
            bottomButton.setEnabled(true);
        } else {
            bottomButton.setText(LocaleController.getString("VoipVideoUnavailable", R.string.VoipVideoUnavailable), animated);
            bottomButton.setChecked(true, animated);
            bottomButton.startLottieAnimation(R.raw.video_stop, animated);
            bottomButton.setOnClickListener(null);
            bottomButton.setEnabled(true);
            bottomButton.setOnClickListener((v) -> {
                bottomButton.playClickAnimation();
                videoUnavailableHint.showForView(bottomButton, true);
            });
        }
    }

    private void updateSpeakerPhoneIcon() {
        VoIPService service = VoIPService.getSharedInstance();
        if (service == null) {
            return;
        }
        if (service.isBluetoothOn()) {
            speakerPhoneIcon.setImageResource(R.drawable.calls_bluetooth);
        } else if (service.isSpeakerphoneOn()) {
            speakerPhoneIcon.setImageResource(R.drawable.calls_speaker);
        } else {
            if (service.isHeadsetPlugged()) {
                speakerPhoneIcon.setImageResource(R.drawable.calls_menu_headset);
            } else {
                speakerPhoneIcon.setImageResource(R.drawable.calls_menu_phone);
            }
        }
    }

    private void setSpeakerPhoneAction(VoIPToggleButton2 bottomButton, VoIPService service, boolean animated) {
        boolean fromVideoMode = bottomButton.getCurrentAnimation() == R.raw.camera_flip;

        if (service.isBluetoothOn()) {
            bottomButton.setText(LocaleController.getString("VoipAudioRoutingBluetooth", R.string.VoipAudioRoutingBluetooth), animated);
            bottomButton.setChecked(false, animated);
            bottomButton.startLottieAnimation(R.raw.speaker_to_bt, animated && !fromVideoMode);
        } else if (service.isSpeakerphoneOn()) {
            bottomButton.setText(LocaleController.getString("VoipSpeaker", R.string.VoipSpeaker), animated);
            bottomButton.setChecked(true, animated);
            bottomButton.startLottieAnimation(R.raw.bt_to_speaker, animated && !fromVideoMode);
        } else {
            bottomButton.setText(LocaleController.getString("VoipSpeaker", R.string.VoipSpeaker), animated);
            bottomButton.setChecked(false, animated);
            bottomButton.startLottieAnimation(R.raw.bt_to_speaker, animated && !fromVideoMode);
        }
        bottomButton.setEnabled(true);
        bottomButton.setOnClickListener(view -> {
            if (VoIPService.getSharedInstance() != null) {
                VoIPService.getSharedInstance().toggleSpeakerphoneOrShowRouteSheet(activity, false);
            }
            bottomButton.playClickAnimation();
        });
    }

    private void setFrontalCameraAction(VoIPToggleButton2 bottomButton, VoIPService service, boolean animated) {
        if (!currentUserIsVideo) {
            bottomButton.setText(LocaleController.getString("VoipFlip", R.string.VoipFlip), animated);
            bottomButton.setOnClickListener(null);
            bottomButton.setEnabled(false);
            bottomButton.setChecked(false, animated);
            bottomButton.startLottieAnimation(R.raw.camera_flip, animated);
        } else {
            boolean oldChecked = bottomButton.getChecked();
            boolean newChecked;
            bottomButton.setEnabled(true);
            if (!service.isFrontFaceCamera()) {
                bottomButton.setText(LocaleController.getString("VoipFlip", R.string.VoipFlip), animated);
                bottomButton.setChecked(newChecked = true, animated);
                bottomButton.startLottieAnimation(R.raw.camera_flip, animated);
            } else {
                bottomButton.setText(LocaleController.getString("VoipFlip", R.string.VoipFlip), animated);
                bottomButton.setChecked(newChecked = false, animated);
                bottomButton.startLottieAnimation(R.raw.camera_flip, animated);
            }
            if (oldChecked != newChecked) {
                bottomButton.restartLottieAnimation();
            }
            bottomButton.setOnClickListener(view -> {
                final VoIPService serviceInstance = VoIPService.getSharedInstance();
                if (serviceInstance != null) {
                    if (accessibilityManager.isTouchExplorationEnabled()) {
                        final String text;
                        if (service.isFrontFaceCamera()) {
                            text = LocaleController.getString("AccDescrVoipCamSwitchedToBack", R.string.AccDescrVoipCamSwitchedToBack);
                        } else {
                            text = LocaleController.getString("AccDescrVoipCamSwitchedToFront", R.string.AccDescrVoipCamSwitchedToFront);
                        }
                        view.announceForAccessibility(text);
                    }
                    serviceInstance.switchCamera();
                }
                bottomButton.playClickAnimation();
            });
        }
    }

    public void onScreenCastStart() {
        if (previewDialog == null) {
            return;
        }
        previewDialog.dismiss(true, true);
    }

    private void toggleCameraInput() {
        VoIPService service = VoIPService.getSharedInstance();
        if (service != null) {
            if (accessibilityManager.isTouchExplorationEnabled()) {
                final String text;
                if (!currentUserIsVideo) {
                    text = LocaleController.getString("AccDescrVoipCamOn", R.string.AccDescrVoipCamOn);
                } else {
                    text = LocaleController.getString("AccDescrVoipCamOff", R.string.AccDescrVoipCamOff);
                }
                fragmentView.announceForAccessibility(text);
            }
            if (!currentUserIsVideo) {
                if (Build.VERSION.SDK_INT >= 21) {
                    if (previewDialog == null) {
                        service.createCaptureDevice(false);
                        if (!service.isFrontFaceCamera()) {
                            service.switchCamera();
                        }
                        windowView.setLockOnScreen(true);
                        videoPreviewAppear.set(true, false);
                        videoPreviewAppear.set(false, false);
                        videoPreviewAppear.set(true);
                        previewDialog = new PrivateVideoPreviewDialog(fragmentView.getContext(), false, true, false) {
                            @Override
                            public void onDismiss(boolean screencast, boolean apply) {
                                videoPreviewAppear.set(false, true, new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationEnd(Animator animation) {
                                        super.onAnimationEnd(animation);
                                        if (previewDialog != null) {
                                            fragmentView.removeView(previewDialog);
                                            previewDialog = null;
                                        }
                                    }

                                    @Override
                                    public void onAnimationCancel(Animator animation) {
                                        super.onAnimationCancel(animation);
                                        if (previewDialog != null) {
                                            fragmentView.removeView(previewDialog);
                                            previewDialog = null;
                                        }
                                    }
                                });
                                // previewDialog = null;
                                VoIPService service = VoIPService.getSharedInstance();
                                windowView.setLockOnScreen(false);
                                if (apply) {
                                    currentUserIsVideo = true;
                                    if (service != null && !screencast) {
                                        service.requestVideoCall(false);
                                        service.setVideoState(false, Instance.VIDEO_STATE_ACTIVE);
                                    }
                                    if (!callingUserIsVideo) {
                                        previewDialog.animateDismiss();
                                        previewDialog = null;
                                    }
                                } else {
                                    if (service != null) {
                                        service.setVideoState(false, Instance.VIDEO_STATE_INACTIVE);
                                    }
                                    previewDialog = null;
                                }
                                // previewDialog = null;
                                previousState = currentState;
                                updateViewState();
                            }
                        };
                        if (lastInsets != null) {
                            previewDialog.setBottomPadding(lastInsets.getSystemWindowInsetBottom());
                        }
                        fragmentView.addView(previewDialog);
                        cancelHideUserInterface();
                    }
                    return;
                } else {
                    currentUserIsVideo = true;
                    if (!service.isSpeakerphoneOn()) {
                        VoIPService.getSharedInstance().toggleSpeakerphoneOrShowRouteSheet(activity, false);
                    }
                    service.requestVideoCall(false);
                    service.setVideoState(false, Instance.VIDEO_STATE_ACTIVE);
                }
            } else {
                currentUserTextureView.saveCameraLastBitmap();
                service.setVideoState(false, Instance.VIDEO_STATE_INACTIVE);
                if (Build.VERSION.SDK_INT >= 21) {
                    service.clearCamera();
                }
            }
            previousState = currentState;
            updateViewState();
        }
    }

    public static void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (instance != null) {
            instance.onRequestPermissionsResultInternal(requestCode, permissions, grantResults);
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void onRequestPermissionsResultInternal(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 101) {
            if (VoIPService.getSharedInstance() == null) {
                windowView.finish();
                return;
            }
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                VoIPService.getSharedInstance().acceptIncomingCall();
            } else {
                if (!activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                    VoIPService.getSharedInstance().declineIncomingCall();
                    VoIPHelper.permissionDenied(activity, () -> windowView.finish(), requestCode);
                    return;
                }
            }
        }
        if (requestCode == 102) {
            if (VoIPService.getSharedInstance() == null) {
                windowView.finish();
                return;
            }
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                toggleCameraInput();
            }
        }
    }

    private void updateSystemBarColors() {
       checkLayout();
    }

    public static void onPause() {
        if (instance != null) {
            instance.onPauseInternal();
        }
        if (VoIPPiPView.getInstance() != null) {
            VoIPPiPView.getInstance().onPause();
        }
    }

    public static void onResume() {
        if (instance != null) {
            instance.onResumeInternal();
        }
        if (VoIPPiPView.getInstance() != null) {
            VoIPPiPView.getInstance().onResume();
        }
    }

    public void onPauseInternal() {
        PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);

        boolean screenOn;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            screenOn = pm.isInteractive();
        } else {
            screenOn = pm.isScreenOn();
        }

        boolean hasPermissionsToPip = AndroidUtilities.checkInlinePermissions(activity);

        if (canSwitchToPip && hasPermissionsToPip) {
            int h = instance.windowView.getMeasuredHeight();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH && instance.lastInsets != null) {
                h -= instance.lastInsets.getSystemWindowInsetBottom();
            }
            VoIPPiPView.show(instance.activity, instance.currentAccount, instance.windowView.getMeasuredWidth(), h, VoIPPiPView.ANIMATION_ENTER_TYPE_SCALE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH && instance.lastInsets != null) {
                VoIPPiPView.topInset = instance.lastInsets.getSystemWindowInsetTop();
                VoIPPiPView.bottomInset = instance.lastInsets.getSystemWindowInsetBottom();
            }
        }

        if (currentUserIsVideo && (!hasPermissionsToPip || !screenOn)) {
            VoIPService service = VoIPService.getSharedInstance();
            if (service != null) {
                service.setVideoState(false, Instance.VIDEO_STATE_PAUSED);
            }
        }
    }

    public void onResumeInternal() {
        if (VoIPPiPView.getInstance() != null) {
            VoIPPiPView.finish();
        }
        VoIPService service = VoIPService.getSharedInstance();
        if (service != null) {
            if (service.getVideoState(false) == Instance.VIDEO_STATE_PAUSED) {
                service.setVideoState(false, Instance.VIDEO_STATE_ACTIVE);
            }
            updateViewState();
        } else {
            windowView.finish();
        }

        deviceIsLocked = ((KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE)).inKeyguardRestrictedInputMode();
    }

    private void showErrorDialog(CharSequence message) {
        if (activity.isFinishing()) {
            return;
        }
        AlertDialog dlg = new DarkAlertDialog.Builder(activity)
                .setTitle(LocaleController.getString("VoipFailed", R.string.VoipFailed))
                .setMessage(message)
                .setPositiveButton(LocaleController.getString("OK", R.string.OK), null)
                .show();
        dlg.setCanceledOnTouchOutside(true);
        dlg.setOnDismissListener(dialog -> windowView.finish());
    }

    @SuppressLint("InlinedApi")
    private void requestInlinePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AlertsCreator.createDrawOverlayPermissionDialog(activity, (dialogInterface, i) -> {
                if (windowView != null) {
                    windowView.finish();
                }
            }).show();
        }
    }



    /**/

    private void checkVideoPreviewLayout () {
        float previewAppear = videoPreviewAppear.get();
        float left, top, right, bottom, radius;
        if (videoPreviewAppear.getValue()) {
            left = AnimationUtilities.fromTo(buttonsLayout.getButtonLeft(1), 0, previewAppear);
            top = AnimationUtilities.fromTo(buttonsLayout.getTop() + buttonsLayout.getButtonsTop(), 0, previewAppear);
            right = AnimationUtilities.fromTo(buttonsLayout.getButtonLeft(1) + AndroidUtilities.dp(60), fragmentView.getMeasuredWidth(), previewAppear);
            bottom = AnimationUtilities.fromTo(buttonsLayout.getTop() + buttonsLayout.getButtonsTop() + AndroidUtilities.dp(60), fragmentView.getMeasuredHeight(), previewAppear);
            radius = AnimationUtilities.fromTo(AndroidUtilities.dp(30), 0, previewAppear);
        } else {
            left = AnimationUtilities.fromTo(currentUserCameraFloatingLayout.getX(), 0, previewAppear);
            top = AnimationUtilities.fromTo(currentUserCameraFloatingLayout.getY(), 0, previewAppear);
            right = AnimationUtilities.fromTo(currentUserCameraFloatingLayout.getX() + currentUserCameraFloatingLayout.getWidth(), fragmentView.getMeasuredWidth(), previewAppear);
            bottom = AnimationUtilities.fromTo(currentUserCameraFloatingLayout.getY() + currentUserCameraFloatingLayout.getHeight(), fragmentView.getMeasuredHeight(), previewAppear);
            radius = AnimationUtilities.fromTo(AndroidUtilities.dp(4), 0, previewAppear);
        }

        videoPreviewClipRect.set(left, top, right, bottom);
        videoPreviewClipPath.reset();
        videoPreviewClipPath.addRoundRect(videoPreviewClipRect, radius, radius, Path.Direction.CW);
        videoPreviewClipPath.close();

        boolean isLandscape = fragmentView.getMeasuredWidth() > fragmentView.getMeasuredHeight();
        float bottomInset = (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT_WATCH) ? lastInsets.getSystemWindowInsetBottom(): 0;
        float padding = AndroidUtilities.dp(isLandscape ? 80: 16) * previewAppear;
        float left2 = AnimationUtilities.fromTo(buttonsLayout.getButtonLeft(1), padding, previewAppear);
        float right2 = AnimationUtilities.fromTo(buttonsLayout.getButtonLeft(1) + AndroidUtilities.dp(60), fragmentView.getMeasuredWidth() - padding, previewAppear);
        float top2 = AnimationUtilities.fromTo(
            buttonsLayout.getTop() + buttonsLayout.getButtonsTop(),
            fragmentView.getMeasuredHeight() - bottomInset - AndroidUtilities.dp(64 + 54),
            previewAppear);
        float bottom2 = AnimationUtilities.fromTo(
            buttonsLayout.getTop() + buttonsLayout.getButtonsTop() + AndroidUtilities.dp(60),
            fragmentView.getMeasuredHeight() - bottomInset - AndroidUtilities.dp(64),
            previewAppear);

        videoPreviewButtonRect.set(left2, top2, right2, bottom2);

        if (previewDialog != null) {
            previewDialog.setInterfaceVisibility(previewAppear, (!videoPreviewAppear.getValue() || previewAppear == 1f));
        }

        checkUiShadowsLayout();

        fragmentView.invalidate();
    }

    private void checkUiShadowsLayout () {
        float ratingValue = ratingVisible.get();
        float videoValue = Math.min(hasAnyVideo.get(), (1f - ratingValue));
        float uiValue = uiVisibility.get();
        float shadowsValue = Math.min(Math.min(uiValue, videoValue), 1f - videoPreviewAppear.get());

        bottomShadow.setAlpha(shadowsValue);
        topShadow.setAlpha(shadowsValue);
        overlayPaint.setColor(ColorUtils.setAlphaComponent(Color.BLACK, (int) (100f * shadowsValue)));
        overlayBottomPaint.setColor(ColorUtils.setAlphaComponent(Color.BLACK, (int) (100f * shadowsValue)));
    }

    private void checkAvatarLayout () {
        float buttonsPresentValue = callOutButtonsPresent.get();
        float ratingValue = ratingVisible.get();
        float emojiValue = Math.min(emojiExpand.get(), (1f - ratingValue));

        float avatarScale = Math.min(1f - emojiValue, 0.85f + 0.15f * Math.min(buttonsPresentValue / 0.3f, 1f)) * callGradientBackground.renderer.getAvatarScale();
        float avatarOffset = 0f
                + AndroidUtilities.dp(60 + VoIPFragment.STATUS_LAYOUT_EMOJI_EXPAND_OFFSET) * emojiValue
                - AndroidUtilities.dp(33) * ratingValue;

        callingUserAvatarView.setTranslationY(avatarOffset);
        callingUserAvatarView.setAlpha(1f - emojiValue);
        callingUserAvatarView.setScaleX(avatarScale);
        callingUserAvatarView.setScaleY(avatarScale);

        float s = callGradientBackground.renderer.getAcceptButtonWavesScale();
        buttonsLayout.setAcceptButtonWavesScale(s * 1.1f + (s % 1f) * 2f);
    }

    private void checkLayout () {
        float buttonsPresentValue = callOutButtonsPresent.get();
        float ratingValue = ratingVisible.get();
        float videoValue = Math.min(hasAnyVideo.get(), (1f - ratingValue));
        float emojiValue = Math.min(emojiExpand.get(), (1f - ratingValue));
        float acceptValue = Math.min(acceptVisible.get(), (1f - ratingValue));
        float uiValue = uiVisibility.get();

        checkUiShadowsLayout();
        checkAvatarLayout();

        callGradientBackground.setHasAnyVideoValue(videoValue);

        // Emoji

        float s1 = emojiLayout.emojiLayout.getMeasuredWidth();
        float s2 = fragmentView.getMeasuredWidth() - AndroidUtilities.dp(174);
        float emojiOffset = 0f
            - AndroidUtilities.dp(50) * (1f - uiValue);

        emojiLayout.updateLayout(videoValue);
        emojiLayout.onEmojiExpandAnimationUpdate(emojiValue);
        emojiLayout.setEmojiVisible(1f - ratingValue);
        emojiLayout.setTranslationY(emojiOffset);
        emojiLayout.setAlpha(uiValue);

        if (s1 > 0 && s2 > 0) {
            float scale = s2 / s1;
            float oh = AndroidUtilities.dp(30);
            float nh = oh * scale;
            float emojiOffset2 = 0f
                    + (AndroidUtilities.dp(127) + (nh - oh)) * emojiValue;

            emojiLayout.emojiLayout.setScaleX(AnimationUtilities.fromTo(1f, scale, emojiValue));
            emojiLayout.emojiLayout.setScaleY(AnimationUtilities.fromTo(1f, scale, emojiValue));
            emojiLayout.emojiLayout.setTranslationY(emojiOffset2);
        }

        // Status Layuot

        float statusLayoutVisibility = (1f - (emojiValue * videoValue)) * uiValue;
        float statusLayoutOffset = 0f
            - AndroidUtilities.dp(226) * videoValue
            - AndroidUtilities.dp(33) * ratingValue
            + AndroidUtilities.dp(STATUS_LAYOUT_EMOJI_EXPAND_OFFSET) * emojiValue
            - AndroidUtilities.dp(50) * (1f - uiValue);

        statusLayout.setTranslationY(statusLayoutOffset);
        statusLayout.setAlpha(statusLayoutVisibility);

        // Buttons

        buttonsLayout.updateLayout(acceptValue, ratingValue, buttonsPresentValue);
        buttonsLayout.setAlpha(uiValue);
        for (int i = 0; i < 3; i++) {
            bottomButtons[i].setHasAnyVideoValue(videoValue);
        }

        // Notifications layout

        float notificationsOffset = 0f
            - AndroidUtilities.dp(16)
            - AndroidUtilities.dp(96) * uiValue;

        notificationsLayout.updateLayout(videoValue);
        notificationsLayout.setTranslationY(notificationsOffset);
        notificationsLayout.setAlpha(Math.min(Math.max(uiValue, videoValue), (1f - ratingValue)));

        //

        rateView.updateLayout(ratingValue);
        statusTextView.updateLayout(ratingValue, videoValue);
        tapToVideoTooltip.updateLayout(videoValue);
        tapToEmojiTooltip.updateLayout(videoValue);
        videoUnavailableHint.updateLayout(videoValue);
        fragmentView.invalidate();

        callingUserTextureView.setAlpha(Math.min(callingUserTextureViewVisible.get(), 1f - ratingValue));
    }

    /**/

    private Runnable stopInterfaceUpdatesRunnable;

    private void scheduleStopInterfaceUpdates () {
        callGradientBackground.setStopWhenStableState(false);
        callingUserAvatarView.setLiteMode(false);

        if (stopInterfaceUpdatesRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(stopInterfaceUpdatesRunnable);
        }

        AndroidUtilities.runOnUIThread(stopInterfaceUpdatesRunnable = this::stopInterfaceUpdates, 8500);
    }

    private void stopInterfaceUpdates () {
        callGradientBackground.setStopWhenStableState(true);
        callingUserAvatarView.setLiteMode(true);
        stopInterfaceUpdatesRunnable = null;
    }

    /**/

    private void cancelHideUserInterface () {
        if (hideUIRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(hideUIRunnable);
            hideUIRunnable = null;
        }
    }

    private void scheduleHideUserInterface () {
        cancelHideUserInterface();
        AndroidUtilities.runOnUIThread(hideUIRunnable = this::hideUserInterface, 3000);
    }

    private void hideUserInterface () {
        if (canHideUI && uiVisible && !emojiExpand.getValue()) {
            lastContentTapTime = System.currentTimeMillis();
            showUi(false);
            previousState = currentState;
            updateViewState();
        }

        hideUIRunnable = null;
    }

    private Runnable hideUIRunnable;

    /**/

    private void expandEmoji(boolean expanded) {
        if (!emojiLayout.emojiLoaded || !uiVisibility.getValue()) {
            return;
        }

        emojiLayout.setExpanded(expanded);
        emojiExpand.set(expanded);
        if (expanded) {
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            preferences.edit().putBoolean("emoji_key_was_expanded", true).apply();
        } else {
            scheduleHideUserInterface();
        }
    }

    private void showEmojiKeyHint () {
        AndroidUtilities.runOnUIThread(() -> {
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            if (preferences.getBoolean("emoji_key_was_expanded", false)) return;
            tapToEmojiTooltip.showForView(emojiLayout.emojiLayout, true);
        }, 200);
    }

    /**/

    private Runnable finishRunnable;

    private void scheduleFinish (long delay) {
        if (finishRunnable != null) return;
        finishRunnable = () -> {
            finishRunnable = null;
            if (windowView != null) {
                windowView.finish();
            }
        };
        AndroidUtilities.runOnUIThread(finishRunnable, delay);
    }

    private void cancelFinish () {
        if (finishRunnable == null) return;
        AndroidUtilities.cancelRunOnUIThread(finishRunnable);
    }

    /**/

    private final VoIPButtonsLayout2.Listener buttonsListener = new VoIPButtonsLayout2.Listener() {
        @Override
        public void onAccept() {
            if (currentState == VoIPService.STATE_BUSY) {
                buttonsLayout.setRetryMod(false);
                Intent intent = new Intent(activity, VoIPService.class);
                intent.putExtra("user_id", callingUser.id);
                intent.putExtra("is_outgoing", true);
                intent.putExtra("start_incall_activity", false);
                intent.putExtra("video_call", isVideoCall);
                intent.putExtra("can_video_call", isVideoCall);
                intent.putExtra("account", currentAccount);
                try {
                    activity.startService(intent);
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            } else {
                int offset = 0;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT_WATCH) {
                    offset = lastInsets.getSystemWindowInsetBottom();
                }
                callGradientBackground.renderer.showConnected(buttonsLayout.getAcceptButtonCenterX(), fragmentView.getMeasuredHeight() - AndroidUtilities.dp(186) - offset + buttonsLayout.getAcceptButtonCenterY());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 101);
                } else {
                    if (VoIPService.getSharedInstance() != null) {
                        VoIPService.getSharedInstance().acceptIncomingCall();
                        if (currentUserIsVideo) {
                            VoIPService.getSharedInstance().requestVideoCall(false);
                        }
                    }
                }
            }
        }

        @Override
        public void onDecline() {
            if (privateCallToRate != null) {
                int rating = rateView.getRating();
                if (rating > 0) {
                    final TLRPC.TL_phone_setCallRating req = new TLRPC.TL_phone_setCallRating();
                        req.rating = rating;
                        req.comment = "";

                        req.peer = new TLRPC.TL_inputPhoneCall();
                        req.peer.access_hash = privateCallToRate.access_hash;
                        req.peer.id = privateCallToRate.id;
                        req.user_initiative = false;

                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                            if (response instanceof TLRPC.TL_updates) {
                                TLRPC.TL_updates updates = (TLRPC.TL_updates) response;
                                MessagesController.getInstance(currentAccount).processUpdates(updates, false);
                            }
                        });
                }
                windowView.finish();
                return;
            }
            if (VoIPService.getSharedInstance() == null || currentState == VoIPService.STATE_BUSY) {
                windowView.finish();
                return;
            }
            if (currentState == VoIPService.STATE_WAITING_INCOMING) {
                VoIPService.getSharedInstance().declineIncomingCall();
            } else {
                VoIPService.getSharedInstance().hangUp();
            }
        }
    };

    /* Bluetooth notifications */

    private boolean currentBluetoothConnected;
    private boolean currentHeadsetConnected;
    private Runnable notificationsClearRunnable;

    private void checkHeadsetConnected () {
        VoIPService service = VoIPService.getSharedInstance();
        if (service == null) {
            return;
        }

        boolean newHeadsetConnected = service.isHeadsetPlugged();
        boolean newBluetoothConnected = service.isBluetoothHeadsetConnected();
        if (currentHeadsetConnected == newHeadsetConnected && currentBluetoothConnected == newBluetoothConnected) return;

        if (notificationsClearRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(notificationsClearRunnable);
        }

        if (currentHeadsetConnected != newHeadsetConnected) {
            if (newHeadsetConnected) {
                notificationsLayout.addNotification(0, LocaleController.getString(R.string.VoipHeadsetConnected), "headset-connected", true);
                notificationsLayout.removeNotification("headset-disconnected");
            } else {
                notificationsLayout.addNotification(0, LocaleController.getString(R.string.VoipHeadsetDisconnected), "headset-disconnected", true);
                notificationsLayout.removeNotification("headset-connected");
            }
        }

        if (currentBluetoothConnected != newBluetoothConnected) {
            if (newBluetoothConnected) {
                notificationsLayout.addNotification(0, LocaleController.getString(R.string.VoipBtDeviceConnected), "bt-connected", true);
                notificationsLayout.removeNotification("bt-disconnected");
            } else {
                notificationsLayout.addNotification(0, LocaleController.getString(R.string.VoipBtDeviceDisconnected), "bt-disconnected", true);
                notificationsLayout.removeNotification("bt-connected");
            }
        }

        AndroidUtilities.runOnUIThread(notificationsClearRunnable = this::clearNotifications, 4000);
        currentHeadsetConnected = newHeadsetConnected;
        currentBluetoothConnected = newBluetoothConnected;
    }

    private void clearNotifications() {
        if (notificationsLayout != null) {
            notificationsLayout.removeNotification("headset-disconnected");
            notificationsLayout.removeNotification("headset-connected");
            notificationsLayout.removeNotification("bt-disconnected");
            notificationsLayout.removeNotification("bt-connected");
        }
        notificationsClearRunnable = null;
    }

    /**/

    public static class BooleanAnimation {
        private final Runnable callback;
        private ValueAnimator animator;
        private float animatedValue = 0f;
        private boolean value = false;
        private long setDuration, unsetDuration, startDelay;
        private TimeInterpolator setInterpolator = CubicBezierInterpolator.DEFAULT;


        public BooleanAnimation (Runnable callback) {
            this(callback, 280, 220);
        }

        public BooleanAnimation (Runnable callback, long setDuration, long unsetDuration) {
            this.callback = callback;
            this.setDuration = setDuration;
            this.unsetDuration = unsetDuration;
            this.startDelay = 0;
        }

        public BooleanAnimation setStartDelay(long startDelay) {
            this.startDelay = startDelay;
            return this;
        }

        public BooleanAnimation setSetInterpolator(TimeInterpolator setInterpolator) {
            this.setInterpolator = setInterpolator;
            return this;
        }

        public void set (boolean value) {
            set(value, true);
        }

        public void set (boolean value, boolean animated) {
            set(value, animated, null);
        }

        public void set (boolean value, boolean animated, AnimatorListenerAdapter listener) {
            if (this.value == value) return;

            if (animator != null) {
                animator.cancel();
                animator = null;
            }

            if (animated) {
                if (value) {
                    animator = ValueAnimator.ofFloat(animatedValue, 1);
                    animator.setInterpolator(setInterpolator);
                    animator.setDuration(setDuration);
                } else {
                    animator = ValueAnimator.ofFloat(animatedValue, 0);
                    animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                    animator.setDuration(unsetDuration);
                }
                if (startDelay > 0) {
                    animator.setStartDelay(startDelay);
                }
                if (listener != null) {
                    animator.addListener(listener);
                }
                animator.addUpdateListener(this::onUpdate);
                animator.start();
            } else {
                animatedValue = value ? 1f: 0;
                callback.run();
            }
            this.value = value;
        }



        public float get() {
            return animatedValue;
        }

        public boolean getValue () {
            return value;
        }

        private void onUpdate (ValueAnimator valueAnimator) {
            animatedValue = (float) valueAnimator.getAnimatedValue();
            callback.run();
        }
    }
}
