package com.ligtas.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A teacher's report, during an alert, about the room they are in: everyone accounted for, some students missing,
 * or help needed. One report per teacher per alert (sending again replaces it). Stored under alerts/{id}/rooms/{email}.
 */
public final class RoomReport {
    public static final String SAFE = "SAFE";
    public static final String MISSING = "MISSING";
    public static final String HELP = "HELP";

    public final String email, name, room, status, note;
    public final int headcount;      // students with the teacher, -1 = not given
    public final long reportedAt;    // 0 = not known yet

    public RoomReport(String email, String name, String room, String status, int headcount, String note, long reportedAt) {
        this.email = email == null ? "" : email;
        this.name = name == null ? "" : name;
        this.room = room == null ? "" : room.trim();
        this.status = status == null ? "" : status;
        this.headcount = headcount;
        this.note = note == null ? "" : note.trim();
        this.reportedAt = reportedAt;
    }

    public static boolean isValidStatus(String s) {
        return SAFE.equals(s) || MISSING.equals(s) || HELP.equals(s);
    }

    public static String label(String status) {
        if (SAFE.equals(status)) return "All students accounted for";
        if (MISSING.equals(status)) return "Some students missing";
        if (HELP.equals(status)) return "Needs help";
        return "Unknown";
    }

    /** Problems first: HELP, then MISSING, then SAFE. */
    private static int rank(String status) {
        return HELP.equals(status) ? 0 : MISSING.equals(status) ? 1 : 2;
    }

    /** One line for the admin status screen, for example "NEEDS HELP: CICS 204 (Nicole Atienza), 24 with her. Note: ..." */
    public String line() {
        StringBuilder sb = new StringBuilder();
        if (HELP.equals(status)) sb.append("NEEDS HELP: ");
        else if (MISSING.equals(status)) sb.append("MISSING STUDENTS: ");
        else sb.append("Safe: ");
        sb.append(room.isEmpty() ? "(no room given)" : room);
        if (!name.isEmpty()) sb.append(" (").append(name).append(")");
        if (headcount >= 0) sb.append(", ").append(headcount).append(headcount == 1 ? " student" : " students");
        if (!note.isEmpty()) sb.append(". Note: ").append(note);
        return sb.toString();
    }

    /** Sorted copy, most urgent first, then by room name. */
    public static List<RoomReport> sorted(List<RoomReport> list) {
        List<RoomReport> out = new ArrayList<>(list);
        Collections.sort(out, new Comparator<RoomReport>() {
            @Override public int compare(RoomReport a, RoomReport b) {
                int c = Integer.compare(rank(a.status), rank(b.status));
                return c != 0 ? c : a.room.compareToIgnoreCase(b.room);
            }
        });
        return out;
    }

    /** "3 rooms reported: 1 needs help, 1 has missing students, 1 all safe" or "No room reports yet." */
    public static String summary(List<RoomReport> list) {
        if (list == null || list.isEmpty()) return "No room reports yet.";
        int help = 0, missing = 0, safe = 0;
        for (RoomReport r : list) {
            if (HELP.equals(r.status)) help++;
            else if (MISSING.equals(r.status)) missing++;
            else safe++;
        }
        List<String> parts = new ArrayList<>();
        if (help > 0) parts.add(help + (help == 1 ? " needs help" : " need help"));
        if (missing > 0) parts.add(missing + (missing == 1 ? " has missing students" : " have missing students"));
        if (safe > 0) parts.add(safe + " all safe");
        return list.size() + (list.size() == 1 ? " room" : " rooms") + " reported: " + String.join(", ", parts);
    }
}
