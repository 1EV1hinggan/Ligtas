package com.ligtas.app;

/**
 * CustomAlert is a kind of Alert (inheritance: "extends Alert").
 * It is used for the "Others" option, when none of the ready-made types fit.
 * It reuses everything from Alert and only supplies its own severity and title.
 */
public class CustomAlert extends Alert {

    public CustomAlert(String alertId, String targetArea, String instruction) {
        super(alertId, targetArea, instruction);   // pass the data up to Alert
    }

    // Polymorphism: Alert declares this method; CustomAlert gives its own answer.
    @Override
    public String getSeverityLevel() {
        return "MEDIUM";
    }

    @Override
    public String getTitle() {
        return "CAMPUS ALERT";
    }
}