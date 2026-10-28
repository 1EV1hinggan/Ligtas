package com.ligtas.app;

/**
 * Acknowledgment records that one user tapped "Acknowledge" on one alert.
 * It stores which alert, which user, and the exact time of the tap.
 *
 * OOP idea shown here: ENCAPSULATION. All three fields are private and
 * read-only (there are only getters, no setters), so a record cannot be
 * changed after it is created.
 */
public class Acknowledgment {

    private String alertId;   // which alert was acknowledged
    private String userId;    // who acknowledged it
    private long timestamp;   // when, in milliseconds

    // Constructor: the time is recorded automatically at the moment of creation.
    public Acknowledgment(String alertId, String userId) {
        this.alertId = alertId;
        this.userId = userId;
        this.timestamp = System.currentTimeMillis();
    }

    public String getAlertId() { return alertId; }
    public String getUserId() { return userId; }
    public long getTimestamp() { return timestamp; }
}