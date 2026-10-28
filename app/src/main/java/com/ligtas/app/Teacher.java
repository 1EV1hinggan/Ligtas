package com.ligtas.app;

/**
 * Teacher is a kind of User (inheritance).
 * Teachers receive alerts like students, and can also report whether
 * everyone in their room is accounted for. They cannot send alerts.
 */
public class Teacher extends User {

    // Private: the room the teacher is assigned to, for example "CICS 102".
    private String assignedRoom;

    public Teacher(String id, String name, String assignedRoom) {
        super(id, name);
        this.assignedRoom = assignedRoom;
    }

    public String getAssignedRoom() { return assignedRoom; }

    // Polymorphism: Teacher's own answer. Same method as Student's, same result.
    @Override
    public boolean canSendAlerts() {
        return false;
    }
}