package com.ligtas.app;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Map;

/** Reads the campus setup (campuses/{id}) that admins edit in the web console. */
public final class CampusStore {
    private static CampusConfig cache;

    private CampusStore() { }

    public static CampusConfig cached() {
        return cache;
    }

    private static CampusConfig parse(DocumentSnapshot d) {
        Map<String, Object> data = d.getData();
        return data == null ? null : CampusConfig.fromMap(d.getId(), data);
    }

    /**
     * Loads a campus. If the roster names one and it exists, that one is used. If the roster has no campus,
     * or names one that does not exist (for example "alangilan" while the console saved "bsu-alangilan-campus"),
     * the campus an admin saved most recently is used instead. Reports null only when nothing was ever saved.
     */
    public static void load(String campusId, AlertTracker.Callback<CampusConfig> cb) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        if (campusId != null && !campusId.isEmpty()) {
            db.collection("campuses").document(campusId).get()
                    .addOnSuccessListener(d -> {
                        CampusConfig c = d.exists() ? parse(d) : null;
                        if (c != null) { cache = c; cb.onSuccess(c); } else loadLatest(db, cb);
                    })
                    .addOnFailureListener(e -> cb.onError(String.valueOf(e.getMessage())));
        } else {
            loadLatest(db, cb);
        }
    }

    private static void loadLatest(FirebaseFirestore db, AlertTracker.Callback<CampusConfig> cb) {
        db.collection("campuses").limit(20).get()
                .addOnSuccessListener(snap -> {
                    DocumentSnapshot best = null;
                    long bestTime = -1;
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        com.google.firebase.Timestamp t = d.getTimestamp("updatedAt");
                        long ms = t == null ? 0 : t.toDate().getTime();
                        if (best == null || ms > bestTime) { best = d; bestTime = ms; }
                    }
                    CampusConfig c = best == null ? null : parse(best);
                    if (c != null) cache = c;
                    cb.onSuccess(c);
                })
                .addOnFailureListener(e -> cb.onError(String.valueOf(e.getMessage())));
    }
}
