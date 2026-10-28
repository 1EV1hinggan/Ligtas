package com.ligtas.app;

/**
 * ChemicalLeakAlert is a kind of Alert (inheritance: "extends Alert").
 * It reuses everything from Alert and only supplies its own severity and title.
 */
public class ChemicalLeakAlert extends Alert {

    public ChemicalLeakAlert(String alertId, String targetArea, String instruction) {
        super(alertId, targetArea, instruction);   // pass the data up to Alert
    }

    // Polymorphism: Alert declares this method; ChemicalLeakAlert gives its own answer.
    @Override
    public String getSeverityLevel() {
        return "HIGH";
    }

    @Override
    public String getTitle() {
        return "CHEMICAL LEAK ALERT";
    }
}