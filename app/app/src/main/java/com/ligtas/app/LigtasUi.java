package com.ligtas.app;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;

/** The dark navy glass look: dark system bars, the Inter font everywhere, glass sheets and dropdowns. */
public final class LigtasUi {
    private static Typeface regular, medium, semibold;

    private LigtasUi() { }

    /** Dark status and navigation bars with light icons, then the font on every view of the screen. */
    public static void applyChrome(Activity a) {
        Window w = a.getWindow();
        w.setStatusBarColor(0xFF090F1C);
        w.setNavigationBarColor(0xFF090F1C);
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(w, w.getDecorView());
        c.setAppearanceLightStatusBars(false);
        c.setAppearanceLightNavigationBars(false);
        style(a.findViewById(android.R.id.content));
        padForSystemBars(a);
        // The slow "alert broadcast" background, behind everything (LigtasBackdrop.kt)
        if (a instanceof androidx.activity.ComponentActivity) BackdropBridge.attach((androidx.activity.ComponentActivity) a);
    }

    /**
     * Android 15+ draws apps edge to edge (behind the status bar, camera cut-out and gesture bar).
     * This pads the screen's root view by the size of those bars, so the header is never covered.
     * The root keeps its own padding; the background still reaches the screen edges.
     */
    private static void padForSystemBars(Activity a) {
        ViewGroup content = a.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0) return;
        final View root = content.getChildAt(0);
        final int l = root.getPaddingLeft(), t = root.getPaddingTop(), r = root.getPaddingRight(), b = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(l + bars.left, t + bars.top, r + bars.right, b + bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private static void load(Context ctx) {
        if (regular != null) return;
        regular = ResourcesCompat.getFont(ctx, R.font.inter_regular);
        medium = ResourcesCompat.getFont(ctx, R.font.inter_medium);
        semibold = ResourcesCompat.getFont(ctx, R.font.inter_semibold);
    }

    /** Sets Inter on every text view under root (bold text becomes semibold) and styles spinner pop-ups. Safe to call again. */
    public static void style(View root) {
        if (root == null) return;
        load(root.getContext());
        walk(root);
    }

    private static void walk(View v) {
        if (v instanceof Spinner) {
            ((Spinner) v).setPopupBackgroundResource(R.drawable.lg_popup);
        }
        if (v instanceof TextView && regular != null) {
            TextView t = (TextView) v;
            Typeface cur = t.getTypeface();
            boolean bold = cur != null && cur.isBold();
            t.setTypeface(bold ? semibold : (t instanceof android.widget.Button ? medium : regular));
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) walk(g.getChildAt(i));
        }
    }

    /** A bottom sheet: show the glass background (not a white box behind it) and apply the font. */
    public static void sheet(BottomSheetDialog dialog, View content) {
        style(content);
        dialog.setOnShowListener(d -> {
            View container = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (container != null) container.setBackgroundResource(android.R.color.transparent);
        });
    }
}
