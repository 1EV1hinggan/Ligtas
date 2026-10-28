package com.ligtas.app;

import android.Manifest;
import android.content.Intent;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * "Alert readiness": the "Let alerts reach you" sheet (notifications, vibration, lock screen, Do Not Disturb,
 * battery, autostart) and the reminders that come back when one of them was turned off.
 * Moved out of MainActivity; the behaviour is unchanged.
 */
final class SetupController {
    private final AppCompatActivity activity;
    private final Supplier<String> userEmail;   // the roster email, known only after sign-in
    private final Runnable onClosed;            // called when the sheet closes so the home screen can refresh

    private BottomSheetDialog dialog;           // the sheet, when open
    private SetupSheetState state;              // what the sheet shows
    private final List<Runnable> fixes = new ArrayList<>();
    private final List<Runnable> confirms = new ArrayList<>();
    private boolean reminderShowing = false;
    private long lastCriticalReminder = 0;

    SetupController(AppCompatActivity activity, Supplier<String> userEmail, Runnable onClosed) {
        this.activity = activity;
        this.userEmail = userEmail;
        this.onClosed = onClosed;
    }

    boolean isShowing() {
        return dialog != null && dialog.isShowing();
    }

    void show() {
        if (isShowing()) return;
        dialog = new BottomSheetDialog(activity);
        state = new SetupSheetState();
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.getBehavior().setSkipCollapsed(true);
        SetupSheetBridge.attach(dialog, activity, state, new SetupSheetListener() {
            @Override public void onFix(int index) {
                if (index < fixes.size()) fixes.get(index).run();
            }
            @Override public void onConfirmed(int index) { if (index < confirms.size()) confirms.get(index).run(); }
            @Override public void onDone() { dialog.dismiss(); }
        });
        dialog.setOnDismissListener(d -> {
            PermissionHelper.markSetupDone(activity, userEmail.get()); // do not ask again automatically
            dialog = null;
            state = null;
            onClosed.run();
        });
        SheetUi.makeTransparent(dialog);
        refreshRows();
        dialog.show();
    }

    /** Rebuilds the rows with the current state (also called when returning from a settings screen). */
    void refreshRows() {
        if (dialog == null || state == null) return;
        state.getRows().clear();
        fixes.clear();
        confirms.clear();

        addRow(Lang.pick("Notifications", "Notifications"),
                Lang.pick("Without this, emergency alerts cannot appear on your phone at all.",
                        "Kung walang notifications, hindi lalabas sa phone mo ang emergency alerts."),
                PermissionHelper.notificationsAllowed(activity), Lang.pick("Turn on", "I-on"), this::fixNotifications, false);
        addRow(Lang.pick("Alert vibration", "Alert vibration"),
                Lang.pick("Keeps alerts vibrating even if your phone is on silent. If the alert channel is muted, you could miss an emergency.",
                        "Nagva-vibrate pa rin ang alerts kahit naka-silent ang phone. Kapag naka-mute ang alert channel, baka hindi mo marinig o maramdaman ang emergency."),
                PermissionHelper.channelHealthy(activity), Lang.pick("Fix", "Ayusin"), () -> startSettings(PermissionHelper.channelSettingsIntent(activity)), false);
        addRow(Lang.pick("Show on lock screen", "Ipakita sa lock screen"),
                Lang.pick("Lets you see an emergency alert without unlocking your phone. Also check that your phone's main lock screen setting shows notifications: Ligtas cannot see that one.",
                        "Makikita mo ang emergency alert kahit naka-lock ang phone. I-check din na naka-show ang notifications sa main lock screen setting ng phone mo: hindi ito nakikita ng Ligtas."),
                !PermissionHelper.lockscreenHidden(activity), Lang.pick("Fix", "Ayusin"), () -> startSettings(PermissionHelper.channelSettingsIntent(activity)), false);
        addRow(Lang.pick("Allow during Do Not Disturb", "Payagan kahit naka-Do Not Disturb"),
                Lang.pick("Do Not Disturb silences most notifications. On the next screen, find Ligtas in the list and turn it on so emergency alerts come through.",
                        "Pinatatahimik ng Do Not Disturb ang karamihan ng notifications. Sa susunod na screen, hanapin ang Ligtas sa listahan at i-on ito para makalusot ang emergency alerts."),
                PermissionHelper.canBypassDnd(activity), Lang.pick("Allow", "Payagan"), () -> startSettings(PermissionHelper.dndSettingsIntent()), false);
        String batteryWhy = Lang.pick("Your phone may delay or block alerts to save battery, especially when the app is closed.",
                "Maaaring i-delay o i-block ng phone mo ang alerts para makatipid ng battery, lalo na kapag sarado ang app.");
        if (PermissionHelper.isXiaomiFamily()) {
            batteryWhy += Lang.pick(" On Xiaomi, Redmi and Poco: also open Settings > Apps > Manage apps > Ligtas > Battery saver and choose No restrictions.",
                    " Sa Xiaomi, Redmi at Poco: buksan din ang Settings > Apps > Manage apps > Ligtas > Battery saver at piliin ang No restrictions.");
        }
        addRow(Lang.pick("Run without battery limits", "Tumakbo nang walang battery limits"),
                batteryWhy,
                PermissionHelper.batteryUnrestricted(activity), Lang.pick("Allow", "Payagan"), this::fixBattery, false);
        if (PermissionHelper.needsAutostartStep()) {
            // The "I turned it on" button only appears after the person has opened the Autostart screen from here
            boolean confirmed = PermissionHelper.autostartConfirmed(activity);
            addRow(Lang.pick("Autostart", "Autostart"),
                    Lang.pick("On Xiaomi, Oppo, Vivo, Realme and similar phones, Ligtas can receive alerts while closed only if Autostart is on. "
                                    + "1) Tap Open settings. 2) Switch Ligtas ON. 3) Come back here and confirm. Ligtas cannot check this for you.",
                            "Sa Xiaomi, Oppo, Vivo, Realme at kahawig na phone, makakatanggap lang ng alerts ang Ligtas kahit sarado ito kung naka-ON ang Autostart. "
                                    + "1) I-tap ang Open settings. 2) I-ON ang Ligtas. 3) Bumalik dito at i-confirm. Hindi ito matsek ng Ligtas."),
                    confirmed, Lang.pick("Open settings", "Buksan ang settings"), this::fixAutostart,
                    !confirmed && PermissionHelper.autostartOpened(activity));
        }
    }

    /** One row of the setup sheet. */
    private void addRow(String title, String why, boolean ok, String buttonText, Runnable fix, boolean needsConfirm) {
        state.getRows().add(new SetupRow(title, why, ok, buttonText, needsConfirm));
        fixes.add(fix);
        confirms.add(() -> { PermissionHelper.setAutostartConfirmed(activity, true); refreshRows(); });
    }

    /** Reminds the person when something that alerts depend on has been turned off since last time. */
    void maybeRemind() {
        if (reminderShowing || isShowing()) return;
        if (!PermissionHelper.setupDoneFor(activity, userEmail.get())) return; // first-time sheet comes first

        List<PermissionHelper.Problem> problems = PermissionHelper.problems(activity);
        if (problems.isEmpty()) return;

        long now = System.currentTimeMillis();
        boolean critical = PermissionHelper.hasCriticalProblem(problems);
        if (critical) {
            if (now - lastCriticalReminder < 2 * 60 * 1000L) return;      // not again within 2 minutes
        } else {
            if (now - PermissionHelper.lastNormalReminder(activity) < 24L * 60 * 60 * 1000L) return; // once a day
        }

        StringBuilder message = new StringBuilder();
        for (PermissionHelper.Problem p : problems) message.append("• ").append(p.message).append("\n\n");
        message.append("Emergency alerts only work when these are on.");

        reminderShowing = true;
        SheetUi.prompt(activity, critical ? "Alerts may not reach you" : "Improve alert reliability", message.toString(),
                new String[]{"Fix now", "Later"}, new int[]{PromptKind.PRIMARY, PromptKind.GHOST}, true,
                i -> { if (i == 0) show(); },
                () -> {
                    reminderShowing = false;
                    if (critical) lastCriticalReminder = System.currentTimeMillis();
                    else PermissionHelper.markNormalReminder(activity);
                });
    }

    // ---- the fixes ----
    private void startSettings(Intent intent) {
        try {
            activity.startActivity(intent);
        } catch (Exception e) {
            try {
                activity.startActivity(PermissionHelper.appDetailsIntent(activity));
            } catch (Exception ignored) {
                Toast.makeText(activity, "Open Settings > Apps > Ligtas to change this.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void fixNotifications() {
        if (!PermissionHelper.notificationPermissionGranted(activity)
                && !PermissionHelper.askedNotificationPermission(activity)) {
            PermissionHelper.markAskedNotificationPermission(activity);
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        } else {
            startSettings(PermissionHelper.notificationSettingsIntent(activity));
        }
    }

    private void fixBattery() {
        try {
            activity.startActivity(PermissionHelper.batteryRequestIntent(activity));
        } catch (Exception e) {
            startSettings(PermissionHelper.batteryListIntent());
        }
    }

    private void fixAutostart() {
        PermissionHelper.setAutostartOpened(activity, true);
        for (Intent i : PermissionHelper.autostartIntents(activity)) {
            try {
                activity.startActivity(i);
                return;
            } catch (Exception ignored) {
                // try the next one
            }
        }
    }
}
