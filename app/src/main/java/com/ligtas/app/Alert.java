package com.ligtas.app;

public abstract class Alert {
    private String alertId;
    private String targetArea;
    private String instruction;
    private String status; // e.g., "CREATED", "SENT", "ACKNOWLEDGED"

    public Alert(String alertId, String targetArea, String instruction) {
        this.alertId = alertId;
        this.targetArea = targetArea;
        this.instruction = instruction;
        this.status = "CREATED"; // Default status
    }

    public String getAlertId() { return alertId; }
    public String getTargetArea() { return targetArea; }
    public String getInstruction() { return instruction; }
    public String getStatus() { return status; }

    public void setStatus(String status) {
        this.status = status;
    }

    /** The text recipients see: the affected area followed by the instruction. */
    public String getMessage() {
        return "[" + targetArea + "] " + instruction;
    }

    // Each specific alert type must define its own severity and notification title
    public abstract String getSeverityLevel();
    public abstract String getTitle();
}
