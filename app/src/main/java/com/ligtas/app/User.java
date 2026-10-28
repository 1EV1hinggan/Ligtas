package com.ligtas.app;

public abstract class User {
    // Encapsulation: Private data hidden from the outside
    private String id;
    private String name;

    public User(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() { return id; }
    public String getName() { return name; }

    // Abstraction & Polymorphism: Subclasses MUST define this behavior
    public abstract boolean canSendAlerts();
}