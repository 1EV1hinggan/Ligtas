package com.ligtas.app;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.AggregateSource;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything that records and reads alert delivery and acknowledgment in Firestore.
 * Encapsulates the database layout so the screens never touch it directly.
 *
 *   alerts/{alertId}                 the alert, plus who was expected to receive it
 *   alerts/{alertId}/acks/{email}    one record per person: deliveredAt, ackedAt
 */
public final class AlertTracker {

    private AlertTracker() { }

    public interface Callback<T> {
        void onSuccess(T value);
        void onError(String message);
    }

    public static class Recipient {
        public final String email;
        public final String name;
        public Recipient(String email, String name) {
            this.email = email;
            this.name = name;
        }
    }

    /** What the admin sees on the status screen. */
    public static class Status {
        public String title = "";
        public String message = "";
        public int expected = 0;
        public int delivered = 0;
        public int acknowledged = 0;
        public double avgSeconds = -1; // -1 = nobody has acknowledged yet
        public String state = "ACTIVE";    // ACTIVE, ALL_CLEAR or FALSE_ALARM
        public String sentBy = "";
        public String audience = "";
        public long sentAtMillis = -1;
        public long resolvedAtMillis = -1;
        public List<String> pending = new ArrayList<>();
    }

    /** The most recent alert anyone sent (used for the send cooldown and to reopen Status after a restart). */
    public static class Latest {
        public String alertId = "";
        public String title = "";
        public String sentBy = "";
        public long sentAtMillis = -1;
        public String state = "ACTIVE";
    }

    private static FirebaseFirestore db() {
        return FirebaseFirestore.getInstance();
    }

    private static String myEmail() {
        FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
        return (u == null || u.getEmail() == null) ? null : u.getEmail().toLowerCase();
    }

    // ---------------------------- admin side ----------------------------

    /** Step 1 when sending: record the alert so recipients' delivery records are accepted. */
    public static void saveAlert(Alert alert, String senderEmail, String senderName,
                                 AudiencePlan plan, Callback<Void> cb) {
        Map<String, Object> data = new HashMap<>();
        data.put("alertId", alert.getAlertId());
        data.put("title", alert.getTitle());
        data.put("message", alert.getMessage());
        data.put("area", alert.getTargetArea());
        data.put("sentBy", senderEmail);
        data.put("sentByName", senderName == null ? "" : senderName);
        data.put("status", "ACTIVE");
        data.put("audience", plan.audience.label());
        data.put("targets", plan.targets);          // exactly where it was pushed, so an all-clear goes to the same people
        data.put("onlineGroups", plan.onlineCsv()); // groups that were online and skipped
        data.put("sentAt", FieldValue.serverTimestamp());
        db().collection("alerts").document(alert.getAlertId()).set(data)
                .addOnSuccessListener(v -> cb.onSuccess(null))
                .addOnFailureListener(e -> cb.onError(String.valueOf(e.getMessage())));
    }

    /** Reads the whole roster (admins only). */
    public static void loadRoster(Callback<List<Audience.Member>> cb) {
        db().collection("roster").get()
                .addOnSuccessListener(snap -> {
                    List<Audience.Member> list = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        String token = d.getString("fcmToken");
                        String name = d.getString("name");
                        String college = d.getString("college");
                        if (college == null || college.isEmpty()) college = d.getString("department"); // older roster rows
                        Audience.Member mem = new Audience.Member(d.getId(),
                                (name == null || name.isEmpty()) ? d.getId() : name,
                                d.getString("role"), college, d.getString("program"),
                                yearOf(d.get("yearLevel")), d.getString("building"),
                                token != null && !token.isEmpty());
                        mem.consented = yearOf(d.get("consentVersion")) >= ConsentNotice.VERSION;
                        mem.teaching = Teaching.parse(d.get("teaches"));
                        list.add(mem);
                    }
                    cb.onSuccess(list);
                })
                .addOnFailureListener(e -> cb.onError(String.valueOf(e.getMessage())));
    }

    private static int yearOf(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(String.valueOf(o).trim()); } catch (NumberFormatException e) { return 0; }
    }

    /** Step 2: store who was expected to receive it (registered people in the audience, except the sender). */
    public static void storeRecipients(String alertId, String senderEmail, AudiencePlan plan, List<Audience.Member> members) {
        if (members == null) {
            // Big roster: store only how many were expected (a list of thousands would not fit in one document)
            estimateReach(plan.audience, new Callback<Reach>() {
                @Override public void onSuccess(Reach r) {
                    Map<String, Object> update = new HashMap<>();
                    update.put("expectedCount", r.reachable);
                    update.put("namesListed", false);
                    db().collection("alerts").document(alertId).update(update);
                }
                @Override public void onError(String message) { }
            });
            return;
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (Audience.Member m : members) {
            if (!m.hasToken || m.email.equals(senderEmail) || !plan.includes(m)) continue;
            Map<String, Object> entry = new HashMap<>();
            entry.put("email", m.email);
            entry.put("name", m.name);
            list.add(entry);
        }
        Map<String, Object> update = new HashMap<>();
        update.put("recipients", list);
        update.put("expectedCount", list.size());
        db().collection("alerts").document(alertId).update(update);
    }

    /** How many people an audience covers: in the group, and how many of them can actually be reached. */
    public static class Reach {
        public int inGroup = 0;
        public int reachable = 0;
        public boolean estimated = false;   // from database counts (big rosters): ignores online-week skipping and teachers' teaching lists
    }

    /** Roster size and a few counts without downloading anyone (each count query costs about one read per 1000 people). */
    public static void rosterCounts(Callback<int[]> cb) {      // {total, students, phones, agreed}
        final int[] out = new int[4];
        final java.util.concurrent.atomic.AtomicInteger left = new java.util.concurrent.atomic.AtomicInteger(4);
        final java.util.concurrent.atomic.AtomicBoolean failed = new java.util.concurrent.atomic.AtomicBoolean(false);
        Query[] qs = {
            db().collection("roster"),
            db().collection("roster").whereEqualTo("role", "STUDENT"),
            db().collection("roster").whereEqualTo("hasToken", true),
            db().collection("roster").whereGreaterThanOrEqualTo("consentVersion", ConsentNotice.VERSION)
        };
        for (int i = 0; i < qs.length; i++) {
            final int k = i;
            qs[i].count().get(AggregateSource.SERVER)
                .addOnSuccessListener(r -> { out[k] = (int) r.getCount(); if (left.decrementAndGet() == 0 && !failed.get()) cb.onSuccess(out); })
                .addOnFailureListener(e -> { if (failed.compareAndSet(false, true)) cb.onError(String.valueOf(e.getMessage())); });
        }
    }

    /** People in the roster, as one cheap count. */
    public static void countRoster(Callback<Integer> cb) {
        db().collection("roster").count().get(AggregateSource.SERVER)
                .addOnSuccessListener(r -> cb.onSuccess((int) r.getCount()))
                .addOnFailureListener(e -> cb.onError(String.valueOf(e.getMessage())));
    }

    /** Reach of an audience from count queries only (used when the roster is too big to download). */
    public static void estimateReach(Audience aud, Callback<Reach> cb) {
        Query base = db().collection("roster");
        for (Map.Entry<String, Object> e : aud.filters().entrySet()) base = base.whereEqualTo(e.getKey(), e.getValue());
        final Query inGroupQ = base, phoneQ = base.whereEqualTo("hasToken", true);
        final Reach r = new Reach();
        r.estimated = true;
        inGroupQ.count().get(AggregateSource.SERVER).addOnSuccessListener(a -> {
            r.inGroup = (int) a.getCount();
            phoneQ.count().get(AggregateSource.SERVER).addOnSuccessListener(b -> { r.reachable = (int) b.getCount(); cb.onSuccess(r); })
                    .addOnFailureListener(e -> cb.onError(String.valueOf(e.getMessage())));
        }).addOnFailureListener(e -> cb.onError(String.valueOf(e.getMessage())));
    }

    public static Reach countAudience(List<Audience.Member> members, AudiencePlan plan, String senderEmail) {
        Reach r = new Reach();
        for (Audience.Member m : members) {
            if (m.email.equals(senderEmail) || !plan.includes(m)) continue;
            r.inGroup++;
            if (m.hasToken) r.reachable++;
        }
        return r;
    }

    /** Where an alert was pushed and which groups were skipped. Older alerts have none: callers use the whole campus. */
    public static class Routing {
        public final List<String> targets = new ArrayList<>();
        public String onlineGroups = "";
    }

    public static void getRouting(String alertId, Callback<Routing> cb) {
        db().collection("alerts").document(alertId).get()
                .addOnSuccessListener(doc -> {
                    Routing r = new Routing();
                    Object raw = doc == null ? null : doc.get("targets");
                    if (raw instanceof List) for (Object o : (List<?>) raw) r.targets.add(String.valueOf(o));
                    String ol = doc == null ? null : doc.getString("onlineGroups");
                    r.onlineGroups = ol == null ? "" : ol;
                    cb.onSuccess(r);
                })
                .addOnFailureListener(e -> cb.onError(String.valueOf(e.getMessage())));
    }

    /**
     * Ends an alert: state is "ALL_CLEAR" or "FALSE_ALARM". Only works while the alert is still ACTIVE
     * (the security rules enforce this), so an alert cannot be resolved twice.
     */
    public static void resolveAlert(String alertId, String state, String adminEmail, String message, Callback<Void> cb) {
        Map<String, Object> update = new HashMap<>();
        update.put("status", state);
        update.put("resolvedAt", FieldValue.serverTimestamp());
        update.put("resolvedBy", adminEmail);
        update.put("resolutionMessage", message);
        db().collection("alerts").document(alertId).update(update)
                .addOnSuccessListener(v -> cb.onSuccess(null))
                .addOnFailureListener(e -> cb.onError(String.valueOf(e.getMessage())));
    }

    /** Reads the newest alert, or reports null when none has been sent yet. */
    public static void latestAlert(Callback<Latest> cb) {
        db().collection("alerts").orderBy("sentAt", Query.Direction.DESCENDING).limit(1).get()
                .addOnSuccessListener(snap -> {
                    if (snap.isEmpty()) { cb.onSuccess(null); return; }
                    DocumentSnapshot d = snap.getDocuments().get(0);
                    Latest l = new Latest();
                    l.alertId = d.getId();
                    l.title = d.getString("title") == null ? "" : d.getString("title");
                    String name = d.getString("sentByName");
                    l.sentBy = (name == null || name.isEmpty()) ? String.valueOf(d.getString("sentBy")) : name;
                    Timestamp t = d.getTimestamp("sentAt");
                    l.sentAtMillis = t == null ? -1 : t.toDate().getTime();
                    String st = d.getString("status");
                    l.state = st == null ? "ACTIVE" : st;
                    cb.onSuccess(l);
                })
                .addOnFailureListener(e -> cb.onError(String.valueOf(e.getMessage())));
    }

    /** Watches one alert live: counts, who is still pending, and average response time. */
    public static class Watcher {
        private final Callback<Status> cb;
        private ListenerRegistration alertReg;
        private ListenerRegistration acksReg;

        private String title = "";
        private String message = "";
        private long sentAtMillis = -1;
        private String state = "ACTIVE";
        private String sentBy = "";
        private String audience = "";
        private long resolvedAtMillis = -1;
        private final List<Recipient> recipients = new ArrayList<>();
        private int expectedCount = 0;
        private final Set<String> delivered = new HashSet<>();
        private final Map<String, Long> ackedAt = new HashMap<>();

        public Watcher(String alertId, Callback<Status> cb) {
            this.cb = cb;

            alertReg = db().collection("alerts").document(alertId).addSnapshotListener((doc, e) -> {
                if (e != null) { cb.onError(String.valueOf(e.getMessage())); return; }
                if (doc == null || !doc.exists()) return;
                title = String.valueOf(doc.get("title") == null ? "" : doc.get("title"));
                message = String.valueOf(doc.get("message") == null ? "" : doc.get("message"));
                Timestamp sent = doc.getTimestamp("sentAt");
                sentAtMillis = sent == null ? -1 : sent.toDate().getTime();
                String st = doc.getString("status");
                state = st == null ? "ACTIVE" : st;
                String byName = doc.getString("sentByName");
                sentBy = (byName == null || byName.isEmpty()) ? String.valueOf(doc.getString("sentBy")) : byName;
                audience = doc.getString("audience") == null ? "" : doc.getString("audience");
                Timestamp res = doc.getTimestamp("resolvedAt");
                resolvedAtMillis = res == null ? -1 : res.toDate().getTime();
                recipients.clear();
                Object ec = doc.get("expectedCount");
                expectedCount = ec instanceof Number ? ((Number) ec).intValue() : 0;
                Object raw = doc.get("recipients");
                if (raw instanceof List) {
                    for (Object o : (List<?>) raw) {
                        if (o instanceof Map) {
                            Map<?, ?> m = (Map<?, ?>) o;
                            recipients.add(new Recipient(String.valueOf(m.get("email")), String.valueOf(m.get("name"))));
                        }
                    }
                }
                publish();
            });

            acksReg = db().collection("alerts").document(alertId).collection("acks").addSnapshotListener((snap, e) -> {
                if (e != null) { cb.onError(String.valueOf(e.getMessage())); return; }
                if (snap == null) return;
                delivered.clear();
                ackedAt.clear();
                for (DocumentSnapshot d : snap.getDocuments()) {
                    Timestamp acked = d.getTimestamp("ackedAt");
                    if (acked != null) {
                        delivered.add(d.getId());
                        ackedAt.put(d.getId(), acked.toDate().getTime());
                    } else if (d.getTimestamp("deliveredAt") != null) {
                        delivered.add(d.getId());
                    }
                }
                publish();
            });
        }

        private void publish() {
            Status s = new Status();
            s.title = title;
            s.message = message;
            s.state = state;
            s.sentBy = sentBy;
            s.audience = audience;
            s.sentAtMillis = sentAtMillis;
            s.resolvedAtMillis = resolvedAtMillis;
            s.expected = recipients.isEmpty() ? expectedCount : recipients.size();
            if (recipients.isEmpty() && expectedCount > 0) {          // big audience: counts only, no names
                double tot = 0; int n = 0;
                for (Long at : ackedAt.values()) if (sentAtMillis > 0) { tot += Math.max(0, (at - sentAtMillis) / 1000.0); n++; }
                s.delivered = delivered.size();
                s.acknowledged = ackedAt.size();
                if (n > 0) s.avgSeconds = tot / n;
                s.pending.add("Names are not listed for large audiences. Only counts are shown.");
                cb.onSuccess(s);
                return;
            }
            double totalSeconds = 0;
            int timed = 0;
            for (Recipient r : recipients) {
                if (delivered.contains(r.email)) s.delivered++;
                Long acked = ackedAt.get(r.email);
                if (acked != null) {
                    s.acknowledged++;
                    if (sentAtMillis > 0) {
                        totalSeconds += Math.max(0, (acked - sentAtMillis) / 1000.0);
                        timed++;
                    }
                } else {
                    s.pending.add(delivered.contains(r.email) ? r.name : r.name + "  (not delivered yet)");
                }
            }
            if (timed > 0) s.avgSeconds = totalSeconds / timed;
            if (s.pending.size() > 200) {
                int more = s.pending.size() - 200;
                s.pending = new ArrayList<>(s.pending.subList(0, 200));
                s.pending.add("+" + more + " more");
            }
            cb.onSuccess(s);
        }

        public void stop() {
            if (alertReg != null) alertReg.remove();
            if (acksReg != null) acksReg.remove();
        }
    }

    // --------------------------- recipient side ---------------------------

    /** The phone received the alert (written automatically, no tap needed). */
    public static void markDelivered(String alertId) {
        write(alertId, "deliveredAt", null);
    }

    /** The person tapped Acknowledge. */
    public static void markAcknowledged(String alertId, Callback<Void> cb) {
        write(alertId, "ackedAt", cb);
    }

    /** A teacher's report about their room during an alert (sending again replaces it). */
    public static void saveRoomReport(String alertId, RoomReport r, Callback<Void> cb) {
        String email = myEmail();
        if (email == null || alertId == null || alertId.isEmpty()) {
            if (cb != null) cb.onError("Not signed in");
            return;
        }
        Map<String, Object> d = new HashMap<>();
        d.put("email", email);
        d.put("name", r.name);
        d.put("room", r.room);
        d.put("status", r.status);
        if (r.headcount >= 0) d.put("headcount", r.headcount);
        if (!r.note.isEmpty()) d.put("note", r.note);
        d.put("reportedAt", FieldValue.serverTimestamp());
        db().collection("alerts").document(alertId).collection("rooms").document(email).set(d)
                .addOnSuccessListener(v -> { if (cb != null) cb.onSuccess(null); })
                .addOnFailureListener(e -> { if (cb != null) cb.onError(String.valueOf(e.getMessage())); });
    }

    /** Admins: live list of the room reports for an alert. Call remove() on the result when the screen closes. */
    public static ListenerRegistration watchRooms(String alertId, Callback<List<RoomReport>> cb) {
        return db().collection("alerts").document(alertId).collection("rooms")
                .addSnapshotListener((snap, e) -> {
                    if (e != null) { cb.onError(String.valueOf(e.getMessage())); return; }
                    List<RoomReport> list = new ArrayList<>();
                    if (snap != null) {
                        for (DocumentSnapshot d : snap.getDocuments()) {
                            Object hc = d.get("headcount");
                            Timestamp at = d.getTimestamp("reportedAt");
                            list.add(new RoomReport(d.getId(), d.getString("name"), d.getString("room"), d.getString("status"),
                                    hc instanceof Number ? ((Number) hc).intValue() : -1, d.getString("note"),
                                    at == null ? 0 : at.toDate().getTime()));
                        }
                    }
                    cb.onSuccess(RoomReport.sorted(list));
                });
    }

    private static void write(String alertId, String field, Callback<Void> cb) {
        String email = myEmail();
        if (email == null || alertId == null || alertId.isEmpty()) {
            if (cb != null) cb.onError("Not signed in");
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("email", email);
        data.put(field, FieldValue.serverTimestamp());
        db().collection("alerts").document(alertId).collection("acks").document(email)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(v -> { if (cb != null) cb.onSuccess(null); })
                .addOnFailureListener(e -> { if (cb != null) cb.onError(String.valueOf(e.getMessage())); });
    }
}
