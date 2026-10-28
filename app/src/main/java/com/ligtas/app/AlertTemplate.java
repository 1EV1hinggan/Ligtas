package com.ligtas.app;

/**
 * A pre-approved alert template. The wording is locked here in code.
 * The admin can only choose between the listed instruction variants,
 * pick an affected area, and add a short note (max NOTE_MAX characters).
 *
 * Every template has an English and a Modern Taglish version, kept side by side so they always match.
 * The admin never picks a language: both go out with the alert and each phone shows the one its owner chose.
 *
 * IMPORTANT: these messages are DRAFTS. Have your school's security or
 * guidance office review and approve the final wording (both languages) before any drill.
 */
public class AlertTemplate {
    public static final int NOTE_MAX = 80;

    private final String key;
    private final String label;
    private final String[] instructions;     // [0] is the default
    private final String titleTl;            // Taglish alert title
    private final String[] instructionsTl;   // same order and meaning as instructions
    private final boolean noteRequired;

    private AlertTemplate(String key, String label, String[] instructions, String titleTl, String[] instructionsTl,
                          boolean noteRequired) {
        this.key = key;
        this.label = label;
        this.instructions = instructions;
        this.titleTl = titleTl;
        this.instructionsTl = instructionsTl;
        this.noteRequired = noteRequired;
    }

    public String getKey() { return key; }
    public String getLabel() { return label; }
    public String[] getInstructions() { return instructions; }
    public boolean isNoteRequired() { return noteRequired; }

    /** Same order as the tiles on the admin screen. */
    public static AlertTemplate[] all() {
        return new AlertTemplate[]{
                new AlertTemplate("EARTHQUAKE", "Earthquake", new String[]{
                        "Earthquake. Drop, cover, and hold on. Wait for further instructions.",
                        "Shaking has stopped. Evacuate calmly to the open field. Do not use elevators."
                }, "LINDOL ALERT", new String[]{
                        "Lindol! Mag-drop, cover, at hold on. Hintayin ang susunod na instructions.",
                        "Tumigil na ang pagyanig. Kalmadong lumabas papunta sa open field. Huwag gumamit ng elevator."
                }, false),
                new AlertTemplate("SHOOTER", "Active Shooter", new String[]{
                        "Lockdown. Lock the door, stay quiet and out of sight. Wait for official instructions.",
                        "Threat reported nearby. Stay away from the area and follow staff instructions."
                }, "LOCKDOWN ALERT", new String[]{
                        "Lockdown. I-lock ang pinto, manahimik at magtago. Hintayin ang official instructions.",
                        "May banta sa malapit. Lumayo sa area at sundin ang instructions ng staff."
                }, false),
                new AlertTemplate("FIRE", "Fire", new String[]{
                        "Fire reported. Evacuate now through the nearest safe exit. Do not use elevators.",
                        "Fire reported nearby. Stay in your room and wait for further instructions."
                }, "SUNOG ALERT", new String[]{
                        "May sunog. Lumabas na agad sa pinakamalapit na safe exit. Huwag gumamit ng elevator.",
                        "May sunog sa malapit. Manatili sa room at hintayin ang susunod na instructions."
                }, false),
                new AlertTemplate("BOMB", "Bomb Threat", new String[]{
                        "Bomb threat reported. Evacuate calmly as instructed by staff. Do not touch suspicious objects.",
                        "Bomb threat reported. Stay away from the area and wait for official instructions."
                }, "BOMB THREAT ALERT", new String[]{
                        "May bomb threat. Kalmadong lumabas ayon sa instructions ng staff. Huwag hawakan ang mga kahina-hinalang gamit.",
                        "May bomb threat. Lumayo sa area at hintayin ang official instructions."
                }, false),
                new AlertTemplate("CHEMICAL", "Chemical Leaks", new String[]{
                        "Chemical leak reported. Move away from the area, cover your nose and mouth, and follow staff instructions.",
                        "Chemical leak reported. Stay indoors, keep windows closed, and wait for instructions."
                }, "CHEMICAL LEAK ALERT", new String[]{
                        "May chemical leak. Lumayo sa area, takpan ang ilong at bibig, at sundin ang instructions ng staff.",
                        "May chemical leak. Manatili sa loob, isara ang mga bintana, at hintayin ang instructions."
                }, false),
                new AlertTemplate("OTHERS", "Others", new String[]{
                        "Emergency reported. Follow staff instructions."
                }, "EMERGENCY ALERT", new String[]{
                        "May emergency. Sundin ang instructions ng staff."
                }, true)
        };
    }

    /** Factory: picks the right Alert subclass (polymorphism) for this template. */
    public Alert createAlert(String alertId, String area, int variantIndex, String note) {
        int i = (variantIndex >= 0 && variantIndex < instructions.length) ? variantIndex : 0;
        String instruction = instructions[i];
        if (note != null && !note.trim().isEmpty()) {
            instruction = instruction + " Note: " + note.trim();
        }
        switch (key) {
            case "EARTHQUAKE": return new EarthquakeAlert(alertId, area, instruction);
            case "SHOOTER":    return new LockdownAlert(alertId, area, instruction);
            case "FIRE":       return new FireAlert(alertId, area, instruction);
            case "BOMB":       return new BombThreatAlert(alertId, area, instruction);
            case "CHEMICAL":   return new ChemicalLeakAlert(alertId, area, instruction);
            default:           return new CustomAlert(alertId, area, instruction);
        }
    }

    /**
     * The same alert in Taglish: same severity, same message layout (it goes through the same Alert class), only the
     * title and instruction are Taglish. The admin's note is kept exactly as typed.
     */
    public Alert createAlertTl(String alertId, String area, int variantIndex, String note) {
        int i = (variantIndex >= 0 && variantIndex < instructionsTl.length) ? variantIndex : 0;
        String instruction = instructionsTl[i];
        if (note != null && !note.trim().isEmpty()) {
            instruction = instruction + " Paalala: " + note.trim();
        }
        final String title = titleTl;
        final String severity = createAlert(alertId, area, variantIndex, note).getSeverityLevel();
        return new Alert(alertId, "Whole campus".equals(area) ? "Buong campus" : area, instruction) {
            @Override public String getSeverityLevel() { return severity; }
            @Override public String getTitle() { return title; }
        };
    }
}
