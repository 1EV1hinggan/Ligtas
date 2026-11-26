package com.ligtas.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Tests for the Mode enum: ONSITE, ONLINE, VACANT. */
public class ModeTest {

    @Test
    public void parse_readsTheShortCodes() {
        assertEquals(Mode.ONLINE, Mode.parse("OL"));
        assertEquals(Mode.VACANT, Mode.parse("NC"));
        assertEquals(Mode.ONSITE, Mode.parse("OS"));
    }

    @Test
    public void parse_ignoresSpacesAndLetterCase() {
        assertEquals(Mode.ONLINE, Mode.parse("  ol "));
        assertEquals(Mode.VACANT, Mode.parse("no class"));
    }

    @Test
    public void parse_unknownOrNull_isOnsite_theSafeChoice() {
        assertEquals(Mode.ONSITE, Mode.parse(null));
        assertEquals(Mode.ONSITE, Mode.parse(""));
        assertEquals(Mode.ONSITE, Mode.parse("onsit"));
    }

    @Test
    public void code_turnsAModeBackIntoItsShortCode() {
        assertEquals("OL", Mode.ONLINE.code());
        assertEquals("NC", Mode.VACANT.code());
        assertEquals("OS", Mode.ONSITE.code());
    }
}
