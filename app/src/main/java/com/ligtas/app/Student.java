package com.ligtas.app;

/**
 * Student is a kind of User (inheritance: "extends User").
 * Students are RECEIVE-ONLY: they get alerts and can acknowledge them,
 * but they can never send an alert.
 */
public class Student extends User {

    // Private: the course and block, for example "BSIT 2114".
    private String courseAndBlock;

    public Student(String id, String name, String courseAndBlock) {
        super(id, name);   // super(...) passes id and name up to User
        this.courseAndBlock = courseAndBlock;
    }

    public String getCourseAndBlock() { return courseAndBlock; }

    // Polymorphism: this is Student's own answer to User's abstract method.
    @Override
    public boolean canSendAlerts() {
        return false;
    }
}