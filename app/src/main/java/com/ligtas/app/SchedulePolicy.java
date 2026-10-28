package com.ligtas.app;

import java.util.List;
import java.util.Map;

/**
 * A college's "set-up": the rule that says which year levels are online or on-site in a week.
 * Each kind of rule is its own subclass, so a college with a different way of scheduling
 * (CAFAD, say) only needs a new subclass; the rest of the app keeps calling modeFor().
 */
public abstract class SchedulePolicy {

    public final String id;
    public final String name;

    protected SchedulePolicy(String id, String name) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
    }

    /** The mode for a year level (1, 2, 3...) in an ODD or EVEN week. */
    public abstract Mode modeFor(int yearLevel, boolean oddWeek);

    /** A short description for screens, for example "Alternating by year level". */
    public abstract String describe();

    // ------------------------------------------------------------------

    /** Same mode every week (for example a program that is always on-site, or fully online). */
    public static class Fixed extends SchedulePolicy {
        private final Mode mode;
        public Fixed(String id, String name, Mode mode) { super(id, name); this.mode = mode; }
        @Override public Mode modeFor(int yearLevel, boolean oddWeek) { return mode; }
        @Override public String describe() { return mode == Mode.ONLINE ? "Always online" : "Always on-site"; }
    }

    /** The table from the BSU diagram: rows are year levels, columns are ODD and EVEN weeks. */
    public static class Grid extends SchedulePolicy {
        private final Mode[] odd;   // index = yearLevel - 1
        private final Mode[] even;
        public Grid(String id, String name, Mode[] odd, Mode[] even) {
            super(id, name);
            this.odd = odd;
            this.even = even;
        }
        @Override public Mode modeFor(int yearLevel, boolean oddWeek) {
            Mode[] row = oddWeek ? odd : even;
            if (yearLevel < 1 || yearLevel > row.length) return Mode.ONSITE; // year not in the table: on-site
            return row[yearLevel - 1];
        }
        @Override public String describe() { return "Odd/even weeks by year level"; }
    }

    // ------------------------------------------------------------------

    /** Builds a policy from its stored form: {id, name, type, grid:[{year, odd, even}]}. */
    public static SchedulePolicy fromMap(Map<String, Object> m) {
        String id = Json.str(m.get("id"));
        String name = Json.str(m.get("name"));
        String type = Json.str(m.get("type")).toUpperCase();
        if (type.equals("FIXED_ONLINE")) return new Fixed(id, name, Mode.ONLINE);
        if (type.equals("GRID")) {
            int max = 0;
            List<Map<String, Object>> rows = Json.listOfMaps(m.get("grid"));
            for (Map<String, Object> r : rows) max = Math.max(max, Json.intOf(r.get("year"), 0));
            Mode[] odd = new Mode[max];
            Mode[] even = new Mode[max];
            for (int i = 0; i < max; i++) { odd[i] = Mode.ONSITE; even[i] = Mode.ONSITE; }
            for (Map<String, Object> r : rows) {
                int y = Json.intOf(r.get("year"), 0);
                if (y >= 1 && y <= max) {
                    odd[y - 1] = Mode.parse(Json.str(r.get("odd")));
                    even[y - 1] = Mode.parse(Json.str(r.get("even")));
                }
            }
            return new Grid(id, name, odd, even);
        }
        return new Fixed(id, name, Mode.ONSITE); // FIXED_ONSITE and anything unknown
    }
}
