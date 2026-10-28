package com.ligtas.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.compose.ui.platform.ComposeView;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private User currentUser;

    // Who is signed in (filled from the roster by LoginActivity)
    private String userRole = "";
    private String userEmail = "";
    private String userBuilding = "";
    private String userDepartment = "";   // college (older roster rows called it department)
    private String userProgram = "";
    private List<String> userTeaches = new ArrayList<>();   // teachers: "BSIT-2", "BSME" ...
    private int userYear = 0;
    private String userCampus = "";       // campus id from the roster ("" = the only campus)
    private String campusId = "";         // resolved campus id (used for topics)
    private SharedPreferences.OnSharedPreferenceChangeListener lastAlertListener; // must stay referenced
    // Helpers that used to live in this file (see SetupController.java and SuspendController.java)
    private final SetupController setup = new SetupController(this, () -> userEmail, this::refreshReadiness);
    private final SuspendController suspend = new SuspendController(this);
    // Settings: language, alert readiness and sign out (opened from the profile button)
    private final SettingsController settings = new SettingsController(this, () -> userEmail, () -> userRole,
            this::onLanguageChanged, this::confirmSignOut, () -> setup.show());
    private final RoomReportController roomReports = new RoomReportController(this, () -> userEmail, () -> currentUser.getName(), this::refreshRollCall);
    private final StatusController status = new StatusController(this, () -> userEmail, () -> currentUser.getName());
    private final AlertDispatcher dispatcher = new AlertDispatcher(this, new AlertDispatcher.Host() {
        @Override public boolean hasTemplate() { return selectedTemplate != null; }
        @Override public Alert buildAlert(String alertId) { return MainActivity.this.buildAlert(alertId); }
        @Override public Alert buildAlertTl(String alertId) { return MainActivity.this.buildAlertTl(alertId); }
        @Override public Audience audience() { return currentAudience(); }
        @Override public User user() { return currentUser; }
        @Override public String userEmail() { return userEmail; }
        @Override public String campusId() { return campusId; }
        @Override public void onRecorded(String alertId) { lastAlertId = alertId; refreshStatusButton(); }
        @Override public void showStatus(String alertId) { status.show(alertId); }
        @Override public void onDispatched() { clearSelection(); }   // ready for the next alert, prevents accidental repeats
    });

    // Shared variables
    private String fcmToken = "Fetching token...";
    private String lastAlertId = null; // the most recent alert this admin sent
    private boolean signingOut = false;

    /** An alert older than this is shown as possibly out of date on the home screen. */
    private static final long STALE_MS = 30 * 60_000;

    // Admin: the alert being prepared

    private EmergencyViewModel emergencyVm;
    private MemberViewModel memberVm;   // student / teacher home (Compose)
    private AlertTemplate[] templates;
    private AlertTemplate selectedTemplate = null; // null = nothing selected yet
    private final AlertEdits edits = new AlertEdits();
    private final EditSheetController editSheet = new EditSheetController(this, edits, () -> campusId, () -> userEmail, this::refreshEditButton);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Keep the app in light mode so the colors match the design
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        Lang.load(this);   // the language chosen in Settings (English or Taglish)

        // No login details (for example opened from a notification)? Go through the login screen.
        Intent in = getIntent();
        String role = in.getStringExtra("role");
        if (role == null || FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        userRole = role;
        userEmail = valueOf(in.getStringExtra("email"));
        userBuilding = valueOf(in.getStringExtra("building"));
        String name = valueOf(in.getStringExtra("name"));
        String studentNo = valueOf(in.getStringExtra("studentNo"));
        String department = valueOf(in.getStringExtra("department"));
        userDepartment = in.getStringExtra("college") != null && !in.getStringExtra("college").isEmpty()
                ? in.getStringExtra("college") : department;
        userProgram = valueOf(in.getStringExtra("program"));
        ArrayList<String> teachesExtra = in.getStringArrayListExtra("teaches");
        if (teachesExtra != null) userTeaches = teachesExtra;
        userYear = in.getIntExtra("yearLevel", 0);
        userCampus = valueOf(in.getStringExtra("campus"));

        AlertNotifications.createChannel(this);

        registerDevice();
        buildScreenForRole(name, studentNo, department);
        handleAlertIntent(getIntent());

        // First time on this phone: explain and request what alerts need
        if (!PermissionHelper.setupDoneFor(this, userEmail)) setup.show();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleAlertIntent(intent); // tapped an alert while the app was already open
    }

    private static String valueOf(String s) {
        return s == null ? "" : s;
    }

    /** Join the right push topics and save this phone's token on the person's roster entry. */
    private void registerDevice() {
        // Find the campus first (a blank roster campus means the only campus), then join its topics
        CampusStore.load(userCampus, new AlertTracker.Callback<CampusConfig>() {
            @Override public void onSuccess(CampusConfig c) { joinTopics(c == null ? userCampus : c.id, c); }
            @Override public void onError(String message) { joinTopics(userCampus, null); }
        });

        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                fcmToken = task.getResult();
                // Write only when the token changed (or was never saved): thousands of phones must not each write on every launch
                final SharedPreferences tp = getSharedPreferences("ligtas_token", MODE_PRIVATE);
                final String saved = userEmail + "|" + fcmToken + "|v2";
                if (saved.equals(tp.getString("saved", ""))) return;
                Map<String, Object> update = new HashMap<>();
                update.put("fcmToken", fcmToken);
                update.put("hasToken", true);
                update.put("tokenUpdatedAt", FieldValue.serverTimestamp());
                FirebaseFirestore.getInstance().collection("roster").document(userEmail).update(update)
                        .addOnSuccessListener(v -> tp.edit().putString("saved", saved).apply());
            }
        });
    }

    private void joinTopics(String id, CampusConfig config) {
        campusId = id;
        // Staff may leave the college blank; derive it from the program when we can
        String college = userDepartment;
        if (college.isEmpty() && config != null) college = config.collegeOfProgram(userProgram);
        Topics.subscribe(this, id, userRole, userBuilding, college, userProgram, userYear, userTeaches);
    }

    /** The role comes from the roster, so a person cannot choose to be an admin. */
    private void buildScreenForRole(String name, String studentNo, String department) {
        String id = studentNo.isEmpty() ? userEmail : studentNo;

        if ("ADMIN".equals(userRole)) {
            currentUser = new Administrator(id, name, department);
            setContentView(R.layout.activity_admin_dashboard_compose);
            LigtasUi.applyChrome(this);
            setupAdminUI();
        } else if ("TEACHER".equals(userRole)) {
            currentUser = new Teacher(id, name, userBuilding);
            setContentView(R.layout.activity_member_compose); LigtasUi.applyChrome(this);
            setupMemberUI(name);
        } else {
            currentUser = new Student(id, name, department);
            setContentView(R.layout.activity_member_compose); LigtasUi.applyChrome(this);
            setupMemberUI(name);
        }
    }

    private static String firstName(String fullName) {
        String[] parts = fullName == null ? new String[0] : fullName.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) return "there";
        return parts[0].substring(0, 1).toUpperCase() + parts[0].substring(1).toLowerCase();
    }

    /** See SheetUi.prompt. */
    private void showPrompt(String title, String message, String[] labels, int[] kinds, boolean cancelable,
                            java.util.function.IntConsumer onChoice, Runnable onDismiss) {
        SheetUi.prompt(this, title, message, labels, kinds, cancelable, onChoice, onDismiss);
    }

    private void confirmSignOut() {
        showPrompt("Sign out?", "", new String[]{"Sign out", "Cancel"},
                new int[]{PromptKind.DANGER, PromptKind.GHOST}, true,
                i -> { if (i == 0) signOutAndReturn(); }, null);
    }

    private void signOutAndReturn() {
        if (signingOut) return;
        signingOut = true;
        final boolean[] done = {false};
        Runnable finishSignOut = () -> {
            if (done[0]) return;
            done[0] = true;
            Topics.unsubscribeAll(this);
            LastAlertStore.clear(this);
            getSharedPreferences("ligtas_token", MODE_PRIVATE).edit().clear().apply();
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        };
        // Remove this phone's token from the roster first, so it no longer counts as a recipient
        FirebaseFirestore.getInstance().collection("roster").document(userEmail)
                .update("fcmToken", FieldValue.delete(), "hasToken", false)
                .addOnCompleteListener(t -> finishSignOut.run());
        // Do not wait forever if the phone is offline
        new Handler(Looper.getMainLooper()).postDelayed(finishSignOut, 2500);
    }

    // ------------------------------------------------------------------
    // ADMIN SCREEN
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // ADMIN MENU: readiness, quick class suspension, campus console
    // ------------------------------------------------------------------
    /** The hosted campus console (Firebase Hosting). Change this if the console lives at another address. */
    private static final String CONSOLE_URL = "https://ligtas-eb260.web.app";

    private void openConsole() {
        try {
            // Opens in the browser: Google sign-in does not work inside an app's embedded web view
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(CONSOLE_URL)));
        } catch (Exception e) {
            Toast.makeText(this, "No browser found to open " + CONSOLE_URL, Toast.LENGTH_LONG).show();
        }
    }

    private void makeSheetTransparent(BottomSheetDialog dialog) {
        SheetUi.makeTransparent(dialog);
    }

    private void setupAdminUI() {
        emergencyVm = new ViewModelProvider(this).get(EmergencyViewModel.class);

        // Start with nothing selected (the Edit button stays locked)
        templates = AlertTemplate.all();
        clearSelection();

        // Red button: checks the alert is ready, then opens the slide-to-confirm step
        final Runnable alertEveryone = () -> {
            if (selectedTemplate == null) {
                Toast.makeText(this, "Select an emergency type first", Toast.LENGTH_SHORT).show();
                return;
            }
            if (selectedTemplate.isNoteRequired() && edits.note.isEmpty()) {
                Toast.makeText(this, "Tap Edit and add a short note first", Toast.LENGTH_SHORT).show();
                return;
            }
            Alert preview = buildAlert("PREVIEW");
            final Audience aud = currentAudience();
            final String base = preview.getMessage();
            openConfirm(preview.getTitle());
            AudienceContext ready = AudienceContext.fresh();
            showSummary(base, aud, ready);
            if (ready == null) {
                AudienceContext.load(campusId, new AlertTracker.Callback<AudienceContext>() {
                    @Override public void onSuccess(AudienceContext ctx) {
                        if (confirmIsOpen()) showSummary(base, aud, ctx);
                    }
                    @Override public void onError(String message) {
                        if (confirmIsOpen()) {
                            setConfirmPreview(base + "\n\nTo: " + aud.label() + "\n(could not count recipients)");
                        }
                    }
                });
            }
        };
        attachEmergencyControls(alertEveryone);
        // Status button: opens the live delivery / acknowledgment view of the last alert sent
        refreshStatusButton();
        AlertTracker.latestAlert(new AlertTracker.Callback<AlertTracker.Latest>() {
            @Override public void onSuccess(AlertTracker.Latest l) {
                if (l != null && lastAlertId == null) { lastAlertId = l.alertId; refreshStatusButton(); }
            }
            @Override public void onError(String message) { }
        });
    }

    private void openEdit() {
        if (selectedTemplate != null) editSheet.show(selectedTemplate);
    }

    private void openStatus() {
        if (lastAlertId != null) status.show(lastAlertId);
    }

    private void refreshStatusButton() {
        if (emergencyVm != null) emergencyVm.updateStatusEnabled(lastAlertId != null);
    }

    /** Connects the emergency tiles, the header and the confirm card to this activity. */
    private void attachEmergencyControls(Runnable alertEveryone) {
        emergencyVm.updateGreeting(Lang.pick("Hi, ", "Kumusta, ") + firstName(currentUser.getName()));
        final View header = findViewById(R.id.composeHeader);
        EmergencyListener listener = new EmergencyListener() {
            @Override public void onTypeSelected(int index) { selectTemplate(index); }
            @Override public void onAlertEveryone() { alertEveryone.run(); }
            @Override public void onEdit() { openEdit(); }
            @Override public void onStatus() { openStatus(); }
            @Override public void onReadiness() { setup.show(); }
            @Override public void onSuspend() { suspend.show(campusId, currentUser.getName()); }
            @Override public void onConsole() { openConsole(); }
            @Override public void onProfile() { settings.show(); }
            @Override public void onConfirmSend() { closeConfirm(); dispatcher.dispatch(); }
            @Override public void onConfirmCancel() { closeConfirm(); }
        };
        AdminHeaderBridge.attach((ComposeView) header, emergencyVm, listener);
        EmergencyBridge.attach((ComposeView) findViewById(R.id.composeEmergency), emergencyVm, listener);
        ConfirmBridge.attach((ComposeView) findViewById(R.id.composeConfirm), emergencyVm, listener);
    }

    private void selectTemplate(int index) {
        selectedTemplate = templates[index];
        // Switching type discards any earlier edits
        edits.reset(getResources().getStringArray(R.array.campus_areas)[0]);

        applyTileStyles(index);
        refreshEditButton();
    }

    private void clearSelection() {
        selectedTemplate = null;
        edits.reset(getResources().getStringArray(R.array.campus_areas)[0]);
        applyTileStyles(-1);
        refreshEditButton();
    }

    private static final int[] TILE_DRAWABLES = {R.drawable.ic_activity, R.drawable.ic_gun,
            R.drawable.ic_flame, R.drawable.ic_bomb, R.drawable.ic_flask, R.drawable.ic_ellipsis};

    /** The icon shown on the confirm card; the tiles themselves are drawn by Compose from the selected index. */
    private void applyTileStyles(int selected) {
        if (emergencyVm == null) return;
        emergencyVm.select(selected);
        if (selected >= 0) emergencyVm.updateConfirmIcon(TILE_DRAWABLES[selected]);
    }

    /** Edit button looks faded and cannot be tapped until a type is selected. */
    private void refreshEditButton() {
        if (emergencyVm != null) emergencyVm.updateEdit(selectedTemplate != null, edits.edited);
    }

    private Alert buildAlert(String alertId) {
        return selectedTemplate.createAlert(alertId, currentAudience().label(), edits.variant, edits.note);
    }

    /** The same alert in Taglish; both versions go out and each phone shows the language its owner chose. */
    private Alert buildAlertTl(String alertId) {
        return selectedTemplate.createAlertTl(alertId, currentAudience().label(), edits.variant, edits.note);
    }

    /** Settings changed the language: rebuild the texts Java produces (Compose texts redraw by themselves). */
    private void onLanguageChanged() {
        String hi = Lang.pick("Hi, ", "Kumusta, ") + firstName(currentUser.getName());
        if (emergencyVm != null) emergencyVm.updateGreeting(hi);
        if (memberVm != null) {
            memberVm.setGreeting(hi);
            refreshLastAlertCard();
            refreshReadiness();
            refreshRollCall();
        }
    }

    private Audience audienceFor(String area, String college, String program, int year, boolean onSiteOnly) {
        return AlertEdits.audienceFor(campusId, getResources().getStringArray(R.array.campus_areas)[0],
                area, college, program, year, onSiteOnly);
    }

    private Audience currentAudience() {
        return audienceFor(edits.area, edits.college, edits.program, edits.year, edits.onCampus);
    }

    /** "To: Whole campus" plus, once the roster is loaded, how many phones that actually reaches. */
    private void showSummary(String base, Audience aud, AudienceContext ctx) {
        setConfirmPreview(base + "\n\n" + audienceSummary(aud, ctx, () -> {
            if (confirmIsOpen()) showSummary(base, aud, ctx);
        }));
    }

    // ---- the slide-to-send card (Compose, see SlideToSend.kt) ----
    private void openConfirm(String title) {
        emergencyVm.showConfirm(title);
        findViewById(R.id.composeConfirm).setVisibility(View.VISIBLE);
    }

    private void closeConfirm() {
        emergencyVm.hideConfirm();
        findViewById(R.id.composeConfirm).setVisibility(View.GONE);
    }

    private boolean confirmIsOpen() {
        return findViewById(R.id.composeConfirm).getVisibility() == View.VISIBLE;
    }

    private void setConfirmPreview(String text) {
        emergencyVm.updateConfirmPreview(text);
    }

    private String audienceSummary(Audience aud, AudienceContext ctx, Runnable onUpdate) {
        StringBuilder sb = new StringBuilder("To: ").append(aud.label());
        if (ctx == null) return sb.append("\n(counting recipients...)").toString();
        AudiencePlan plan = aud.plan(ctx.config, System.currentTimeMillis());
        AlertTracker.Reach r = ctx.reach(aud, plan, userEmail, onUpdate);
        if (r == null) return sb.append("\n(counting phones...)").toString();
        sb.append("\nAbout ").append(r.reachable).append(r.reachable == 1 ? " phone" : " phones")
                .append(" can be reached");
        if (r.reachable == 0) sb.append("\nWARNING: nobody would receive this alert.");
        if (r.inGroup > r.reachable) sb.append(" (").append(r.inGroup - r.reachable).append(" in this group not signed in)");
        if (aud.usesSchedule()) {
            if (ctx.config == null) {
                sb.append("\nNo campus setup found: nobody is skipped.");
            } else if (!plan.onlineGroups.isEmpty()) {
                sb.append("\nSkipping online this week: ").append(TextUtils.join(", ", new java.util.TreeSet<>(plan.onlineGroups)));
            }
        }
        return sb.toString();
    }

    /** Shown to a recipient who tapped a notification: an alert (acknowledge it) or an all-clear / false alarm. */
    private void handleAlertIntent(Intent intent) {
        if (intent == null) return;
        final String alertId = intent.getStringExtra("alertId");
        if (alertId == null || alertId.isEmpty()) return;
        String title = valueOf(intent.getStringExtra("alertTitle"));
        String body = valueOf(intent.getStringExtra("alertBody"));
        String type = valueOf(intent.getStringExtra("alertType"));
        String by = valueOf(intent.getStringExtra("alertBy"));
        long sentAt = intent.getLongExtra("alertSentAt", 0);
        intent.removeExtra("alertId"); // show it only once

        StringBuilder text = new StringBuilder(body);
        if (sentAt > 0) {
            text.append("\n\n").append(Lang.pick("Sent ", "Ipinadala ")).append(TimeText.when(this, sentAt));
            if (!by.isEmpty()) text.append(Lang.pick(" by ", " ni ")).append(by);
        }

        final String sheetTitle = title.isEmpty() ? "Ligtas alert" : title;
        if (!type.isEmpty() && !LigtasMessagingService.TYPE_ALERT.equals(type)) {
            // All-clear or false alarm: nothing to acknowledge
            showPrompt(sheetTitle, text.toString(), new String[]{"OK"}, new int[]{PromptKind.PRIMARY}, true, null, null);
            return;
        }

        // Buttons: Acknowledge, an optional role action, then Close
        final boolean admin = "ADMIN".equals(userRole);
        final boolean teacher = !admin && currentUser instanceof Teacher;
        final String ack = Lang.pick("Acknowledge", "I-acknowledge");
        final String close = Lang.pick("Close", "Isara");
        final String[] labels = admin ? new String[]{ack, "Status", close}
                : teacher ? new String[]{ack, Lang.pick("Account for my room", "I-account ang room ko"), close}
                : new String[]{ack, close};
        final int[] kinds = labels.length == 3 ? new int[]{PromptKind.DANGER, PromptKind.PRIMARY, PromptKind.GHOST}
                : new int[]{PromptKind.DANGER, PromptKind.GHOST};
        showPrompt(sheetTitle, text.toString(), labels, kinds, false, i -> {
            if (i == 0) {
                AlertTracker.markAcknowledged(alertId, new AlertTracker.Callback<Void>() {
                    @Override public void onSuccess(Void value) {
                        LastAlertStore.markAcked(MainActivity.this, alertId);
                        Toast.makeText(MainActivity.this, Lang.pick("Acknowledged. Thank you.", "Na-acknowledge na. Salamat!"), Toast.LENGTH_SHORT).show();
                    }
                    @Override public void onError(String message) {
                        Toast.makeText(MainActivity.this, Lang.pick("Could not record: ", "Hindi na-record: ") + message, Toast.LENGTH_LONG).show();
                    }
                });
            } else if (labels.length == 3 && i == 1) {
                if (admin) {
                    // Admins can jump to the live status (and end the alert) from the notification
                    lastAlertId = alertId; refreshStatusButton(); status.show(alertId);
                } else {
                    // Teachers can report their room straight from the alert
                    roomReports.show(alertId);
                }
            }
        }, null);
    }

    /** The student / teacher home (MemberHome.kt): values go into memberVm, taps come back here. */
    private void setupMemberUI(String name) {
        memberVm = new ViewModelProvider(this).get(MemberViewModel.class);
        boolean teacher = currentUser instanceof Teacher;
        String role = teacher ? "Teacher" : "Student";
        String detail = teacher ? userBuilding : userDepartment;
        if (!teacher && !userProgram.isEmpty()) {
            detail = (userDepartment.isEmpty() ? "" : userDepartment + "  \u2022  ") + userProgram
                    + (userYear > 0 ? "  \u2022  Year " + userYear : "");
        }
        memberVm.setGreeting(Lang.pick("Hi, ", "Kumusta, ") + firstName(name));
        memberVm.setRoleLine(detail.isEmpty() ? role : role + "  \u2022  " + detail);

        MemberListener listener = new MemberListener() {
            @Override public void onProfile() { settings.show(); }
            @Override public void onSignOut() { confirmSignOut(); }
            @Override public void onReadiness() { setup.show(); }
            @Override public void onRollCall() {
                LastAlertStore.Saved last = LastAlertStore.get(MainActivity.this);
                if (last != null && !last.isResolved()) roomReports.show(last.alertId);
            }
            @Override public void onAcknowledge() {
                AlertNotifications.stopAlertVibration(MainActivity.this);
                LastAlertStore.Saved last = LastAlertStore.get(MainActivity.this);
                if (last == null) return;
                AlertTracker.markAcknowledged(last.alertId, new AlertTracker.Callback<Void>() {
                    @Override public void onSuccess(Void value) {
                        LastAlertStore.markAcked(MainActivity.this, last.alertId);
                        Toast.makeText(MainActivity.this, Lang.pick("Acknowledged. Thank you.", "Na-acknowledge na. Salamat!"), Toast.LENGTH_SHORT).show();
                    }
                    @Override public void onError(String message) {
                        Toast.makeText(MainActivity.this, Lang.pick("Could not record: ", "Hindi na-record: ") + message, Toast.LENGTH_LONG).show();
                    }
                });
            }
        };
        MemberHomeBridge.attach((ComposeView) findViewById(R.id.composeMember), memberVm, listener);

        // Refresh the card whenever a new alert arrives or is acknowledged
        lastAlertListener = (prefs, key) -> { refreshLastAlertCard(); refreshRollCall(); };
        LastAlertStore.prefs(this).registerOnSharedPreferenceChangeListener(lastAlertListener);

        refreshLastAlertCard();
        refreshRollCall();
        refreshReadiness();
    }

    /** The teacher's room-report button: only while an alert is active, and it shows what was already reported. */
    private void refreshRollCall() {
        if (memberVm == null) return;   // only the student / teacher home has these cards
        LastAlertStore.Saved l = LastAlertStore.get(this);
        boolean show = currentUser instanceof Teacher && l != null && !l.isResolved()
                && System.currentTimeMillis() - l.displayTime() < 6L * 60 * 60 * 1000;
        memberVm.setShowRollCall(show);
        if (!show) return;
        RoomReportStore.Mine mine = RoomReportStore.get(this, l.alertId);
        if (mine == null) {
            memberVm.setRollCallText("Account for my room");
        } else {
            String how = RoomReportStore.FAILED.equals(mine.delivery) ? "  (not sent, tap to retry)"
                    : RoomReportStore.SENDING.equals(mine.delivery) ? "  (sending...)" : "";
            memberVm.setRollCallText("Reported: " + (mine.room.isEmpty() ? "room" : mine.room) + " \u2022 "
                    + RoomReport.label(mine.status) + how + "\nTap to update");
        }
    }

    /** Compose version of refreshLastAlertCard: same rules, the result goes into memberVm. */
    private void refreshMemberLastAlert() {
        LastAlertStore.Saved last = LastAlertStore.get(this);
        if (last == null) {
            memberVm.setHasLast(false);
            memberVm.setWarningIcon(false);
            memberVm.setHeadline(Lang.pick("No active alerts", "Walang active na alert"));
            memberVm.setHeadlineTone(Tone.NEUTRAL);
            memberVm.setSub(Lang.pick("You are registered and ready to receive campus alerts.", "Naka-register ka at handa nang tumanggap ng campus alerts."));
            return;
        }
        memberVm.setHasLast(true);
        memberVm.setLastTitle(last.title);
        memberVm.setLastBody(last.body);
        StringBuilder when = new StringBuilder();
        if (last.sentAt > 0) {
            when.append(Lang.pick("Sent ", "Ipinadala ")).append(TimeText.when(this, last.sentAt));
            if (!last.sentBy.isEmpty()) when.append(Lang.pick(" by ", " ni ")).append(last.sentBy);
            when.append("  \u2022  ");
        }
        when.append(Lang.pick("Received ", "Natanggap ")).append(DateUtils.getRelativeTimeSpanString(
                last.receivedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));
        if (last.isResolved() && last.resolvedAt > 0) when.append(Lang.pick("\nEnded ", "\nTapos na ")).append(TimeText.when(this, last.resolvedAt));
        memberVm.setLastTime(when.toString());

        if (last.isResolved()) {
            boolean falseAlarm = LastAlertStore.STATE_FALSE_ALARM.equals(last.state);
            memberVm.setWarningIcon(false);
            memberVm.setHeadline(falseAlarm ? Lang.pick("False alarm", "Maling alarma") : "All clear");
            memberVm.setHeadlineTone(falseAlarm ? Tone.WARN : Tone.OK);
            memberVm.setSub(last.resolutionText.isEmpty() ? Lang.pick("This alert has ended.", "Tapos na ang alert na ito.") : last.resolutionText);
            memberVm.setShowAcknowledge(false);
            memberVm.setShowAckedBadge(last.acked);
            return;
        }
        boolean stale = System.currentTimeMillis() - last.displayTime() > STALE_MS;
        String staleNote = stale ? Lang.pick("\nThis alert was sent over 30 minutes ago. Check official updates.", "\nMahigit 30 minuto na mula nang ipadala ang alert na ito. I-check ang official updates.") : "";
        if (last.acked) {
            memberVm.setWarningIcon(false);
            memberVm.setHeadline(Lang.pick("Alert acknowledged", "Na-acknowledge na ang alert"));
            memberVm.setHeadlineTone(Tone.NEUTRAL);
            memberVm.setSub(Lang.pick("Follow the instructions below and wait for further updates.", "Sundin ang instructions sa ibaba at hintayin ang susunod na update.") + staleNote);
            memberVm.setShowAcknowledge(false);
            memberVm.setShowAckedBadge(true);
        } else {
            memberVm.setWarningIcon(true);
            memberVm.setHeadline(Lang.pick("Alert received", "May natanggap na alert"));
            memberVm.setHeadlineTone(Tone.DANGER);
            memberVm.setSub(Lang.pick("Please acknowledge that you have seen it.", "Paki-acknowledge na nakita mo ito.") + staleNote);
            memberVm.setShowAcknowledge(true);
            memberVm.setShowAckedBadge(false);
        }
    }

    private void refreshLastAlertCard() {
        if (memberVm != null) refreshMemberLastAlert();
    }

    private void refreshReadiness() {
        refreshReadinessText();
    }

    private void refreshReadinessText() {
        if (memberVm == null) return;   // only the student / teacher home has these cards
        List<PermissionHelper.Problem> ps = PermissionHelper.problems(this);
        if (ps.isEmpty()) {
            String dnd = PermissionHelper.activeDndMode(this);
            if (dnd == null) {
                memberVm.setReadiness("Alert readiness: all set");
                memberVm.setReadinessTone(Tone.OK);
            } else {
                memberVm.setReadiness("Alert readiness: all set\nDo Not Disturb is ON (" + dnd
                        + "). Some phones may still hide or silence alerts.");
                memberVm.setReadinessTone(Tone.WARN);
            }
        } else {
            memberVm.setReadiness("Alert readiness: " + ps.size() + (ps.size() == 1 ? " thing needs" : " things need")
                    + " attention. Tap to fix.");
            memberVm.setReadinessTone(PermissionHelper.hasCriticalProblem(ps) ? Tone.DANGER : Tone.WARN);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        AlertNotifications.stopAlertVibration(this); // opening the app silences a repeating alert buzz
        AlertNotifications.createChannel(this); // creates the Do Not Disturb channel once access is granted
        refreshLastAlertCard();
        refreshReadiness();
        setup.refreshRows();   // the person may have just come back from a settings screen
        setup.maybeRemind();
    }

    @Override
    protected void onDestroy() {
        if (lastAlertListener != null) {
            LastAlertStore.prefs(this).unregisterOnSharedPreferenceChangeListener(lastAlertListener);
        }
        super.onDestroy();
    }
}
