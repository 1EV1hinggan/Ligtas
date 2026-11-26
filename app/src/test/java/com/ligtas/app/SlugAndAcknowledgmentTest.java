package com.ligtas.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Small tests for Slug (clean keys) and Acknowledgment (read-only record). */
public class SlugAndAcknowledgmentTest {

    @Test
    public void slug_makesLowercaseKeysWithUnderscores() {
        assertEquals("college_of_engineering", Slug.of("College of Engineering"));
    }

    @Test
    public void slug_removesExtraSymbolsAndEdges() {
        assertEquals("cics_204", Slug.of("  CICS - 204!! "));
    }

    @Test
    public void slug_nullBecomesEmptyText() {
        assertEquals("", Slug.of(null));
    }

    @Test
    public void acknowledgment_keepsTheAlertAndUser_andRecordsTheTime() {
        long before = System.currentTimeMillis();
        Acknowledgment ack = new Acknowledgment("alert-1", "user-1");
        long after = System.currentTimeMillis();
        assertEquals("alert-1", ack.getAlertId());
        assertEquals("user-1", ack.getUserId());
        assertTrue(ack.getTimestamp() >= before && ack.getTimestamp() <= after);
    }
}
