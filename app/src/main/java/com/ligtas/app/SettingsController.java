package com.ligtas.app;

import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.List;
import java.util.function.Supplier;

/** The Settings sheet: language, alert readiness and sign out. Opened from the profile button for every role. */
final class SettingsController {
    private final AppCompatActivity activity;
    private final Supplier<String> email;
    private final Supplier<String> role;
    private final Runnable onLanguageChanged;
    private final Runnable onSignOut;
    private final Runnable onReadiness;

    SettingsController(AppCompatActivity activity, Supplier<String> email, Supplier<String> role,
                       Runnable onLanguageChanged, Runnable onSignOut, Runnable onReadiness) {
        this.activity = activity;
        this.email = email;
        this.role = role;
        this.onLanguageChanged = onLanguageChanged;
        this.onSignOut = onSignOut;
        this.onReadiness = onReadiness;
    }

    void show() {
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.getBehavior().setSkipCollapsed(true);

        final SettingsState st = new SettingsState();
        st.setEmail(email.get());
        String r = role.get();
        st.setRole("ADMIN".equals(r) ? "Admin" : "TEACHER".equals(r) ? "Teacher" : "Student");

        // The readiness line follows what the person fixes in the system settings while this sheet stays open
        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable refresh = new Runnable() {
            @Override public void run() {
                List<PermissionHelper.Problem> ps = PermissionHelper.problems(activity);
                st.setProblems(ps.size());
                st.setCritical(PermissionHelper.hasCriticalProblem(ps));
                String dnd = ps.isEmpty() ? PermissionHelper.activeDndMode(activity) : null;
                st.setDnd(dnd == null ? "" : dnd);
                handler.postDelayed(this, 1500);
            }
        };
        refresh.run();
        dialog.setOnDismissListener(d -> handler.removeCallbacks(refresh));

        SettingsListener listener = new SettingsListener() {
            @Override public void onLanguage(boolean taglish) {
                Lang.set(activity, taglish);
                onLanguageChanged.run();   // texts that Java builds are rebuilt in the new language
            }
            @Override public void onReadiness() { onReadiness.run(); }
            @Override public void onSignOut() { dialog.dismiss(); onSignOut.run(); }
            @Override public void onClose() { dialog.dismiss(); }
        };
        SettingsBridge.attach(dialog, activity, st, listener);
        SheetUi.makeTransparent(dialog);
        dialog.show();
    }
}
