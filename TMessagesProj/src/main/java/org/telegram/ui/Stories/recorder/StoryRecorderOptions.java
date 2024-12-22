package org.telegram.ui.Stories.recorder;

import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;

public class StoryRecorderOptions {
    final StoryRecorder.SourceView sourceView;
    final CharSequence titlePreviewOverride;
    final CharSequence buttonDoneTextOverride;
    final Utilities.Callback<StoryEntry> sendStoryCallback;
    final long sendDialogId;
    final int backButtonIconRes;
    final boolean buttonDoneTextArrow;
    final boolean allowRatioChange;
    final boolean isChatAttachMode;
    final boolean disallowRecordHevc;

    private StoryRecorderOptions(
        StoryRecorder.SourceView sourceView,
        CharSequence titlePreviewOverride,
        CharSequence buttonDoneTextOverride,
        Utilities.Callback<StoryEntry> sendStoryCallback,
        long sendDialogId,
        int backButtonIconRes,
        boolean buttonDoneTextArrow,
        boolean isChatAttachMode,
        boolean disallowRecordHevc
    ) {
        this.sourceView = sourceView;
        this.titlePreviewOverride = titlePreviewOverride;
        this.buttonDoneTextOverride = buttonDoneTextOverride;
        this.sendStoryCallback = sendStoryCallback;
        this.buttonDoneTextArrow = buttonDoneTextArrow;
        this.backButtonIconRes = backButtonIconRes;
        this.isChatAttachMode = isChatAttachMode;
        this.allowRatioChange = isChatAttachMode;
        this.disallowRecordHevc = disallowRecordHevc;
        this.sendDialogId = sendDialogId;
    }

    public static StoryRecorderOptions getDefault(StoryRecorder.SourceView sourceView) {
        return new Builder().setSourceView(sourceView).build();
    }

    public static class Builder {
        private StoryRecorder.SourceView sourceView;
        private CharSequence previewTitleOverride;
        private CharSequence buttonDoneTextOverride;
        private Utilities.Callback<StoryEntry> sendStoryCallback;
        private long dialogId;
        private int backButtonIconRes = R.drawable.msg_photo_back;
        private boolean buttonDoneTextArrow;
        private boolean isChatAttachMode;
        private boolean disallowRecordHevc;

        public Builder disallowRecordHevc() {
            disallowRecordHevc = true;
            return this;
        }

        public Builder setDialogId(long dialogId) {
            this.dialogId = dialogId;
            return this;
        }
        
        public Builder setTitlePreview(CharSequence title) {
            this.previewTitleOverride = title;
            return this;
        }

        public Builder setButtonDoneText(CharSequence text, boolean arrow) {
            this.buttonDoneTextOverride = text;
            this.buttonDoneTextArrow = arrow;
            return this;
        }

        public Builder callback(Utilities.Callback<StoryEntry> sendStoryCallback) {
            this.sendStoryCallback = sendStoryCallback;
            return this;
        }

        public Builder setBackButtonIcon(int icon) {
            this.backButtonIconRes = icon;
            return this;
        }

        public Builder setSourceView(StoryRecorder.SourceView sourceView) {
            this.sourceView = sourceView;
            return this;
        }

        public Builder isChatAttachMode(boolean isChatAttachMode) {
            this.isChatAttachMode = isChatAttachMode;
            return this;
        }

        public StoryRecorderOptions build() {
            return new StoryRecorderOptions(
                sourceView,
                previewTitleOverride,
                buttonDoneTextOverride,
                sendStoryCallback,
                dialogId,
                backButtonIconRes,
                buttonDoneTextArrow,
                isChatAttachMode,
                disallowRecordHevc
            );
        }
    }
}
