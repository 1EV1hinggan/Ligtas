package com.ligtas.app;

public class FireAlert extends Alert {
    public FireAlert(String alertId, String targetArea, String instruction) {
        super(alertId, targetArea, instruction);
    }

    @Override
    public String getSeverityLevel() {
        return "HIGH";
    }

    @Override
    public String getTitle() {
        return "FIRE ALERT";
    }
}
