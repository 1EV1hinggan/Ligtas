package com.ligtas.app;

import android.text.TextUtils;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.function.Supplier;

/**
 * The admin's limited-edit sheet: area, who receives it, one approved instruction and a short note.
 * Moved out of MainActivity; the behaviour is unchanged. Saved choices go into AlertEdits.
 */
final class EditSheetController {
    private final AppCompatActivity activity;
    private final AlertEdits edits;
    private final Supplier<String> campusId;
    private final Supplier<String> userEmail;
    private final Runnable onSaved;

    EditSheetController(AppCompatActivity activity, AlertEdits edits, Supplier<String> campusId,
                        Supplier<String> userEmail, Runnable onSaved) {
        this.activity = activity;
        this.edits = edits;
        this.campusId = campusId;
        this.userEmail = userEmail;
        this.onSaved = onSaved;
    }

    private Audience audience(String area, String college, String program, int year, boolean onSiteOnly) {
        return AlertEdits.audienceFor(campusId.get(), activity.getResources().getStringArray(R.array.campus_areas)[0],
                area, college, program, year, onSiteOnly);
    }

    /** The limited-edit tab: area, one of the approved instructions, and a short note. */
    void show(AlertTemplate selectedTemplate) {
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.getBehavior().setSkipCollapsed(true);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        // Compose draws the sheet from this state object; everything below is the same logic as before
        final EditAlertSheetState st = new EditAlertSheetState();
        st.setTitle("Edit alert: " + selectedTemplate.getLabel());
        st.setNoteRequired(selectedTemplate.isNoteRequired());

        // Affected area (replaced by the campus setup's Locations list once it loads)
        String[] areas = activity.getResources().getStringArray(R.array.campus_areas);
        java.util.Collections.addAll(st.getAreaOptions(), areas);
        st.setAreaIndex(Math.max(0, st.getAreaOptions().indexOf(edits.area)));

        // College / Program / Year level come from the campus setup (loaded in a moment)
        st.getCollegeOptions().add("All colleges");
        st.getProgramOptions().add("All programs");
        st.getYearOptions().add("All years");
        st.setOnCampus(edits.onCampus);
        // Always read the campus setup again, so what an admin just saved in the console shows up right away
        AudienceContext.invalidate();
        final AudienceContext[] loaded = {null};

        // Approved instruction variants (the wording is locked, only the choice is yours)
        java.util.Collections.addAll(st.getInstructions(), selectedTemplate.getInstructions());
        st.setVariant(edits.variant);
        st.setNote(edits.note);

        // Live preview of exactly what everyone will receive
        Runnable updatePreview = () -> {
            Alert a = selectedTemplate.createAlert("PREVIEW",
                    audience(st.area(), st.college(), st.program(), st.yearLevel(), st.getOnCampus()).label(),
                    st.getVariant(),
                    AlertEdits.cleanNote(st.getNote()));
            st.setPreview(a.getTitle() + "\n" + a.getMessage());
        };
        // Keeps the checkbox and the hint in step with the choices
        final Runnable[] refreshSelf = {null};
        Runnable refreshAudienceUi = () -> {
            String area = st.area();
            boolean wholeCampus = area.equals(activity.getResources().getStringArray(R.array.campus_areas)[0]);
            boolean everyone = st.college().isEmpty() && st.program().isEmpty() && st.yearLevel() == 0;
            st.setOnCampusVisible(wholeCampus && everyone);
            StringBuilder hint = new StringBuilder();
            AudienceContext ctx = loaded[0];
            if (ctx == null) {
                hint.append("Loading the campus setup...");
            } else if (ctx.config == null) {
                hint.append(ctx.configError.isEmpty()
                        ? "No campus setup found in the database (campuses collection is empty or in a different Firebase project)."
                        : "Could not read the campus setup (" + ctx.configError + ").")
                        .append(" Nobody is skipped.");
            } else if (wholeCampus && everyone && st.getOnCampus()) {
                AudiencePlan plan = audience(area, "", "", 0, true).plan(ctx.config, System.currentTimeMillis());
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
                Audience chosen = audience(area, st.college(), st.program(), st.yearLevel(), st.getOnCampus());
                AlertTracker.Reach rr = ctx.reach(chosen, chosen.plan(ctx.config, System.currentTimeMillis()), userEmail.get(),
                        () -> { if (refreshSelf[0] != null) refreshSelf[0].run(); });
                if (rr == null) {
                    hint.append(hint.length() > 0 ? "\n" : "").append("Counting phones...");
                } else {
                hint.append(hint.length() > 0 ? "\n" : "").append(rr.estimated ? "Reaches at most about " : "Reaches about ").append(rr.reachable)
                        .append(rr.reachable == 1 ? " phone." : " phones.");
                if (rr.reachable == 0) hint.append(" NOBODY would receive this. Untick \"Only people on campus\" to alert everyone.");
                }
            }
            st.setHint(hint.toString());
        };
        refreshSelf[0] = refreshAudienceUi;

        // Rebuilds Program and Year level for the chosen college. initial = keep the values saved earlier.
        java.util.function.Consumer<Boolean> fillPrograms = initial -> {
            AudienceContext ctx = loaded[0];
            String college = st.college();
            String keepProgram = initial ? edits.program : st.program();
            st.getProgramOptions().clear();
            st.getProgramOptions().add("All programs");
            if (ctx != null && ctx.config != null) {
                for (CampusConfig.Program pr : ctx.config.programs) {
                    if (college.isEmpty() || Slug.of(pr.collegeCode).equals(Slug.of(college))) st.getProgramOptions().add(pr.code);
                }
            }
            int idx = Math.max(0, st.getProgramOptions().indexOf(keepProgram));
            st.setProgramIndex(idx);
            // Year levels: up to the longest program in the choice
            int maxYears = 4;
            if (ctx != null && ctx.config != null) {
                maxYears = 1;
                String prog = st.getProgramOptions().get(idx);
                for (CampusConfig.Program pr : ctx.config.programs) {
                    boolean inScope = !prog.equals("All programs") ? pr.code.equals(prog)
                            : (college.isEmpty() || Slug.of(pr.collegeCode).equals(Slug.of(college)));
                    if (inScope) maxYears = Math.max(maxYears, pr.years);
                }
            }
            int keepYear = initial ? edits.year : st.yearLevel();
            st.getYearOptions().clear();
            st.getYearOptions().add("All years");
            for (int y = 1; y <= maxYears; y++) st.getYearOptions().add("Year " + y);
            st.setYearIndex(keepYear >= 0 && keepYear < st.getYearOptions().size() ? keepYear : 0);
        };

        // Fill the lists from the campus setup
        Runnable fillAll = () -> {
            AudienceContext ctx = loaded[0];
            if (ctx != null && ctx.config != null && !ctx.config.locations.isEmpty()) {
                st.getAreaOptions().clear();
                st.getAreaOptions().add(activity.getResources().getStringArray(R.array.campus_areas)[0]); // "Whole campus"
                st.getAreaOptions().addAll(ctx.config.locations);
                st.setAreaIndex(Math.max(0, st.getAreaOptions().indexOf(edits.area)));
            }
            st.getCollegeOptions().clear();
            st.getCollegeOptions().add("All colleges");
            if (ctx != null && ctx.config != null) for (CampusConfig.College c : ctx.config.colleges) st.getCollegeOptions().add(c.code);
            st.setCollegeIndex(Math.max(0, st.getCollegeOptions().indexOf(edits.college)));
            fillPrograms.accept(true);
            refreshAudienceUi.run();
            updatePreview.run();
        };

        EditAlertSheetListener listener = new EditAlertSheetListener() {
            @Override public void onCollegeChanged() {
                fillPrograms.accept(false);
                refreshAudienceUi.run();
                updatePreview.run();
            }
            @Override public void onAudienceChanged() {
                refreshAudienceUi.run();
                updatePreview.run();
            }
            @Override public void onContentChanged() { updatePreview.run(); }
            @Override public void onCancel() { dialog.dismiss(); }
            @Override public void onConfirm() {
                String note = AlertEdits.cleanNote(st.getNote());
                if (selectedTemplate.isNoteRequired() && note.isEmpty()) {
                    st.setNoteError("A short note is required for this type");
                    return;
                }
                // Save: these changes are used when the alert is sent
                edits.area = st.area();
                edits.college = st.college();
                edits.program = st.program();
                edits.year = st.yearLevel();
                edits.onCampus = st.getOnCampus();
                edits.variant = st.getVariant();
                edits.note = note;
                edits.edited = true;
                onSaved.run();
                dialog.dismiss();
                Toast.makeText(activity, "Changes saved. They will be sent with the alert.", Toast.LENGTH_SHORT).show();
            }
        };

        if (loaded[0] != null) {
            fillAll.run();
        } else {
            AudienceContext.load(campusId.get(), new AlertTracker.Callback<AudienceContext>() {
                @Override public void onSuccess(AudienceContext ctx) { loaded[0] = ctx; fillAll.run(); }
                @Override public void onError(String message) { st.setHint("Could not load the campus setup: " + message); }
            });
        }
        refreshAudienceUi.run();
        updatePreview.run();

        EditAlertSheetBridge.attach(dialog, activity, st, listener);
        SheetUi.makeTransparent(dialog); // keep the dark glass look: no white box behind the Compose sheet
        dialog.show();
    }
}
