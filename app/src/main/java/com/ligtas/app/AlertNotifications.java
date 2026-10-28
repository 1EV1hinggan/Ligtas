package com.ligtas.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

/**
 * Creates the emergency-alert notification channel and shows alerts.
 * Encapsulates all notification details so the rest of the app only calls show().
 */
public class AlertNotifications {

    public static final String CHANNEL_ID = "ligtas_alerts_v5"; // v2: Android never updates an existing channel, so a new id applies the new settings
    // Second channel, created only after the person grants Do Not Disturb access to the app.
    public static final String DND_CHANNEL_ID = "ligtas_alerts_dnd_v5";

    // All-clear and false-alarm updates: one calm buzz, on their own channel
    public static final String UPDATES_CHANNEL_ID = "ligtas_updates_v3";
    // Vibration is done by vibrateAlert() / vibrateResolution() only. The channels do NOT vibrate,
    // so the system and the app never compete for the motor.
    /** All-clear / false alarm: short triple buzz (buzz, pause, buzz, pause, long buzz). */
    public static final long[] UPDATE_PATTERN = {0, 600, 200, 600, 200, 1200};

    @RequiresApi(Build.VERSION_CODES.O)
    private static NotificationChannel buildChannel(String id, String name) {
        NotificationChannel channel = new NotificationChannel(id, name, NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Urgent campus emergency alerts");
        channel.enableVibration(false); // vibrateAlert() does it
        channel.setSound(null, null); // silent by design: vibrate only
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        // Only honored when the app has Do Not Disturb access
        channel.setBypassDnd(true);
        return channel;
    }

    /** Safe to call repeatedly (MainActivity start and resume, and before showing an alert). */
    public static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return; // channels only exist on Android 8.0+
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.deleteNotificationChannel("ligtas_alerts");      // old versions
        manager.deleteNotificationChannel("ligtas_alerts_dnd");
        manager.deleteNotificationChannel("ligtas_alerts_v2");
        manager.deleteNotificationChannel("ligtas_alerts_v3");
        manager.deleteNotificationChannel("ligtas_alerts_v4");
        manager.deleteNotificationChannel("ligtas_alerts_dnd_v4");
        manager.deleteNotificationChannel("ligtas_updates_v2");
        manager.deleteNotificationChannel("ligtas_alerts_dnd_v3");
        manager.deleteNotificationChannel("ligtas_alerts_dnd_v2");
        manager.createNotificationChannel(buildChannel(CHANNEL_ID, "Emergency alerts"));
        NotificationChannel updates = new NotificationChannel(UPDATES_CHANNEL_ID, "All-clear updates", NotificationManager.IMPORTANCE_HIGH);
        updates.setDescription("Messages that an emergency is over or was a false alarm");
        updates.enableVibration(false); // vibrateResolution() does it
        updates.setSound(null, null);
        updates.setBypassDnd(true);
        updates.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        manager.createNotificationChannel(updates);
        if (PermissionHelper.policyAccessGranted(context)) {
            manager.createNotificationChannel(buildChannel(DND_CHANNEL_ID, "Emergency alerts (override Do Not Disturb)"));
        }
    }

    /** The channel alerts are posted to: the Do Not Disturb one once access has been granted. */
    public static String activeChannelId(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && PermissionHelper.policyAccessGranted(context)) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null && manager.getNotificationChannel(DND_CHANNEL_ID) != null) {
                return DND_CHANNEL_ID;
            }
        }
        return CHANNEL_ID;
    }

    /** Notification id for an alert, or for its all-clear (a different id so both can be seen). */
    private static int notificationIdFor(String alertId, String type) {
        if (alertId == null || alertId.isEmpty()) return (int) System.currentTimeMillis();
        boolean alert = type == null || LigtasMessagingService.TYPE_ALERT.equals(type);
        return alert ? alertId.hashCode() : (alertId + "#" + type).hashCode();
    }

    /** Removes the original alert from the notification shade once it has been resolved. */
    public static void cancelAlert(Context context, String alertId) {
        if (alertId == null || alertId.isEmpty()) return;
        NotificationManagerCompat.from(context).cancel(alertId.hashCode());
    }

    public static void show(Context context, String alertId, String title, String body) {
        show(context, alertId, title, body, LigtasMessagingService.TYPE_ALERT, System.currentTimeMillis(), "");
    }

    /** Shows one notification: an alert, an all-clear or a false alarm. sentAt is when the admin sent it. */
    public static boolean show(Context context, String alertId, String title, String body,
                               String type, long sentAt, String sentBy) {
        // Android 13+ requires the user to allow notifications.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        boolean isAlert = type == null || LigtasMessagingService.TYPE_ALERT.equals(type);

        // Tapping opens the app through the login check, carrying the message so it can be shown or acknowledged
        Intent openApp = new Intent(context, LoginActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        openApp.putExtra("alertId", alertId);
        openApp.putExtra("alertTitle", title);
        openApp.putExtra("alertBody", body);
        openApp.putExtra("alertType", isAlert ? LigtasMessagingService.TYPE_ALERT : type);
        openApp.putExtra("alertSentAt", sentAt);
        openApp.putExtra("alertBy", sentBy == null ? "" : sentBy);

        int notificationId = notificationIdFor(alertId, type);
        PendingIntent tapIntent = PendingIntent.getActivity(
                context, notificationId, openApp, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context,
                isAlert ? activeChannelId(context) : UPDATES_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setColor(0xFFFF5A52)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(isAlert ? NotificationCompat.CATEGORY_ALARM : NotificationCompat.CATEGORY_STATUS)
                .setContentIntent(tapIntent)
                .setAutoCancel(true)
                // Show when the admin SENT it, not when this phone happened to get it
                .setShowWhen(true)
                .setWhen(sentAt > 0 ? sentAt : System.currentTimeMillis());
        if (sentBy != null && !sentBy.isEmpty()) builder.setSubText((Lang.isTaglish(context) ? "Mula sa " : "Sent by ") + sentBy);

        NotificationManagerCompat.from(context).notify(notificationId, builder.build());
        return true;
    }

    /** One calm buzz for an all-clear or false alarm. */
    public static void vibrateResolution(Context context) {
        synchronized (MAIN) {
            if (stopTask != null) { MAIN.removeCallbacks(stopTask); stopTask = null; }
        }
        // An alert buzz may still be running (or its timer may have been lost if the phone killed this app):
        // always stop it first, so the all-clear can never leave it buzzing.
        Vibrator running = (Vibrator) context.getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
        if (running != null) running.cancel();
        vibrate(context, UPDATE_PATTERN);
    }

    /** How long an alert keeps buzzing if nobody opens it (milliseconds). */
    public static final long ALERT_BUZZ_MS = 15000;
    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());
    private static Runnable stopTask;

    /**
     * Vibrates directly as an alarm so the pattern is the same in every mode (normal, mute,
     * Do Not Disturb). It buzzes continuously for ALERT_BUZZ_MS or until stopAlertVibration() is called
     * (opening the app or tapping Acknowledge).
     */
    public static void vibrateAlert(Context context) {
        final Context app = context.getApplicationContext();
        Vibrator vibrator = (Vibrator) app.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) return;
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Feels like one continuous buzz: 3 s on at full strength, a 40 ms blink, repeated until stopped.
            // (A single very long buzz gets cut short by some phones; short repeating segments are not.)
            // It is FINITE (about ALERT_BUZZ_MS in total, no repeat), so even if this app is killed
            // the phone itself ends the buzz and it can never run forever.
            final int reps = (int) (ALERT_BUZZ_MS / 3040);
            long[] timings = new long[reps * 2];
            int[] amps = new int[reps * 2];
            for (int i = 0; i < reps; i++) { timings[i * 2] = 3000; amps[i * 2] = 255; timings[i * 2 + 1] = 40; amps[i * 2 + 1] = 0; }
            VibrationEffect effect = vibrator.hasAmplitudeControl()
                    ? VibrationEffect.createWaveform(timings, amps, -1)
                    : VibrationEffect.createWaveform(timings, -1);
            vibrator.vibrate(effect, attrs);
        } else {
            long[] pattern = new long[(int) (ALERT_BUZZ_MS / 3040) * 2];
            for (int i = 0; i < pattern.length; i += 2) { pattern[i] = 3000; pattern[i + 1] = 40; }
            vibrator.vibrate(pattern, -1, attrs);
        }
        synchronized (MAIN) {
            if (stopTask != null) MAIN.removeCallbacks(stopTask);
            stopTask = () -> stopAlertVibration(app);
            MAIN.postDelayed(stopTask, ALERT_BUZZ_MS);
        }
    }

    /** Stops the repeating buzz (safe to call any time). */
    public static void stopAlertVibration(Context context) {
        synchronized (MAIN) {
            if (stopTask == null) return; // no alert buzz running: leave any all-clear buzz alone
            MAIN.removeCallbacks(stopTask);
            stopTask = null;
        }
        Vibrator vibrator = (Vibrator) context.getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null) vibrator.cancel();
    }

    private static void vibrate(Context context, long[] pattern) {
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) return;
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1), attrs);
        } else {
            vibrator.vibrate(pattern, -1, attrs);
        }
    }
}
