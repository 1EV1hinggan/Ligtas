package com.ligtas.app;

public class LockdownAlert extends Alert {
    public LockdownAlert(String alertId, String targetArea, String instruction) {
        super(alertId, targetArea, instruction);
    }

    @Override
    public String getSeverityLevel() {
        return "CRITICAL_TWO_PERSON_APPROVAL";
    }

    @Override
    public String getTitle() {
        return "LOCKDOWN ALERT";
    }
}
