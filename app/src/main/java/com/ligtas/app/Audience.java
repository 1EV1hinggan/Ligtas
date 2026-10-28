package com.ligtas.app;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;

/**
 * Who an alert is aimed at. Each kind of audience knows its own label, who belongs to it and which
 * push topics reach it (polymorphism: the sending code does not care which kind it holds).
 */
public abstract class Audience {

    /** One person from the roster. */
    public static class Member {
        public final String email, name, role, college, program, building;
        public final int yearLevel;   // 0 = none
        public final boolean hasToken;
        public boolean consented;     // agreed to the privacy notice (set when the roster is loaded)
        public List<Teaching.Slot> teaching = new ArrayList<>();   // teachers: programs and years they handle
        public Member(String email, String name, String role, String college, String program,
                      int yearLevel, String building, boolean hasToken) {
            this.email = email;
            this.name = name;
            this.role = role == null ? "" : role;
            this.college = college == null ? "" : college;
            this.program = program == null ? "" : program;
            this.yearLevel = yearLevel;
            this.building = building == null ? "" : building;
            this.hasToken = hasToken;
        }
        /** Students with a program and a year level belong to a schedule group; staff do not. */
        public boolean isStudentInGroup() {
            return "STUDENT".equals(role) && !program.isEmpty() && yearLevel > 0;
        }
    }

    /** Shown in the alert text and on the status screen, for example "Whole campus" or "CICS · BSIT · Year 2". */
    public abstract String label();

    /** Does this person belong to the group the alert is aimed at (ignoring online weeks)? */
    protected abstract boolean inGroup(Member m);

    /** The topics ALL of which a phone must be in: one topic, or several joined with AND. */
    protected abstract List<String> requiredTopics();

    /** Turns required topics into one FCM target: "/topics/x" or "condition:'a' in topics && 'b' in topics". */
    protected final String target() {
        List<String> t = requiredTopics();
        if (t.size() == 1) return "/topics/" + t.get(0);
        StringBuilder sb = new StringBuilder("condition:");
        for (int i = 0; i < t.size(); i++) {
            if (i > 0) sb.append(" && ");
            sb.append("'").append(t.get(i)).append("' in topics");
        }
        return sb.toString();
    }

    /** Equality filters on roster fields that describe this audience, used for cheap count queries on big rosters. */
    public Map<String, Object> filters() {
        return new LinkedHashMap<String, Object>();
    }

    /** True when this audience can skip online students (and so needs the campus setup). */
    public boolean usesSchedule() {
        return false;
    }

    /** Works out where to push and who to skip, using the campus setup (which may be null when it could not be loaded). */
    public AudiencePlan plan(CampusConfig config, long when) {
        List<String> targets = new ArrayList<>();
        targets.add(target());
        return new AudiencePlan(this, targets, new HashSet<String>());
    }

    // ------------------------------------------------------------------

    /** Everyone on the campus; optionally skipping the program/year groups that are online this week. */
    public static class Campus extends Audience {
        private final String campusId;
        private final boolean onSiteOnly;
        public Campus(String campusId, boolean onSiteOnly) { this.campusId = campusId; this.onSiteOnly = onSiteOnly; }

        @Override public String label() { return "Whole campus"; }
        @Override protected boolean inGroup(Member m) { return true; }
        @Override public boolean usesSchedule() { return onSiteOnly; }
        @Override protected List<String> requiredTopics() {
            List<String> t = new ArrayList<>();
            t.add(TopicNames.campus(campusId));
            return t;
        }

        @Override public AudiencePlan plan(CampusConfig config, long when) {
            List<String> targets = new ArrayList<>();
            targets.add(target());
            Set<String> online = new HashSet<>();
            if (onSiteOnly && config != null) online.addAll(config.onlineGroups(when)); // no setup: skip nobody
            return new AudiencePlan(this, targets, online);
        }
    }

    /** Everyone whose roster entry lists this building. */
    public static class Building extends Audience {
        protected final String campusId, building;
        public Building(String campusId, String building) { this.campusId = campusId; this.building = building; }

        @Override public String label() { return building; }
        @Override public Map<String, Object> filters() {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("building", building);
            return f;
        }
        @Override protected boolean inGroup(Member m) { return Slug.of(m.building).equals(Slug.of(building)); }
        @Override protected List<String> requiredTopics() {
            List<String> t = new ArrayList<>();
            t.add(TopicNames.campus(campusId));
            t.add(TopicNames.building(building));
            return t;
        }
    }

    /** A college, a program or a year level (any combination; "" / 0 means all). */
    public static class Group extends Audience {
        protected final String campusId, college, program;
        protected final int yearLevel;
        public Group(String campusId, String college, String program, int yearLevel) {
            this.campusId = campusId;
            this.college = college == null ? "" : college;
            this.program = program == null ? "" : program;
            this.yearLevel = yearLevel;
        }

        @Override public String label() {
            List<String> parts = new ArrayList<>();
            if (!college.isEmpty()) parts.add(college);
            if (!program.isEmpty()) parts.add(program);
            if (yearLevel > 0) parts.add("Year " + yearLevel);
            return String.join(" · ", parts);
        }

        @Override public Map<String, Object> filters() {
            Map<String, Object> f = new LinkedHashMap<>();
            if (!college.isEmpty()) f.put("college", college);
            if (!program.isEmpty()) f.put("program", program);
            if (yearLevel > 0) f.put("yearLevel", yearLevel);
            return f;
        }

        /** Students (and staff) whose own roster fields match. */
        protected boolean ownFieldsMatch(Member m) {
            if (!program.isEmpty() && !Slug.of(m.program).equals(Slug.of(program))) return false;
            if (!college.isEmpty() && !Slug.of(m.college).equals(Slug.of(college))) return false;
            return yearLevel <= 0 || m.yearLevel == yearLevel;
        }

        /** A teacher whose "teaches" list includes this program (and year). */
        protected boolean teachesHere(Member m) {
            return "TEACHER".equals(m.role) && !program.isEmpty() && Teaching.teaches(m.teaching, program, yearLevel);
        }

        @Override protected boolean inGroup(Member m) {
            return ownFieldsMatch(m) || teachesHere(m);
        }

        /** Teachers are reached through their own topics: this is the extra push for them. */
        protected List<String> teacherTargets() {
            List<String> t = new ArrayList<>();
            if (program.isEmpty()) return t;
            if (yearLevel > 0) {
                t.add("condition:'" + TopicNames.teach(program, yearLevel) + "' in topics || '"
                        + TopicNames.teach(program, 0) + "' in topics");
            } else {
                t.add("/topics/" + TopicNames.teachProgram(program));
            }
            return t;
        }

        @Override public AudiencePlan plan(CampusConfig config, long when) {
            List<String> targets = new ArrayList<>();
            targets.add(target());
            targets.addAll(teacherTargets());
            return new AudiencePlan(this, targets, new HashSet<String>());
        }

        @Override protected List<String> requiredTopics() {
            List<String> t = new ArrayList<>();
            t.add(TopicNames.campus(campusId));
            if (!college.isEmpty()) t.add(TopicNames.college(college));
            if (!program.isEmpty()) t.add(TopicNames.program(program));
            if (yearLevel > 0) t.add(TopicNames.year(yearLevel));
            return t;
        }
    }

    /** One group inside one building (both must match). */
    public static class BuildingGroup extends Group {
        private final String building;
        public BuildingGroup(String campusId, String building, String college, String program, int yearLevel) {
            super(campusId, college, program, yearLevel);
            this.building = building;
        }

        @Override public String label() { return building + " · " + super.label(); }
        @Override public Map<String, Object> filters() {
            Map<String, Object> f = super.filters();
            f.put("building", building);
            return f;
        }
        // Inside one building only people whose own roster entry matches are counted; teachers are not
        // reached by their teaching list here (a teacher's building is in their own entry).
        @Override protected boolean inGroup(Member m) {
            return Slug.of(m.building).equals(Slug.of(building)) && ownFieldsMatch(m);
        }
        @Override protected List<String> teacherTargets() { return new ArrayList<>(); }
        @Override protected List<String> requiredTopics() {
            List<String> t = super.requiredTopics();
            t.add(TopicNames.building(building));
            return t;
        }
    }

    // ------------------------------------------------------------------

    /**
     * Builds the right kind of audience from what the admin picked.
     * @param area      the whole-campus label, or a building name
     * @param college   "" = all colleges
     * @param program   "" = all programs
     * @param yearLevel 0 = all years
     */
    public static Audience create(String campusId, String area, String wholeCampusName,
                                  String college, String program, int yearLevel, boolean onSiteOnly) {
        boolean wholeCampus = area == null || area.equals(wholeCampusName);
        boolean everyone = (college == null || college.isEmpty()) && (program == null || program.isEmpty()) && yearLevel <= 0;
        if (wholeCampus && everyone) return new Campus(campusId, onSiteOnly);
        if (wholeCampus) return new Group(campusId, college, program, yearLevel);
        if (everyone) return new Building(campusId, area);
        return new BuildingGroup(campusId, area, college, program, yearLevel);
    }
}
