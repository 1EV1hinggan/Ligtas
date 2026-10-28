package com.ligtas.app;

/**
 * User is the base class for everyone who uses Ligtas.
 *
 * It is ABSTRACT, which means we never create a plain "User" object.
 * We only create a Student, a Teacher or an Administrator, and each of
 * them is a kind of User.
 *
 * OOP ideas shown here:
 *  - Abstraction:   canSendAlerts() is declared but not written here. Each
 *                   child class has to write its own version.
 *  - Encapsulation: id and name are private. Other classes can only read
 *                   them through the getters below.
 *  - Inheritance:   Student, Teacher and Administrator extend this class,
 *                   so they all get id and name without repeating code.
 */
public abstract class User {

    // Private: only this class can change these directly.
    private String id;
    private String name;

    // Constructor: sets the id and name when a user object is created.
    public User(String id, String name) {
        this.id = id;
        this.name = name;
    }

    // Getters: the safe way for other classes to read the private fields.
    public String getId() { return id; }
    public String getName() { return name; }

    /**
     * Can this kind of user send emergency alerts?
     * Each child class answers differently (polymorphism):
     * Student = false, Teacher = false, Administrator = true.
     */
    public abstract boolean canSendAlerts();
}