package com.ligtas.app;

import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.function.IntConsumer;

/** Small helpers shared by every Compose bottom sheet in the app. */
final class SheetUi {
    private SheetUi() { }

    /** Keeps the dark glass look: no white box behind a Compose bottom sheet. */
    static void makeTransparent(BottomSheetDialog dialog) {
        dialog.setOnShowListener(d -> {
            View container = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (container != null) container.setBackgroundResource(android.R.color.transparent);
        });
    }

    /**
     * The app's own confirmation sheet (replaces the plain system dialogs).
     * kinds: PromptKind.PRIMARY / DANGER / GHOST, one per label. onChoice gets the tapped button's index.
     */
    static void prompt(AppCompatActivity activity, String title, String message, String[] labels, int[] kinds,
                       boolean cancelable, IntConsumer onChoice, Runnable onDismiss) {
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        dialog.setCancelable(cancelable);
        dialog.setCanceledOnTouchOutside(cancelable);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.getBehavior().setSkipCollapsed(true);
        PromptBridge.attach(dialog, activity, title, message, labels, kinds, index -> {
            dialog.dismiss();
            if (onChoice != null) onChoice.accept(index);
        });
        makeTransparent(dialog);
        if (onDismiss != null) dialog.setOnDismissListener(d -> onDismiss.run());
        dialog.show();
    }
}
