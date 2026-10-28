package com.ligtas.app;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Remembers on this phone what the teacher reported for the current alert, so the home screen keeps showing
 * "reported" after the app is restarted, and remembers the last room name to pre-fill next time.
 */
public final class RoomReportStore {
    private static final String PREFS = "ligtas_room_report";
    public static final String SENDING = "SENDING", SENT = "SENT", FAILED = "FAILED";

    private RoomReportStore() { }

    public static final class Mine {
        public final String alertId, room, status, delivery;
        Mine(String alertId, String room, String status, String delivery) {
            this.alertId = alertId; this.room = room; this.status = status; this.delivery = delivery;
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The report made for this alert, or null (reports for other alerts are ignored). */
    public static Mine get(Context c, String alertId) {
        SharedPreferences p = prefs(c);
        if (alertId == null || !alertId.equals(p.getString("alertId", null))) return null;
        return new Mine(alertId, p.getString("room", ""), p.getString("status", ""), p.getString("delivery", SENDING));
    }

    public static void save(Context c, String alertId, String room, String status, String delivery) {
        prefs(c).edit().putString("alertId", alertId).putString("room", room).putString("status", status)
                .putString("delivery", delivery).putString("lastRoom", room).apply();
    }

    /** Updates how sending went, only if the stored report is still for this alert. */
    public static void setDelivery(Context c, String alertId, String delivery) {
        SharedPreferences p = prefs(c);
        if (alertId != null && alertId.equals(p.getString("alertId", null))) p.edit().putString("delivery", delivery).apply();
    }

    public static String lastRoom(Context c) {
        return prefs(c).getString("lastRoom", "");
    }

    public static void clear(Context c) {
        prefs(c).edit().clear().apply();
    }
}
