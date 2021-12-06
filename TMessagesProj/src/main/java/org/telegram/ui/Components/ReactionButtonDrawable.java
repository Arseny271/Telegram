package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.SvgHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.GroupCallUserCell;

import java.util.ArrayList;

public class ReactionButtonDrawable {

    private final Paint buttonBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint buttonTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint buttonSelectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private View parentView;
    private StaticLayout buttonTextLayout;

    private StaticLayout buttonTextCommonLayout;
    private StaticLayout buttonTextNewLayout;
    private StaticLayout buttonTextOldLayout;

    private ImageReceiver imageView;
    private int currentAccount;

    private ArrayList<AvatarDrawable> avatarDrawables = new ArrayList<>();
    private ArrayList<ImageReceiver> imageReceivers = new ArrayList<>();

    private float selected;

    float x = 0f;
    float y = 0f;
    float width = 0f;
    float height = 0f;
    float scale = 1f;
    private RectF rect = new RectF();

    float textWidth = 0;
    float commonWidth = 0;
    float textProgress = 1f;

    private String currentText = "";
    private Paint xRefP = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ReactionButtonDrawable(View view) {
        this(view, -1);
    }

    public ReactionButtonDrawable(View view, int currentAccount) {
        parentView = view;
        imageView = new ImageReceiver(view);
        this.currentAccount = currentAccount;

        buttonTextPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        buttonTextPaint.setTextSize(AndroidUtilities.dp(13));
        buttonTextPaint.setColor(Theme.getColor(Theme.key_chat_botButtonText));

        buttonSelectedPaint.setColor(Theme.getColor(Theme.key_chat_botButtonText));
        buttonSelectedPaint.setStrokeWidth(AndroidUtilities.dp(1.2f));
        buttonSelectedPaint.setStyle(Paint.Style.STROKE);

        xRefP.setColor(0);
        xRefP.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

    }

    public void setRecentUsers(ArrayList<TLRPC.User> recentUsers) {
        avatarDrawables.clear();
        imageReceivers.clear();

        if (recentUsers == null) {
            return;
        }

        for (int a = 0; a < recentUsers.size(); a++) {
            TLRPC.User user = recentUsers.get(a);

            AvatarDrawable avatarDrawable = new AvatarDrawable();
            ImageReceiver imageReceiver = new ImageReceiver(parentView);

            avatarDrawable.setInfo(user);
            imageReceiver.setForUserOrChat(user, avatarDrawable);

            avatarDrawables.add(avatarDrawable);
            imageReceivers.add(imageReceiver);
        }
    }

    public void setText(CharSequence newText) {
        setText(newText, newText);
    }

    public void setText(CharSequence newText, CharSequence oldText) {
        StringBuilder commonPartBuilder = new StringBuilder();
        StringBuilder newPartBuilder = new StringBuilder();
        StringBuilder oldPartBuilder = new StringBuilder();

        int length = 0;
        for (int a = 0; a < Math.min(newText.length(), oldText.length()); a++) {
            char c1 = newText.charAt(a);
            char c2 = oldText.charAt(a);
            if (c1 != c2) {
                break;
            }

            commonPartBuilder.append(c1);
            length++;
        }

        oldPartBuilder.append(oldText, length, oldText.length());
        newPartBuilder.append(newText, length, newText.length());

        String commonPart = commonPartBuilder.toString();
        String newPart = newPartBuilder.toString();
        String oldPart = oldPartBuilder.toString();

        buttonTextLayout = new StaticLayout(newText, buttonTextPaint, AndroidUtilities.dp(100), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        buttonTextCommonLayout = new StaticLayout(commonPart, buttonTextPaint, AndroidUtilities.dp(100), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);;
        buttonTextNewLayout = new StaticLayout(newPart, buttonTextPaint, AndroidUtilities.dp(100), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        buttonTextOldLayout = new StaticLayout(oldPart, buttonTextPaint, AndroidUtilities.dp(100), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        textWidth = buttonTextPaint.measureText((String)newText);
        if (length > 0) {
            commonWidth = buttonTextPaint.measureText(commonPart);
        } else {
            commonWidth = 0;
        }

        currentText = (String) newText;
    }

    public String getCurrentText() {
        return currentText;
    }

    public float getButtonWidth() {
        if (avatarDrawables.isEmpty()) {
            return AndroidUtilities.dp(38) + textWidth;
        } else {
            return AndroidUtilities.dp(33 + 21 + (avatarDrawables.size() - 1) * 13);
        }
    }

    public void setSelectedAlpha(float selected) {
        this.selected = selected;
    }

    public void setTextProgress(float progress) {
        textProgress = progress;
    }

    public void setImage(TLRPC.Document document) {
        if (document != null) {
            TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90);
            SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(document, Theme.key_windowBackgroundGray, 1.0f);
            imageView.setImage(ImageLocation.getForDocument(thumb, document), null, null, null, svgThumb, 0, null, null, 0);
        }
    }

    int oldBackgroundColor = 0;

    public void setColors(int backgroundColor, int textColor) {
        if (oldBackgroundColor != backgroundColor) {
            oldBackgroundColor = backgroundColor;
        }
        buttonBackgroundPaint.setColor(backgroundColor);
        buttonTextPaint.setColor(textColor);
        buttonSelectedPaint.setColor(textColor);
    }

    public void setButtonCords(float x, float y, float width, float height) {
        this.scale = 1f;
        this.width = width;
        this.height = height;
        this.x = x;
        this.y = y;
    }

    public void setScale(float scale) {
        this.scale = scale;
    }

    public ImageReceiver getImageView() {
        return imageView;
    }

    public boolean draw(Canvas canvas) {
        if (buttonTextLayout == null || buttonTextCommonLayout == null || buttonTextNewLayout == null || buttonTextOldLayout == null) {
            return true;
        }

        rect.set(x, y, x + width, y + height);

        canvas.drawRoundRect(rect, height / 2f, height / 2f, buttonBackgroundPaint);
        if (selected > 0) {
            buttonSelectedPaint.setAlpha((int) (selected * 255));
            canvas.drawRoundRect(rect, height / 2f, height / 2f, buttonSelectedPaint);
        }

        canvas.save();
        canvas.clipRect(rect);
        canvas.scale(scale, scale, x + width / 2, y + height / 2);

        imageView.setImageCoords(x + AndroidUtilities.dp(6), y + AndroidUtilities.dp(3), AndroidUtilities.dp(20), AndroidUtilities.dp(20));
        imageView.draw(canvas);

        float baseTextX = x + AndroidUtilities.dp(30);
        float baseTextY = y + (height - buttonTextLayout.getLineBottom(buttonTextLayout.getLineCount() - 1)) / 2;
        float baseTextH = AndroidUtilities.dp(14);

        canvas.save();
        canvas.translate(baseTextX, baseTextY);
        buttonTextPaint.setAlpha(255);
        buttonTextCommonLayout.draw(canvas);
        canvas.restore();

        canvas.save();
        canvas.translate(baseTextX + commonWidth, baseTextY + baseTextH * (1f - textProgress));
        buttonTextPaint.setAlpha((int)(255 * textProgress));
        buttonTextNewLayout.draw(canvas);
        canvas.restore();

        canvas.save();
        canvas.translate(baseTextX + commonWidth, baseTextY - baseTextH * textProgress);
        buttonTextPaint.setAlpha(255 - (int)(255 * textProgress));
        buttonTextOldLayout.draw(canvas);
        canvas.restore();

        canvas.restore();


        canvas.saveLayerAlpha(rect, 255, Canvas.ALL_SAVE_FLAG);

        for (int a = imageReceivers.size() - 1; a >= 0; a--) {
            ImageReceiver imageReceiver = imageReceivers.get(a);
            float offset = AndroidUtilities.dp(a * 13);

            if (imageReceiver.hasImageSet()) {
                imageReceiver.setImageCoords(x + AndroidUtilities.dp(30) + offset, y + AndroidUtilities.dp(2.5f), AndroidUtilities.dp(21), AndroidUtilities.dp(21));
                imageReceiver.setRoundRadius(AndroidUtilities.dp(10.5f));
                canvas.drawCircle(imageReceiver.getCenterX(), imageReceiver.getCenterY(), AndroidUtilities.dp(11.5f), xRefP);
                imageReceiver.draw(canvas);
            }
        }

        canvas.restore();

        return true;
    }



}
