package com.ligtas.app;

import java.util.List;
import java.util.Map;

/**
 * A college's "set-up": the rule that says which year levels are online or on-site in a week.
 * Each kind of rule is its own subclass, so a college with a different way of scheduling
 * (CAFAD, say) only needs a new subclass; the rest of the app keeps calling modeFor().
 *
 * OOP ideas shown here:
 *  - Abstraction:   this class is abstract. It only says WHAT every schedule rule must do
 *                   (modeFor and describe), not HOW.
 *  - Inheritance:   Fixed and Grid are two kinds of SchedulePolicy (nested classes below).
 *  - Polymorphism:  other code calls policy.modeFor(...) and does not care whether it is a
 *                   Fixed or a Grid. Each one answers in its own way.
 */
public abstract class SchedulePolicy {

    // Final: set once in the constructor and never changed.
    public final String id;
    public final String name;

    // Protected constructor: only the child classes (Fixed, Grid) can call it through super(...).
    // A null id or name becomes "" so the rest of the app never meets null.
    protected SchedulePolicy(String id, String name) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
    }

    /** The mode for a year level (1, 2, 3...) in an ODD or EVEN week. */
    // Abstract: no body here. Fixed and Grid must each write their own version.
    public abstract Mode modeFor(int yearLevel, boolean oddWeek);

    /** A short description for screens, for example "Alternating by year level". */
    public abstract String describe();

    // ------------------------------------------------------------------

    /** Same mode every week (for example a program that is always on-site, or fully online). */
    public static class Fixed extends SchedulePolicy {
        private final Mode mode;   // the one mode used every week
        public Fixed(String id, String name, Mode mode) { super(id, name); this.mode = mode; }
        // Polymorphism: Fixed ignores the year and the week and always returns the same mode.
        @Override public Mode modeFor(int yearLevel, boolean oddWeek) { return mode; }
        @Override public String describe() { return mode == Mode.ONLINE ? "Always online" : "Always on-site"; }
    }

    /** The table from the BSU diagram: rows are year levels, columns are ODD and EVEN weeks. */
    public static class Grid extends SchedulePolicy {
        // Arrays: one Mode per year level. Index 0 is year 1, index 1 is year 2, and so on.
        private final Mode[] odd;   // index = yearLevel - 1
        private final Mode[] even;
        public Grid(String id, String name, Mode[] odd, Mode[] even) {
            super(id, name);
            this.odd = odd;
            this.even = even;
        }
        // Polymorphism: Grid looks up the answer in its table (odd or even row, then the year).
        @Override public Mode modeFor(int yearLevel, boolean oddWeek) {
            Mode[] row = oddWeek ? odd : even;   // choose the row for this kind of week
            if (yearLevel < 1 || yearLevel > row.length) return Mode.ONSITE; // year not in the table: on-site
            return row[yearLevel - 1];
        }
        @Override public String describe() { return "Odd/even weeks by year level"; }
    }

    // ------------------------------------------------------------------

    /** Builds a policy from its stored form: {id, name, type, grid:[{year, odd, even}]}. */
    // This reads the saved settings (from Firestore) and creates the right kind of policy object.
    public static SchedulePolicy fromMap(Map<String, Object> m) {
        String id = Json.str(m.get("id"));
        String name = Json.str(m.get("name"));
        String type = Json.str(m.get("type")).toUpperCase();
        if (type.equals("FIXED_ONLINE")) return new Fixed(id, name, Mode.ONLINE);
        if (type.equals("GRID")) {
            int max = 0;
            List<Map<String, Object>> rows = Json.listOfMaps(m.get("grid"));
            for (Map<String, Object> r : rows) max = Math.max(max, Json.intOf(r.get("year"), 0));
            // Make the arrays as long as the biggest year level, and fill them with ONSITE first (safe default).
            Mode[] odd = new Mode[max];
            Mode[] even = new Mode[max];
            for (int i = 0; i < max; i++) { odd[i] = Mode.ONSITE; even[i] = Mode.ONSITE; }
            // Then put in the real value for each year from the saved table.
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