package com.ligtas.app;

import android.content.Context;
import android.text.format.DateUtils;

import java.util.Locale;

/** Small text helpers for times, shared by the status sheet, incoming alerts and the member card. */
final class TimeText {
    private TimeText() {}

    /** "1:35 PM", or with the date when it was not today. */
    static String when(Context context, long millis) {
        int flags = DateUtils.FORMAT_SHOW_TIME | (DateUtils.isToday(millis) ? 0 : DateUtils.FORMAT_SHOW_DATE);
        return DateUtils.formatDateTime(context, millis, flags);
    }

    /** "45 s" or "2 min 5 s". */
    static String seconds(double seconds) {
        if (seconds < 60) return String.format(Locale.US, "%.0f s", seconds);
        int total = (int) Math.round(seconds);
        return (total / 60) + " min " + (total % 60) + " s";
    }
}
