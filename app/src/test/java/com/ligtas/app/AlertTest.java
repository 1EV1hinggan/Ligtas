package com.ligtas.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for Alert and its subclasses.
 * Shows ENCAPSULATION (private fields, read through getters, status changed
 * only through setStatus) and POLYMORPHISM (each alert type answers
 * getTitle() and getSeverityLevel() in its own way).
 */
public class AlertTest {

    @Test
    public void newAlert_startsAsCreated() {
        Alert alert = new FireAlert("a1", "Whole campus", "Evacuate now.");
        assertEquals("CREATED", alert.getStatus());
    }

    @Test
    public void setStatus_changesTheStatus() {
        Alert alert = new FireAlert("a1", "Whole campus", "Evacuate now.");
        alert.setStatus("SENT");
        assertEquals("SENT", alert.getStatus());
    }

    @Test
    public void getMessage_putsTheAreaInBracketsBeforeTheInstruction() {
        Alert alert = new FireAlert("a1", "Whole campus", "Evacuate now.");
        assertEquals("[Whole campus] Evacuate now.", alert.getMessage());
    }

    @Test
    public void gettersReturnWhatTheConstructorReceived() {
        Alert alert = new EarthquakeAlert("a2", "Building 3", "Drop, cover, and hold on.");
        assertEquals("a2", alert.getAlertId());
        assertEquals("Building 3", alert.getTargetArea());
        assertEquals("Drop, cover, and hold on.", alert.getInstruction());
    }

    @Test
    public void eachAlertType_hasItsOwnTitle() {
        Alert[] alerts = {
                new FireAlert("1", "x", "y"),
                new EarthquakeAlert("2", "x", "y"),
                new LockdownAlert("3", "x", "y"),
                new BombThreatAlert("4", "x", "y"),
                new ChemicalLeakAlert("5", "x", "y"),
                new CustomAlert("6", "x", "y")
        };
        // Same method call on each one, a different answer from each one.
        for (int i = 0; i < alerts.length; i++) {
            for (int j = i + 1; j < alerts.length; j++) {
                assertTrue(!alerts[i].getTitle().equals(alerts[j].getTitle()));
            }
        }
        assertEquals("FIRE ALERT", alerts[0].getTitle());
    }

    @Test
    public void fireAlert_isHighSeverity_andCustomAlert_isMedium() {
        assertEquals("HIGH", new FireAlert("1", "x", "y").getSeverityLevel());
        assertEquals("MEDIUM", new CustomAlert("2", "x", "y").getSeverityLevel());
    }
}
