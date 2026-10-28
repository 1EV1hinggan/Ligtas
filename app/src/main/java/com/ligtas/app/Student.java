package com.ligtas.app;

public class Student extends User {
    private String courseAndBlock;

    public Student(String id, String name, String courseAndBlock) {
        super(id, name);
        this.courseAndBlock = courseAndBlock;
    }

    public String getCourseAndBlock() { return courseAndBlock; }

    @Override
    public boolean canSendAlerts() {
        // Students are strictly receive-only for security
        return false;
    }
}