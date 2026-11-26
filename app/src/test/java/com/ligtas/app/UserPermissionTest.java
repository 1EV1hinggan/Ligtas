package com.ligtas.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for User and its three child classes.
 * Shows INHERITANCE (getId/getName come from User) and POLYMORPHISM
 * (the same canSendAlerts() call gives a different answer for each kind of user).
 */
public class UserPermissionTest {

    @Test
    public void student_cannotSendAlerts() {
        User student = new Student("25-00001", "Sample Student", "BSIT 2114");
        assertFalse(student.canSendAlerts());
    }

    @Test
    public void teacher_cannotSendAlerts() {
        User teacher = new Teacher("t-001", "Sample Teacher", "CICS 102");
        assertFalse(teacher.canSendAlerts());
    }

    @Test
    public void administrator_canSendAlerts() {
        User admin = new Administrator("a-001", "Sample Admin", "CICS");
        assertTrue(admin.canSendAlerts());
    }

    @Test
    public void childClasses_inheritIdAndNameFromUser() {
        User student = new Student("25-00001", "Sample Student", "BSIT 2114");
        assertEquals("25-00001", student.getId());
        assertEquals("Sample Student", student.getName());
    }

    @Test
    public void childClasses_keepTheirOwnExtraField() {
        Student student = new Student("25-00001", "Sample Student", "BSIT 2114");
        Teacher teacher = new Teacher("t-001", "Sample Teacher", "CICS 102");
        Administrator admin = new Administrator("a-001", "Sample Admin", "CICS");
        assertEquals("BSIT 2114", student.getCourseAndBlock());
        assertEquals("CICS 102", teacher.getAssignedRoom());
        assertEquals("CICS", admin.getDepartment());
    }
}
