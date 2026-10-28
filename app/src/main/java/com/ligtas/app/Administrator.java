package com.ligtas.app;

/**
 * Administrator is a kind of User (inheritance).
 * Administrators are the ONLY users who can send emergency alerts.
 */
public class Administrator extends User {

    // Private: the department the administrator belongs to.
    private String department;

    public Administrator(String id, String name, String department) {
        super(id, name);
        this.department = department;
    }

    public String getDepartment() { return department; }

    // Polymorphism: this is the only version that returns true.
    // AlertService asks canSendAlerts() and gets a different answer
    // depending on which kind of user it was given.
    @Override
    public boolean canSendAlerts() {
        return true;
    }
}