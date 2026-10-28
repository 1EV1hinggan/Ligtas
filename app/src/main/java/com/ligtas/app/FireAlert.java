package com.ligtas.app;

/**
 * FireAlert is a kind of Alert (inheritance: "extends Alert").
 * It reuses everything from Alert and only supplies its own severity and title.
 */
public class FireAlert extends Alert {

    public FireAlert(String alertId, String targetArea, String instruction) {
        super(alertId, targetArea, instruction);   // pass the data up to Alert
    }

    // Polymorphism: Alert declares this method; FireAlert gives its own answer.
    @Override
    public String getSeverityLevel() {
        return "HIGH";
    }

    @Override
    public String getTitle() {
        return "FIRE ALERT";
    }
}