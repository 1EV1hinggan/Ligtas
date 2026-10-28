package com.ligtas.app;

/**
 * Where a group of students is on a given day: ONSITE (on campus), ONLINE (at home: remote class, work from home,
 * suspension) or VACANT (no classes: holiday or a free day). Only ONLINE is ever skipped by an on-site-only alert,
 * because people on a vacant day may still be on campus.
 */
public enum Mode {
    ONSITE, ONLINE, VACANT;

    /** Reads "OS" / "OL" / "NC" (and a few spellings). Anything unknown counts as ON-SITE: the safe choice for alerts. */
    public static Mode parse(String s) {
        if (s == null) return ONSITE;
        String t = s.trim().toUpperCase();
        if (t.equals("OL") || t.equals("ONLINE") || t.equals("REMOTE")) return ONLINE;
        if (t.equals("NC") || t.equals("NONE") || t.equals("HOLIDAY") || t.equals("VACANT") || t.equals("NO CLASS")) return VACANT;
        return ONSITE;
    }

    public String code() {
        return this == ONLINE ? "OL" : this == VACANT ? "NC" : "OS";
    }
}
