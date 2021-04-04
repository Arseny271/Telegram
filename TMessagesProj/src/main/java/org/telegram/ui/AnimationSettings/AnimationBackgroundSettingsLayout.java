package org.telegram.ui.AnimationSettings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

public class AnimationBackgroundSettingsLayout {

    private BaseFragment currentFragment;
    private Context mContext;

    public RecyclerListView listView;
    public LinearLayoutManager layoutManager;
    public ListAdapter adapter;

    private ColorEditorAlert editorAlert;

    public ChatBackgroundView getBackgroundView() {
        if (backgroundPreviewRow == -1) return null;
        RecyclerView.ViewHolder view = listView.findViewHolderForAdapterPosition(backgroundPreviewRow);
        if (view == null) return null;
        if (view.itemView == null) return null;
        return (ChatBackgroundView) view.itemView;
    }

    public void updateBackgroundViewColor(int color, int number) {
        ChatBackgroundView backgroundView = getBackgroundView();
        if (backgroundView != null) {
            backgroundView.setColor(color, number);
            backgroundView.redraw();
        }
    }

    int times[] = {200, 300, 400, 500, 600, 700, 800, 900, 1000, 1500, 2000, 3000, 10000};


    private ActionBarPopupWindow scrimPopupWindow;


    SettingRows selectedParam;

    private void saveDurationTime(AnimationParam param, int time) {
        currentFragment.getAnimationController().saveAnimationDuration(param.paramName, time);
    }

    private void processSelectedOption(int id) {
        if (selectedParam != null) {
            if (selectedParam instanceof AnimationParam) {
                AnimationParam animationParam = (AnimationParam) selectedParam;
                currentFragment.getAnimationController().saveAnimationDuration(animationParam.paramName, times[id]);
                RecyclerView.ViewHolder viewHolderDuration = listView.findViewHolderForAdapterPosition(animationParam.durationRow);
                if (viewHolderDuration != null) {
                    TextSettingsCell cell = (TextSettingsCell) viewHolderDuration.itemView;
                    cell.setValue(String.format("%dms", times[id]));
                }

                RecyclerView.ViewHolder viewHolderSliders = listView.findViewHolderForAdapterPosition(animationParam.slidersRow);
                if (viewHolderSliders != null) {
                    AnimationCubicBezierSlider cell = (AnimationCubicBezierSlider) viewHolderSliders.itemView;
                    cell.setDurationTime(times[id]);
                }
            } else if (selectedParam instanceof DurationParam) {
                DurationParam durationParam = (DurationParam) selectedParam;
                RecyclerView.ViewHolder viewHolderDuration = listView.findViewHolderForAdapterPosition(durationParam.durationRow);
                if (viewHolderDuration != null) {
                    TextSettingsCell cell = (TextSettingsCell) viewHolderDuration.itemView;
                    cell.setValue(String.format("%dms", times[id]));
                }

                for (int i = 0; i < durationParam.animationParams.length; i++) {
                    currentFragment.getAnimationController().saveAnimationDuration(durationParam.animationParams[i].paramName, times[id]);
                    RecyclerView.ViewHolder viewHolderSliders = listView.findViewHolderForAdapterPosition(durationParam.animationParams[i].slidersRow);
                    if (viewHolderSliders != null) {
                        AnimationCubicBezierSlider cell = (AnimationCubicBezierSlider) viewHolderSliders.itemView;
                        cell.setDurationTime(times[id]);
                    }
                }

            }
        }
        selectedParam = null;
    }

    public void createMenu(View v, float x, float y) {
        if (scrimPopupWindow != null) {
            scrimPopupWindow.dismiss();
            scrimPopupWindow = null;
            return;
        }

        ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(currentFragment.getParentActivity());
        Drawable shadowDrawable = currentFragment.getParentActivity().getResources().getDrawable(R.drawable.popup_fixed_alert).mutate();

        Rect backgroundPaddings = new Rect();
        shadowDrawable.getPadding(backgroundPaddings);
        popupLayout.setBackgroundDrawable(shadowDrawable);
        popupLayout.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground));

        LinearLayout linearLayout = new LinearLayout(currentFragment.getParentActivity());
        ScrollView scrollView;
        if (Build.VERSION.SDK_INT >= 21) {
            scrollView = new ScrollView(currentFragment.getParentActivity(), null, 0, R.style.scrollbarShapeStyle) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                    setMeasuredDimension(linearLayout.getMeasuredWidth(), getMeasuredHeight());
                }
            };
        } else {
            scrollView = new ScrollView(currentFragment.getParentActivity());
        }
        scrollView.setClipToPadding(false);
        popupLayout.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        linearLayout.setMinimumWidth(AndroidUtilities.dp(180));
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        for (int a = 0, N = times.length; a < N; a++) {
            ActionBarMenuSubItem cell = new ActionBarMenuSubItem(currentFragment.getParentActivity(), a == 0, a == N - 1);
            cell.setText(String.format("%d ms", times[a]));
            linearLayout.addView(cell);

            final int i = a;
            cell.setOnClickListener(v1 -> {
                processSelectedOption(i);
                if (scrimPopupWindow != null) {
                    scrimPopupWindow.dismiss();
                }
            });
        }
        scrollView.addView(linearLayout, LayoutHelper.createScroll(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
        scrimPopupWindow = new ActionBarPopupWindow(popupLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
            @Override
            public void dismiss() {
                super.dismiss();
                if (scrimPopupWindow != this) {
                    return;
                }
                scrimPopupWindow = null;
            }
        };
        scrimPopupWindow.setPauseNotifications(true);
        scrimPopupWindow.setDismissAnimationDuration(220);
        scrimPopupWindow.setOutsideTouchable(true);
        scrimPopupWindow.setClippingEnabled(true);
        scrimPopupWindow.setAnimationStyle(R.style.PopupContextAnimation);
        scrimPopupWindow.setFocusable(true);
        popupLayout.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(1000), View.MeasureSpec.AT_MOST));
        scrimPopupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
        scrimPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
        scrimPopupWindow.getContentView().setFocusableInTouchMode(true);

        int popupX = listView.getMeasuredWidth() - AndroidUtilities.dp(6) - popupLayout.getMeasuredWidth();
        if (AndroidUtilities.isTablet()) {
            int[] location = new int[2];
            currentFragment.getFragmentView().getLocationInWindow(location);
            popupX += location[0];
        }
        int totalHeight = currentFragment.getFragmentView().getHeight();
        int height = popupLayout.getMeasuredHeight();
        int popupY;
        if (height < totalHeight) {
            popupY = (int) (listView.getY() + v.getTop() + y);
            if (height - backgroundPaddings.top - backgroundPaddings.bottom > AndroidUtilities.dp(240)) {
                popupY += AndroidUtilities.dp(240) - height;
            }
            if (popupY < listView.getY() + AndroidUtilities.dp(24)) {
                popupY = (int) (listView.getY() + AndroidUtilities.dp(24));
            } else if (popupY > totalHeight - height - AndroidUtilities.dp(8)) {
                popupY = totalHeight - height - AndroidUtilities.dp(8);
            }
        } else {
            popupY = currentFragment.isInBubbleMode() ? 0 : AndroidUtilities.statusBarHeight;
        }
        scrimPopupWindow.showAtLocation(listView, Gravity.LEFT | Gravity.TOP, popupX, popupY);
    }

    AnimationBackgroundSettingsLayout(BaseFragment fragment, AnimationSettingsActivity.AnimationRecyclerView lview) {
        currentFragment = fragment;
        mContext = fragment.getParentActivity();

        editorAlert = new ColorEditorAlert(mContext);

        listView = lview;
        listView.setItemAnimator(null);
        listView.setLayoutAnimation(null);
        listView.setVerticalScrollbarPosition(LocaleController.isRTL ? RecyclerListView.SCROLLBAR_POSITION_LEFT : RecyclerListView.SCROLLBAR_POSITION_RIGHT);
        listView.setVerticalScrollBarEnabled(true);
        listView.setInstantClick(true);
        listView.setAnimateEmptyView(true, 0);
        listView.setClipToPadding(false);
        listView.setPivotY(0);
        listView.setOnItemClickListener((view, position, x, y) -> {
            int viewType = adapter.getItemViewType(position);
            if (viewType == PARAMETER_VIEW_TYPE) {
                selectedParam = getSettingRowsByPosition(position);
                createMenu(view, x, y);
            } else if (viewType == COLOR_VIEW_TYPE) {
                final SetColorCell colorCell = (SetColorCell) view;
                final int oldColor = colorCell.getColorValue();
                final int number = colorCell.getColorId();
                editorAlert.setDelegate(new ColorEditorAlert.ColorEditorAlertDelegate() {
                    @Override
                    public void onColorChanged(int color) {
                        colorCell.setColorValue(color);
                        updateBackgroundViewColor(color, number);
                    }

                    @Override
                    public void onColorApplied(int color) {
                        currentFragment.getAnimationController().saveColor(color, number);
                        updateBackgroundViewColor(color, number);
                    }

                    @Override
                    public void onColorCanceled() {
                        colorCell.setColorValue(oldColor);
                        updateBackgroundViewColor(oldColor, number);
                    }
                });
                editorAlert.setColor(oldColor);
                editorAlert.show();
            } else if (viewType == BACKGROUND_VIEW_TYPE) {
                ChatBackgroundView backgroundView = (ChatBackgroundView) view;
                backgroundView.rotate();
            } else if (viewType == BUTTON_VIEW_TYPE) {
                if (position == backgroundPreviewFullscreenRow) {
                    currentFragment.presentFragment(new BackgroundFullscreenActivity());
                }
            }
        });

        layoutManager = new LinearLayoutManager(mContext);
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        listView.setLayoutManager(layoutManager);

        adapter = new ListAdapter(mContext);
        listView.setAdapter(adapter);
    }


    private interface SettingRows {
        int setRows(int position);
        void clearRows();
        int checkType(int position);
        int getMinPosition();
        int getMaxPosition();
    }

    public class AnimationParam implements SettingRows {
        public int headerRow;
        public int durationRow;
        public int slidersRow;
        public int emptyRow;
        public String paramName;
        public String header;
        public int minPosition;
        public int maxPosition;

        AnimationParam(String paramName, String header) {
            this.paramName = paramName;
            this.header = header;
        };

        @Override
        public int setRows(int position) {
            return setRows(position, false);
        }

        public int setRows(int position, boolean withDuration) {
            minPosition = position;
            headerRow = position++;
            if (withDuration) {
                durationRow = position++;
            }
            slidersRow = position++;
            maxPosition = position;
            emptyRow = position++;
            return position;
        }

        @Override
        public void clearRows() {
            minPosition = -1;
            maxPosition = -1;
            headerRow = -1;
            durationRow = -1;
            slidersRow = -1;
            emptyRow = -1;
        }

        @Override
        public int checkType(int position) {
            if (position == headerRow) {
                return HEADER_VIEW_TYPE;
            } else if (position == durationRow) {
                return PARAMETER_VIEW_TYPE;
            } else if (position == slidersRow) {
                return LINE_VIEW_TYPE;
            } else if (position == emptyRow) {
                return EMPTY_VIEW_TYPE;
            }
            return -1;
        }

        @Override
        public int getMinPosition() {
            return minPosition;
        };

        @Override
        public int getMaxPosition() {
            return maxPosition;
        };
    }

    public class DurationParam implements SettingRows {
        public int durationRow;
        public int emptyRow;
        public int minPosition;
        public int maxPosition;
        public AnimationParam animationParams[];

        DurationParam(AnimationParam[] params) {
            this.animationParams = params;
        };


        public int setRows(int position) {
            minPosition = position;
            durationRow = position++;
            maxPosition = position;
            emptyRow = position++;
            return position;
        }

        @Override
        public void clearRows() {
            minPosition = -1;
            maxPosition = -1;
            durationRow = -1;
            emptyRow = -1;
        }

        @Override
        public int checkType(int position) {
            if (position == durationRow) {
                return PARAMETER_VIEW_TYPE;
            } else if (position == emptyRow) {
                return EMPTY_VIEW_TYPE;
            }
            return -1;
        }

        @Override
        public int getMinPosition() {
            return minPosition;
        };

        @Override
        public int getMaxPosition() {
            return maxPosition;
        };
    }

    AnimationParam sendMessageParam = new AnimationParam("SendMessage", "Send Message");
    AnimationParam openChatParam = new AnimationParam("OpenChat", "Open Chat");
    AnimationParam jumpToMessageParam = new AnimationParam("JumpToMessage", "Jump to Message");

    AnimationParam shortTextXParam              = new AnimationParam("ShortTextX", "X Position");
    AnimationParam shortTextYParam              = new AnimationParam("ShortTextY", "Y Position");
    AnimationParam shortTextBubbleShapeParam    = new AnimationParam("ShortTextBubbleShape", "Bubble shape");
    AnimationParam shortTextScaleParam          = new AnimationParam("ShortTextScale", "Text scale");
    AnimationParam shortTextColorChangeParam    = new AnimationParam("ShortTextColorChange", "Color change");
    AnimationParam shortTextTimeAppearsParam    = new AnimationParam("ShortTextTimeAppears", "Time appears");
    DurationParam shortTextDurationParam = new DurationParam(new AnimationParam[] {
        shortTextXParam, shortTextYParam, shortTextBubbleShapeParam, shortTextScaleParam,
        shortTextColorChangeParam, shortTextTimeAppearsParam
    });

    AnimationParam longTextXParam           = new AnimationParam("LongTextX", "X Position");
    AnimationParam longTextYParam           = new AnimationParam("LongTextY", "Y Position");
    AnimationParam longTextBubbleShapeParam = new AnimationParam("LongTextBubbleShape", "Bubble shape");
    AnimationParam longTextScaleParam       = new AnimationParam("LongTextScale", "Text scale");
    AnimationParam longTextColorChangeParam = new AnimationParam("LongTextColorChange", "Color change");
    AnimationParam longTextTimeAppearsParam = new AnimationParam("LongTextTimeAppears", "Time appears");
    DurationParam longTextDurationParam = new DurationParam(new AnimationParam[] {
            longTextXParam, longTextYParam, longTextBubbleShapeParam,
            longTextScaleParam, longTextColorChangeParam, longTextTimeAppearsParam
    });

    AnimationParam linkXParam           = new AnimationParam("LinkX", "X Position");
    AnimationParam linkYParam           = new AnimationParam("LinkY", "Y Position");
    AnimationParam linkBubbleShapeParam = new AnimationParam("LinkBubbleShape", "Bubble shape");
    AnimationParam linkScaleParam       = new AnimationParam("LinkScale", "Text scale");
    AnimationParam linkColorChangeParam = new AnimationParam("LinkColorChange", "Color change");
    AnimationParam linkTimeAppearsParam = new AnimationParam("LinkTimeAppears", "Time appears");
    DurationParam linkDurationParam = new DurationParam(new AnimationParam[] {
            linkXParam, linkYParam, linkBubbleShapeParam,
            linkScaleParam, linkColorChangeParam, linkTimeAppearsParam
    });

    AnimationParam emojiXParam              = new AnimationParam("EmojiX", "X Position");
    AnimationParam emojiYParam              = new AnimationParam("EmojiY", "Y Position");
    AnimationParam emojiScaleParam          = new AnimationParam("EmojiScale", "Emoji scale");
    AnimationParam emojiTimeAppearsParam    = new AnimationParam("EmojiTimeAppears", "Time appears");
    DurationParam emojiDurationParam = new DurationParam(new AnimationParam[] {
            emojiXParam, emojiYParam, emojiScaleParam, emojiTimeAppearsParam
    });

    AnimationParam voiceXParam              = new AnimationParam("VoiceX", "X Position");
    AnimationParam voiceYParam              = new AnimationParam("VoiceY", "Y Position");
    AnimationParam voiceScaleParam          = new AnimationParam("VoiceScale", "Icon scale");
    AnimationParam voiceColorChangeParam    = new AnimationParam("VoiceColorChange", "Color change");
    AnimationParam voiceTimeAppearsParam    = new AnimationParam("VoiceTimeAppears", "Time appears");
    DurationParam voiceDurationParam = new DurationParam(new AnimationParam[] {
            voiceXParam, voiceYParam, voiceScaleParam,
            voiceColorChangeParam, voiceTimeAppearsParam
    });

    AnimationParam videoXParam              = new AnimationParam("RoundX", "X Position");
    AnimationParam videoYParam              = new AnimationParam("RoundY", "Y Position");
    AnimationParam videoScaleParam          = new AnimationParam("RoundScale", "Round scale");
    AnimationParam videoTimeAppearsParam    = new AnimationParam("RoundTimeAppears", "Time appears");
    DurationParam videoDurationParam = new DurationParam(new AnimationParam[] {
            videoXParam, videoYParam, videoScaleParam, videoTimeAppearsParam
    });

    AnimationParam photoXParam              = new AnimationParam("PhotoX", "X Position");
    AnimationParam photoYParam              = new AnimationParam("PhotoY", "Y Position");
    AnimationParam photoScaleParam          = new AnimationParam("PhotoScale", "Round scale");
    AnimationParam photoTimeAppearsParam    = new AnimationParam("PhotoTimeAppears", "Time appears");
    DurationParam photoDurationParam = new DurationParam(new AnimationParam[] {
            photoXParam, photoYParam, photoScaleParam, photoTimeAppearsParam
    });

    SettingRows animationParams[] = {
        sendMessageParam, openChatParam, jumpToMessageParam,

        shortTextDurationParam, shortTextXParam, shortTextYParam, shortTextBubbleShapeParam,
            shortTextScaleParam, shortTextColorChangeParam, shortTextTimeAppearsParam,

        longTextDurationParam, longTextXParam, longTextYParam, longTextBubbleShapeParam,
            longTextScaleParam, longTextColorChangeParam, longTextTimeAppearsParam,

        linkDurationParam, linkXParam, linkYParam, linkBubbleShapeParam,
            linkScaleParam, linkColorChangeParam, linkTimeAppearsParam,

        emojiDurationParam, emojiXParam, emojiYParam, emojiScaleParam, emojiTimeAppearsParam,

        voiceDurationParam, voiceXParam, voiceYParam, voiceScaleParam, voiceColorChangeParam,
            voiceTimeAppearsParam,

        videoDurationParam, videoXParam, videoYParam, videoScaleParam, videoTimeAppearsParam,

        photoDurationParam, photoXParam, photoYParam, photoScaleParam, photoTimeAppearsParam

    };




    public SettingRows getSettingRowsByPosition(int position) {
        if (position == -1) return null;
        for (int i = 0; i < animationParams.length; i++) {
            if (animationParams[i].getMaxPosition() >= position &&
                animationParams[i].getMinPosition() <= position) {
                return animationParams[i];
            }
        }
        return null;
    }

    private int rowCount;
    private int backgroundPreviewHeaderRow;
    private int backgroundPreviewRow;
    private int backgroundPreviewFullscreenRow;
    private int empty1Row;
    private int colorsHeaderRow;
    private int color1Row;
    private int color2Row;
    private int color3Row;
    private int color4Row;
    private int empty2Row;

    public static int pageCounter = 0;
    public static final int BACKGROUND_SETTINGS_PAGE = pageCounter++;
    public static final int SHORT_TEXT_SETTINGS_PAGE = pageCounter++;
    public static final int LONG_TEXT_SETTINGS_PAGE = pageCounter++;
    public static final int LINK_SETTINGS_PAGE = pageCounter++;
    public static final int EMOJI_SETTINGS_PAGE = pageCounter++;
    public static final int VOICE_SETTINGS_PAGE = pageCounter++;
    public static final int VIDEO_SETTINGS_PAGE = pageCounter++;
    public static final int PHOTOS_SETTINGS_PAGE = pageCounter++;

    public void updateRowsIds(int pageType) {
        clearRowIds();

        if (pageType == BACKGROUND_SETTINGS_PAGE) {
            backgroundPreviewHeaderRow = rowCount++;
            backgroundPreviewRow = rowCount++;
            backgroundPreviewFullscreenRow = rowCount++;
            empty1Row = rowCount++;
            colorsHeaderRow = rowCount++;
            color1Row = rowCount++;
            color2Row = rowCount++;
            color3Row = rowCount++;
            color4Row = rowCount++;
            empty2Row = rowCount++;

            rowCount = sendMessageParam.setRows(rowCount, true);
            rowCount = openChatParam.setRows(rowCount, true);
            rowCount = jumpToMessageParam.setRows(rowCount, true);
        } else if (pageType == SHORT_TEXT_SETTINGS_PAGE) {
            rowCount = shortTextDurationParam.setRows(rowCount);
            rowCount = shortTextXParam.setRows(rowCount);
            rowCount = shortTextYParam.setRows(rowCount);
            rowCount = shortTextBubbleShapeParam.setRows(rowCount);
            rowCount = shortTextScaleParam.setRows(rowCount);
            rowCount = shortTextColorChangeParam.setRows(rowCount);
            rowCount = shortTextTimeAppearsParam.setRows(rowCount);
        } else if (pageType == LONG_TEXT_SETTINGS_PAGE) {
            rowCount = longTextDurationParam.setRows(rowCount);
            rowCount = longTextXParam.setRows(rowCount);
            rowCount = longTextYParam.setRows(rowCount);
            rowCount = longTextBubbleShapeParam.setRows(rowCount);
            rowCount = longTextScaleParam.setRows(rowCount);
            rowCount = longTextColorChangeParam.setRows(rowCount);
            rowCount = longTextTimeAppearsParam.setRows(rowCount);
        } else if (pageType == LINK_SETTINGS_PAGE) {
            rowCount = linkDurationParam.setRows(rowCount);
            rowCount = linkXParam.setRows(rowCount);
            rowCount = linkYParam.setRows(rowCount);
            rowCount = linkBubbleShapeParam.setRows(rowCount);
            rowCount = linkScaleParam.setRows(rowCount);
            rowCount = linkColorChangeParam.setRows(rowCount);
            rowCount = linkTimeAppearsParam.setRows(rowCount);
        } else if (pageType == EMOJI_SETTINGS_PAGE) {
            rowCount = emojiDurationParam.setRows(rowCount);
            rowCount = emojiXParam.setRows(rowCount);
            rowCount = emojiYParam.setRows(rowCount);
            rowCount = emojiScaleParam.setRows(rowCount);
            rowCount = emojiTimeAppearsParam.setRows(rowCount);
        } else if (pageType == VOICE_SETTINGS_PAGE) {
            rowCount = voiceDurationParam.setRows(rowCount);
            rowCount = voiceXParam.setRows(rowCount);
            rowCount = voiceYParam.setRows(rowCount);
            rowCount = voiceScaleParam.setRows(rowCount);;
            rowCount = voiceColorChangeParam.setRows(rowCount);
            rowCount = voiceTimeAppearsParam.setRows(rowCount);
        } else if (pageType == VIDEO_SETTINGS_PAGE) {
            rowCount = videoDurationParam.setRows(rowCount);
            rowCount = videoXParam.setRows(rowCount);
            rowCount = videoYParam.setRows(rowCount);
            rowCount = videoScaleParam.setRows(rowCount);
            rowCount = videoTimeAppearsParam.setRows(rowCount);
        } else if (pageType == PHOTOS_SETTINGS_PAGE) {
            rowCount = photoDurationParam.setRows(rowCount);
            rowCount = photoXParam.setRows(rowCount);
            rowCount = photoYParam.setRows(rowCount);
            rowCount = photoScaleParam.setRows(rowCount);
            rowCount = photoTimeAppearsParam.setRows(rowCount);
        }
    }

    public void clearRowIds() {
        rowCount = 0;

        for (int i = 0; i < animationParams.length; i++) {
            animationParams[i].clearRows();
        }

        backgroundPreviewHeaderRow = -1;
        backgroundPreviewRow = -1;
        backgroundPreviewFullscreenRow = -1;
        empty1Row = -1;
        colorsHeaderRow = -1;
        color1Row = -1;
        color2Row = -1;
        color3Row = -1;
        color4Row = -1;
        empty2Row = -1;
    }

    public static int typeCounter = 1;
    public static final int HEADER_VIEW_TYPE = typeCounter++;
    public static final int BACKGROUND_VIEW_TYPE = typeCounter++;
    public static final int BUTTON_VIEW_TYPE = typeCounter++;
    public static final int COLOR_VIEW_TYPE = typeCounter++;
    public static final int PARAMETER_VIEW_TYPE = typeCounter++;
    public static final int LINE_VIEW_TYPE = typeCounter++;
    public static final int EMPTY_VIEW_TYPE = typeCounter++;

    public class ListAdapter extends RecyclerListView.SelectionAdapter {
        private Context mContext;
        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            final int type = holder.getItemViewType();
            return (type == BUTTON_VIEW_TYPE || type == PARAMETER_VIEW_TYPE || type == COLOR_VIEW_TYPE);
        }

        public int getExtendedItemViewType(int position) {
            if (position == empty1Row || position == empty2Row) {
                return EMPTY_VIEW_TYPE;
            } else if (position == backgroundPreviewFullscreenRow) {
                return BUTTON_VIEW_TYPE;
            } else if (position == backgroundPreviewRow) {
                return BACKGROUND_VIEW_TYPE;
            } else if (position == backgroundPreviewHeaderRow || position == colorsHeaderRow) {
                return HEADER_VIEW_TYPE;
            } else if (position == color1Row || position == color2Row ||
                    position == color3Row || position == color4Row) {
                return COLOR_VIEW_TYPE;
            }

            return -1;
        }

        @Override
        public int getItemViewType(int position) {
            final SettingRows animationParam = getSettingRowsByPosition(position);
            if (animationParam != null) return animationParam.checkType(position);

            return getExtendedItemViewType(position);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = null;
            if (viewType == HEADER_VIEW_TYPE) {
                view = new HeaderCell(mContext);
                view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            } else if (viewType == EMPTY_VIEW_TYPE) {
                view = new ShadowSectionCell(mContext);
            } else if (viewType == BACKGROUND_VIEW_TYPE) {
                view = new ChatBackgroundView(currentFragment) {
                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(180));
                        setResolution(getMeasuredWidth(), getMeasuredHeight());
                    }
                };
            } else if (viewType == BUTTON_VIEW_TYPE) {
                view = new TextCell(mContext);
                view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            } else if (viewType == COLOR_VIEW_TYPE) {
                view = new SetColorCell(mContext);
                view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            } else if (viewType == PARAMETER_VIEW_TYPE) {
                view = new TextSettingsCell(mContext);
                view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            } else if (viewType == LINE_VIEW_TYPE) {
                view = new AnimationCubicBezierSlider(mContext);
                view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            }

            return new RecyclerListView.Holder(view);
        }

        @SuppressLint("DefaultLocale")
        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, final int position) {
            final SettingRows settingRows = getSettingRowsByPosition(position);
            final int type = (settingRows != null) ?
                settingRows.checkType(position):
                getExtendedItemViewType(position);

            if (settingRows != null) {
                if (settingRows instanceof AnimationParam) {
                    AnimationParam animationParam = (AnimationParam) settingRows;
                    if (type == HEADER_VIEW_TYPE) {
                        HeaderCell headerCell = (HeaderCell) holder.itemView;
                        headerCell.setText(animationParam.header);
                    } else if (type == PARAMETER_VIEW_TYPE) {
                        TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                        textCell.setText("Duration", true);
                        textCell.setValue(String.format("%dms", currentFragment.getAnimationController().getAnimationDuration(animationParam.paramName)));
                    } else if (type == LINE_VIEW_TYPE) {
                        AnimationCubicBezierSlider sliderCell = (AnimationCubicBezierSlider) holder.itemView;
                        sliderCell.setAnimationParams(currentFragment.getAnimationController().getAnimationCoefficients(animationParam.paramName));
                        sliderCell.setDurationTime(currentFragment.getAnimationController().getAnimationDuration(animationParam.paramName));
                        sliderCell.setDelegate(new AnimationCubicBezierSlider.AnimationCubicBezierSliderDelegate() {
                            @Override
                            public void onChangeParams(AnimationCubicBezierSlider.AnimationParams params) {
                                currentFragment.getAnimationController().saveAnimationCoefficients(animationParam.paramName, params);
                            }
                        });
                    }
                } else if (settingRows instanceof DurationParam) {
                    DurationParam durationParam = (DurationParam) settingRows;
                    if (type == PARAMETER_VIEW_TYPE) {
                        TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                        textCell.setText("Duration", true);
                        textCell.setValue(String.format("%dms", currentFragment.getAnimationController().getAnimationDuration(durationParam.animationParams[0].paramName)));
                    }
                }
            } else if (type == HEADER_VIEW_TYPE) {
                HeaderCell headerCell = (HeaderCell) holder.itemView;
                if (position == backgroundPreviewHeaderRow) {
                    headerCell.setText("Background Preview");
                    headerCell.setBottomMargin();
                } else if (position == colorsHeaderRow) {
                    headerCell.setText("Colors");
                }
            } else if (type == BACKGROUND_VIEW_TYPE) {
                ChatBackgroundView backgroundView = (ChatBackgroundView) holder.itemView;
                int colors[] = new int[4];
                currentFragment.getAnimationController().getColors(colors);
                backgroundView.setColors(colors[0], colors[1], colors[2], colors[3]);
                //backgroundView.reinit();
                backgroundView.redraw();
            } else if (type == BUTTON_VIEW_TYPE) {
                TextCell cell = (TextCell) holder.itemView;
                if (position == backgroundPreviewFullscreenRow) {
                    cell.setText("Open Full Screen", false);
                    cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
                }
            } else if (type == COLOR_VIEW_TYPE) {
                SetColorCell colorCell = (SetColorCell) holder.itemView;
                int number = 0;
                if (position == color1Row) {number = 0;}
                else if (position == color2Row) {number = 1;}
                else if (position == color3Row) {number = 2;}
                else if (position == color4Row) {number = 3;}
                int color = currentFragment.getAnimationController().getColor(number);
                colorCell.setColorId(number);
                colorCell.setTextAndValue("Color " + number, color, true);
            } else if (type == EMPTY_VIEW_TYPE) {
                holder.itemView.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
            }
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }
    }
}
