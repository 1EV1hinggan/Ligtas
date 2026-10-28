package com.ligtas.app;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything the admin set up for one campus: colleges, programs, schedule policies, semester dates,
 * one-off overrides, locations and class times. Pure Java (no Android), read from the Firestore
 * document campuses/{id}.
 *
 * Campus > College > Program > Year level; each college has a policy, a program may override it.
 */
public final class CampusConfig {

    private static final long DAY_MS = 24L * 60 * 60 * 1000;
    private static final long WEEK_MS = 7 * DAY_MS;

    public static class College {
        public final String code, name, policyId;
        College(String code, String name, String policyId) { this.code = code; this.name = name; this.policyId = policyId; }
    }

    public static class Program {
        public final String code, name, collegeCode, policyId;
        public final int years;
        Program(String code, String name, String collegeCode, int years, String policyId) {
            this.code = code; this.name = name; this.collegeCode = collegeCode; this.years = years; this.policyId = policyId;
        }
    }

    /**
     * A calendar exception: a college (or the whole campus, "*") is forced online, on-site or vacant on certain days.
     * Dates are optional and so are weekdays: "every Friday" = no dates, days FRI; "typhoon" = two dates, no weekdays;
     * "holiday" = one date, mode VACANT. A rule with neither dates nor weekdays never applies.
     */
    public static class Override {
        public final long start, end;     // 0 = no limit; end is the start of the LAST day (inclusive)
        public final String college;      // "*" = everyone
        public final Mode mode;
        public final String reason;
        public final boolean[] days;      // index 0 = Monday ... 6 = Sunday; all false = every day
        Override(long start, long end, String college, Mode mode, String reason, boolean[] days) {
            this.start = start; this.end = end; this.college = college; this.mode = mode; this.reason = reason; this.days = days;
        }
        boolean hasDays() { for (boolean d : days) if (d) return true; return false; }
        boolean covers(long when) {
            boolean anyDays = hasDays();
            if (start <= 0 && end <= 0 && !anyDays) return false;
            if (start > 0 && when < start) return false;
            if (end > 0 && when >= end + DAY_MS) return false;
            return !anyDays || days[weekdayIndex(when)];
        }
    }

    /** 0 = Monday ... 6 = Sunday, in the device time zone. */
    public static int weekdayIndex(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        return (c.get(Calendar.DAY_OF_WEEK) + 5) % 7;
    }

    /** "FRI", "MON,TUE", "WEEKDAYS", "" (= every day) -> seven flags. */
    static boolean[] parseDays(String text) {
        boolean[] d = new boolean[7];
        if (text == null) return d;
        String[] names = {"MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"};
        for (String part : text.toUpperCase().split("[,; ]+")) {
            if (part.equals("WEEKDAYS")) { for (int i = 0; i < 5; i++) d[i] = true; continue; }
            if (part.equals("WEEKEND")) { d[5] = true; d[6] = true; continue; }
            for (int i = 0; i < 7; i++) if (part.length() >= 3 && part.startsWith(names[i])) d[i] = true;
        }
        return d;
    }

    public final String id;
    public final String name;
    public final long semesterStart;  // Monday of Week 1 (an ODD week); 0 = not set
    public final long semesterEnd;    // 0 = not set
    public final List<College> colleges = new ArrayList<>();
    public final List<Program> programs = new ArrayList<>();
    public final Map<String, SchedulePolicy> policies = new HashMap<>();
    public final List<Override> overrides = new ArrayList<>();
    public final List<String> locations = new ArrayList<>();

    private CampusConfig(String id, String name, long semesterStart, long semesterEnd) {
        this.id = id;
        this.name = name;
        this.semesterStart = semesterStart;
        this.semesterEnd = semesterEnd;
    }

    /** Builds a campus from its stored document. Missing or odd values are skipped, never fatal. */
    public static CampusConfig fromMap(String id, Map<String, Object> m) {
        CampusConfig c = new CampusConfig(id, Json.str(m.get("name")),
                Json.longOf(m.get("semesterStart"), 0), Json.longOf(m.get("semesterEnd"), 0));
        for (Map<String, Object> p : Json.listOfMaps(m.get("policies"))) {
            SchedulePolicy sp = SchedulePolicy.fromMap(p);
            if (!sp.id.isEmpty()) c.policies.put(sp.id, sp);
        }
        for (Map<String, Object> x : Json.listOfMaps(m.get("colleges"))) {
            String code = Json.str(x.get("code"));
            if (!code.isEmpty()) c.colleges.add(new College(code, Json.str(x.get("name")), Json.str(x.get("policy"))));
        }
        for (Map<String, Object> x : Json.listOfMaps(m.get("programs"))) {
            String code = Json.str(x.get("code"));
            if (!code.isEmpty()) {
                c.programs.add(new Program(code, Json.str(x.get("name")), Json.str(x.get("college")),
                        Math.max(1, Json.intOf(x.get("years"), 4)), Json.str(x.get("policy"))));
            }
        }
        for (Map<String, Object> x : Json.listOfMaps(m.get("overrides"))) {
            String college = Json.str(x.get("college"));
            c.overrides.add(new Override(Json.longOf(x.get("start"), 0), Json.longOf(x.get("end"), 0),
                    college.isEmpty() ? "*" : college, Mode.parse(Json.str(x.get("mode"))), Json.str(x.get("reason")),
                    parseDays(Json.str(x.get("days")))));
        }
        for (Map<String, Object> x : Json.listOfMaps(m.get("locations"))) {
            String n = Json.str(x.get("name"));
            // Rooms are not "affected areas" (a school can have thousands); only buildings and other sites are listed
            if (!n.isEmpty() && !"room".equalsIgnoreCase(Json.str(x.get("type")).trim())) c.locations.add(n);
        }
        return c;
    }

    // ----------------------------- weeks and modes -----------------------------

    /** Monday 00:00 (device time zone) of the week containing the moment. */
    public static long mondayOf(long millis) {
        Calendar c = Calendar.getInstance();
        c.setFirstDayOfWeek(Calendar.MONDAY);
        c.setTimeInMillis(millis);
        c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** 1 for the first week of the semester, 2 for the next... or 0 when the semester dates do not cover this moment. */
    public int weekNumber(long when) {
        if (semesterStart <= 0 || when < mondayOf(semesterStart)) return 0;
        if (semesterEnd > 0 && when >= semesterEnd + DAY_MS) return 0;
        return (int) Math.round((mondayOf(when) - mondayOf(semesterStart)) / (double) WEEK_MS) + 1;
    }

    public boolean isOddWeek(long when) {
        int w = weekNumber(when);
        return w > 0 && w % 2 == 1;
    }

    public College college(String code) {
        for (College c : colleges) if (Slug.of(c.code).equals(Slug.of(code))) return c;
        return null;
    }

    public Program program(String code) {
        for (Program p : programs) if (Slug.of(p.code).equals(Slug.of(code))) return p;
        return null;
    }

    public List<Program> programsOf(String collegeCode) {
        List<Program> out = new ArrayList<>();
        for (Program p : programs) if (Slug.of(p.collegeCode).equals(Slug.of(collegeCode))) out.add(p);
        return out;
    }

    /** The college a program belongs to, from the setup (people's rosters may leave it out). */
    public String collegeOfProgram(String programCode) {
        Program p = program(programCode);
        return p == null ? "" : p.collegeCode;
    }

    /**
     * Is this group on-site or online at this moment?
     * Anything unknown (no policy, semester not set, group not in the setup) counts as ON-SITE,
     * so an alert is never skipped because of missing setup.
     */
    public Mode modeFor(String collegeCode, String programCode, int yearLevel, long when) {
        Program prog = programCode == null || programCode.isEmpty() ? null : program(programCode);
        String collegeKey = collegeCode == null || collegeCode.isEmpty()
                ? (prog == null ? "" : prog.collegeCode) : collegeCode;

        Mode forced = null;
        for (Override o : overrides) {                       // the last matching override wins
            if (o.covers(when) && (o.college.equals("*") || Slug.of(o.college).equals(Slug.of(collegeKey)))) forced = o.mode;
        }
        if (forced != null) return forced;

        int week = weekNumber(when);
        if (week == 0) return Mode.ONSITE;

        String policyId = prog != null && !prog.policyId.isEmpty() ? prog.policyId : "";
        if (policyId.isEmpty()) {
            College col = college(collegeKey);
            if (col != null) policyId = col.policyId;
        }
        SchedulePolicy policy = policies.get(policyId);
        if (policy == null) return Mode.ONSITE;
        return policy.modeFor(yearLevel, week % 2 == 1);
    }

    /** "bsit_1" style key for a program and year level (the same key the phone uses for itself). */
    public static String groupKey(String programCode, int yearLevel) {
        return Slug.of(programCode) + "_" + yearLevel;
    }

    /** Every program/year group that is ONLINE at this moment. */
    public List<String> onlineGroups(long when) {
        List<String> out = new ArrayList<>();
        for (Program p : programs) {
            for (int y = 1; y <= p.years; y++) {
                if (modeFor(p.collegeCode, p.code, y, when) == Mode.ONLINE) out.add(groupKey(p.code, y));
            }
        }
        return out;
    }
}
