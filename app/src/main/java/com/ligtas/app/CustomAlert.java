package com.ligtas.app;

public class CustomAlert extends Alert {
    public CustomAlert(String alertId, String targetArea, String instruction) {
        super(alertId, targetArea, instruction);
    }

    @Override
    public String getSeverityLevel() {
        return "MEDIUM";
    }

    @Override
    public String getTitle() {
        return "CAMPUS ALERT";
    }
}
