package com.ligtas.app;

public class ChemicalLeakAlert extends Alert {
    public ChemicalLeakAlert(String alertId, String targetArea, String instruction) {
        super(alertId, targetArea, instruction);
    }

    @Override
    public String getSeverityLevel() {
        return "HIGH";
    }

    @Override
    public String getTitle() {
        return "CHEMICAL LEAK ALERT";
    }
}
