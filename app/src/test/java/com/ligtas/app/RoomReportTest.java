package com.ligtas.app;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Tests for RoomReport: input cleaning, validation, sorting and the summary text. */
public class RoomReportTest {

    private static RoomReport report(String room, String status) {
        return new RoomReport("t@school.edu", "Teacher", room, status, -1, "", 0);
    }

    @Test
    public void constructor_turnsNullsIntoEmptyText_andTrimsSpaces() {
        RoomReport r = new RoomReport(null, null, "  CICS 204  ", null, 10, "  note  ", 0);
        assertEquals("", r.email);
        assertEquals("", r.name);
        assertEquals("CICS 204", r.room);
        assertEquals("", r.status);
        assertEquals("note", r.note);
    }

    @Test
    public void isValidStatus_acceptsOnlySafeMissingHelp() {
        assertTrue(RoomReport.isValidStatus("SAFE"));
        assertTrue(RoomReport.isValidStatus("MISSING"));
        assertTrue(RoomReport.isValidStatus("HELP"));
        assertFalse(RoomReport.isValidStatus("safe"));
        assertFalse(RoomReport.isValidStatus("OK"));
        assertFalse(RoomReport.isValidStatus(null));
    }

    @Test
    public void label_givesTheWordsShownOnScreen() {
        assertEquals("All students accounted for", RoomReport.label(RoomReport.SAFE));
        assertEquals("Some students missing", RoomReport.label(RoomReport.MISSING));
        assertEquals("Needs help", RoomReport.label(RoomReport.HELP));
        assertEquals("Unknown", RoomReport.label("???"));
    }

    @Test
    public void sorted_putsMostUrgentFirst_andDoesNotChangeTheOriginalList() {
        List<RoomReport> original = new ArrayList<>();
        original.add(report("B room", RoomReport.SAFE));
        original.add(report("A room", RoomReport.HELP));
        original.add(report("C room", RoomReport.MISSING));

        List<RoomReport> sorted = RoomReport.sorted(original);

        assertEquals(RoomReport.HELP, sorted.get(0).status);
        assertEquals(RoomReport.MISSING, sorted.get(1).status);
        assertEquals(RoomReport.SAFE, sorted.get(2).status);
        assertEquals(RoomReport.SAFE, original.get(0).status);   // original untouched
    }

    @Test
    public void summary_noReports() {
        assertEquals("No room reports yet.", RoomReport.summary(new ArrayList<RoomReport>()));
        assertEquals("No room reports yet.", RoomReport.summary(null));
    }

    @Test
    public void summary_countsEachKind() {
        List<RoomReport> list = new ArrayList<>();
        list.add(report("A", RoomReport.HELP));
        list.add(report("B", RoomReport.MISSING));
        list.add(report("C", RoomReport.SAFE));
        assertEquals("3 rooms reported: 1 needs help, 1 has missing students, 1 all safe",
                RoomReport.summary(list));
    }

    @Test
    public void line_showsRoomNameHeadcountAndNote() {
        RoomReport r = new RoomReport("t@school.edu", "Nicole Atienza", "CICS 204",
                RoomReport.HELP, 24, "Need a medic", 0);
        assertEquals("NEEDS HELP: CICS 204 (Nicole Atienza), 24 students. Note: Need a medic", r.line());
    }

    @Test
    public void line_withNoRoom_saysSo() {
        RoomReport r = report("", RoomReport.SAFE);
        assertTrue(r.line().startsWith("Safe: (no room given)"));
    }
}
