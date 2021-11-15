package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextPaint;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimationProperties;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SharedMediaLayout;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Calendar;

public class MediaCalendarActivity extends BaseFragment {

    public static final int CALENDAR_TYPE_DEFAULT = 0;
    public static final int CALENDAR_TYPE_CLEAR_HISTORY = 1;
    private int type = CALENDAR_TYPE_DEFAULT;

    FrameLayout contentView;
    FrameLayout bottomView;
    TextView bottomTextView;
    private View blurredView;

    RecyclerListView listView;
    LinearLayoutManager layoutManager;
    TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    TextPaint activeTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    TextPaint textPaint2 = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    Paint blackoutPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint selectedBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Paint selectedBackgroundPaint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    TLRPC.User user;
    private long dialogId;
    private boolean loading;
    private boolean checkEnterItems;
    private boolean editEnable = false;

    private ObjectAnimator checkAnimator;
    float progress = 1f;

    private int selectIndex = 0;
    private long selectedDates[] = new long[2];

    int startFromYear;
    int startFromMonth;
    int monthCount;

    ChatActivity historyFragmentPreview;
    private boolean fragmentDayPreviewActive = false;
    private AnimatorSet fragmentDayPreviewAnimator;
    private AnimatorSet scrimAnimatorSet;
    int longSelectedDate = 0;

    CalendarAdapter adapter;
    Callback callback;

    private ActionBarPopupWindow scrimPopupWindow;

    SparseArray<SparseArray<PeriodDay>> messagesByYearMounth = new SparseArray<>();
    boolean endReached;
    int startOffset = 0;
    int lastId;
    int minMontYear;
    private int photosVideosTypeFilter;
    private boolean isOpened;
    int selectedYear;
    int selectedMonth;

    public MediaCalendarActivity(Bundle args, int photosVideosTypeFilter, int selectedDate) {
        this(args, photosVideosTypeFilter, selectedDate, CALENDAR_TYPE_DEFAULT);
    }

    public MediaCalendarActivity(Bundle args, int photosVideosTypeFilter, int selectedDate, int calendarType) {
        super(args);
        this.photosVideosTypeFilter = photosVideosTypeFilter;
        this.type = calendarType;

        selectedDates[0] = 0;
        selectedDates[1] = 0;

        if (selectedDate != 0) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(selectedDate * 1000L);
            selectedYear = calendar.get(Calendar.YEAR);
            selectedMonth = calendar.get(Calendar.MONTH);
        }
    }

    public void setProgress(float value) {
        if (progress == value) {
            return;
        }
        progress = value;

        if (listView != null) {
            listView.invalidateViews();
        }
    }

    @Override
    public boolean onFragmentCreate() {
        dialogId = getArguments().getLong("dialog_id");
        user = getMessagesController().getUser(dialogId);

        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        textPaint.setTextSize(AndroidUtilities.dp(16));
        textPaint.setTextAlign(Paint.Align.CENTER);

        textPaint2.setTextSize(AndroidUtilities.dp(11));
        textPaint2.setTextAlign(Paint.Align.CENTER);
        textPaint2.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

        activeTextPaint.setTextSize(AndroidUtilities.dp(16));
        activeTextPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        activeTextPaint.setTextAlign(Paint.Align.CENTER);

        selectedBackgroundPaint2.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        selectedBackgroundPaint.setColor(getThemedColor(Theme.key_dialogRoundCheckBox));
        selectedBackgroundPaint.setAlpha((int)(255 * 0.2f));

        contentView = new FrameLayout(context) {
/*
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

                    measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                }
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                final int count = getChildCount();
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
                            childTop = (b - t - height) / 2 + lp.topMargin - lp.bottomMargin;
                            break;
                        case Gravity.BOTTOM:
                            childTop = (b - t) - height - lp.bottomMargin;
                            break;
                        default:
                            childTop = lp.topMargin;
                    }

                    child.layout(childLeft, childTop, childLeft + width, childTop + height);
                }
            }
            */
            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                if (blurredView != null && blurredView.getVisibility() == View.VISIBLE) {
                    if (blurredView.getAlpha() != 1f) {
                        if (blurredView.getAlpha() != 0) {
                            canvas.saveLayerAlpha(blurredView.getLeft(), blurredView.getTop(), blurredView.getRight(), blurredView.getBottom(), (int) (255 * blurredView.getAlpha()), Canvas.ALL_SAVE_FLAG);
                            canvas.translate(blurredView.getLeft(), blurredView.getTop());
                            blurredView.draw(canvas);
                            canvas.restore();
                        }
                    } else {
                        blurredView.draw(canvas);
                    }
                }
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (child == blurredView) {
                    return true;
                }

                return super.drawChild(canvas, child, drawingTime);
            }
        };
        createActionBar(context);
        contentView.addView(actionBar);
        actionBar.setTitle(LocaleController.getString("Calendar", R.string.Calendar));
        actionBar.setCastShadows(false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (editEnable) {
                        editEnable = false;
                        updateBottomView();
                    } else {
                        finishFragment();
                    }
                }
            }
        });

        listView = new RecyclerListView(context) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                checkEnterItems = false;
            }
        };
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context));
        layoutManager.setReverseLayout(true);
        listView.setAdapter(adapter = new CalendarAdapter());
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                checkLoadNext();
            }
        });

        listView.setOnItemLongClickListener(new RecyclerListView.OnItemLongClickListenerExtended() {
            @Override
            public boolean onItemClick(View view, int position, float x, float y) {
                return onItemLongClickMonth(view, position, x, y);
            }

            @Override
            public void onMove(float dx, float dy) {
                if (historyFragmentPreview != null && !fragmentDayPreviewActive) {
                    if (dy < AndroidUtilities.dp(60)) {
                        movePreviewFragment(dy);
                    } else {
                        int statusBarHeight = (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
                        int fullHeight = getParentLayout().getMeasuredHeight();// - statusBarHeight;
                        int fragmentHeight = historyFragmentPreview.getPreviewHeight();
                        float translation = (fullHeight - fragmentHeight) / 2f; // + AndroidUtilities.getStatusBarHeight(getParentActivity()) - AndroidUtilities.dp(8);

                        if (!fragmentDayPreviewActive) {
                            ObjectAnimator animator = movePreviewFragmentAnimated(translation);
                            ObjectAnimator animator2 = animatePreviewFragmentBackground(0);

                            AnimatorSet animatorSet = new AnimatorSet();
                            animatorSet.playTogether(animator, animator2);

                            animatorSet.setDuration(180);
                            animatorSet.setInterpolator(CubicBezierInterpolator.DEFAULT);
                            animatorSet.addListener(new Animator.AnimatorListener() {
                                @Override
                                public void onAnimationStart(Animator animation) {

                                }

                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    if (fragmentDayPreviewActive && historyFragmentPreview != null) {
                                        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) historyFragmentPreview.getFragmentView().getLayoutParams();
                                        showHistoryOptionsMenu(0, (int) ( layoutParams.topMargin + layoutParams.height - translation ));
                                        setPreviewBackgroundAlpha(0f);
                                    }
                                }

                                @Override
                                public void onAnimationCancel(Animator animation) {

                                }

                                @Override
                                public void onAnimationRepeat(Animator animation) {

                                }
                            });

                            animatorSet.start();
                            fragmentDayPreviewActive = true;
                            fragmentDayPreviewAnimator = animatorSet;
                        }
                    }
                }
            }

            @Override
            public void onLongClickRelease() {
                if (!fragmentDayPreviewActive) {
                    finishPreviewFragment();
                }
            }
        });

        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 0, 36, 0, (type == CALENDAR_TYPE_CLEAR_HISTORY && user != null)?50:0));

        final String[] daysOfWeek = new String[]{
                LocaleController.getString("CalendarWeekNameShortMonday", R.string.CalendarWeekNameShortMonday),
                LocaleController.getString("CalendarWeekNameShortTuesday", R.string.CalendarWeekNameShortTuesday),
                LocaleController.getString("CalendarWeekNameShortWednesday", R.string.CalendarWeekNameShortWednesday),
                LocaleController.getString("CalendarWeekNameShortThursday", R.string.CalendarWeekNameShortThursday),
                LocaleController.getString("CalendarWeekNameShortFriday", R.string.CalendarWeekNameShortFriday),
                LocaleController.getString("CalendarWeekNameShortSaturday", R.string.CalendarWeekNameShortSaturday),
                LocaleController.getString("CalendarWeekNameShortSunday", R.string.CalendarWeekNameShortSunday),
        };

        Drawable headerShadowDrawable = ContextCompat.getDrawable(context, R.drawable.header_shadow).mutate();

        View calendarSignatureView = new View(context) {

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float xStep = getMeasuredWidth() / 7f;
                for (int i = 0; i < 7; i++) {
                    float cx = xStep * i + xStep / 2f;
                    float cy = (getMeasuredHeight() - AndroidUtilities.dp(2)) / 2f;
                    canvas.drawText(daysOfWeek[i], cx, cy + AndroidUtilities.dp(5), textPaint2);
                }
                headerShadowDrawable.setBounds(0, getMeasuredHeight() - AndroidUtilities.dp(3), getMeasuredWidth(), getMeasuredHeight());
                headerShadowDrawable.draw(canvas);
            }
        };

        contentView.addView(calendarSignatureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, 0, 0, 0, 0, 0));

        fragmentView = contentView;

        Calendar calendar = Calendar.getInstance();
        startFromYear = calendar.get(Calendar.YEAR);
        startFromMonth = calendar.get(Calendar.MONTH);

        if (selectedYear != 0) {
            monthCount = (startFromYear - selectedYear) * 12 + startFromMonth - selectedMonth + 1;
            layoutManager.scrollToPositionWithOffset(monthCount - 1, AndroidUtilities.dp(120));
        }
        if (monthCount < 3) {
            monthCount = 3;
        }

        if (type == CALENDAR_TYPE_CLEAR_HISTORY && user != null) {
            bottomView = new FrameLayout(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    int allWidth = MeasureSpec.getSize(widthMeasureSpec);
                    FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) bottomTextView.getLayoutParams();
                    layoutParams.width = allWidth;
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                }

                @Override
                public void onDraw(Canvas canvas) {
                    int bottom = Theme.chat_composeShadowDrawable.getIntrinsicHeight();
                    Theme.chat_composeShadowDrawable.setBounds(0, 0, getMeasuredWidth(), bottom);
                    Theme.chat_composeShadowDrawable.draw(canvas);
                    canvas.drawRect(0, bottom, getMeasuredWidth(), getMeasuredHeight(), Theme.getThemePaint(Theme.key_paint_chatComposeBackground));
                }
            };
            bottomView.setWillNotDraw(false);
            bottomView.setPadding(0, AndroidUtilities.dp(1.5f), 0, 0);
            bottomView.setClipChildren(false);
            contentView.addView(bottomView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 51, Gravity.BOTTOM));

            bottomTextView = new TextView(context);
            bottomTextView.setOnClickListener(view -> {
                onBottomButtonClick();
            });

            bottomTextView.setTextColor(getThemedColor(Theme.key_dialogTextBlue2));
            bottomTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            bottomTextView.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
            bottomTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            bottomTextView.setGravity(Gravity.CENTER);

            bottomTextView.setBackgroundDrawable(Theme.createSelectorWithBackgroundDrawable(getThemedColor(Theme.key_dialogBackground), getThemedColor(Theme.key_listSelector)));
            bottomView.addView(bottomTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 0, 0, 0, 0));
        }

        blurredView = new View(context) {
            @Override
            public void setAlpha(float alpha) {
                super.setAlpha(alpha);
                if (fragmentView != null) {
                    fragmentView.invalidate();
                }
            }
        };
        blurredView.setVisibility(View.GONE);
        contentView.addView(blurredView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, 0, 0, 0));

        loadNext();
        updateColors();
        updateBottomView();
        activeTextPaint.setColor(Color.WHITE);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        return fragmentView;
    }

    @Override
    protected void onTransitionAnimationProgress(boolean isOpen, float progress) {
        if (blurredView != null && blurredView.getVisibility() == View.VISIBLE) {
            if (isOpen) {
                blurredView.setAlpha(1.0f - progress);
            } else {
                blurredView.setAlpha(progress);
            }
        }
    }

    @Override
    protected void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen && blurredView != null && blurredView.getVisibility() == View.VISIBLE) {
            blurredView.setVisibility(View.GONE);
            blurredView.setBackground(null);
        }
    }

    private void animateRange() {
        checkAnimator = ObjectAnimator.ofFloat( MediaCalendarActivity.this, "progress", 0, 1);
        checkAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animation.equals(checkAnimator)) {
                    checkAnimator = null;
                }
            }
        });
        checkAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
        checkAnimator.setDuration(250);
        checkAnimator.start();
    }

    private void onBottomButtonClick() {
        if (editEnable && selectedDates[0] != 0 || selectedDates[1] != 0) {
            AlertsCreator.createClearHistoryRangeAlert(this, user, (int)selectedDates[0], (int)selectedDates[1] + 86399, this::onHistoryCleared, null);
        } else if (!editEnable) {
            editEnable = true;
            updateBottomView();
        }
    }

    private void updateBottomView() {
        if (editEnable) {
            actionBar.setBackButtonImage(R.drawable.miniplayer_close);
            if (selectedDates[0] != 0 || selectedDates[1] != 0) {
                actionBar.setTitle(LocaleController.formatPluralString("Days", (int)((selectedDates[1] - selectedDates[0]) / 86400) + 1));
                if (bottomTextView != null) {
                    bottomTextView.setTextColor(getThemedColor(Theme.key_dialogTextRed2));
                }
            } else {
                actionBar.setTitle("Select days");
                if (bottomTextView != null) {
                    bottomTextView.setTextColor(getThemedColor(Theme.key_dialogTextRed2) & 0x80FFFFFF);
                }
            }

            if (bottomTextView != null) {
                bottomTextView.setText("CLEAR HISTORY");
            }

        } else {
            actionBar.setTitle(LocaleController.getString("Calendar", R.string.Calendar));
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            if (bottomTextView != null) {
                bottomTextView.setTextColor(getThemedColor(Theme.key_dialogTextBlue2));
                bottomTextView.setText("SELECT DAYS");
            }
            selectedDates[0] = 0;
            selectedDates[1] = 0;
            listView.invalidateViews();
        }

        animateRange();
    }

    private void updateColors() {
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        activeTextPaint.setColor(Color.WHITE);
        textPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textPaint2.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setTitleColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_listSelector), false);
    }

    private void loadNext() {
        if (loading || endReached) {
            return;
        }
        loading = true;
        TLRPC.TL_messages_getSearchResultsCalendar req = new TLRPC.TL_messages_getSearchResultsCalendar();
        if (photosVideosTypeFilter == SharedMediaLayout.FILTER_PHOTOS_ONLY) {
            req.filter = new TLRPC.TL_inputMessagesFilterPhotos();
        } else if (photosVideosTypeFilter == SharedMediaLayout.FILTER_VIDEOS_ONLY) {
            req.filter = new TLRPC.TL_inputMessagesFilterVideo();
        } else {
            req.filter = new TLRPC.TL_inputMessagesFilterPhotoVideo();
        }

        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
        req.offset_id = lastId;

        Calendar calendar = Calendar.getInstance();
        listView.setItemAnimator(null);
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                TLRPC.TL_messages_searchResultsCalendar res = (TLRPC.TL_messages_searchResultsCalendar) response;

                for (int i = 0; i < res.periods.size(); i++) {
                    TLRPC.TL_searchResultsCalendarPeriod period = res.periods.get(i);
                    calendar.setTimeInMillis(period.date * 1000L);
                    int month = calendar.get(Calendar.YEAR) * 100 + calendar.get(Calendar.MONTH);
                    SparseArray<PeriodDay> messagesByDays = messagesByYearMounth.get(month);
                    if (messagesByDays == null) {
                        messagesByDays = new SparseArray<>();
                        messagesByYearMounth.put(month, messagesByDays);
                    }
                    PeriodDay periodDay = new PeriodDay();
                    MessageObject messageObject = new MessageObject(currentAccount, res.messages.get(i), false, false);
                    periodDay.messageObject = messageObject;
                    startOffset += res.periods.get(i).count;
                    periodDay.startOffset = startOffset;
                    int index = calendar.get(Calendar.DAY_OF_MONTH) - 1;
                    if (messagesByDays.get(index, null) == null) {
                        messagesByDays.put(index, periodDay);
                    }
                    if (month < minMontYear || minMontYear == 0) {
                        minMontYear = month;
                    }

                }

                loading = false;
                if (!res.messages.isEmpty()) {
                    lastId = res.messages.get(res.messages.size() - 1).id;
                    endReached = false;
                    checkLoadNext();
                } else {
                    endReached = true;
                }
                if (isOpened) {
                    checkEnterItems = true;
                }
                listView.invalidate();
                int newMonthCount = (int) (((calendar.getTimeInMillis() / 1000) - res.min_date) / 2629800) + 1;
                adapter.notifyItemRangeChanged(0, monthCount);
                if (newMonthCount > monthCount) {
                    adapter.notifyItemRangeInserted(monthCount + 1, newMonthCount);
                    monthCount = newMonthCount;
                }
                if (endReached) {
                    resumeDelayedFragmentAnimation();
                }
            }
        }));
    }

    private void checkLoadNext() {
        if (loading || endReached) {
            return;
        }
        int listMinMonth = Integer.MAX_VALUE;
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            if (child instanceof MonthView) {
                int currentMonth = ((MonthView) child).currentYear * 100 + ((MonthView) child).currentMonthInYear;
                if (currentMonth < listMinMonth) {
                    listMinMonth = currentMonth;
                }
            }
        };
        int min1 = (minMontYear / 100 * 12) + minMontYear % 100;
        int min2 = (listMinMonth / 100 * 12) + listMinMonth % 100;
        if (min1 + 3 >= min2) {
            loadNext();
        }
    }

    private class CalendarAdapter extends RecyclerView.Adapter {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new MonthView(parent.getContext()));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            MonthView monthView = (MonthView) holder.itemView;

            int year = startFromYear - position / 12;
            int month = startFromMonth - position % 12;
            if (month < 0) {
                month += 12;
                year--;
            }
            boolean animated = monthView.currentYear == year && monthView.currentMonthInYear == month;
            monthView.setDate(year, month, messagesByYearMounth.get(year * 100 + month), animated);
        }

        @Override
        public long getItemId(int position) {
            int year = startFromYear - position / 12;
            int month = startFromMonth - position % 12;
            return year * 100L + month;
        }

        @Override
        public int getItemCount() {
            return monthCount;
        }
    }

    private class MonthView extends FrameLayout {

        SimpleTextView titleView;
        int currentYear;
        int currentMonthInYear;
        int daysInMonth;
        int startDayOfWeek;
        int cellCount;
        int startMonthTime;
        float[] ranged;
        float[] selected;
        float[] rangedR;

        SparseArray<PeriodDay> messagesByDays = new SparseArray<>();
        SparseArray<ImageReceiver> imagesByDays = new SparseArray<>();

        SparseArray<PeriodDay> animatedFromMessagesByDays = new SparseArray<>();
        SparseArray<ImageReceiver> animatedFromImagesByDays = new SparseArray<>();

        boolean attached;

        public MonthView(Context context) {
            super(context);
            setWillNotDraw(false);
            titleView = new SimpleTextView(context);
            titleView.setTextSize(15);
            titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            titleView.setGravity(Gravity.CENTER);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 28, 0, 0, 12, 0, 4));
        }

        public void setDate(int year, int monthInYear, SparseArray<PeriodDay> messagesByDays, boolean animated) {
            boolean dateChanged = year != currentYear && monthInYear != currentMonthInYear;
            currentYear = year;
            currentMonthInYear = monthInYear;
            this.messagesByDays = messagesByDays;

            if (dateChanged) {
                if (imagesByDays != null) {
                    for (int i = 0; i < imagesByDays.size(); i++) {
                        imagesByDays.valueAt(i).onDetachedFromWindow();
                        imagesByDays.valueAt(i).setParentView(null);
                    }
                    imagesByDays = null;
                }
            }
            if (messagesByDays != null) {
                if (imagesByDays == null) {
                    imagesByDays = new SparseArray<>();
                }

                for (int i = 0; i < messagesByDays.size(); i++) {
                    int key = messagesByDays.keyAt(i);
                    if (imagesByDays.get(key, null) != null) {
                        continue;
                    }
                    ImageReceiver receiver = new ImageReceiver();
                    receiver.setParentView(this);
                    PeriodDay periodDay = messagesByDays.get(key);
                    MessageObject messageObject = periodDay.messageObject;
                    if (messageObject != null) {
                        if (messageObject.isVideo()) {
                            TLRPC.Document document = messageObject.getDocument();
                            TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 50);
                            TLRPC.PhotoSize qualityThumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 320);
                            if (thumb == qualityThumb) {
                                qualityThumb = null;
                            }
                            if (thumb != null) {
                                if (messageObject.strippedThumb != null) {
                                    receiver.setImage(ImageLocation.getForDocument(qualityThumb, document), "44_44", messageObject.strippedThumb, null, messageObject, 0);
                                } else {
                                    receiver.setImage(ImageLocation.getForDocument(qualityThumb, document), "44_44", ImageLocation.getForDocument(thumb, document), "b", (String) null, messageObject, 0);
                                }
                            }
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto && messageObject.messageOwner.media.photo != null && !messageObject.photoThumbs.isEmpty()) {
                            TLRPC.PhotoSize currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 50);
                            TLRPC.PhotoSize currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 320, false, currentPhotoObjectThumb, false);
                            if (messageObject.mediaExists || DownloadController.getInstance(currentAccount).canDownloadMedia(messageObject)) {
                                if (currentPhotoObject == currentPhotoObjectThumb) {
                                    currentPhotoObjectThumb = null;
                                }
                                if (messageObject.strippedThumb != null) {
                                    receiver.setImage(ImageLocation.getForObject(currentPhotoObject, messageObject.photoThumbsObject), "44_44", null, null, messageObject.strippedThumb, currentPhotoObject != null ? currentPhotoObject.size : 0, null, messageObject, messageObject.shouldEncryptPhotoOrVideo() ? 2 : 1);
                                } else {
                                    receiver.setImage(ImageLocation.getForObject(currentPhotoObject, messageObject.photoThumbsObject), "44_44", ImageLocation.getForObject(currentPhotoObjectThumb, messageObject.photoThumbsObject), "b", currentPhotoObject != null ? currentPhotoObject.size : 0, null, messageObject, messageObject.shouldEncryptPhotoOrVideo() ? 2 : 1);
                                }
                            } else {
                                if (messageObject.strippedThumb != null) {
                                    receiver.setImage(null, null, messageObject.strippedThumb, null, messageObject, 0);
                                } else {
                                    receiver.setImage(null, null, ImageLocation.getForObject(currentPhotoObjectThumb, messageObject.photoThumbsObject), "b", (String) null, messageObject, 0);
                                }
                            }
                        }
                        receiver.setRoundRadius(AndroidUtilities.dp(22));
                        imagesByDays.put(key, receiver);
                    }
                }
            }

            YearMonth yearMonthObject = YearMonth.of(year, monthInYear + 1);
            daysInMonth = yearMonthObject.lengthOfMonth();
            selected = new float[daysInMonth];
            rangedR = new float[daysInMonth];
            ranged = new float[daysInMonth];

            Calendar calendar = Calendar.getInstance();
            calendar.set(year, monthInYear, 0);
            startDayOfWeek = (calendar.get(Calendar.DAY_OF_WEEK) + 6) % 7;
            startMonthTime= (int) (calendar.getTimeInMillis() / 1000L);

            int totalColumns = daysInMonth + startDayOfWeek;
            cellCount = (int) (totalColumns / 7f) + (totalColumns % 7 == 0 ? 0 : 1);
            calendar.set(year, monthInYear + 1, 0);
            titleView.setText(LocaleController.formatYearMont(calendar.getTimeInMillis() / 1000, true));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(cellCount * (44 + 8) + 44), MeasureSpec.EXACTLY));
        }

        boolean pressed;
        float pressedX;
        float pressedY;

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (type == CALENDAR_TYPE_CLEAR_HISTORY && !editEnable) {
                return super.onTouchEvent(event);
            }

            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                pressed = true;
                pressedX = event.getX();
                pressedY = event.getY();
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                if (pressed) {
                    if (type == CALENDAR_TYPE_CLEAR_HISTORY && editEnable) {
                        int currentCell = 0;
                        int currentColumn = startDayOfWeek;
                        float xStep = getMeasuredWidth() / 7f;
                        float yStep = AndroidUtilities.dp(44 + 8);

                        for (int i = 0; i < daysInMonth; i++) {
                            float cx = xStep * currentColumn + xStep / 2f;
                            float cy = yStep * currentCell + yStep / 2f + AndroidUtilities.dp(44);
                            RectF rect = new RectF(
                                    cx - AndroidUtilities.dp(22),
                                    cy - AndroidUtilities.dp(22),
                                    cx + AndroidUtilities.dp(22),
                                    cy + AndroidUtilities.dp(22));

                            if (rect.contains(pressedX, pressedY)) {
                                Calendar calendar = Calendar.getInstance();
                                calendar.set(currentYear, currentMonthInYear, i + 1, 0, 0, 0);
                                selectedDates[selectIndex] = (int)(calendar.getTimeInMillis() / 1000);
                                if (selectedDates[1] < selectedDates[0]) {
                                    long swap = selectedDates[0];
                                    selectedDates[0] = selectedDates[1];
                                    selectedDates[1] = swap;
                                } else {
                                    selectIndex = (selectIndex + 1) % 2;
                                }

                                if (selectedDates[0] == 0) {
                                    selectedDates[0] = selectedDates[1];
                                }

                                updateBottomView();
                            }

                            currentColumn++;
                            if (currentColumn >= 7) {
                                currentColumn = 0;
                                currentCell++;
                            }
                        }
                    } else {
                        for (int i = 0; i < imagesByDays.size(); i++) {
                            if (imagesByDays.valueAt(i).getDrawRegion().contains(pressedX, pressedY)) {
                                PeriodDay periodDay = messagesByDays.valueAt(i);
                                MessageObject messageObject = periodDay.messageObject;
                                if (callback != null) {
                                    callback.onDateSelected(messageObject.getId(), periodDay.startOffset);
                                    finishFragment();
                                    break;
                                }
                            }
                        }
                    }
                }
                pressed = false;
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                pressed = false;
            }
            return pressed;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            int currentCell = 0;
            int currentColumn = startDayOfWeek;

            float xStep = getMeasuredWidth() / 7f;
            float yStep = AndroidUtilities.dp(44 + 8);
            for (int i = 0; i < daysInMonth; i++) {
                float cx = xStep * currentColumn + xStep / 2f;
                float cy = yStep * currentCell + yStep / 2f + AndroidUtilities.dp(44);

                Calendar calendar = Calendar.getInstance();
                calendar.set(currentYear, currentMonthInYear, i + 1, 0, 0, 0);
                int currentDate = (int)(calendar.getTimeInMillis() / 1000);
                int nowTime = (int) (System.currentTimeMillis() / 1000L);

                boolean isSelected1 = currentDate == selectedDates[0];
                boolean isSelected2 = currentDate == selectedDates[1];
                boolean isSelected = isSelected1 || isSelected2;
                boolean inRange = (selectedDates[0] <= currentDate && currentDate <= selectedDates[1]);

                float checkedProgress;
                float rangedLeftProgress;
                float rangedRightProgress;
                if (inRange && !isSelected1) {
                    rangedLeftProgress = Math.max(ranged[i], progress);
                } else {
                    rangedLeftProgress = Math.min(ranged[i], 1f - progress);
                }

                if (inRange && !isSelected2) {
                    rangedRightProgress = Math.max(rangedR[i], progress);
                } else {
                    rangedRightProgress = Math.min(rangedR[i], 1f - progress);
                }

                if (isSelected) {
                    checkedProgress = Math.max(selected[i], progress);
                } else {
                    checkedProgress = Math.min(selected[i], 1f - progress);
                }

                selected[i] = checkedProgress;
                ranged[i] = rangedLeftProgress;
                rangedR[i] = rangedRightProgress;

                if (Math.max(rangedLeftProgress, rangedRightProgress) != 0) {
                    float rad = AndroidUtilities.dp(22) * Math.max(rangedLeftProgress, rangedRightProgress);
                    float sideLeft = AndroidUtilities.dp(22) * rangedLeftProgress;
                    float sideRight = AndroidUtilities.dp(22) * rangedRightProgress;

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        if (currentColumn == 0 || i == 0 /*|| isSelected1*/) {
                            canvas.drawArc(
                                    cx - rad, cy - rad, cx + rad, cy + rad,
                                    270f, -180f, false, selectedBackgroundPaint);
                        } else {
                            canvas.drawRect(cx - xStep/2, cy - sideLeft, cx, cy + sideLeft, selectedBackgroundPaint);
                        }
                        if (currentColumn == 6 || i == daysInMonth - 1 /*|| isSelected2*/) {
                            canvas.drawArc(
                                    cx - rad, cy - rad, cx + rad, cy + rad,
                                    270f, 180f, false, selectedBackgroundPaint);
                        } else {
                            canvas.drawRect(cx, cy - sideRight, cx + xStep/2 , cy + sideRight, selectedBackgroundPaint);
                        }
                    }
                }


                if (checkedProgress != 0) {
                    Theme.checkboxSquare_checkPaint.setColor(getThemedColor(Theme.key_dialogRoundCheckBox));
                    Theme.checkboxSquare_checkPaint.setAlpha((int)(255 * checkedProgress));
                    selectedBackgroundPaint2.setAlpha((int)(255 * checkedProgress));
                    selectedPaint.setColor(getThemedColor(Theme.key_dialogRoundCheckBox));
                    selectedPaint.setAlpha((int)(255 * checkedProgress));
                    canvas.drawCircle(cx, cy, AndroidUtilities.dp(22), selectedBackgroundPaint2);
                    canvas.drawCircle(cx, cy, AndroidUtilities.dp(22), Theme.checkboxSquare_checkPaint);
                    if (imagesByDays == null || imagesByDays.get(i) == null) {

                        //selectedBackgroundPaint2.setAlpha((int)((1-checkedProgress) * 255));
                        canvas.drawCircle(cx, cy, AndroidUtilities.dp(22 * 0.857f), selectedPaint);
                        canvas.drawCircle(cx, cy, AndroidUtilities.dp(22 * 0.857f * (1-checkedProgress)), selectedBackgroundPaint2);
                    }
                }

                textPaint.setColor(isSelected?Color.WHITE:Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));

                if (inRange) {
                    textPaint.setTypeface(Typeface.DEFAULT_BOLD);
                } else {
                    textPaint.setTypeface(Typeface.DEFAULT);
                }

                if (nowTime < startMonthTime + (i + 1) * 86400) {
                    int oldAlpha = textPaint.getAlpha();
                    textPaint.setAlpha((int) (oldAlpha * 0.3f));
                    canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), textPaint);
                    textPaint.setAlpha(oldAlpha);
                } else if (messagesByDays != null && messagesByDays.get(i, null) != null) {
                    float alpha = 1f;
                    if (imagesByDays.get(i) != null) {
                        if (checkEnterItems && !messagesByDays.get(i).wasDrawn) {
                            messagesByDays.get(i).enterAlpha = 0f;
                            messagesByDays.get(i).startEnterDelay = (cy + getY()) / listView.getMeasuredHeight() * 150;
                        }
                        if (messagesByDays.get(i).startEnterDelay > 0) {
                            messagesByDays.get(i).startEnterDelay -= 16;
                            if (messagesByDays.get(i).startEnterDelay < 0) {
                                messagesByDays.get(i).startEnterDelay = 0;
                            } else {
                                invalidate();
                            }
                        }
                        if (messagesByDays.get(i).startEnterDelay == 0 && messagesByDays.get(i).enterAlpha != 1f) {
                            messagesByDays.get(i).enterAlpha += 16 / 220f;
                            if (messagesByDays.get(i).enterAlpha > 1f) {
                                messagesByDays.get(i).enterAlpha = 1f;
                            } else {
                                invalidate();
                            }
                        }
                        alpha = messagesByDays.get(i).enterAlpha;

                        final float max = Math.max(rangedLeftProgress, Math.max(rangedRightProgress, checkedProgress));
                        if (checkedProgress != 0 || max != 0) {
                            canvas.save();
                            float s = 1 - (1 - 0.857f) * max;
                            canvas.scale(s, s, cx, cy);
                        } else if (alpha != 1f) {
                            canvas.save();
                            float s = 0.8f + 0.2f * alpha;
                            canvas.scale(s, s, cx, cy);
                        }
                        imagesByDays.get(i).setAlpha(messagesByDays.get(i).enterAlpha);
                        imagesByDays.get(i).setImageCoords(cx - AndroidUtilities.dp(44) / 2f, cy - AndroidUtilities.dp(44) / 2f, AndroidUtilities.dp(44), AndroidUtilities.dp(44));
                        imagesByDays.get(i).draw(canvas);

                        blackoutPaint.setColor(ColorUtils.setAlphaComponent(Color.BLACK, (int) (messagesByDays.get(i).enterAlpha * 80)));
                        canvas.drawCircle(cx, cy, AndroidUtilities.dp(44) / 2f, blackoutPaint);
                        messagesByDays.get(i).wasDrawn = true;
                        if (alpha != 1f || checkedProgress != 0 || max != 0) {
                            canvas.restore();
                        }
                    }
                    if (alpha != 1f) {
                        int oldAlpha = textPaint.getAlpha();
                        textPaint.setAlpha((int) (oldAlpha * (1f - alpha)));
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), textPaint);
                        textPaint.setAlpha(oldAlpha);

                        oldAlpha = textPaint.getAlpha();
                        activeTextPaint.setAlpha((int) (oldAlpha * alpha));
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), activeTextPaint);
                        activeTextPaint.setAlpha(oldAlpha);
                    } else {
                        canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), activeTextPaint);
                    }
                } else {
                    canvas.drawText(Integer.toString(i + 1), cx, cy + AndroidUtilities.dp(5), textPaint);
                }

                currentColumn++;
                if (currentColumn >= 7) {
                    currentColumn = 0;
                    currentCell++;
                }
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            attached = true;
            if (imagesByDays != null) {
                for (int i = 0; i < imagesByDays.size(); i++) {
                    imagesByDays.valueAt(i).onAttachedToWindow();
                }
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            attached = false;
            if (imagesByDays != null) {
                for (int i = 0; i < imagesByDays.size(); i++) {
                    imagesByDays.valueAt(i).onDetachedFromWindow();
                }
            }
        }
    }

    private boolean onItemLongClickMonth(View view, int position, float x, float y) {
        if (AndroidUtilities.isTablet()) return true;

        if ( view instanceof MonthView) {
            MonthView monthView = (MonthView) view;

            int currentCell = 0;
            int currentColumn = monthView.startDayOfWeek;
            float xStep = monthView.getMeasuredWidth() / 7f;
            float yStep = AndroidUtilities.dp(44 + 8);

            for (int i = 0; i < monthView.daysInMonth; i++) {
                float cx = xStep * currentColumn + xStep / 2f;
                float cy = yStep * currentCell + yStep / 2f + AndroidUtilities.dp(44);
                RectF rect = new RectF(
                        cx - AndroidUtilities.dp(22),
                        cy - AndroidUtilities.dp(22),
                        cx + AndroidUtilities.dp(22),
                        cy + AndroidUtilities.dp(22));

                if (rect.contains(x, y)) {
                    Calendar calendar = Calendar.getInstance();
                    calendar.clear();
                    calendar.set(monthView.currentYear, monthView.currentMonthInYear, i + 1);
                    longSelectedDate = (int)(calendar.getTimeInMillis() / 1000);

                    Bundle bundle = new Bundle();
                    bundle.putInt("chatMode", ChatActivity.MODE_DATE_HISTORY);
                    bundle.putInt("history_day", longSelectedDate);
                    if (dialogId > 0) {
                        bundle.putLong("user_id", dialogId);
                    } else {
                        bundle.putLong("chat_id", -dialogId);
                    }

                    ChatActivity fragment = new ChatActivity(bundle) {
                        @Override
                        public void onFragmentDestroy() {
                            super.onFragmentDestroy();
                            historyFragmentPreview = null;
                            fragmentDayPreviewActive = false;

                            if (fragmentDayPreviewAnimator != null) {
                                fragmentDayPreviewAnimator.cancel();
                                fragmentDayPreviewAnimator = null;
                            }
                        }
                    };

                    //ArrayList<MessageObject> allMessages = getMessagesController();
                    //ArrayList<MessageObject> messages = new ArrayList<>();

                    fragment.userInfo = getMessagesController().getUserFull(dialogId);
                    fragment.chatInfo = getMessagesController().getChatFull(-dialogId);

                    historyFragmentPreview = fragment;
                    presentFragmentAsPreview(fragment);
                    prepareBlurBitmap();
                }

                currentColumn++;
                if (currentColumn >= 7) {
                    currentColumn = 0;
                    currentCell++;
                }
            }
        }

        return true;
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    private void onHistoryCleared() {
        if (scrimPopupWindow != null) {
            scrimPopupWindow.dismiss();
        }

        AndroidUtilities.runOnUIThread(this::finishFragment, 300);
    }

    public interface Callback {
        void onDateSelected(int messageId, int startOffset);
    }

    private class PeriodDay {
        MessageObject messageObject;
        int startOffset;
        float enterAlpha = 1f;
        float startEnterDelay = 1f;
        boolean wasDrawn;
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {

        ThemeDescription.ThemeDescriptionDelegate descriptionDelegate = new ThemeDescription.ThemeDescriptionDelegate() {
            @Override
            public void didSetColor() {
                updateColors();
            }
        };
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();
        new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_windowBackgroundWhite);
        new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_windowBackgroundWhiteBlackText);
        new ThemeDescription(null, 0, null, null, null, descriptionDelegate, Theme.key_listSelector);


        return super.getThemeDescriptions();
    }

    @Override
    public boolean needDelayOpenAnimation() {
        return true;
    }

    @Override
    protected void onTransitionAnimationStart(boolean isOpen, boolean backward) {
        super.onTransitionAnimationStart(isOpen, backward);
        isOpened = true;
    }

    /*private void checkShowBlur(boolean animated) {
        boolean show = (parentLayout != null && parentLayout.isInPreviewMode() && !inPreviewMode);
        if (show && (blurredView == null || blurredView.getTag() == null)) {
            if (blurredView == null) {
                blurredView = new BluredView(fragmentView.getContext(), fragmentView, null) {
                    @Override
                    public void setAlpha(float alpha) {
                        super.setAlpha(alpha);
                        fragmentView.invalidate();
                    }

                    @Override
                    public void setVisibility(int visibility) {
                        super.setVisibility(visibility);
                        fragmentView.invalidate();
                    }
                };
                contentView.addView(blurredView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            } else {
                int idx = contentView.indexOfChild(blurredView);
                if (idx != contentView.getChildCount() - 1) {
                    contentView.removeView(blurredView);
                    contentView.addView(blurredView);
                }
                blurredView.update();
                blurredView.setVisibility(View.VISIBLE);
            }

            blurredView.setAlpha(0.0f);
            blurredView.animate().setListener(null).cancel();
            blurredView.animate().alpha(1f).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    fragmentView.invalidate();
                }
            }).start();

            blurredView.setTag(1);
        } else if (!show && blurredView != null && blurredView.getTag() != null) {
            blurredView.animate().setListener(null).cancel();
            blurredView.animate().setListener(new HideViewAfterAnimation(blurredView)).alpha(0).start();
            blurredView.setTag(null);
            fragmentView.invalidate();
        }
    }*/

    private void prepareBlurBitmap() {
        if (blurredView == null) {
            return;
        }
        int w = (int) (fragmentView.getMeasuredWidth() / 6.0f);
        int h = (int) (fragmentView.getMeasuredHeight() / 6.0f);
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.scale(1.0f / 6.0f, 1.0f / 6.0f);
        fragmentView.draw(canvas);
        Utilities.stackBlurBitmap(bitmap, Math.max(7, Math.max(w, h) / 180));
        blurredView.setBackground(new BitmapDrawable(bitmap));
        blurredView.setAlpha(0.0f);
        blurredView.setVisibility(View.VISIBLE);
    }


    private void showHistoryOptionsMenu(int x, int y) {
        ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(getParentActivity(), R.drawable.popup_fixed_alert, null);
        popupLayout.setMinimumWidth(AndroidUtilities.dp(200));
        Rect backgroundPaddings = new Rect();
        Drawable shadowDrawable = getParentActivity().getResources().getDrawable(R.drawable.popup_fixed_alert).mutate();
        shadowDrawable.getPadding(backgroundPaddings);
        popupLayout.setBackgroundColor(getThemedColor(Theme.key_actionBarDefaultSubmenuBackground));

        ActionBarMenuSubItem jumpToDateButton = new ActionBarMenuSubItem(getParentActivity(), true, false, null);
        jumpToDateButton.setMinimumWidth(AndroidUtilities.dp(200));
        jumpToDateButton.setTextAndIcon(LocaleController.getString("JumpToDate", R.string.JumpToDate), R.drawable.msg_message);
        jumpToDateButton.setOnClickListener(view -> {
            if (scrimPopupWindow != null) {
                scrimPopupWindow.dismiss();
            }

            if (callback != null) {
                callback.onDateSelected(0, longSelectedDate);
            }

            AndroidUtilities.runOnUIThread(this::finishFragment, 300);



            /*
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis((long) historyFragmentDatetime * 1000);
            int year = calendar.get(Calendar.YEAR);
            int monthOfYear = calendar.get(Calendar.MONTH);
            int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);

            calendar.clear();
            calendar.set(year, monthOfYear, dayOfMonth);
            jumpToDate((int) (calendar.getTime().getTime() / 1000));
            */

        });
        popupLayout.addView(jumpToDateButton);

        if (user != null) {
            ActionBarMenuSubItem selectDayButton = new ActionBarMenuSubItem(getParentActivity(), false, false, null);
            selectDayButton.setMinimumWidth(AndroidUtilities.dp(200));
            selectDayButton.setTextAndIcon("Select this day", R.drawable.msg_select);
            selectDayButton.setOnClickListener(view -> {
                if (scrimPopupWindow != null) {
                    scrimPopupWindow.dismiss();
                    editEnable = true;
                    selectedDates[0] = longSelectedDate;
                    selectedDates[1] = longSelectedDate;
                    animateRange();
                    updateBottomView();
                }
            });
            popupLayout.addView(selectDayButton);

            ActionBarMenuSubItem clearHistoryButton = new ActionBarMenuSubItem(getParentActivity(), false, true, null);
            clearHistoryButton.setMinimumWidth(AndroidUtilities.dp(200));
            clearHistoryButton.setTextAndIcon("Clear history", R.drawable.msg_delete);
            clearHistoryButton.setOnClickListener(view -> {
                AlertsCreator.createClearHistoryRangeAlert(this, user, longSelectedDate, longSelectedDate + 86399, this::onHistoryCleared, null);
            });
            popupLayout.addView(clearHistoryButton);
        }

        LinearLayout scrimPopupContainerLayout = new LinearLayout(contentView.getContext()) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0 && scrimPopupWindow != null && scrimPopupWindow.isShowing()) {
                    scrimPopupWindow.dismiss();
                }
                return super.dispatchKeyEvent(event);
            }
        };
        scrimPopupContainerLayout.setOrientation(LinearLayout.VERTICAL);
        scrimPopupContainerLayout.addView(popupLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0, 0));
        scrimPopupWindow = new ActionBarPopupWindow(scrimPopupContainerLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
            @Override
            public void dismiss() {
                super.dismiss();
                if (scrimPopupWindow != this) {
                    return;
                }

                fragmentDayPreviewActive = false;
                if (historyFragmentPreview != null) {
                    finishPreviewFragment();
                }

                scrimPopupWindow = null;
                if (scrimAnimatorSet != null) {
                    scrimAnimatorSet.cancel();
                    scrimAnimatorSet = null;
                }

                scrimAnimatorSet = new AnimatorSet();
                ArrayList<Animator> animators = new ArrayList<>();
                animators.add(ObjectAnimator.ofInt(scrimPaint, AnimationProperties.PAINT_ALPHA, 0));

                scrimAnimatorSet.playTogether(animators);
                scrimAnimatorSet.setDuration(220);
                scrimAnimatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        contentView.invalidate();
                    }
                });
                scrimAnimatorSet.start();
            }
        };
        scrimPopupWindow.setPauseNotifications(true);
        scrimPopupWindow.setDismissAnimationDuration(220);
        scrimPopupWindow.setOutsideTouchable(false);
        scrimPopupWindow.setClippingEnabled(true);
        scrimPopupWindow.setAnimationStyle(R.style.PopupContextAnimation);
        scrimPopupWindow.setFocusable(true);
        scrimPopupContainerLayout.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST));
        scrimPopupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
        scrimPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
        scrimPopupWindow.getContentView().setFocusableInTouchMode(true);
        popupLayout.setFitItems(true);

        scrimPopupWindow.showAtLocation(getParentLayout(), Gravity.LEFT | Gravity.TOP, x, y);
    }
}
