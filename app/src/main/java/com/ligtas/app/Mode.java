package com.ligtas.app;

/**
 * Where a group of students is on a given day: ONSITE (on campus), ONLINE (at home: remote class, work from home,
 * suspension) or VACANT (no classes: holiday or a free day). Only ONLINE is ever skipped by an on-site-only alert,
 * because people on a vacant day may still be on campus.
 *
 * In simple words: this is an ENUM, a fixed list of allowed values. A Mode can only be
 * ONSITE, ONLINE or VACANT, nothing else. That prevents typing mistakes like "onsit".
 */
public enum Mode {
    // The three allowed values.
    ONSITE, ONLINE, VACANT;

    /**
     * Reads "OS" / "OL" / "NC" (and a few spellings). Anything unknown counts as ON-SITE: the safe choice for alerts.
     *
     * This is INPUT VALIDATION: text from the database can be messy (null, extra spaces,
     * small letters), so we clean it first. If we do not understand it, we choose ONSITE
     * so that nobody is skipped by mistake in an emergency.
     */
    public static Mode parse(String s) {
        if (s == null) return ONSITE;               // no value given -> safe choice
        String t = s.trim().toUpperCase();          // remove spaces, make it CAPITAL letters
        if (t.equals("OL") || t.equals("ONLINE") || t.equals("REMOTE")) return ONLINE;
        if (t.equals("NC") || t.equals("NONE") || t.equals("HOLIDAY") || t.equals("VACANT") || t.equals("NO CLASS")) return VACANT;
        return ONSITE;                              // anything else -> safe choice
    }

    // Turns a Mode back into its short code: OL, NC or OS (used when saving to the database).
    public String code() {
        return this == ONLINE ? "OL" : this == VACANT ? "NC" : "OS";
    }
}