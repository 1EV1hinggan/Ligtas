package com.ligtas.app;

public class Acknowledgment {
    private String alertId;
    private String userId;
    private long timestamp;

    public Acknowledgment(String alertId, String userId) {
        this.alertId = alertId;
        this.userId = userId;
        this.timestamp = System.currentTimeMillis(); // Automatically records exact time of tap
    }

    public String getAlertId() { return alertId; }
    public String getUserId() { return userId; }
    public long getTimestamp() { return timestamp; }
}