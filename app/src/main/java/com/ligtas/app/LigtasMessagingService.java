package com.ligtas.app;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/**
 * Receives Firebase Cloud Messaging pushes.
 * Later, the Ligtas server will send DATA messages (title, body, alertId, severity)
 * so this method always runs, even when the app is in the background.
 */
public class LigtasMessagingService extends FirebaseMessagingService {

    private static final String TAG = "LigtasFCM";

    // What kind of message this is
    public static final String TYPE_ALERT = "ALERT";
    public static final String TYPE_ALL_CLEAR = "ALL_CLEAR";
    public static final String TYPE_FALSE_ALARM = "FALSE_ALARM";

    @Override
    public void onMessageReceived(@NonNull RemoteMessage message) {
        String title = "Ligtas alert";
        String body = "";
        String alertId = "";
        String type = TYPE_ALERT;
        String by = "";
        String onlineGroups = "";
        boolean trackDelivery = false;
        long sentAt = 0;
        String titleTl = "";
        String bodyTl = "";
        boolean reminder = false;   // sent again by the server because this person had not acknowledged

        Map<String, String> data = message.getData();
        if (!data.isEmpty()) {
            // Preferred path: data message from our own server
            if (data.get("title") != null) title = data.get("title");
            if (data.get("body") != null) body = data.get("body");
            if (data.get("incoming_alert_id") != null) alertId = data.get("incoming_alert_id");
            if (data.get("type") != null && !data.get("type").isEmpty()) type = data.get("type");
            if (data.get("by") != null) by = data.get("by");
            if (data.get("ol") != null) onlineGroups = data.get("ol");
            trackDelivery = data.get("td") != null;
            reminder = data.get("esc") != null;
            if (data.get("title_tl") != null) titleTl = data.get("title_tl");
            if (data.get("body_tl") != null) bodyTl = data.get("body_tl");
            try {
                if (data.get("sent_at") != null) sentAt = Long.parseLong(data.get("sent_at"));
            } catch (NumberFormatException ignored) { }
        } else if (message.getNotification() != null) {
            // Fallback: notification message (for example from the Firebase console test)
            if (message.getNotification().getTitle() != null) title = message.getNotification().getTitle();
            if (message.getNotification().getBody() != null) body = message.getNotification().getBody();
        }
        if (sentAt <= 0) sentAt = System.currentTimeMillis();
        // This phone's owner chose Taglish in Settings: show that version when the alert carries one
        if (Lang.isTaglish(this) && !titleTl.isEmpty() && !bodyTl.isEmpty()) {
            title = titleTl;
            body = bodyTl;
        }

        Log.d(TAG, "Message received: " + type + " / " + title);
        // Alert meant for on-site students only, and this phone's program/year is online this week: stay quiet.
        // (Fails open: with no group on this phone, or no list in the message, the alert is shown.)
        String myGroup = Topics.myGroupKey(this);
        if (!onlineGroups.isEmpty() && !myGroup.isEmpty()
                && java.util.Arrays.asList(onlineGroups.split(",")).contains(myGroup)) {
            Log.d(TAG, "Ignored: group " + myGroup + " is online this week");
            return;
        }
        AlertNotifications.createChannel(this);

        if (TYPE_ALERT.equals(type)) {
            // A person can match two topics (for example teacher + department): show the alert only once
            LastAlertStore.Saved existing = LastAlertStore.get(this);
            boolean same = existing != null && !alertId.isEmpty() && alertId.equals(existing.alertId);
            if (reminder && same) {
                // Already on this phone: only buzz again if it was not acknowledged and has not ended
                if (existing.acked || existing.isResolved()) return;
                if (AlertNotifications.show(this, alertId, title, body, type, sentAt, by)) AlertNotifications.vibrateAlert(this);
                if (trackDelivery) AlertTracker.markDelivered(alertId);
                return;
            }
            if (same) return;
            LastAlertStore.save(this, alertId, title, body, sentAt, by);
            // Buzz only if the alert was actually shown (notification permission granted)
            if (AlertNotifications.show(this, alertId, title, body, type, sentAt, by)) {
                AlertNotifications.vibrateAlert(this);
            }
            // Record that this phone received the alert (no tap needed)
            if (trackDelivery) AlertTracker.markDelivered(alertId);
        } else {
            // All-clear or false alarm: replaces the original alert on the phone
            String state = TYPE_FALSE_ALARM.equals(type)
                    ? LastAlertStore.STATE_FALSE_ALARM : LastAlertStore.STATE_ALL_CLEAR;
            LastAlertStore.resolve(this, alertId, state, body, sentAt);
            AlertNotifications.cancelAlert(this, alertId);
            AlertNotifications.show(this, alertId, title, body, type, sentAt, by);
            AlertNotifications.vibrateResolution(this);
        }
    }

    @Override
    public void onNewToken(@NonNull String token) {
        // Later: send this token to the Ligtas server so it knows where to push this user.
        Log.d(TAG, "New FCM token: " + token);
    }
}
