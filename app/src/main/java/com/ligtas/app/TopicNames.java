package com.ligtas.app;

/** The push-topic names. A phone joins the topics that match its roster entry. */
public final class TopicNames {
    public static final String CAMPUS_ALL = "ligtas_campus";

    private TopicNames() { }

    public static String campus(String campusId) { return Slug.of(campusId).isEmpty() ? CAMPUS_ALL : "campus_" + Slug.of(campusId); }
    public static String role(String role) { return "role_" + Slug.of(role); }
    public static String building(String building) { return "bld_" + Slug.of(building); }
    public static String college(String college) { return "col_" + Slug.of(college); }
    public static String program(String program) { return "prog_" + Slug.of(program); }
    public static String year(int yearLevel) { return "yr_" + yearLevel; }
    /** Teachers of one program and year ("teach_bsit_2"); year 0 means every year ("teach_bsit_all"). */
    public static String teach(String program, int yearLevel) {
        return "teach_" + Slug.of(program) + "_" + (yearLevel > 0 ? String.valueOf(yearLevel) : "all");
    }
    /** Teachers of a program in any year ("teachp_bsit"). */
    public static String teachProgram(String program) { return "teachp_" + Slug.of(program); }
}
