package com.ligtas.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Tests for the ready-made alert templates (English and Taglish). */
public class AlertTemplateTest {

    private static AlertTemplate find(String key) {
        for (AlertTemplate t : AlertTemplate.all()) {
            if (t.getKey().equals(key)) return t;
        }
        throw new IllegalArgumentException("No template " + key);
    }

    @Test
    public void thereAreSixTemplates() {
        assertEquals(6, AlertTemplate.all().length);
    }

    @Test
    public void everyTemplate_hasAtLeastOneInstruction() {
        for (AlertTemplate t : AlertTemplate.all()) {
            assertTrue(t.getKey(), t.getInstructions().length >= 1);
        }
    }

    @Test
    public void onlyOthers_requiresANote() {
        for (AlertTemplate t : AlertTemplate.all()) {
            assertEquals(t.getKey().equals("OTHERS"), t.isNoteRequired());
        }
    }

    @Test
    public void createAlert_returnsTheRightSubclass() {
        assertTrue(find("FIRE").createAlert("a", "Whole campus", 0, null) instanceof FireAlert);
        assertTrue(find("EARTHQUAKE").createAlert("a", "Whole campus", 0, null) instanceof EarthquakeAlert);
        assertTrue(find("SHOOTER").createAlert("a", "Whole campus", 0, null) instanceof LockdownAlert);
        assertTrue(find("BOMB").createAlert("a", "Whole campus", 0, null) instanceof BombThreatAlert);
        assertTrue(find("CHEMICAL").createAlert("a", "Whole campus", 0, null) instanceof ChemicalLeakAlert);
        assertTrue(find("OTHERS").createAlert("a", "Whole campus", 0, null) instanceof CustomAlert);
    }

    @Test
    public void createAlert_addsTheTrimmedNote() {
        Alert alert = find("FIRE").createAlert("a", "Whole campus", 0, "  Use Exit B  ");
        assertTrue(alert.getInstruction().endsWith(" Note: Use Exit B"));
    }

    @Test
    public void createAlert_withoutNote_hasNoNoteText() {
        Alert withNull = find("FIRE").createAlert("a", "Whole campus", 0, null);
        Alert withBlank = find("FIRE").createAlert("a", "Whole campus", 0, "   ");
        assertFalse(withNull.getInstruction().contains("Note:"));
        assertFalse(withBlank.getInstruction().contains("Note:"));
    }

    @Test
    public void createAlert_badVariantIndex_fallsBackToTheDefault() {
        AlertTemplate fire = find("FIRE");
        Alert tooBig = fire.createAlert("a", "Whole campus", 99, null);
        Alert negative = fire.createAlert("a", "Whole campus", -1, null);
        assertEquals(fire.getInstructions()[0], tooBig.getInstruction());
        assertEquals(fire.getInstructions()[0], negative.getInstruction());
    }

    @Test
    public void taglishAlert_keepsTheSameSeverity_butHasItsOwnTitle() {
        AlertTemplate fire = find("FIRE");
        Alert en = fire.createAlert("a", "Whole campus", 0, null);
        Alert tl = fire.createAlertTl("a", "Whole campus", 0, null);
        assertEquals(en.getSeverityLevel(), tl.getSeverityLevel());
        assertEquals("SUNOG ALERT", tl.getTitle());
        assertEquals("Buong campus", tl.getTargetArea());
    }

    @Test
    public void taglishAlert_usesPaalalaForTheNote() {
        Alert tl = find("FIRE").createAlertTl("a", "Whole campus", 0, "Gamitin ang Exit B");
        assertTrue(tl.getInstruction().endsWith(" Paalala: Gamitin ang Exit B"));
    }

    @Test
    public void noteLimit_is80() {
        assertEquals(80, AlertTemplate.NOTE_MAX);
        assertNotNull(AlertTemplate.all()[0].getLabel());
    }
}
