package com.ligtas.app;

/**
 * What the admin changed in the Edit sheet for the alert about to be sent (area, who, instruction, note).
 * Holds plain values only; the screen resets it whenever a type is selected or the selection is cleared.
 */
final class AlertEdits {
    String area = "Whole campus";
    String college = "";        // "" = all colleges
    String program = "";        // "" = all programs
    int year = 0;               // 0 = all year levels
    boolean onCampus = true;    // whole-campus alerts skip program/year groups that are online this week
    int variant = 0;
    String note = "";
    boolean edited = false;

    void reset(String wholeCampus) {
        area = wholeCampus;
        college = "";
        program = "";
        year = 0;
        onCampus = true;
        variant = 0;
        note = "";
        edited = false;
    }

    static String cleanNote(CharSequence raw) {
        return raw == null ? "" : raw.toString().replaceAll("\\s+", " ").trim();
    }

    static Audience audienceFor(String campusId, String wholeCampus, String area, String college,
                                String program, int year, boolean onSiteOnly) {
        return Audience.create(campusId, area, wholeCampus, college, program, year, onSiteOnly);
    }
}
