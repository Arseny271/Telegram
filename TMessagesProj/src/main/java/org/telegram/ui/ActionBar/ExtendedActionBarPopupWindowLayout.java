/*
* This is the source code of Telegram for Android v. 5.x.x.
* It is licensed under GNU GPL v. 2 or later.
* You should have received a copy of the license in this archive (see LICENSE).
*
* Copyright Nikolai Kudashov, 2013-2018.
*/

package org.telegram.ui.ActionBar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public class ExtendedActionBarPopupWindowLayout extends FrameLayout {

    private Context context;

    private FrameLayout reactionsFrameLayout;
    private HorizontalScrollView reactionsScrollView;
    private LinearLayout reactionsLinearLayout;

    private RectF reactionsRect = new RectF();
    private Path reactionsPath = new Path();

    private ViewGroup linearLayout;
    private Drawable shadowDrawable;

    Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private ArrayList<TLRPC.TL_availableReaction> availableReactionsList = new ArrayList<>();
    private int availableReactionsListWidth;

    public interface ReactionTapDelegate {
        void onReactionTapDelegate(String reaction, int x, int y);
        void onWindowClose();
    }

    private ReactionTapDelegate delegate;

    public ExtendedActionBarPopupWindowLayout(Context context, ViewGroup linearLayout) {
        super(context);
        this.context = context;
        this.linearLayout = linearLayout;

        bubblePaint.setColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground));
        shadowPaint.setColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground));
        shadowPaint.setShadowLayer(3, 0, 0, 0x60000000);

        setLayerType(LAYER_TYPE_SOFTWARE, shadowPaint);

        reactionsLinearLayout = new LinearLayout(context);
        reactionsLinearLayout.setOrientation(LinearLayout.HORIZONTAL);
        reactionsLinearLayout.setPadding(AndroidUtilities.dp(5), 0, AndroidUtilities.dp(5), 0);

        reactionsScrollView = new HorizontalScrollView(context);
        reactionsScrollView.setHorizontalScrollBarEnabled(false);
        reactionsScrollView.setWillNotDraw(false);
        reactionsScrollView.addView(reactionsLinearLayout, new ScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        reactionsFrameLayout = new FrameLayout(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                reactionsPath.reset();
                reactionsRect.set(0, 0, getWidth(), getHeight());
                reactionsPath.addRoundRect(reactionsRect, AndroidUtilities.dp(38/2f), AndroidUtilities.dp(38/2f), Path.Direction.CW);
                reactionsPath.close();

                canvas.save();
                canvas.clipPath(reactionsPath);
                super.onDraw(canvas);
                canvas.restore();
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                reactionsPath.reset();
                reactionsRect.set(0, 0, getWidth(), getHeight());
                reactionsPath.addRoundRect(reactionsRect, AndroidUtilities.dp(38/2f), AndroidUtilities.dp(38/2f), Path.Direction.CW);
                reactionsPath.close();

                canvas.save();
                canvas.clipPath(reactionsPath);
                boolean result = super.drawChild(canvas, child, drawingTime);
                canvas.restore();

                return result;
            }
        };
        reactionsFrameLayout.addView(reactionsScrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 0, 53, 44, 0));
        addView(reactionsFrameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 46, Gravity.TOP | Gravity.RIGHT, 0, 8, 8, 0));

        shadowDrawable = context.getResources().getDrawable(R.drawable.lock_round_shadow).mutate();
        shadowDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelVoiceLockShadow), PorterDuff.Mode.MULTIPLY));

        setWillNotDraw(false);
    }

    public void setAvailableReactionsList(ArrayList<TLRPC.TL_availableReaction> availableReactionsList) {
        this.availableReactionsList = availableReactionsList;

        for (TLRPC.TL_availableReaction availableReaction : availableReactionsList) {
            TLRPC.Document document = availableReaction.activate_animation;
            BackupImageView imageView = new BackupImageView(context);
            imageView.setAspectFit(true);
            imageView.setLayerNum(1);
            imageView.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), AndroidUtilities.dp(45/2f), AndroidUtilities.dp(45/2f)));
            imageView.setOnClickListener(v -> {
                if (delegate != null) {
                    int x, y;
                    int[] position = new int[2];
                    getLocationInWindow(position);
                    x = -position[0];
                    y = -position[1];

                    v.getLocationInWindow(position);
                    x += position[0];
                    y += position[1];

                    delegate.onReactionTapDelegate(availableReaction.reaction, x, y);
                }
            });


            Object parentObject = availableReactionsList;
            if (document != null) {
                TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90);
                SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(document, Theme.key_windowBackgroundGray, 1.0f);
                if (svgThumb != null) {
                    imageView.setImage(ImageLocation.getForDocument(document), "80_80", null, svgThumb, parentObject);
                } else if (thumb != null) {
                    imageView.setImage(ImageLocation.getForDocument(document), "80_80", ImageLocation.getForDocument(thumb, document), null, 0, parentObject);
                } else {
                    imageView.setImage(ImageLocation.getForDocument(document), "80_80", null, null, parentObject);
                }
            }

            imageView.setSize(AndroidUtilities.dp(35), AndroidUtilities.dp(35));
            reactionsLinearLayout.addView(imageView, LayoutHelper.createFrame(45, 45, Gravity.CENTER, 0, 0, 0, 0));
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        canvas.drawCircle(getMeasuredWidth() - AndroidUtilities.dp(33), AndroidUtilities.dp(58 + 8), AndroidUtilities.dp(4), shadowPaint);
        canvas.drawCircle(getMeasuredWidth() - AndroidUtilities.dp(33 + 5), AndroidUtilities.dp(45 + 8), AndroidUtilities.dp(7), shadowPaint);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            canvas.drawRoundRect(getMeasuredWidth() - AndroidUtilities.dp(8) - availableReactionsListWidth, AndroidUtilities.dp(8), getMeasuredWidth() - AndroidUtilities.dp(8), AndroidUtilities.dp(45 + 8), AndroidUtilities.dp(18), AndroidUtilities.dp(18), bubblePaint);
        } else {
            canvas.drawRect(getMeasuredWidth() - AndroidUtilities.dp(8) - availableReactionsListWidth, AndroidUtilities.dp(8), getMeasuredWidth() - AndroidUtilities.dp(8), AndroidUtilities.dp(45 + 8), bubblePaint);
        }

        shadowDrawable.setBounds(getMeasuredWidth() - AndroidUtilities.dp(11) - availableReactionsListWidth, AndroidUtilities.dp(8 - 3), getMeasuredWidth() - AndroidUtilities.dp(5), AndroidUtilities.dp(45 + 8 + 3));
        shadowDrawable.draw(canvas);

        canvas.drawCircle(getMeasuredWidth() - AndroidUtilities.dp(33), AndroidUtilities.dp(58 + 8), AndroidUtilities.dp(4), bubblePaint);
        canvas.drawCircle(getMeasuredWidth() - AndroidUtilities.dp(33 + 5), AndroidUtilities.dp(45 + 8), AndroidUtilities.dp(7), bubblePaint);

        super.dispatchDraw(canvas);
    }

    public void setDelegate(ReactionTapDelegate delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        float x1 = getMeasuredWidth() - AndroidUtilities.dp(36);
        float x2 = getMeasuredWidth();
        float y1 = AndroidUtilities.dp(61);
        float y2 = getMeasuredHeight();

        if (event != null && x1 < x && x < x2 && y1 < x && x < y2 && event.getAction() == MotionEvent.ACTION_DOWN) {
            delegate.onWindowClose();
            return true;
        }

        return super.onTouchEvent(event);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        if (linearLayout != null) {
            int maxWidth = linearLayout.getMeasuredWidth();
            setMeasuredDimension(maxWidth + AndroidUtilities.dp(36), getMeasuredHeight());

            availableReactionsListWidth = Math.min(maxWidth + AndroidUtilities.dp(25), availableReactionsList.size() * AndroidUtilities.dp(45) + AndroidUtilities.dp(10));

            reactionsFrameLayout.measure(
                MeasureSpec.makeMeasureSpec(availableReactionsListWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(45), MeasureSpec.EXACTLY)
            );

            reactionsScrollView.measure(
                    MeasureSpec.makeMeasureSpec(availableReactionsListWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(45), MeasureSpec.EXACTLY)
            );
        }
    }
}