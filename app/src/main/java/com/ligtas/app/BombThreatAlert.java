package com.ligtas.app;

/**
 * BombThreatAlert is a kind of Alert (inheritance: "extends Alert").
 * It reuses everything from Alert and only supplies its own severity and title.
 *
 * NOTE: the label "CRITICAL_TWO_PERSON_APPROVAL" is a planned rule. The app
 * does not yet require a second approver, so any administrator can send it.
 */
public class BombThreatAlert extends Alert {

    public BombThreatAlert(String alertId, String targetArea, String instruction) {
        super(alertId, targetArea, instruction);   // pass the data up to Alert
    }

    // Polymorphism: Alert declares this method; BombThreatAlert gives its own answer.
    @Override
    public String getSeverityLevel() {
        return "CRITICAL_TWO_PERSON_APPROVAL";
    }

    @Override
    public String getTitle() {
        return "BOMB THREAT ALERT";
    }
}