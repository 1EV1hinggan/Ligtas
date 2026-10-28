package com.ligtas.app;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps the status sheet's re-alert card up to date: how many rounds went out, when, and the auto timer.
 * Reads only the alert document (written by the server and by admins), never anyone's device token.
 */
final class EscalationFeed {
    /** Minutes to wait before the first automatic re-alert. Admins can change or switch it off on the status sheet. */
    static final int DEFAULT_AUTO_MINUTES = 5;
    static final int[] AUTO_CHOICES = {0, 2, 5, 10};   // 0 = off

    private final String alertId;
    private final AlertStatusState state;
    private ListenerRegistration reg;

    EscalationFeed(String alertId, AlertStatusState state) {
        this.alertId = alertId;
        this.state = state;
    }

    void start() {
        reg = FirebaseFirestore.getInstance().collection("alerts").document(alertId).addSnapshotListener((doc, e) -> {
            if (e != null || doc == null || !doc.exists()) return;
            Object recips = doc.get("recipients");
            state.setEscNamed(recips instanceof List && !((List<?>) recips).isEmpty());
            state.setEscAutoMin(number(doc, "autoEscalateMin"));
            state.setEscRounds(number(doc, "escalations"));
            state.setEscLastCount(number(doc, "lastEscalatedCount"));
            state.setEscLastAt(millis(doc.getTimestamp("lastEscalatedAt")));
            state.setEscSentAt(millis(doc.getTimestamp("sentAt")));
        });
    }

    void stop() {
        if (reg != null) reg.remove();
    }

    /** Admin changed the timer on the status sheet. */
    void setAuto(int minutes, AlertTracker.Callback<Void> cb) {
        state.setEscAutoMin(minutes);   // show it at once; the listener confirms it
        FirebaseFirestore.getInstance().collection("alerts").document(alertId).update("autoEscalateMin", minutes)
                .addOnSuccessListener(v -> cb.onSuccess(null))
                .addOnFailureListener(e -> cb.onError(String.valueOf(e.getMessage())));
    }

    /** Called when an alert is sent: turns the automatic re-alert on with the default delay. */
    static void applyDefault(String alertId, String titleTl, String messageTl) {
        Map<String, Object> m = new HashMap<>();
        m.put("autoEscalateMin", DEFAULT_AUTO_MINUTES);
        m.put("titleTl", titleTl);        // kept so a re-alert can go out in Taglish too
        m.put("messageTl", messageTl);
        FirebaseFirestore.getInstance().collection("alerts").document(alertId).update(m);
    }

    private static int number(DocumentSnapshot d, String key) {
        Object o = d.get(key);
        return o instanceof Number ? ((Number) o).intValue() : 0;
    }

    private static long millis(Timestamp t) {
        return t == null ? 0L : t.toDate().getTime();
    }
}
