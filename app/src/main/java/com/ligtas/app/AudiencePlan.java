package com.ligtas.app;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * The final answer to "who gets this alert, right now": where to push, which program/year groups are
 * online this week (their phones ignore the alert), and who is counted as an expected recipient.
 */
public final class AudiencePlan {

    /** Keep the push payload small (FCM allows 4 KB). Past this, nobody is skipped. */
    private static final int MAX_ONLINE_LIST_CHARS = 2500;

    public final Audience audience;
    public final List<String> targets;
    public final Set<String> onlineGroups;   // never null

    public AudiencePlan(Audience audience, List<String> targets, Set<String> onlineGroups) {
        this.audience = audience;
        this.targets = targets;
        String csv = String.join(",", onlineGroups);
        this.onlineGroups = csv.length() > MAX_ONLINE_LIST_CHARS ? Collections.<String>emptySet() : onlineGroups;
    }

    /** The groups to skip, comma separated, to put in the push message ("" = skip nobody). */
    public String onlineCsv() {
        return String.join(",", onlineGroups);
    }

    public boolean includes(Audience.Member m) {
        if (!audience.inGroup(m)) return false;
        if (onlineGroups.isEmpty() || !m.isStudentInGroup()) return true;
        return !onlineGroups.contains(CampusConfig.groupKey(m.program, m.yearLevel));
    }
}
