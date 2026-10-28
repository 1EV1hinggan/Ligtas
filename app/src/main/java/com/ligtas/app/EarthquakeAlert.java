package com.ligtas.app;

public class EarthquakeAlert extends Alert {
    public EarthquakeAlert(String alertId, String targetArea, String instruction) {
        super(alertId, targetArea, instruction);
    }

    @Override
    public String getSeverityLevel() {
        return "HIGH";
    }

    @Override
    public String getTitle() {
        return "EARTHQUAKE ALERT";
    }
}
