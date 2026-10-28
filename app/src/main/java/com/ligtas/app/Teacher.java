package com.ligtas.app;

public class Teacher extends User {
    private String assignedRoom;

    public Teacher(String id, String name, String assignedRoom) {
        super(id, name);
        this.assignedRoom = assignedRoom;
    }

    public String getAssignedRoom() { return assignedRoom; }

    @Override
    public boolean canSendAlerts() {
        // Teachers receive alerts and do roll call, but cannot broadcast campus-wide
        return false;
    }
}