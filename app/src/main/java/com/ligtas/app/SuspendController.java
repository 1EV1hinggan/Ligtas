package com.ligtas.app;

import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin "Suspend classes": a one-tap calendar rule for emergencies like a typhoon. It writes the same rule that the
 * campus console's Calendar rules tab writes. Moved out of MainActivity; the behaviour is unchanged.
 */
final class SuspendController {
    private final AppCompatActivity activity;

    SuspendController(AppCompatActivity activity) {
        this.activity = activity;
    }

    /** Loads the campus setup, then shows the sheet. adminName is written into the rule's reason. */
    void show(String campusId, String adminName) {
        Toast.makeText(activity, "Loading the campus setup...", Toast.LENGTH_SHORT).show();
        CampusStore.load(campusId, new AlertTracker.Callback<CampusConfig>() {
            @Override public void onSuccess(CampusConfig config) {
                if (config == null) {
                    Toast.makeText(activity, "No campus setup found. Save one in the campus console first.", Toast.LENGTH_LONG).show();
                    return;
                }
                build(config, adminName);
            }
            @Override public void onError(String message) {
                Toast.makeText(activity, "Could not read the campus setup: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void build(CampusConfig config, String adminName) {
        final List<String> whoCodes = new ArrayList<>();
        final SuspendState st = new SuspendState();
        st.getWhoLabels().add("Whole campus");
        whoCodes.add("*");
        for (CampusConfig.College col : config.colleges) {
            st.getWhoLabels().add(col.code + (col.name.isEmpty() ? "" : " - " + col.name));
            whoCodes.add(col.code);
        }

        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.getBehavior().setSkipCollapsed(true);
        SuspendBridge.attach(dialog, activity, st, new SuspendListener() {
            @Override public void onCancel() { dialog.dismiss(); }
            @Override public void onApply() {
                long today = startOfDay(System.currentTimeMillis());
                long tomorrow = startOfDay(today + 36L * 60 * 60 * 1000);   // 36 h after midnight is safely tomorrow (DST)
                int when = st.getWhenIndex();
                long start = when == 1 ? tomorrow : today;
                long end = when == 0 ? today : tomorrow;
                String code = whoCodes.get(st.getWhoIndex());
                String mode = st.getModeIndex() == 1 ? "NC" : "OL";
                dialog.dismiss();
                saveRule(config, start, end, code, mode, adminName);
            }
        });
        SheetUi.makeTransparent(dialog);
        dialog.show();
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

    private void saveRule(CampusConfig config, long start, long end, String college, String mode, String by) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        com.google.firebase.firestore.DocumentReference ref = db.collection("campuses").document(config.id);
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
            Toast.makeText(activity, "Done. Remove it later in the campus console (Calendar rules).", Toast.LENGTH_LONG).show();
        }).addOnFailureListener(e ->
                Toast.makeText(activity, "Could not save: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }
}
