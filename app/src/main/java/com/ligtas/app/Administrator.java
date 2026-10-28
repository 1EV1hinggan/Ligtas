package com.ligtas.app;

public class Administrator extends User {
    private String department;

    public Administrator(String id, String name, String department) {
        super(id, name);
        this.department = department;
    }

    public String getDepartment() { return department; }

    @Override
    public boolean canSendAlerts() {
        // Only admins are allowed to trigger the system
        return true;
    }
}