package com.ligtas.app;

/** The privacy notice shown once at first sign-in (and again whenever VERSION is raised). */
public final class ConsentNotice {
    private ConsentNotice() { }

    /** Raise this number when the text changes materially: everyone is asked again. */
    public static final int VERSION = 2;

    public static final String TITLE = "Before you continue";

    public static final String TEXT =
            "Ligtas sends emergency alerts from campus administrators to your phone. "
          + "This notice follows the Data Privacy Act of 2012 (RA 10173).\n\n"
          + "WHAT WE KEEP\n"
          + "• Your school email, name, role, college, program, year level and building (from the roster your school prepared)\n"
          + "• For teachers: the programs and year levels you teach, so you receive alerts for those classes\n"
          + "• Any other details your administrator chose to keep on file, such as an employee number (ask them which)\n"
          + "• A notification token that identifies this phone\n"
          + "• For each alert: when it reached your phone and when you tapped \"I'm safe / Acknowledge\"\n\n"
          + "WHAT WE DO NOT DO\n"
          + "• We do not track your location\n"
          + "• We do not read your messages, contacts or files\n"
          + "• We do not show ads or sell your data\n\n"
          + "WHY\n"
          + "Only to send you emergency alerts that apply to you, and to let administrators see who has been reached "
          + "so they can help people who have not responded.\n\n"
          + "WHO SEES IT\n"
          + "Campus administrators. Your data is stored with Google Firebase, the service that delivers the alerts.\n\n"
          + "YOUR RIGHTS\n"
          + "You may ask an administrator to see, correct or remove your roster entry. Signing out stops alerts on this phone.\n\n"
          + "If you decline, you will be signed out and will not receive alerts through Ligtas.";
}
