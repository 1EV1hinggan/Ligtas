package com.ligtas.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Checks whether this phone is set up so emergency alerts can actually reach the person,
 * and builds the settings screens that fix each problem.
 */
public final class PermissionHelper {

    private static final String PREFS = "ligtas_setup";

    private PermissionHelper() { }

    /** One thing that is wrong, with the reason it matters. */
    public static class Problem {
        public final String key;
        public final boolean critical; // critical = alerts will very likely be missed
        public final String message;

        Problem(String key, boolean critical, String message) {
            this.key = key;
            this.critical = critical;
            this.message = message;
        }
    }

    // ------------------------------ checks ------------------------------

    public static boolean notificationsAllowed(Context c) {
        return NotificationManagerCompat.from(c).areNotificationsEnabled();
    }

    private static NotificationChannel alertChannel(Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        return nm == null ? null : nm.getNotificationChannel(AlertNotifications.activeChannelId(c));
    }

    /** The emergency channel must be loud enough to interrupt (the app vibrates by itself, so the channel need not). */
    public static boolean channelHealthy(Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true;
        NotificationChannel ch = alertChannel(c);
        return ch != null && ch.getImportance() >= NotificationManager.IMPORTANCE_HIGH;
    }

    /** Do Not Disturb access: the screen where the person lets Ligtas override Do Not Disturb. */
    public static boolean policyAccessGranted(Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        return nm != null && nm.isNotificationPolicyAccessGranted();
    }

    /** True when alerts are allowed to come through Do Not Disturb. */
    public static boolean canBypassDnd(Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true;
        if (policyAccessGranted(c)) return true;
        NotificationChannel ch = alertChannel(c);
        return ch != null && ch.canBypassDnd();
    }

    /** Name of the Do Not Disturb mode that is on right now, or null when it is off. */
    public static String activeDndMode(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return null;
        switch (nm.getCurrentInterruptionFilter()) {
            case NotificationManager.INTERRUPTION_FILTER_PRIORITY: return "priority only";
            case NotificationManager.INTERRUPTION_FILTER_NONE: return "total silence";
            case NotificationManager.INTERRUPTION_FILTER_ALARMS: return "alarms only";
            default: return null;
        }
    }

    /** True when the phone is set to hide this channel's notifications on the lock screen. */
    public static boolean lockscreenHidden(Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false;
        NotificationChannel ch = alertChannel(c);
        return ch != null && ch.getLockscreenVisibility() == Notification.VISIBILITY_SECRET;
    }

    public static boolean batteryUnrestricted(Context c) {
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(c.getPackageName());
    }

    private static String maker() {
        String m = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase(Locale.US);
        String b = Build.BRAND == null ? "" : Build.BRAND.toLowerCase(Locale.US);
        return m + " " + b;
    }

    /** Xiaomi, Redmi and Poco phones: they have their own battery setting ("No restrictions") besides Android's. */
    public static boolean isXiaomiFamily() {
        String m = maker();
        return m.contains("xiaomi") || m.contains("redmi") || m.contains("poco");
    }

    /** Phone brands that often stop apps in the background unless Autostart is allowed. */
    public static boolean needsAutostartStep() {
        String m = maker();
        return isXiaomiFamily()
                || m.contains("oppo") || m.contains("realme") || m.contains("vivo") || m.contains("iqoo")
                || m.contains("oneplus") || m.contains("honor") || m.contains("huawei")
                || m.contains("tecno") || m.contains("infinix") || m.contains("itel") || m.contains("transsion");
    }

    /** Everything that is currently wrong. Autostart cannot be detected, so it is not listed here. */
    public static List<Problem> problems(Context c) {
        List<Problem> list = new ArrayList<>();
        if (!notificationsAllowed(c)) {
            list.add(new Problem("notifications", true,
                    "Notifications are off. Without them, emergency alerts cannot appear on your phone at all."));
        } else if (!channelHealthy(c)) {
            list.add(new Problem("channel", true,
                    "The alert channel is muted or set to a low importance. You could miss an emergency alert."));
        }
        if (!canBypassDnd(c)) {
            list.add(new Problem("dnd", false,
                    "Do Not Disturb can silence alerts. Give Ligtas Do Not Disturb access so emergency alerts still come through."));
        }
        if (lockscreenHidden(c)) {
            list.add(new Problem("lockscreen", false,
                    "Alerts are hidden on your lock screen. Allow them to show so you can see an emergency without unlocking your phone."));
        }
        if (!batteryUnrestricted(c)) {
            list.add(new Problem("battery", false,
                    "Battery saving may delay or block alerts while the app is closed. Allow Ligtas to run without restrictions."));
        }
        return list;
    }

    public static boolean hasCriticalProblem(List<Problem> problems) {
        for (Problem p : problems) if (p.critical) return true;
        return false;
    }

    // ------------------------------ settings screens ------------------------------

    public static Intent notificationSettingsIntent(Context c) {
        Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        i.putExtra(Settings.EXTRA_APP_PACKAGE, c.getPackageName());
        return i;
    }

    /** The alert channel's own page: vibration, importance, and lock screen visibility live here. */
    public static Intent channelSettingsIntent(Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return notificationSettingsIntent(c);
        Intent i = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
        i.putExtra(Settings.EXTRA_APP_PACKAGE, c.getPackageName());
        i.putExtra(Settings.EXTRA_CHANNEL_ID, AlertNotifications.activeChannelId(c));
        return i;
    }

    public static Intent dndSettingsIntent() {
        return new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
    }

    public static Intent batteryRequestIntent(Context c) {
        return new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + c.getPackageName()));
    }

    public static Intent batteryListIntent() {
        return new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
    }

    public static Intent appDetailsIntent(Context c) {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.getPackageName()));
    }

    /** Best-effort Autostart screens for common brands. Falls back to the app details page. */
    public static Intent[] autostartIntents(Context c) {
        return new Intent[]{
                new Intent().setComponent(new ComponentName("com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity")),
                new Intent().setComponent(new ComponentName("com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
                new Intent().setComponent(new ComponentName("com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
                new Intent().setComponent(new ComponentName("com.oppo.safe",
                        "com.oppo.safe.permission.startup.StartupAppListActivity")),
                new Intent().setComponent(new ComponentName("com.iqoo.secure",
                        "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
                new Intent().setComponent(new ComponentName("com.oneplus.security",
                        "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")),
                new Intent().setComponent(new ComponentName("com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
                new Intent().setComponent(new ComponentName("com.hihonor.systemmanager",
                        "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
                new Intent().setComponent(new ComponentName("com.transsion.phonemaster",
                        "com.cyin.himgr.autostart.AutoStartActivity")),
                appDetailsIntent(c)
        };
    }

    public static boolean notificationPermissionGranted(Context c) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    // ------------------------------ remembered choices ------------------------------

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean setupDoneFor(Context c, String email) {
        return email != null && email.equals(prefs(c).getString("setupDoneFor", null));
    }

    public static void markSetupDone(Context c, String email) {
        prefs(c).edit().putString("setupDoneFor", email).apply();
    }

    public static boolean askedNotificationPermission(Context c) {
        return prefs(c).getBoolean("askedNotif", false);
    }

    public static void markAskedNotificationPermission(Context c) {
        prefs(c).edit().putBoolean("askedNotif", true).apply();
    }

    public static boolean autostartConfirmed(Context c) {
        return prefs(c).getBoolean("autostartConfirmed", false);
    }

    public static void setAutostartConfirmed(Context c, boolean value) {
        prefs(c).edit().putBoolean("autostartConfirmed", value).apply();
    }

    /** True once the person has opened the Autostart screen from Ligtas (the "I turned it on" button appears after that). */
    public static boolean autostartOpened(Context c) {
        return prefs(c).getBoolean("autostartOpened", false);
    }

    public static void setAutostartOpened(Context c, boolean value) {
        prefs(c).edit().putBoolean("autostartOpened", value).apply();
    }

    public static long lastNormalReminder(Context c) {
        return prefs(c).getLong("lastNormalReminder", 0);
    }

    public static void markNormalReminder(Context c) {
        prefs(c).edit().putLong("lastNormalReminder", System.currentTimeMillis()).apply();
    }
}
