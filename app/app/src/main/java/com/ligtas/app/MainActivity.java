package com.ligtas.app;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {

    private User currentUser;

    // Who is signed in (filled from the roster by LoginActivity)
    private String userRole = "";
    private String userEmail = "";
    private String userBuilding = "";
    private String userDepartment = "";   // college (older roster rows called it department)
    private String userProgram = "";
    private java.util.List<String> userTeaches = new java.util.ArrayList<>();   // teachers: "BSIT-2", "BSME" ...
    private int userYear = 0;
    private String userCampus = "";       // campus id from the roster ("" = the only campus)
    private String campusId = "";         // resolved campus id (used for topics)
    private SharedPreferences.OnSharedPreferenceChangeListener lastAlertListener; // must stay referenced
    private BottomSheetDialog setupDialog;       // the "Let alerts reach you" sheet, when open
    private LinearLayout setupRowsContainer;
    private boolean reminderShowing = false;
    private long lastCriticalReminder = 0;

    // Shared variables
    private String fcmToken = "Fetching token...";
    private String lastAlertId = null; // the most recent alert this admin sent
    private boolean signingOut = false;
    private long lastSentLocal = 0; // when THIS phone last sent an alert (for the send cooldown)

    /** Minimum gap before a second alert is sent without asking "are you sure?". */
    private static final long COOLDOWN_MS = 60_000;
    /** An alert older than this is shown as possibly out of date on the home screen. */
    private static final long STALE_MS = 30 * 60_000;

    // Admin: the alert being prepared
    private MaterialCardView[] cards;
    private AlertTemplate[] templates;
    private AlertTemplate selectedTemplate = null; // null = nothing selected yet
    private String editArea = "Whole campus";
    private int editVariant = 0;
    private String editNote = "";
    private String editCollege = "";       // "" = all colleges
    private String editProgram = "";       // "" = all programs
    private int editYear = 0;              // 0 = all year levels
    private boolean editOnCampus = true;   // whole-campus alerts skip program/year groups that are online this week
    private boolean edited = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Keep the app in light mode so the colors match the design
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);

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
        java.util.ArrayList<String> teachesExtra = in.getStringArrayListExtra("teaches");
        if (teachesExtra != null) userTeaches = teachesExtra;
        userYear = in.getIntExtra("yearLevel", 0);
        userCampus = valueOf(in.getStringExtra("campus"));

        AlertNotifications.createChannel(this);

        registerDevice();
        buildScreenForRole(name, studentNo, department);
        handleAlertIntent(getIntent());

        // First time on this phone: explain and request what alerts need
        if (!PermissionHelper.setupDoneFor(this, userEmail)) showSetupSheet();
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
                Map<String, Object> update = new HashMap<>();
                update.put("fcmToken", fcmToken);
                update.put("tokenUpdatedAt", FieldValue.serverTimestamp());
                FirebaseFirestore.getInstance().collection("roster").document(userEmail).update(update);
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
            setContentView(R.layout.activity_admin_dashboard);
            setupAdminUI();
        } else if ("TEACHER".equals(userRole)) {
            currentUser = new Teacher(id, name, userBuilding);
            setContentView(R.layout.activity_student);
            setupMemberUI(name);
        } else {
            currentUser = new Student(id, name, department);
            setContentView(R.layout.activity_student);
            setupMemberUI(name);
        }
    }

    private static String firstName(String fullName) {
        String[] parts = fullName == null ? new String[0] : fullName.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) return "there";
        return parts[0].substring(0, 1).toUpperCase() + parts[0].substring(1).toLowerCase();
    }

    private void confirmSignOut() {
        new AlertDialog.Builder(this)
                .setTitle("Sign out?")
                .setPositiveButton("Sign out", (d, which) -> signOutAndReturn())
                .setNegativeButton("Cancel", null)
                .show();
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
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        };
        // Remove this phone's token from the roster first, so it no longer counts as a recipient
        FirebaseFirestore.getInstance().collection("roster").document(userEmail)
                .update("fcmToken", FieldValue.delete())
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

    private void showAdminMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "Alert readiness");
        menu.getMenu().add(0, 2, 1, "Suspend classes\u2026");
        menu.getMenu().add(0, 3, 2, "Campus console (setup, roster)");
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: showSetupSheet(); return true;
                case 2: showSuspendDialog(); return true;
                case 3:
                    try {
                        // Opens in the browser: Google sign-in does not work inside an app's embedded web view
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(CONSOLE_URL)));
                    } catch (Exception e) {
                        Toast.makeText(this, "No browser found to open " + CONSOLE_URL, Toast.LENGTH_LONG).show();
                    }
                    return true;
                default: return false;
            }
        });
        menu.show();
    }

    /** Local midnight of the day containing the moment. */
    private static long startOfDay(long millis) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(millis);
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** One-tap calendar rule for emergencies like a typhoon: the same rule the console's Calendar rules tab writes. */
    private void showSuspendDialog() {
        Toast.makeText(this, "Loading the campus setup...", Toast.LENGTH_SHORT).show();
        CampusStore.load(campusId, new AlertTracker.Callback<CampusConfig>() {
            @Override public void onSuccess(CampusConfig config) {
                if (config == null) {
                    Toast.makeText(MainActivity.this, "No campus setup found. Save one in the campus console first.", Toast.LENGTH_LONG).show();
                    return;
                }
                buildSuspendDialog(config);
            }
            @Override public void onError(String message) {
                Toast.makeText(MainActivity.this, "Could not read the campus setup: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void buildSuspendDialog(CampusConfig config) {
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);

        TextView lblWho = new TextView(this);
        lblWho.setText("Applies to");
        box.addView(lblWho);
        List<String> whoLabels = new ArrayList<>();
        List<String> whoCodes = new ArrayList<>();
        whoLabels.add("Whole campus");
        whoCodes.add("*");
        for (CampusConfig.College col : config.colleges) {
            whoLabels.add(col.code + (col.name.isEmpty() ? "" : " - " + col.name));
            whoCodes.add(col.code);
        }
        Spinner spWho = new Spinner(this);
        spWho.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, whoLabels));
        box.addView(spWho);

        TextView lblWhen = new TextView(this);
        lblWhen.setText("\nWhen");
        box.addView(lblWhen);
        RadioGroup rgWhen = new RadioGroup(this);
        String[] whenLabels = {"Today", "Tomorrow", "Today and tomorrow"};
        for (int i = 0; i < whenLabels.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(1000 + i);
            rb.setText(whenLabels[i]);
            rgWhen.addView(rb);
        }
        rgWhen.check(1000);
        box.addView(rgWhen);

        TextView lblMode = new TextView(this);
        lblMode.setText("\nWhat happens");
        box.addView(lblMode);
        RadioGroup rgMode = new RadioGroup(this);
        RadioButton rbOnline = new RadioButton(this);
        rbOnline.setId(2000);
        rbOnline.setText("Classes suspended / online (on-site-only alerts skip them)");
        RadioButton rbNone = new RadioButton(this);
        rbNone.setId(2001);
        rbNone.setText("No classes, holiday (still alerted: people may be on campus)");
        rgMode.addView(rbOnline);
        rgMode.addView(rbNone);
        rgMode.check(2000);
        box.addView(rgMode);

        new AlertDialog.Builder(this)
                .setTitle("Suspend classes")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Apply", (d, w) -> {
                    long today = startOfDay(System.currentTimeMillis());
                    long tomorrow = startOfDay(today + 36L * 60 * 60 * 1000);   // 36 h after midnight is safely tomorrow (DST)
                    int when = rgWhen.getCheckedRadioButtonId() - 1000;
                    long start = when == 1 ? tomorrow : today;
                    long end = when == 0 ? today : tomorrow;
                    String code = whoCodes.get(spWho.getSelectedItemPosition());
                    String mode = rgMode.getCheckedRadioButtonId() == 2001 ? "NC" : "OL";
                    saveQuickRule(config, start, end, code, mode);
                })
                .show();
    }

    private void saveQuickRule(CampusConfig config, long start, long end, String college, String mode) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        com.google.firebase.firestore.DocumentReference ref = db.collection("campuses").document(config.id);
        final String by = currentUser.getName();
        db.runTransaction(tx -> {
            com.google.firebase.firestore.DocumentSnapshot snap = tx.get(ref);
            List<Object> rules = new ArrayList<>();
            Object existing = snap.get("overrides");
            if (existing instanceof List) rules.addAll((List<?>) existing);
            Map<String, Object> rule = new HashMap<>();
            rule.put("start", start);
            rule.put("end", end);
            rule.put("days", "");
            rule.put("college", college);
            rule.put("mode", mode);
            rule.put("reason", "Quick action by " + by);
            rules.add(rule);
            tx.update(ref, "overrides", rules);
            return null;
        }).addOnSuccessListener(v -> {
            AudienceContext.invalidate();
            Toast.makeText(this, "Done. Remove it later in the campus console (Calendar rules).", Toast.LENGTH_LONG).show();
        }).addOnFailureListener(e ->
                Toast.makeText(this, "Could not save: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void setupAdminUI() {
        Button btnDispatchAlert = findViewById(R.id.btnDispatchAlert);
        TextView tvAdminGreeting = findViewById(R.id.tvAdminGreeting);
        if (tvAdminGreeting != null) {
            // Mockup shows the first name only: "Hi, [Admin First Name]"
            tvAdminGreeting.setText("Hi, " + firstName(currentUser.getName()));
        }

        // Tap the profile picture (top right) to sign out
        findViewById(R.id.imgAvatarTop).setOnClickListener(v -> confirmSignOut());
        findViewById(R.id.imgMenu).setOnClickListener(v -> showAdminMenu(v));

        // Start with nothing selected (the Edit button stays locked)
        templates = AlertTemplate.all();
        cards = new MaterialCardView[]{
                findViewById(R.id.cardEarthquake), findViewById(R.id.cardShooter),
                findViewById(R.id.cardFire), findViewById(R.id.cardBomb),
                findViewById(R.id.cardChemical), findViewById(R.id.cardOthers)};
        clearSelection();

        for (int i = 0; i < cards.length; i++) {
            final int index = i;
            cards[i].setOnClickListener(v -> selectTemplate(index));
        }

        // Edit button: only works after an emergency type is selected
        findViewById(R.id.btnEditAlert).setOnClickListener(v -> {
            if (selectedTemplate != null) showEditSheet();
        });

        // Red button: checks the alert is ready, then opens the slide-to-confirm step
        View slideOverlay = findViewById(R.id.slideOverlay);
        SeekBar slideSeek = findViewById(R.id.slideSeek);
        TextView slideTitle = findViewById(R.id.slideTitle);
        TextView slidePreview = findViewById(R.id.slidePreview);

        btnDispatchAlert.setOnClickListener(v -> {
            if (selectedTemplate == null) {
                Toast.makeText(this, "Select an emergency type first", Toast.LENGTH_SHORT).show();
                return;
            }
            if (selectedTemplate.isNoteRequired() && editNote.isEmpty()) {
                Toast.makeText(this, "Tap Edit and add a short note first", Toast.LENGTH_SHORT).show();
                return;
            }
            Alert preview = buildAlert("PREVIEW");
            slideTitle.setText(preview.getTitle());
            final Audience aud = currentAudience();
            final String base = preview.getMessage();
            AudienceContext ready = AudienceContext.fresh();
            slidePreview.setText(base + "\n\n" + audienceSummary(aud, ready));
            if (ready == null) {
                AudienceContext.load(campusId, new AlertTracker.Callback<AudienceContext>() {
                    @Override public void onSuccess(AudienceContext ctx) {
                        if (slideOverlay.getVisibility() == View.VISIBLE) {
                            slidePreview.setText(base + "\n\n" + audienceSummary(aud, ctx));
                        }
                    }
                    @Override public void onError(String message) {
                        if (slideOverlay.getVisibility() == View.VISIBLE) {
                            slidePreview.setText(base + "\n\nTo: " + aud.label() + "\n(could not count recipients)");
                        }
                    }
                });
            }
            slideSeek.setProgress(0);
            slideOverlay.setVisibility(View.VISIBLE);
        });
        findViewById(R.id.slideCancel).setOnClickListener(v -> slideOverlay.setVisibility(View.GONE));
        slideSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) { }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) {
                if (s.getProgress() >= 95) {
                    slideOverlay.setVisibility(View.GONE);
                    dispatchAlert();
                } else {
                    s.setProgress(0); // not slid far enough: cancel
                }
            }
        });

        // Status button: opens the live delivery / acknowledgment view of the last alert sent
        refreshStatusButton();
        AlertTracker.latestAlert(new AlertTracker.Callback<AlertTracker.Latest>() {
            @Override public void onSuccess(AlertTracker.Latest l) {
                if (l != null && lastAlertId == null) { lastAlertId = l.alertId; refreshStatusButton(); }
            }
            @Override public void onError(String message) { }
        });
        findViewById(R.id.btnStatus).setOnClickListener(v -> {
            if (lastAlertId != null) showStatusSheet(lastAlertId);
        });
    }

    private void refreshStatusButton() {
        View btn = findViewById(R.id.btnStatus);
        if (btn == null) return;
        boolean ready = lastAlertId != null;
        btn.setEnabled(ready);
        btn.setAlpha(ready ? 1f : 0.4f);
    }

    private void selectTemplate(int index) {
        selectedTemplate = templates[index];
        // Switching type discards any earlier edits
        editArea = getResources().getStringArray(R.array.campus_areas)[0];
        editCollege = "";
        editProgram = "";
        editYear = 0;
        editOnCampus = true;
        editVariant = 0;
        editNote = "";
        edited = false;

        for (MaterialCardView c : cards) c.setStrokeWidth(0);
        cards[index].setStrokeColor(0xFFE53935);
        cards[index].setStrokeWidth(4);
        refreshEditButton();
    }

    private void clearSelection() {
        selectedTemplate = null;
        editArea = getResources().getStringArray(R.array.campus_areas)[0];
        editCollege = "";
        editProgram = "";
        editYear = 0;
        editOnCampus = true;
        editVariant = 0;
        editNote = "";
        edited = false;
        if (cards != null) {
            for (MaterialCardView c : cards) if (c != null) c.setStrokeWidth(0);
        }
        refreshEditButton();
    }

    /** Edit button looks faded and cannot be tapped until a type is selected. */
    private void refreshEditButton() {
        View btn = findViewById(R.id.btnEditAlert);
        TextView label = findViewById(R.id.tvEditLabel);
        if (btn == null) return;
        boolean ready = selectedTemplate != null;
        btn.setEnabled(ready);
        btn.setAlpha(ready ? 1f : 0.4f);
        if (label != null) label.setText(edited ? R.string.edited_label : R.string.edit_label);
    }

    private static String cleanNote(CharSequence raw) {
        return raw == null ? "" : raw.toString().replaceAll("\\s+", " ").trim();
    }

    private Alert buildAlert(String alertId) {
        return selectedTemplate.createAlert(alertId, currentAudience().label(), editVariant, editNote);
    }

    private Audience audienceFor(String area, String college, String program, int year, boolean onSiteOnly) {
        String wholeCampus = getResources().getStringArray(R.array.campus_areas)[0];
        return Audience.create(campusId, area, wholeCampus, college, program, year, onSiteOnly);
    }

    private Audience currentAudience() {
        return audienceFor(editArea, editCollege, editProgram, editYear, editOnCampus);
    }

    /** "To: Whole campus" plus, once the roster is loaded, how many phones that actually reaches. */
    private String audienceSummary(Audience aud, AudienceContext ctx) {
        StringBuilder sb = new StringBuilder("To: ").append(aud.label());
        if (ctx == null) return sb.append("\n(counting recipients...)").toString();
        AudiencePlan plan = aud.plan(ctx.config, System.currentTimeMillis());
        AlertTracker.Reach r = AlertTracker.countAudience(ctx.members, plan, userEmail);
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

    /** The limited-edit tab: area, one of the approved instructions, and a short note. */
    private void showEditSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheet = getLayoutInflater().inflate(R.layout.sheet_edit_alert, null);
        dialog.setContentView(sheet);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);

        TextView tvSheetTitle = sheet.findViewById(R.id.tvSheetTitle);
        Spinner spArea = sheet.findViewById(R.id.spArea);
        RadioGroup rgInstruction = sheet.findViewById(R.id.rgInstruction);
        TextView tvNoteLabel = sheet.findViewById(R.id.tvNoteLabel);
        TextInputEditText etNote = sheet.findViewById(R.id.etNote);
        TextView tvPreview = sheet.findViewById(R.id.tvPreview);
        Spinner spCollege = sheet.findViewById(R.id.spCollege);
        Spinner spProgram = sheet.findViewById(R.id.spProgram);
        Spinner spYear = sheet.findViewById(R.id.spYear);
        CheckBox cbOnCampus = sheet.findViewById(R.id.cbOnCampus);
        TextView tvAudienceHint = sheet.findViewById(R.id.tvAudienceHint);

        tvSheetTitle.setText("Edit alert: " + selectedTemplate.getLabel());
        if (selectedTemplate.isNoteRequired()) tvNoteLabel.setText("Short note (required)");

        // Affected area
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.campus_areas, R.layout.item_spinner);
        adapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spArea.setAdapter(adapter);
        String[] areas = getResources().getStringArray(R.array.campus_areas);
        for (int i = 0; i < areas.length; i++) {
            if (areas[i].equals(editArea)) spArea.setSelection(i);
        }
        // Once the campus setup is loaded, the areas come from its Locations list (see fillAll below)
        final List<String> areaOptions = new ArrayList<>();
        ArrayAdapter<String> areaAdapter = new ArrayAdapter<>(this, R.layout.item_spinner, areaOptions);
        areaAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);

        // College / Program / Year level come from the campus setup (loaded in a moment)
        final List<String> collegeOptions = new ArrayList<>();
        final List<String> programOptions = new ArrayList<>();
        final List<String> yearOptions = new ArrayList<>();
        collegeOptions.add("All colleges");
        programOptions.add("All programs");
        yearOptions.add("All years");
        ArrayAdapter<String> collegeAdapter = new ArrayAdapter<>(this, R.layout.item_spinner, collegeOptions);
        ArrayAdapter<String> programAdapter = new ArrayAdapter<>(this, R.layout.item_spinner, programOptions);
        ArrayAdapter<String> yearAdapter = new ArrayAdapter<>(this, R.layout.item_spinner, yearOptions);
        collegeAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        programAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        yearAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spCollege.setAdapter(collegeAdapter);
        spProgram.setAdapter(programAdapter);
        spYear.setAdapter(yearAdapter);
        cbOnCampus.setChecked(editOnCampus);
        // Always read the campus setup again, so what an admin just saved in the console shows up right away
        AudienceContext.invalidate();
        final AudienceContext[] loaded = {null};

        // Approved instruction variants (the wording is locked, only the choice is yours)
        String[] variants = selectedTemplate.getInstructions();
        for (int i = 0; i < variants.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(View.generateViewId());
            rb.setTag(i);
            rb.setText(variants[i]);
            rb.setTextColor(0xFF111111);
            rb.setTextSize(14);
            rb.setPadding(8, 12, 8, 12);
            rgInstruction.addView(rb);
            if (i == editVariant) rb.setChecked(true);
        }

        etNote.setText(editNote);

        // Live preview of exactly what everyone will receive
        Runnable updatePreview = () -> {
            Alert a = selectedTemplate.createAlert("PREVIEW",
                    audienceFor((String) spArea.getSelectedItem(), selectedChoice(spCollege), selectedChoice(spProgram),
                            selectedYear(spYear), cbOnCampus.isChecked()).label(),
                    checkedVariant(sheet, rgInstruction),
                    cleanNote(etNote.getText()));
            tvPreview.setText(a.getTitle() + "\n" + a.getMessage());
        };
        // Keeps the checkbox and the hint in step with the choices
        Runnable refreshAudienceUi = () -> {
            String area = (String) spArea.getSelectedItem();
            boolean wholeCampus = area.equals(getResources().getStringArray(R.array.campus_areas)[0]);
            boolean everyone = selectedChoice(spCollege).isEmpty() && selectedChoice(spProgram).isEmpty() && selectedYear(spYear) == 0;
            cbOnCampus.setVisibility(wholeCampus && everyone ? View.VISIBLE : View.GONE);
            StringBuilder hint = new StringBuilder();
            AudienceContext ctx = loaded[0];
            if (ctx == null) {
                hint.append("Loading the campus setup...");
            } else if (ctx.config == null) {
                hint.append(ctx.configError.isEmpty()
                        ? "No campus setup found in the database (campuses collection is empty or in a different Firebase project)."
                        : "Could not read the campus setup (" + ctx.configError + ").")
                        .append(" Nobody is skipped.");
            } else if (wholeCampus && everyone && cbOnCampus.isChecked()) {
                AudiencePlan plan = audienceFor(area, "", "", 0, true).plan(ctx.config, System.currentTimeMillis());
                int week = ctx.config.weekNumber(System.currentTimeMillis());
                hint.append(week == 0 ? "Semester dates not set (or outside them): nobody is skipped."
                        : "Week " + week + (week % 2 == 1 ? " (ODD)" : " (EVEN)") + ". Online now: "
                          + (plan.onlineGroups.isEmpty() ? "none" : TextUtils.join(", ", new java.util.TreeSet<>(plan.onlineGroups))));
            } else if (!everyone) {
                hint.append("Picking a college, program or year sends to that group even if it is online this week.");
            }
            if (!wholeCampus) hint.append(hint.length() > 0 ? "\n" : "")
                    .append("Reaches people whose roster entry lists this building.");
            if (ctx != null && ctx.config != null) {
                // Shows exactly which saved setup the app is using
                hint.append(hint.length() > 0 ? "\n" : "").append("Using: ").append(ctx.config.name)
                        .append(" (").append(ctx.config.id).append(") \u2022 ")
                        .append(ctx.config.colleges.size()).append(" colleges, ")
                        .append(ctx.config.programs.size()).append(" programs");
            }
            if (ctx != null) {
                // How many phones this really reaches, so a "Whole campus" label never hides an empty audience
                Audience chosen = audienceFor(area, selectedChoice(spCollege), selectedChoice(spProgram),
                        selectedYear(spYear), cbOnCampus.isChecked());
                AlertTracker.Reach rr = AlertTracker.countAudience(ctx.members,
                        chosen.plan(ctx.config, System.currentTimeMillis()), userEmail);
                hint.append(hint.length() > 0 ? "\n" : "").append("Reaches about ").append(rr.reachable)
                        .append(rr.reachable == 1 ? " phone." : " phones.");
                if (rr.reachable == 0) hint.append(" NOBODY would receive this. Untick \"Only people on campus\" to alert everyone.");
            }
            tvAudienceHint.setText(hint.toString());
        };
        final boolean[] filling = {false};   // true while the lists are being rebuilt (ignore spinner events)
        Runnable fillPrograms = () -> {
            AudienceContext ctx = loaded[0];
            String college = selectedChoice(spCollege);
            String keepProgram = filling[0] ? editProgram : selectedChoice(spProgram);
            programOptions.clear();
            programOptions.add("All programs");
            if (ctx != null && ctx.config != null) {
                for (CampusConfig.Program pr : ctx.config.programs) {
                    if (college.isEmpty() || Slug.of(pr.collegeCode).equals(Slug.of(college))) programOptions.add(pr.code);
                }
            }
            programAdapter.notifyDataSetChanged();
            int idx = Math.max(0, programOptions.indexOf(keepProgram));
            spProgram.setSelection(idx);
            // Year levels: up to the longest program in the choice
            int maxYears = 4;
            if (ctx != null && ctx.config != null) {
                maxYears = 1;
                String prog = programOptions.get(idx);
                for (CampusConfig.Program pr : ctx.config.programs) {
                    boolean inScope = !prog.equals("All programs") ? pr.code.equals(prog)
                            : (college.isEmpty() || Slug.of(pr.collegeCode).equals(Slug.of(college)));
                    if (inScope) maxYears = Math.max(maxYears, pr.years);
                }
            }
            int keepYear = filling[0] ? editYear : selectedYear(spYear);
            yearOptions.clear();
            yearOptions.add("All years");
            for (int y = 1; y <= maxYears; y++) yearOptions.add("Year " + y);
            yearAdapter.notifyDataSetChanged();
            spYear.setSelection(keepYear >= 0 && keepYear < yearOptions.size() ? keepYear : 0);
        };
        AdapterView.OnItemSelectedListener collegeListener = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (filling[0]) return;
                fillPrograms.run();
                refreshAudienceUi.run();
                updatePreview.run();
            }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        };
        AdapterView.OnItemSelectedListener audienceListener = new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (filling[0]) return;
                refreshAudienceUi.run();
                updatePreview.run();
            }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        };
        spArea.setOnItemSelectedListener(audienceListener);
        spCollege.setOnItemSelectedListener(collegeListener);
        spProgram.setOnItemSelectedListener(audienceListener);
        spYear.setOnItemSelectedListener(audienceListener);
        cbOnCampus.setOnCheckedChangeListener((b, checked) -> { refreshAudienceUi.run(); updatePreview.run(); });

        // Fill the lists from the campus setup
        Runnable fillAll = () -> {
            AudienceContext ctx = loaded[0];
            filling[0] = true;
            if (ctx != null && ctx.config != null && !ctx.config.locations.isEmpty()) {
                areaOptions.clear();
                areaOptions.add(getResources().getStringArray(R.array.campus_areas)[0]); // "Whole campus"
                areaOptions.addAll(ctx.config.locations);
                spArea.setAdapter(areaAdapter);
                spArea.setSelection(Math.max(0, areaOptions.indexOf(editArea)));
            }
            collegeOptions.clear();
            collegeOptions.add("All colleges");
            if (ctx != null && ctx.config != null) for (CampusConfig.College c : ctx.config.colleges) collegeOptions.add(c.code);
            collegeAdapter.notifyDataSetChanged();
            spCollege.setSelection(Math.max(0, collegeOptions.indexOf(editCollege)));
            fillPrograms.run();
            filling[0] = false;
            refreshAudienceUi.run();
            updatePreview.run();
        };
        if (loaded[0] != null) {
            fillAll.run();
        } else {
            AudienceContext.load(campusId, new AlertTracker.Callback<AudienceContext>() {
                @Override public void onSuccess(AudienceContext ctx) { loaded[0] = ctx; fillAll.run(); }
                @Override public void onError(String message) { tvAudienceHint.setText("Could not load the campus setup: " + message); }
            });
        }

        updatePreview.run();

        sheet.findViewById(R.id.btnSheetCancel).setOnClickListener(v -> dialog.dismiss());
        sheet.findViewById(R.id.btnSheetConfirm).setOnClickListener(v -> {
            String note = cleanNote(etNote.getText());
            if (selectedTemplate.isNoteRequired() && note.isEmpty()) {
                etNote.setError("A short note is required for this type");
                return;
            }
            // Save: these changes are used when the alert is sent
            editArea = (String) spArea.getSelectedItem();
            editCollege = selectedChoice(spCollege);
            editProgram = selectedChoice(spProgram);
            editYear = selectedYear(spYear);
            editOnCampus = cbOnCampus.isChecked();
            editVariant = checkedVariant(sheet, rgInstruction);
            editNote = note;
            edited = true;
            refreshEditButton();
            dialog.dismiss();
            Toast.makeText(this, "Changes saved. They will be sent with the alert.", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    /** The text chosen in a spinner, or "" for its first entry ("All colleges" / "All programs"). */
    private static String selectedChoice(Spinner sp) {
        Object item = sp.getSelectedItem();
        if (item == null || sp.getSelectedItemPosition() <= 0) return "";
        return item.toString();
    }

    /** Year level chosen in the spinner: 0 for "All years", otherwise 1, 2, 3... */
    private static int selectedYear(Spinner sp) {
        return Math.max(0, sp.getSelectedItemPosition());
    }

    private int checkedVariant(View sheet, RadioGroup group) {
        View checked = sheet.findViewById(group.getCheckedRadioButtonId());
        if (checked != null && checked.getTag() instanceof Integer) {
            return (Integer) checked.getTag();
        }
        return 0;
    }

    /**
     * Send cooldown: a second alert within one minute of the last one (from this phone or any other admin)
     * asks for confirmation first. It never blocks: a real second emergency can still go out.
     */
    private void dispatchAlert() {
        if (selectedTemplate == null) return;

        long sinceLocal = System.currentTimeMillis() - lastSentLocal;
        if (lastSentLocal > 0 && sinceLocal < COOLDOWN_MS) {
            confirmRepeat("you", sinceLocal);
            return;
        }

        // Check what other admins sent, but never hold up an emergency for it: go after 1.5 s at the latest
        final AtomicBoolean decided = new AtomicBoolean(false);
        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable go = () -> { if (decided.compareAndSet(false, true)) sendNow(); };
        handler.postDelayed(go, 1500);
        AlertTracker.latestAlert(new AlertTracker.Callback<AlertTracker.Latest>() {
            @Override public void onSuccess(AlertTracker.Latest l) {
                if (decided.get()) return;
                long age = l == null || l.sentAtMillis <= 0 ? Long.MAX_VALUE : System.currentTimeMillis() - l.sentAtMillis;
                if (age < COOLDOWN_MS) {
                    if (decided.compareAndSet(false, true)) {
                        handler.removeCallbacks(go);
                        confirmRepeat(l.sentBy, age);
                    }
                } else {
                    handler.removeCallbacks(go);
                    go.run();
                }
            }
            @Override public void onError(String message) {
                handler.removeCallbacks(go);
                go.run();
            }
        });
    }

    private void confirmRepeat(String who, long ageMs) {
        long seconds = Math.max(0, ageMs / 1000);
        new AlertDialog.Builder(this)
                .setTitle("Another alert was just sent")
                .setMessage("An alert was sent " + seconds + " seconds ago by " + who
                        + ". Sending again so soon may be a duplicate.\n\nSend anyway?")
                .setPositiveButton("Send anyway", (d, w) -> sendNow())
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Gets the roster and weekly schedule (if the audience needs them) without ever holding up an emergency. */
    private void sendNow() {
        if (selectedTemplate == null) return;
        final Audience aud = currentAudience();
        AudienceContext ready = AudienceContext.fresh();
        if (ready != null || !aud.usesSchedule()) {
            sendWith(aud, ready);
            return;
        }
        // Not loaded yet: wait at most 1.2 s, then send to the whole campus rather than delay
        final AtomicBoolean fired = new AtomicBoolean(false);
        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable fallback = () -> { if (fired.compareAndSet(false, true)) sendWith(aud, null); };
        handler.postDelayed(fallback, 1200);
        AudienceContext.load(campusId, new AlertTracker.Callback<AudienceContext>() {
            @Override public void onSuccess(AudienceContext ctx) {
                handler.removeCallbacks(fallback);
                if (fired.compareAndSet(false, true)) sendWith(aud, ctx);
            }
            @Override public void onError(String message) {
                handler.removeCallbacks(fallback);
                if (fired.compareAndSet(false, true)) sendWith(aud, null);
            }
        });
    }

    private void sendWith(Audience aud, AudienceContext ctx) {
        if (selectedTemplate == null) return;

        AlertService service = new AlertService();
        final long sentAt = System.currentTimeMillis();
        Alert newAlert = buildAlert("A-" + sentAt);

        if (!service.sendAlert(currentUser, newAlert)) {
            Toast.makeText(this, "DENIED: Insufficient permissions.", Toast.LENGTH_SHORT).show();
            return;
        }
        lastSentLocal = sentAt;

        // Where to push and which groups are online right now (no campus setup: skip nobody)
        final AudiencePlan plan = aud.plan(ctx == null ? null : ctx.config, sentAt);
        final List<String> targets = plan.targets;

        // The alert itself must never be held up by record-keeping:
        // push right after the record is saved, or after 1.5 s at the latest.
        final AtomicBoolean pushed = new AtomicBoolean(false);
        Runnable pushNow = () -> {
            if (pushed.compareAndSet(false, true)) {
                Map<String, String> data = new HashMap<>();
                data.put("title", newAlert.getTitle());
                data.put("body", newAlert.getMessage());
                data.put("incoming_alert_id", newAlert.getAlertId());
                data.put("type", LigtasMessagingService.TYPE_ALERT);
                data.put("sent_at", String.valueOf(sentAt));
                data.put("by", currentUser.getName());
                if (!plan.onlineCsv().isEmpty()) data.put("ol", plan.onlineCsv()); // phones in these groups stay quiet
                for (String target : targets) {
                    new FcmSender().send(this, target, data, FcmSender.ALERT_TTL_SECONDS);
                }
            }
        };

        AlertTracker.saveAlert(newAlert, userEmail, currentUser.getName(), plan,
                new AlertTracker.Callback<Void>() {
            @Override public void onSuccess(Void value) {
                pushNow.run();
                lastAlertId = newAlert.getAlertId();
                refreshStatusButton();
                AlertTracker.storeRecipients(newAlert.getAlertId(), userEmail, plan);
                showStatusSheet(newAlert.getAlertId());
            }
            @Override public void onError(String message) {
                pushNow.run();
                Toast.makeText(MainActivity.this,
                        "Alert sent, but tracking is unavailable: " + message, Toast.LENGTH_LONG).show();
            }
        });
        new Handler(Looper.getMainLooper()).postDelayed(pushNow, 1500);

        Toast.makeText(this, "DISPATCHED: " + newAlert.getTitle(), Toast.LENGTH_SHORT).show();
        clearSelection(); // ready for the next alert, prevents accidental repeats
    }

    /** "1:35 PM", or with the date when it was not today. */
    private String formatWhen(long millis) {
        int flags = DateUtils.FORMAT_SHOW_TIME | (DateUtils.isToday(millis) ? 0 : DateUtils.FORMAT_SHOW_DATE);
        return DateUtils.formatDateTime(this, millis, flags);
    }

    /** Live view for the admin: delivered, acknowledged, who is still waiting, average response time. */
    private void showStatusSheet(String alertId) {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheet = getLayoutInflater().inflate(R.layout.sheet_alert_status, null);
        dialog.setContentView(sheet);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);

        TextView tvTitle = sheet.findViewById(R.id.tvStatusTitle);
        TextView tvMessage = sheet.findViewById(R.id.tvStatusMessage);
        TextView tvExpected = sheet.findViewById(R.id.tvExpected);
        TextView tvDelivered = sheet.findViewById(R.id.tvDelivered);
        TextView tvAcked = sheet.findViewById(R.id.tvAcked);
        TextView tvAvg = sheet.findViewById(R.id.tvAvg);
        TextView tvPending = sheet.findViewById(R.id.tvPending);
        TextView tvMeta = sheet.findViewById(R.id.tvStatusMeta);
        View rowResolve = sheet.findViewById(R.id.rowResolve);
        final String[] titleHolder = {""};
        tvPending.setText("Loading...");
        sheet.findViewById(R.id.btnAllClear).setOnClickListener(v -> confirmResolve(alertId, "ALL_CLEAR", titleHolder[0], dialog));
        sheet.findViewById(R.id.btnFalseAlarm).setOnClickListener(v -> confirmResolve(alertId, "FALSE_ALARM", titleHolder[0], dialog));

        AlertTracker.Watcher watcher = new AlertTracker.Watcher(alertId, new AlertTracker.Callback<AlertTracker.Status>() {
            @Override public void onSuccess(AlertTracker.Status s) {
                tvTitle.setText(s.title.isEmpty() ? "Alert status" : s.title);
                titleHolder[0] = s.title;
                tvMessage.setText(s.message);
                StringBuilder meta = new StringBuilder();
                if (s.sentAtMillis > 0) meta.append("Sent ").append(formatWhen(s.sentAtMillis));
                if (!s.sentBy.isEmpty()) meta.append(meta.length() > 0 ? " by " : "Sent by ").append(s.sentBy);
                if (!s.audience.isEmpty()) meta.append("\nTo: ").append(s.audience);
                if ("ALL_CLEAR".equals(s.state) || "FALSE_ALARM".equals(s.state)) {
                    meta.append("\n").append("ALL_CLEAR".equals(s.state) ? "Ended: all clear" : "Ended: false alarm");
                    if (s.resolvedAtMillis > 0) meta.append(" at ").append(formatWhen(s.resolvedAtMillis));
                    rowResolve.setVisibility(View.GONE);
                } else {
                    meta.append("\nStill active");
                    rowResolve.setVisibility(View.VISIBLE);
                }
                tvMeta.setText(meta.toString());
                tvExpected.setText(String.valueOf(s.expected));
                tvDelivered.setText(String.valueOf(s.delivered));
                tvAcked.setText(String.valueOf(s.acknowledged));
                tvAvg.setText(s.avgSeconds >= 0
                        ? "Average response time: " + formatSeconds(s.avgSeconds)
                        : "Average response time: waiting for the first acknowledgment");
                if (s.expected == 0) {
                    tvPending.setText("No registered recipients yet.");
                } else if (s.pending.isEmpty()) {
                    tvPending.setText("Everyone has acknowledged.");
                } else {
                    tvPending.setText(TextUtils.join("\n", s.pending));
                }
            }
            @Override public void onError(String message) {
                tvPending.setText("Live status unavailable: " + message);
            }
        });
        // Room reports from teachers, live, most urgent first
        ViewGroup pendingScroll = (ViewGroup) tvPending.getParent();
        ViewGroup sheetRoot = (ViewGroup) pendingScroll.getParent();
        int at = sheetRoot.indexOfChild(pendingScroll) + 1;
        TextView roomsHead = new TextView(this);
        roomsHead.setText("Rooms reported by teachers");
        roomsHead.setTextColor(0xFF555555);
        roomsHead.setTextSize(12);
        roomsHead.setPadding(0, (int) (12 * getResources().getDisplayMetrics().density), 0, 0);
        final TextView tvRooms = new TextView(this);
        tvRooms.setTextColor(0xFF111111);
        tvRooms.setTextSize(14);
        tvRooms.setText("No room reports yet.");
        int p12 = (int) (12 * getResources().getDisplayMetrics().density);
        tvRooms.setPadding(p12, p12, p12, p12);
        ScrollView roomsScroll = new ScrollView(this);
        roomsScroll.setBackgroundResource(R.drawable.bg_preview);
        roomsScroll.addView(tvRooms);
        sheetRoot.addView(roomsHead, at);
        sheetRoot.addView(roomsScroll, at + 1, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int) (130 * getResources().getDisplayMetrics().density)));
        final com.google.firebase.firestore.ListenerRegistration roomsReg = AlertTracker.watchRooms(alertId, new AlertTracker.Callback<List<RoomReport>>() {
            @Override public void onSuccess(List<RoomReport> list) {
                StringBuilder sb = new StringBuilder(RoomReport.summary(list));
                for (RoomReport r : list) sb.append("\n\n").append(r.line());
                tvRooms.setText(sb.toString());
            }
            @Override public void onError(String message) { tvRooms.setText("Room reports unavailable: " + message); }
        });
        dialog.setOnDismissListener(d -> { watcher.stop(); roomsReg.remove(); });
        sheet.findViewById(R.id.btnStatusClose).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    /** Ends an alert for everyone: the message goes to the whole campus, and the alert cannot be ended twice. */
    private void confirmResolve(String alertId, String state, String alertTitle, BottomSheetDialog statusDialog) {
        final boolean falseAlarm = "FALSE_ALARM".equals(state);
        final String label = alertTitle == null || alertTitle.isEmpty() ? "The earlier alert" : alertTitle.toLowerCase(Locale.US);
        final String title = falseAlarm ? "FALSE ALARM" : "ALL CLEAR";
        final String body = falseAlarm
                ? "The earlier alert (" + label + ") was sent in error. There is no emergency. Please disregard it and resume normal activities."
                : "The emergency (" + label + ") has ended. It is safe to resume normal activities unless school officials tell you otherwise.";

        new AlertDialog.Builder(this)
                .setTitle(falseAlarm ? "Send false-alarm message?" : "Send all-clear message?")
                .setMessage("This goes to everyone who received the alert and ends it. It cannot be undone.\n\n" + title + "\n" + body)
                .setPositiveButton("Send", (d, w) ->
                        AlertTracker.resolveAlert(alertId, state, userEmail, body, new AlertTracker.Callback<Void>() {
                            @Override public void onSuccess(Void value) {
                                Map<String, String> data = new HashMap<>();
                                data.put("title", title);
                                data.put("body", body);
                                data.put("incoming_alert_id", alertId);
                                data.put("type", state);
                                data.put("sent_at", String.valueOf(System.currentTimeMillis()));
                                data.put("by", currentUser.getName());
                                // Same people who got the alert (older alerts without a saved audience go campus-wide)
                                AlertTracker.getRouting(alertId, new AlertTracker.Callback<AlertTracker.Routing>() {
                                    @Override public void onSuccess(AlertTracker.Routing r) {
                                        if (r.targets.isEmpty()) r.targets.add("/topics/" + Topics.CAMPUS);
                                        if (!r.onlineGroups.isEmpty()) data.put("ol", r.onlineGroups);
                                        for (String target : r.targets) {
                                            new FcmSender().send(MainActivity.this, target, data,
                                                    FcmSender.RESOLUTION_TTL_SECONDS);
                                        }
                                    }
                                    @Override public void onError(String message) {
                                        new FcmSender().send(MainActivity.this, "/topics/" + Topics.CAMPUS, data,
                                                FcmSender.RESOLUTION_TTL_SECONDS);
                                    }
                                });
                                Toast.makeText(MainActivity.this, title + " sent.", Toast.LENGTH_SHORT).show();
                            }
                            @Override public void onError(String message) {
                                Toast.makeText(MainActivity.this,
                                        "Not sent: " + message + " (it may already have been ended)", Toast.LENGTH_LONG).show();
                            }
                        }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static String formatSeconds(double seconds) {
        if (seconds < 60) return String.format(Locale.US, "%.0f s", seconds);
        int total = (int) Math.round(seconds);
        return (total / 60) + " min " + (total % 60) + " s";
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
            text.append("\n\nSent ").append(formatWhen(sentAt));
            if (!by.isEmpty()) text.append(" by ").append(by);
        }

        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setTitle(title.isEmpty() ? "Ligtas alert" : title)
                .setMessage(text.toString());

        if (!type.isEmpty() && !LigtasMessagingService.TYPE_ALERT.equals(type)) {
            // All-clear or false alarm: nothing to acknowledge
            dialog.setPositiveButton("OK", null).show();
            return;
        }

        dialog.setCancelable(false)
                .setPositiveButton("Acknowledge", (d, which) ->
                        AlertTracker.markAcknowledged(alertId, new AlertTracker.Callback<Void>() {
                            @Override public void onSuccess(Void value) {
                                LastAlertStore.markAcked(MainActivity.this, alertId);
                                Toast.makeText(MainActivity.this, "Acknowledged. Thank you.", Toast.LENGTH_SHORT).show();
                            }
                            @Override public void onError(String message) {
                                Toast.makeText(MainActivity.this, "Could not record: " + message, Toast.LENGTH_LONG).show();
                            }
                        }))
                .setNegativeButton("Close", null);
        if ("ADMIN".equals(userRole)) {
            // Admins can jump to the live status (and end the alert) from the notification
            dialog.setNeutralButton("Status", (d, w) -> { lastAlertId = alertId; refreshStatusButton(); showStatusSheet(alertId); });
        } else if (currentUser instanceof Teacher) {
            // Teachers can report their room straight from the alert
            dialog.setNeutralButton("Account for my room", (d, w) -> showRoomReportDialog(alertId));
        }
        dialog.show();
    }

    // ------------------------------------------------------------------
    // TEACHER / STUDENT SCREENS
    // ------------------------------------------------------------------
    // ------------------------------------------------------------------
    // STUDENT / TEACHER HOME SCREEN
    // ------------------------------------------------------------------
    private void setupMemberUI(String name) {
        ((TextView) findViewById(R.id.tvMemberGreeting)).setText("Hi, " + firstName(name));

        boolean teacher = currentUser instanceof Teacher;
        String role = teacher ? "Teacher" : "Student";
        String detail = teacher ? userBuilding : userDepartment;
        if (!teacher && !userProgram.isEmpty()) {
            detail = (userDepartment.isEmpty() ? "" : userDepartment + "  \u2022  ") + userProgram
                    + (userYear > 0 ? "  \u2022  Year " + userYear : "");
        }
        ((TextView) findViewById(R.id.tvMemberRole)).setText(detail.isEmpty() ? role : role + "  \u2022  " + detail);

        findViewById(R.id.imgAvatarTop).setOnClickListener(v -> confirmSignOut());
        findViewById(R.id.tvSignOut).setOnClickListener(v -> confirmSignOut());

        // Teachers get a room report button, shown only while an alert is active (see refreshRollCall)
        Button btnRollCall = findViewById(R.id.btnRollCall);
        btnRollCall.setVisibility(View.GONE);
        if (teacher) {
            btnRollCall.setOnClickListener(v -> {
                LastAlertStore.Saved last = LastAlertStore.get(this);
                if (last != null && !last.isResolved()) showRoomReportDialog(last.alertId);
            });
        }

        // Alert readiness card opens the setup sheet
        findViewById(R.id.rowNotifications).setOnClickListener(v -> showSetupSheet());

        // Acknowledge the last alert from the home screen
        findViewById(R.id.btnAcknowledge).setOnClickListener(v -> {
            LastAlertStore.Saved last = LastAlertStore.get(this);
            if (last == null) return;
            AlertTracker.markAcknowledged(last.alertId, new AlertTracker.Callback<Void>() {
                @Override public void onSuccess(Void value) {
                    LastAlertStore.markAcked(MainActivity.this, last.alertId);
                    Toast.makeText(MainActivity.this, "Acknowledged. Thank you.", Toast.LENGTH_SHORT).show();
                }
                @Override public void onError(String message) {
                    Toast.makeText(MainActivity.this, "Could not record: " + message, Toast.LENGTH_LONG).show();
                }
            });
        });

        // Refresh the card whenever a new alert arrives or is acknowledged
        lastAlertListener = (prefs, key) -> { refreshLastAlertCard(); refreshRollCall(); };
        LastAlertStore.prefs(this).registerOnSharedPreferenceChangeListener(lastAlertListener);

        refreshLastAlertCard();
        refreshRollCall();
        refreshReadiness();
    }

    /** The teacher's room-report button: only while an alert is active, and it shows what was already reported. */
    private void refreshRollCall() {
        Button btn = findViewById(R.id.btnRollCall);
        if (btn == null) return;
        LastAlertStore.Saved last = LastAlertStore.get(this);
        // Hide it for an alert that is many hours old (this phone probably missed the all-clear)
        boolean show = currentUser instanceof Teacher && last != null && !last.isResolved()
                && System.currentTimeMillis() - last.displayTime() < 6L * 60 * 60 * 1000;
        btn.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return;
        RoomReportStore.Mine mine = RoomReportStore.get(this, last.alertId);
        if (mine == null) {
            btn.setText("Account for my room");
            return;
        }
        String how = RoomReportStore.FAILED.equals(mine.delivery) ? "  (not sent, tap to retry)"
                : RoomReportStore.SENDING.equals(mine.delivery) ? "  (sending...)" : "";
        btn.setText("Reported: " + (mine.room.isEmpty() ? "room" : mine.room) + " • " + RoomReport.label(mine.status) + how
                + "\nTap to update");
    }

    /** Asks which room, whether everyone is accounted for, and sends it to the administrators. */
    private void showRoomReportDialog(final String alertId) {
        final RoomReportStore.Mine mine = RoomReportStore.get(this, alertId);
        final int pad = (int) (20 * getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);

        TextView intro = new TextView(this);
        intro.setText("Tell the administrators about the room you are in. You can send an update if things change.");
        box.addView(intro);

        final EditText etRoom = new EditText(this);
        etRoom.setHint("Room (for example CICS 204)");
        etRoom.setSingleLine(true);
        etRoom.setFilters(new InputFilter[]{new InputFilter.LengthFilter(40)});
        etRoom.setText(mine != null && !mine.room.isEmpty() ? mine.room : RoomReportStore.lastRoom(this));
        box.addView(etRoom);

        final String[] codes = {RoomReport.SAFE, RoomReport.MISSING, RoomReport.HELP};
        final RadioGroup group = new RadioGroup(this);
        for (int i = 0; i < codes.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(3001 + i);
            rb.setText(RoomReport.label(codes[i]));
            rb.setChecked(mine != null && codes[i].equals(mine.status));
            group.addView(rb);
        }
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        gp.topMargin = pad / 2;
        box.addView(group, gp);

        final EditText etCount = new EditText(this);
        etCount.setHint("Students with you (optional)");
        etCount.setInputType(InputType.TYPE_CLASS_NUMBER);
        etCount.setFilters(new InputFilter[]{new InputFilter.LengthFilter(3)});
        box.addView(etCount);

        final EditText etNote = new EditText(this);
        etNote.setHint("Note for the administrators (optional)");
        etNote.setFilters(new InputFilter[]{new InputFilter.LengthFilter(200)});
        box.addView(etNote);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(box);
        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("Account for my room")
                .setView(scroll)
                .setPositiveButton("Send report", null)   // set below so the dialog stays open when something is missing
                .setNegativeButton("Cancel", null)
                .create();
        dlg.setOnShowListener(x -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String room = etRoom.getText().toString().trim();
            int checked = group.getCheckedRadioButtonId();
            if (room.isEmpty()) { etRoom.setError("Which room are you in?"); return; }
            if (checked < 3001 || checked > 3003) { Toast.makeText(this, "Choose one of the three options.", Toast.LENGTH_SHORT).show(); return; }
            int count = -1;
            String c = etCount.getText().toString().trim();
            if (!c.isEmpty()) { try { count = Integer.parseInt(c); } catch (NumberFormatException e) { count = -1; } }
            sendRoomReport(alertId, new RoomReport(userEmail, currentUser.getName(), room, codes[checked - 3001], count,
                    etNote.getText().toString(), 0));
            dlg.dismiss();
        }));
        dlg.show();
    }

    private void sendRoomReport(final String alertId, final RoomReport report) {
        RoomReportStore.save(this, alertId, report.room, report.status, RoomReportStore.SENDING);
        refreshRollCall();
        AlertTracker.saveRoomReport(alertId, report, new AlertTracker.Callback<Void>() {
            @Override public void onSuccess(Void value) {
                RoomReportStore.setDelivery(MainActivity.this, alertId, RoomReportStore.SENT);
                // Reporting your room shows you know about the alert, so count it as an acknowledgment too
                AlertTracker.markAcknowledged(alertId, null);
                LastAlertStore.markAcked(MainActivity.this, alertId);
                refreshRollCall();
                Toast.makeText(MainActivity.this, "Sent to the administrators: " + report.room + ", "
                        + RoomReport.label(report.status).toLowerCase(Locale.US) + ".", Toast.LENGTH_LONG).show();
            }
            @Override public void onError(String message) {
                RoomReportStore.setDelivery(MainActivity.this, alertId, RoomReportStore.FAILED);
                refreshRollCall();
                Toast.makeText(MainActivity.this, "Report not sent: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void refreshLastAlertCard() {
        View panel = findViewById(R.id.panelLastAlert);
        if (panel == null) return;
        ImageView icon = findViewById(R.id.imgStatusIcon);
        TextView headline = findViewById(R.id.tvStatusHeadline);
        TextView sub = findViewById(R.id.tvStatusSub);
        LastAlertStore.Saved last = LastAlertStore.get(this);

        if (last == null) {
            panel.setVisibility(View.GONE);
            icon.setImageResource(R.drawable.img_shield);
            headline.setText("No active alerts");
            headline.setTextColor(0xFF111111);
            sub.setText("You are registered and ready to receive campus alerts.");
            return;
        }

        panel.setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.tvLastTitle)).setText(last.title);
        ((TextView) findViewById(R.id.tvLastBody)).setText(last.body);

        // "Sent 1:35 PM by Glenn" (the time the admin sent it), then when this phone got it
        StringBuilder when = new StringBuilder();
        if (last.sentAt > 0) {
            when.append("Sent ").append(formatWhen(last.sentAt));
            if (!last.sentBy.isEmpty()) when.append(" by ").append(last.sentBy);
            when.append("  \u2022  ");
        }
        when.append("Received ").append(DateUtils.getRelativeTimeSpanString(
                last.receivedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));
        if (last.isResolved() && last.resolvedAt > 0) {
            when.append("\nEnded ").append(formatWhen(last.resolvedAt));
        }
        ((TextView) findViewById(R.id.tvLastTime)).setText(when.toString());

        View btnAck = findViewById(R.id.btnAcknowledge);
        View badge = findViewById(R.id.tvAckedBadge);

        if (last.isResolved()) {
            boolean falseAlarm = LastAlertStore.STATE_FALSE_ALARM.equals(last.state);
            icon.setImageResource(R.drawable.img_shield);
            headline.setText(falseAlarm ? "False alarm" : "All clear");
            headline.setTextColor(falseAlarm ? 0xFFB26A00 : 0xFF2E7D32);
            sub.setText(last.resolutionText.isEmpty() ? "This alert has ended." : last.resolutionText);
            btnAck.setVisibility(View.GONE);
            badge.setVisibility(last.acked ? View.VISIBLE : View.GONE);
            return;
        }

        boolean stale = System.currentTimeMillis() - last.displayTime() > STALE_MS;
        String staleNote = stale ? "\nThis alert was sent over 30 minutes ago. Check official updates." : "";
        if (last.acked) {
            icon.setImageResource(R.drawable.img_shield);
            headline.setText("Alert acknowledged");
            headline.setTextColor(0xFF111111);
            sub.setText("Follow the instructions below and wait for further updates." + staleNote);
            btnAck.setVisibility(View.GONE);
            badge.setVisibility(View.VISIBLE);
        } else {
            icon.setImageResource(R.drawable.img_shield_alert);
            headline.setText("Alert received");
            headline.setTextColor(0xFFE53935);
            sub.setText("Please acknowledge that you have seen it." + staleNote);
            btnAck.setVisibility(View.VISIBLE);
            badge.setVisibility(View.GONE);
        }
    }

    private String rosterLine = "";   // admins only: "Roster: 40 people, 31 with a phone, 28 agreed to the notice"

    private void loadRosterLine() {
        if (!"ADMIN".equals(userRole)) return;
        AlertTracker.loadRoster(new AlertTracker.Callback<List<Audience.Member>>() {
            @Override public void onSuccess(List<Audience.Member> members) {
                int phones = 0, agreed = 0, students = 0;
                for (Audience.Member m : members) {
                    if (m.hasToken) phones++;
                    if (m.consented) agreed++;
                    if ("STUDENT".equals(m.role)) students++;
                }
                rosterLine = "Roster: " + members.size() + " people (" + students + " students), "
                        + phones + " signed in on a phone, " + agreed + " agreed to the privacy notice";
                refreshReadiness();
            }
            @Override public void onError(String message) { /* keep the old line */ }
        });
    }

    private void refreshReadiness() {
        refreshReadinessText();
        TextView tv = findViewById(R.id.tvNotifStatus);
        if (tv != null && "ADMIN".equals(userRole) && !rosterLine.isEmpty()) {
            tv.setText(tv.getText() + "\n" + rosterLine);
        }
    }

    private void refreshReadinessText() {
        TextView tv = findViewById(R.id.tvNotifStatus);
        if (tv == null) return;
        List<PermissionHelper.Problem> problems = PermissionHelper.problems(this);
        if (problems.isEmpty()) {
            String dnd = PermissionHelper.activeDndMode(this);
            if (dnd == null) {
                tv.setText("Alert readiness: all set");
                tv.setTextColor(0xFF2E7D32);
            } else {
                tv.setText("Alert readiness: all set\nDo Not Disturb is ON (" + dnd
                        + "). Some phones may still hide or silence alerts.");
                tv.setTextColor(0xFFB26A00);
            }
        } else {
            tv.setText("Alert readiness: " + problems.size() + (problems.size() == 1 ? " thing needs" : " things need")
                    + " attention. Tap to fix.");
            tv.setTextColor(PermissionHelper.hasCriticalProblem(problems) ? 0xFFE53935 : 0xFFB26A00);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        AlertNotifications.createChannel(this); // creates the Do Not Disturb channel once access is granted
        refreshLastAlertCard();
        refreshReadiness();
        loadRosterLine();
        refreshSetupRows();   // the person may have just come back from a settings screen
        maybeRemind();
    }

    // ------------------------------------------------------------------
    // ALERT READINESS: setup sheet + reminders
    // ------------------------------------------------------------------
    private void startSettings(Intent intent) {
        try {
            startActivity(intent);
        } catch (Exception e) {
            try {
                startActivity(PermissionHelper.appDetailsIntent(this));
            } catch (Exception ignored) {
                Toast.makeText(this, "Open Settings > Apps > Ligtas to change this.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void fixNotifications() {
        if (!PermissionHelper.notificationPermissionGranted(this)
                && !PermissionHelper.askedNotificationPermission(this)) {
            PermissionHelper.markAskedNotificationPermission(this);
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        } else {
            startSettings(PermissionHelper.notificationSettingsIntent(this));
        }
    }

    private void fixBattery() {
        try {
            startActivity(PermissionHelper.batteryRequestIntent(this));
        } catch (Exception e) {
            startSettings(PermissionHelper.batteryListIntent());
        }
    }

    private void fixAutostart() {
        for (Intent i : PermissionHelper.autostartIntents(this)) {
            try {
                startActivity(i);
                return;
            } catch (Exception ignored) {
                // try the next one
            }
        }
    }

    /** One row of the setup sheet. */
    private void addSetupRow(String title, String why, boolean ok, String buttonText, Runnable fix, boolean needsConfirm) {
        View row = getLayoutInflater().inflate(R.layout.item_setup_row, setupRowsContainer, false);
        ((TextView) row.findViewById(R.id.tvSetupTitle)).setText(title);
        ((TextView) row.findViewById(R.id.tvSetupWhy)).setText(why);
        TextView okView = row.findViewById(R.id.tvSetupOk);
        Button btn = row.findViewById(R.id.btnSetupFix);
        TextView confirm = row.findViewById(R.id.tvSetupConfirm);

        if (ok) {
            okView.setVisibility(View.VISIBLE);
            btn.setVisibility(View.GONE);
        } else {
            btn.setText(buttonText);
            btn.setOnClickListener(v -> fix.run());
            if (needsConfirm) {
                // Autostart cannot be detected, so the person confirms it themselves
                confirm.setVisibility(View.VISIBLE);
                confirm.setOnClickListener(v -> {
                    PermissionHelper.setAutostartConfirmed(this, true);
                    refreshSetupRows();
                });
            }
        }
        setupRowsContainer.addView(row);
    }

    private void showSetupSheet() {
        if (setupDialog != null && setupDialog.isShowing()) return;
        setupDialog = new BottomSheetDialog(this);
        View sheet = getLayoutInflater().inflate(R.layout.sheet_permission_setup, null);
        setupDialog.setContentView(sheet);
        setupDialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        setupRowsContainer = sheet.findViewById(R.id.setupRows);

        sheet.findViewById(R.id.btnSetupDone).setOnClickListener(v -> setupDialog.dismiss());
        setupDialog.setOnDismissListener(d -> {
            PermissionHelper.markSetupDone(this, userEmail); // do not ask again automatically
            setupDialog = null;
            setupRowsContainer = null;
            refreshReadiness();
        });
        refreshSetupRows();
        setupDialog.show();
    }

    /** Rebuilds the rows with the current state (also called when returning from a settings screen). */
    private void refreshSetupRows() {
        if (setupDialog == null || setupRowsContainer == null) return;
        setupRowsContainer.removeAllViews();

        addSetupRow("Notifications",
                "Without this, emergency alerts cannot appear on your phone at all.",
                PermissionHelper.notificationsAllowed(this), "Turn on", this::fixNotifications, false);
        addSetupRow("Alert vibration",
                "Keeps alerts vibrating even if your phone is on silent. If the alert channel is muted, you could miss an emergency.",
                PermissionHelper.channelHealthy(this), "Fix", () -> startSettings(PermissionHelper.channelSettingsIntent(this)), false);
        addSetupRow("Show on lock screen",
                "Lets you see an emergency alert without unlocking your phone. On the next screen, set \"On the Lock screen\" to show notifications.",
                !PermissionHelper.lockscreenHidden(this), "Fix", () -> startSettings(PermissionHelper.channelSettingsIntent(this)), false);
        addSetupRow("Allow during Do Not Disturb",
                "Do Not Disturb silences most notifications. On the next screen, find Ligtas in the list and turn it on so emergency alerts come through.",
                PermissionHelper.canBypassDnd(this), "Allow", () -> startSettings(PermissionHelper.dndSettingsIntent()), false);
        addSetupRow("Run without battery limits",
                "Your phone may delay or block alerts to save battery, especially when the app is closed.",
                PermissionHelper.batteryUnrestricted(this), "Allow", this::fixBattery, false);
        if (PermissionHelper.needsAutostartStep()) {
            addSetupRow("Autostart",
                    "On Xiaomi, Oppo, Vivo and Realme phones, this lets Ligtas receive alerts after you close it. It cannot be checked automatically.",
                    PermissionHelper.autostartConfirmed(this), "Open settings", this::fixAutostart, true);
        }
    }

    /** Reminds the person when something that alerts depend on has been turned off since last time. */
    private void maybeRemind() {
        if (reminderShowing || (setupDialog != null && setupDialog.isShowing())) return;
        if (!PermissionHelper.setupDoneFor(this, userEmail)) return; // first-time sheet comes first

        List<PermissionHelper.Problem> problems = PermissionHelper.problems(this);
        if (problems.isEmpty()) return;

        long now = System.currentTimeMillis();
        boolean critical = PermissionHelper.hasCriticalProblem(problems);
        if (critical) {
            if (now - lastCriticalReminder < 2 * 60 * 1000L) return;      // not again within 2 minutes
        } else {
            if (now - PermissionHelper.lastNormalReminder(this) < 24L * 60 * 60 * 1000L) return; // once a day
        }

        StringBuilder message = new StringBuilder();
        for (PermissionHelper.Problem p : problems) message.append("\u2022 ").append(p.message).append("\n\n");
        message.append("Emergency alerts only work when these are on.");

        reminderShowing = true;
        new AlertDialog.Builder(this)
                .setTitle(critical ? "Alerts may not reach you" : "Improve alert reliability")
                .setMessage(message.toString())
                .setPositiveButton("Fix now", (d, w) -> showSetupSheet())
                .setNegativeButton("Later", null)
                .setOnDismissListener(d -> {
                    reminderShowing = false;
                    if (critical) lastCriticalReminder = System.currentTimeMillis();
                    else PermissionHelper.markNormalReminder(this);
                })
                .show();
    }

    @Override
    protected void onDestroy() {
        if (lastAlertListener != null) {
            LastAlertStore.prefs(this).unregisterOnSharedPreferenceChangeListener(lastAlertListener);
        }
        super.onDestroy();
    }
}
