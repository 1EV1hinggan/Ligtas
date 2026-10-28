package com.ligtas.app;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a teacher teaches, from the roster's "teaches" list. Each entry is a program with an optional year,
 * for example "BSIT-2" (year 2 of BSIT) or "BSME" (every year of BSME). Teachers join push topics for these
 * groups, so an alert aimed at "BSIT Year 2" reaches the students and also the teachers of BSIT Year 2.
 */
public final class Teaching {
    private Teaching() { }

    /** One program and year a teacher handles (year 0 = every year of the program). */
    public static final class Slot {
        public final String program;   // slug of the program code
        public final int year;
        Slot(String program, int year) { this.program = program; this.year = year; }
    }

    private static final Pattern WITH_YEAR = Pattern.compile("^(.*?)[-_\\s]+(\\d{1,2})$");

    /** Reads a roster value: a list of strings, or one string separated by ; | or /. Bad entries are ignored. */
    public static List<Slot> parse(Object raw) {
        List<String> parts = new ArrayList<>();
        if (raw instanceof List) {
            for (Object o : (List<?>) raw) parts.add(String.valueOf(o));
        } else if (raw instanceof String) {
            for (String p : ((String) raw).split("[;|/]")) parts.add(p);
        }
        List<Slot> out = new ArrayList<>();
        for (String p : parts) {
            String t = p == null ? "" : p.trim();
            if (t.isEmpty()) continue;
            String prog = t;
            int year = 0;
            Matcher m = WITH_YEAR.matcher(t);
            if (m.matches()) {
                prog = m.group(1);
                try { year = Integer.parseInt(m.group(2)); } catch (NumberFormatException e) { year = 0; }
            }
            String key = Slug.of(prog);
            if (key.isEmpty()) continue;
            out.add(new Slot(key, year));
        }
        return out;
    }

    /** Does this teacher handle the program (and year, when year > 0)? */
    public static boolean teaches(List<Slot> slots, String program, int year) {
        String key = Slug.of(program);
        if (key.isEmpty() || slots == null) return false;
        for (Slot s : slots) {
            if (!s.program.equals(key)) continue;
            if (year <= 0 || s.year == 0 || s.year == year) return true;
        }
        return false;
    }

    /** The push topics a teacher joins: one per program ("teachp_bsit") and one per program+year ("teach_bsit_2"). */
    public static Set<String> topics(List<Slot> slots) {
        Set<String> t = new HashSet<>();
        if (slots == null) return t;
        for (Slot s : slots) {
            t.add(TopicNames.teachProgram(s.program));
            t.add(TopicNames.teach(s.program, s.year));
        }
        return t;
    }
}
