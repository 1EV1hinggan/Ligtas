package com.ligtas.app;

/** Turns names into safe, comparable keys: "College of Engineering" -> "college_of_engineering". */
public final class Slug {
    private Slug() { }

    public static String of(String s) {
        if (s == null) return "";
        return s.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }
}
