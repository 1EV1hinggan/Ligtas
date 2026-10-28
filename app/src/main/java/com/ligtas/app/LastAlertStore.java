package com.ligtas.app;

import android.content.Context;
import android.content.SharedPreferences;

/** Remembers the last alert this phone received (and how it ended), so the home screen can show it. Stored on the phone only. */
public final class LastAlertStore {

    public static final String PREFS = "ligtas_last_alert";

    public static final String STATE_ACTIVE = "ACTIVE";
    public static final String STATE_ALL_CLEAR = "ALL_CLEAR";
    public static final String STATE_FALSE_ALARM = "FALSE_ALARM";

    private LastAlertStore() { }

    public static class Saved {
        public final String alertId;
        public final String title;
        public final String body;
        public final long receivedAt;  // when this phone got it
        public final long sentAt;      // when the admin sent it (0 = unknown)
        public final String sentBy;
        public final boolean acked;
        public final String state;     // ACTIVE, ALL_CLEAR or FALSE_ALARM
        public final String resolutionText;
        public final long resolvedAt;

        Saved(String alertId, String title, String body, long receivedAt, long sentAt, String sentBy,
              boolean acked, String state, String resolutionText, long resolvedAt) {
            this.alertId = alertId;
            this.title = title;
            this.body = body;
            this.receivedAt = receivedAt;
            this.sentAt = sentAt;
            this.sentBy = sentBy;
            this.acked = acked;
            this.state = state;
            this.resolutionText = resolutionText;
            this.resolvedAt = resolvedAt;
        }

        public boolean isResolved() { return !STATE_ACTIVE.equals(state); }
        /** The time to show people: when it was sent, or when this phone got it if that is unknown. */
        public long displayTime() { return sentAt > 0 ? sentAt : receivedAt; }
    }

    public static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void save(Context c, String alertId, String title, String body, long sentAt, String sentBy) {
        if (alertId == null || alertId.isEmpty()) return;
        prefs(c).edit()
                .putString("id", alertId)
                .putString("title", title)
                .putString("body", body)
                .putLong("at", System.currentTimeMillis())
                .putLong("sentAt", sentAt)
                .putString("by", sentBy == null ? "" : sentBy)
                .putBoolean("acked", false)
                .putString("state", STATE_ACTIVE)
                .putString("resText", "")
                .putLong("resAt", 0)
                .apply();
    }

    /** An all-clear or false-alarm message arrived for an alert. Ignored if a newer, different alert is on screen. */
    public static void resolve(Context c, String alertId, String state, String text, long at) {
        if (alertId == null || alertId.isEmpty()) return;
        SharedPreferences p = prefs(c);
        String current = p.getString("id", null);
        SharedPreferences.Editor e = p.edit();
        if (current == null) {
            // This phone never saw the original alert (it was offline), so show the outcome on its own
            e.putString("id", alertId).putString("title", "Earlier alert").putString("body", "")
                    .putLong("at", System.currentTimeMillis()).putLong("sentAt", 0).putString("by", "")
                    .putBoolean("acked", false);
        } else if (!current.equals(alertId)) {
            return;
        }
        e.putString("state", state).putString("resText", text == null ? "" : text).putLong("resAt", at).apply();
    }

    public static void markAcked(Context c, String alertId) {
        SharedPreferences p = prefs(c);
        if (alertId != null && alertId.equals(p.getString("id", null))) {
            p.edit().putBoolean("acked", true).apply();
        }
    }

    public static Saved get(Context c) {
        SharedPreferences p = prefs(c);
        String id = p.getString("id", null);
        if (id == null) return null;
        return new Saved(id, p.getString("title", ""), p.getString("body", ""),
                p.getLong("at", 0), p.getLong("sentAt", 0), p.getString("by", ""),
                p.getBoolean("acked", false), p.getString("state", STATE_ACTIVE),
                p.getString("resText", ""), p.getLong("resAt", 0));
    }

    public static void clear(Context c) {
        prefs(c).edit().clear().apply();
    }
}
