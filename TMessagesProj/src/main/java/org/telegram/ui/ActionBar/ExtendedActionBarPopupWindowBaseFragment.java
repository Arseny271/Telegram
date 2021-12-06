/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.ActionBar;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.PopupWindow;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocationController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.SecretChatHelper;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public abstract class ExtendedActionBarPopupWindowBaseFragment {

    private boolean isFinished;
    private boolean finishing;
    protected int currentAccount = UserConfig.selectedAccount;

    protected View fragmentView;
    protected ExtendedActionBarPopupWindowFragmentsLayout parentLayout;
    protected int classGuid;
    protected Bundle arguments;
    protected boolean hasOwnBackground = false;
    protected boolean isPaused = true;
    protected boolean inTransitionAnimation = false;
    protected boolean fragmentBeginToShow;

    public ExtendedActionBarPopupWindowBaseFragment() {
        classGuid = ConnectionsManager.generateClassGuid();
    }

    public ExtendedActionBarPopupWindowBaseFragment(Bundle args) {
        arguments = args;
        classGuid = ConnectionsManager.generateClassGuid();
    }

    public ExtendedActionBarPopupWindowBaseFragment(View view) {
        fragmentView = view;
        classGuid = ConnectionsManager.generateClassGuid();
    }

    public ExtendedActionBarPopupWindowBaseFragment(Bundle args, View view) {
        fragmentView = view;
        arguments = args;
        classGuid = ConnectionsManager.generateClassGuid();
    }

    public void setCurrentAccount(int account) {
        if (fragmentView != null) {
            throw new IllegalStateException("trying to set current account when fragment UI already created");
        }
        currentAccount = account;
    }

    public View getFragmentView() {
        return fragmentView;
    }

    public View createView(Context context) {
        if (fragmentView != null) {
            return fragmentView;
        }

        return null;
    }

    public Bundle getArguments() {
        return arguments;
    }

    public int getCurrentAccount() {
        return currentAccount;
    }

    public int getClassGuid() {
        return classGuid;
    }

    public boolean isSwipeBackEnabled(MotionEvent event) {
        return true;
    }

    protected void onPreviewOpenAnimationEnd() {
    }

    protected boolean hideKeyboardOnShow() {
        return false;
    }

    protected void clearViews() {
        if (fragmentView != null) {
            ViewGroup parent = (ViewGroup) fragmentView.getParent();
            if (parent != null) {
                try {
                    onRemoveFromParent();
                    parent.removeViewInLayout(fragmentView);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            fragmentView = null;
        }
        parentLayout = null;
    }

    protected void onRemoveFromParent() {

    }

    protected BaseFragment parentBaseFragment;

    public void setParentBaseFragment(BaseFragment fragment) {
        parentBaseFragment = fragment;
    }

    protected PopupWindow parentPopupWindow;

    public void setParentPopupWindow(PopupWindow fragment) {
        parentPopupWindow = fragment;
    }

    public void setParentFragment(ExtendedActionBarPopupWindowBaseFragment fragment) {
        setParentLayout(fragment.parentLayout);
        fragmentView = createView(parentLayout.getContext());
    }

    protected void setParentLayout(ExtendedActionBarPopupWindowFragmentsLayout layout) {
        if (parentLayout != layout) {
            parentLayout = layout;
            if (fragmentView != null) {
                ViewGroup parent = (ViewGroup) fragmentView.getParent();
                if (parent != null) {
                    try {
                        onRemoveFromParent();
                        parent.removeViewInLayout(fragmentView);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                if (parentLayout != null && parentLayout.getContext() != fragmentView.getContext()) {
                    fragmentView = null;
                }
            }
        }
    }

    public void finishFragment() {
        finishFragment(true);
    }

    public void finishFragment(boolean animated) {
        if (isFinished || parentLayout == null) {
            return;
        }
        finishing = true;
        parentLayout.closeLastFragment(animated);
    }

    public void removeSelfFromStack() {
        if (isFinished || parentLayout == null) {
            return;
        }
        parentLayout.removeFragmentFromStack(this);
    }

    protected boolean isFinishing() {
        return finishing;
    }

    public boolean onFragmentCreate() {
        return true;
    }

    public void onFragmentDestroy() {
        getConnectionsManager().cancelRequestsForGuid(classGuid);
        getMessagesStorage().cancelTasksForGuid(classGuid);
        isFinished = true;
    }

    public boolean needDelayOpenAnimation() {
        return false;
    }

    protected void resumeDelayedFragmentAnimation() {
        if (parentLayout != null) {
            parentLayout.resumeDelayedFragmentAnimation();
        }
    }

    public void onResume() {
        isPaused = false;
    }

    public void onPause() {
        isPaused = true;
    }

    public ExtendedActionBarPopupWindowBaseFragment getFragmentForAlert(int offset) {
        if (parentLayout == null || parentLayout.fragmentsStack.size() <= 1 + offset) {
            return this;
        }
        return parentLayout.fragmentsStack.get(parentLayout.fragmentsStack.size() - 2 - offset);
    }

    public void onConfigurationChanged(android.content.res.Configuration newConfig) {

    }

    public boolean onBackPressed() {
        return true;
    }

    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {

    }

    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {

    }

    public void saveSelfArgs(Bundle args) {

    }

    public void restoreSelfArgs(Bundle args) {

    }

    public boolean isLastFragment() {
        return parentLayout != null && !parentLayout.fragmentsStack.isEmpty() && parentLayout.fragmentsStack.get(parentLayout.fragmentsStack.size() - 1) == this;
    }

    public ExtendedActionBarPopupWindowFragmentsLayout getParentLayout() {
        return parentLayout;
    }

    public FrameLayout getLayoutContainer() {
        if (fragmentView != null) {
            final ViewParent parent = fragmentView.getParent();
            if (parent instanceof FrameLayout) {
                return (FrameLayout) parent;
            }
        }
        return null;
    }

    public boolean presentFragment(ExtendedActionBarPopupWindowBaseFragment fragment) {
        return parentLayout != null && parentLayout.presentFragment(fragment);
    }

    public boolean presentFragment(ExtendedActionBarPopupWindowBaseFragment fragment, boolean removeLast) {
        return parentLayout != null && parentLayout.presentFragment(fragment, removeLast);
    }

    public boolean presentFragment(ExtendedActionBarPopupWindowBaseFragment fragment, boolean removeLast, boolean forceWithoutAnimation) {
        return parentLayout != null && parentLayout.presentFragment(fragment, removeLast, forceWithoutAnimation, true);
    }

    public Activity getParentActivity() {
        if (parentLayout != null) {
            return parentLayout.parentActivity;
        }
        return null;
    }

    protected void setParentActivityTitle(CharSequence title) {
        Activity activity = getParentActivity();
        if (activity != null) {
            activity.setTitle(title);
        }
    }

    public void startActivityForResult(final Intent intent, final int requestCode) {
        if (parentLayout != null) {
            parentLayout.startActivityForResult(intent, requestCode);
        }
    }

    public boolean canBeginSlide() {
        return true;
    }

    public void onBeginSlide() {

    }

    protected void onSlideProgress(boolean isOpen, float progress) {

    }

    protected void onTransitionAnimationProgress(boolean isOpen, float progress) {

    }

    protected void onTransitionAnimationStart(boolean isOpen, boolean backward) {
        inTransitionAnimation = true;
        if (isOpen) {
            fragmentBeginToShow = true;
        }
    }

    protected void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        inTransitionAnimation = false;
    }

    protected AnimatorSet onCustomTransitionAnimation(boolean isOpen, final Runnable callback) {
        return null;
    }

    public ArrayList<ThemeDescription> getThemeDescriptions() {
        return new ArrayList<>();
    }

    public AccountInstance getAccountInstance() {
        return AccountInstance.getInstance(currentAccount);
    }

    public MessagesController getMessagesController() {
        return getAccountInstance().getMessagesController();
    }

    protected ContactsController getContactsController() {
        return getAccountInstance().getContactsController();
    }

    public MediaDataController getMediaDataController() {
        return getAccountInstance().getMediaDataController();
    }

    public ConnectionsManager getConnectionsManager() {
        return getAccountInstance().getConnectionsManager();
    }

    public LocationController getLocationController() {
        return getAccountInstance().getLocationController();
    }

    protected NotificationsController getNotificationsController() {
        return getAccountInstance().getNotificationsController();
    }

    public MessagesStorage getMessagesStorage() {
        return getAccountInstance().getMessagesStorage();
    }

    public SendMessagesHelper getSendMessagesHelper() {
        return getAccountInstance().getSendMessagesHelper();
    }

    public FileLoader getFileLoader() {
        return getAccountInstance().getFileLoader();
    }

    protected SecretChatHelper getSecretChatHelper() {
        return getAccountInstance().getSecretChatHelper();
    }

    protected DownloadController getDownloadController() {
        return getAccountInstance().getDownloadController();
    }

    protected SharedPreferences getNotificationsSettings() {
        return getAccountInstance().getNotificationsSettings();
    }

    public NotificationCenter getNotificationCenter() {
        return getAccountInstance().getNotificationCenter();
    }

    public MediaController getMediaController() {
        return MediaController.getInstance();
    }

    public UserConfig getUserConfig() {
        return getAccountInstance().getUserConfig();
    }

    public void saveKeyboardPositionBeforeTransition() {

    }

    protected Animator getCustomSlideTransition(boolean topFragment, boolean backAnimation, float distanceToMove) {
        return null;
    }

    protected void prepareFragmentToSlide(boolean topFragment, boolean beginSlide) {

    }

    public void setProgressToDrawerOpened(float v) {

    }

    public int getThemedColor(String key) {
        return Theme.getColor(key);
    }

    public Drawable getThemedDrawable(String key) {
        return Theme.getThemeDrawable(key);
    }

    public boolean isBeginToShow() {
        return fragmentBeginToShow;
    }

    public Theme.ResourcesProvider getResourceProvider() {
        return null;
    }
}
