package com.ligtas.app;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashSet;
import java.util.Set;

/**
 * Which Firebase push topics this phone joins, decided by the person's roster entry:
 *   campus_<id>, role_<role>, bld_<building>, col_<college>, prog_<program>, yr_<year>
 * (names are built by TopicNames). Also remembers the phone's own schedule group ("bsit_2"),
 * which the app uses to ignore alerts meant only for on-site students while that group is online.
 */
public final class Topics {
    public static final String CAMPUS = TopicNames.CAMPUS_ALL; // everyone (older builds only know this one)

    private static final String PREFS = "ligtas_topics";

    private Topics() { }

    /**
     * Joins the topics this person belongs to and leaves any they no longer belong to
     * (for example after the roster changed their program or year).
     */
    public static void subscribe(Context context, String campusId, String role, String building,
                                 String college, String program, int yearLevel) {
        subscribe(context, campusId, role, building, college, program, yearLevel, null);
    }

    /** Same, and teachers also join the topics of the programs and years they teach (roster "teaches"). */
    public static void subscribe(Context context, String campusId, String role, String building,
                                 String college, String program, int yearLevel, java.util.List<String> teaches) {
        Set<String> wanted = new HashSet<>();
        wanted.add(CAMPUS);
        if (!Slug.of(campusId).isEmpty()) wanted.add(TopicNames.campus(campusId));
        if (!Slug.of(role).isEmpty()) wanted.add(TopicNames.role(role));
        if (!Slug.of(building).isEmpty()) wanted.add(TopicNames.building(building));
        if (!Slug.of(college).isEmpty()) wanted.add(TopicNames.college(college));
        if (!Slug.of(program).isEmpty()) wanted.add(TopicNames.program(program));
        if (yearLevel > 0) wanted.add(TopicNames.year(yearLevel));
        if ("TEACHER".equalsIgnoreCase(role)) wanted.addAll(Teaching.topics(Teaching.parse(teaches)));

        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> old = new HashSet<>(prefs.getStringSet("joined", new HashSet<>()));
        FirebaseMessaging m = FirebaseMessaging.getInstance();
        for (String t : old) if (!wanted.contains(t)) m.unsubscribeFromTopic(t);
        for (String t : wanted) m.subscribeToTopic(t);

        boolean student = "STUDENT".equalsIgnoreCase(role) && !Slug.of(program).isEmpty() && yearLevel > 0;
        prefs.edit().putStringSet("joined", wanted)
                .putString("group", student ? CampusConfig.groupKey(program, yearLevel) : "")
                .apply();
    }

    /** This phone's schedule group, such as "bsit_2" ("" for staff or people with no program). */
    public static String myGroupKey(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("group", "");
    }

    /** Leaves every topic this phone joined (used when signing out). */
    public static void unsubscribeAll(Context context) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> joined = new HashSet<>(prefs.getStringSet("joined", new HashSet<>()));
        joined.add(CAMPUS);
        FirebaseMessaging m = FirebaseMessaging.getInstance();
        for (String t : joined) m.unsubscribeFromTopic(t);
        prefs.edit().remove("joined").remove("group").apply();
    }
}
