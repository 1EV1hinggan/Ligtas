package com.ligtas.app;

/**
 * Alert is the base class for every emergency alert.
 *
 * It is ABSTRACT: we never create a plain "Alert". We create a specific
 * kind, such as FireAlert or EarthquakeAlert, and each one extends Alert.
 *
 * OOP ideas shown here:
 *  - Abstraction:   getSeverityLevel() and getTitle() are declared but not
 *                   written here. Each alert type fills them in.
 *  - Encapsulation: all fields are private. Other classes read them with
 *                   getters, and the status can only change through setStatus().
 *  - Inheritance:   the six alert types share this code instead of repeating it.
 */
public abstract class Alert {

    // Private fields: the data of one alert.
    private String alertId;       // unique id of this alert
    private String targetArea;    // who or where it is for, e.g. "Whole campus"
    private String instruction;   // what people should do
    private String status;        // e.g. "CREATED", "SENT", "ACKNOWLEDGED"

    // Constructor: every new alert starts with the status "CREATED".
    public Alert(String alertId, String targetArea, String instruction) {
        this.alertId = alertId;
        this.targetArea = targetArea;
        this.instruction = instruction;
        this.status = "CREATED";
    }

    // Getters: read-only access to the private fields.
    public String getAlertId() { return alertId; }
    public String getTargetArea() { return targetArea; }
    public String getInstruction() { return instruction; }
    public String getStatus() { return status; }

    // Setter: the controlled way to change the status.
    public void setStatus(String status) {
        this.status = status;
    }

    // Builds the text people see, e.g. "[Whole campus] Earthquake. Drop, cover..."
    public String getMessage() {
        return "[" + targetArea + "] " + instruction;
    }

    // Abstract methods: no body here. Each alert type must write its own
    // (polymorphism). For example FireAlert returns "FIRE ALERT" as its title.
    public abstract String getSeverityLevel();
    public abstract String getTitle();
}