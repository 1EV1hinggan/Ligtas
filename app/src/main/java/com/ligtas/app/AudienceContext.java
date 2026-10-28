package com.ligtas.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything needed to work out who an alert reaches: the campus setup and the roster.
 * Loaded when the admin gets ready to send, then reused, so the Send step itself stays fast.
 */
public final class AudienceContext {

    private static AudienceContext cache;
    private static final long FRESH_MS = 2 * 60_000;

    public final CampusConfig config;            // null when no campus has been set up yet
    public final List<Audience.Member> members;
    public final String campusId;
    public final String configError;             // why the campus setup could not be read ("" = no problem)
    /** Above this many people the roster is not downloaded; reach comes from count queries instead. */
    public static final int LARGE_ROSTER = 1500;
    public boolean large = false;
    private final Map<String, AlertTracker.Reach> reachCache = new HashMap<>();
    private final java.util.Set<String> reachBusy = new java.util.HashSet<>();

    /**
     * How many people an audience reaches. Small roster: worked out here at once. Large roster: from counts,
     * so the first call returns null and onUpdate runs when the answer is in (call again then).
     */
    public AlertTracker.Reach reach(Audience aud, AudiencePlan plan, String sender, Runnable onUpdate) {
        if (!large) return AlertTracker.countAudience(members, plan, sender);
        String key = aud.label() + "|" + String.join(",", plan.targets);
        AlertTracker.Reach r = reachCache.get(key);
        if (r != null) return r;
        if (reachBusy.add(key)) {
            AlertTracker.estimateReach(aud, new AlertTracker.Callback<AlertTracker.Reach>() {
                @Override public void onSuccess(AlertTracker.Reach v) { reachCache.put(key, v); reachBusy.remove(key); if (onUpdate != null) onUpdate.run(); }
                @Override public void onError(String message) { reachBusy.remove(key); }
            });
        }
        return null;
    }

    private final long loadedAt = System.currentTimeMillis();

    private AudienceContext(String campusId, CampusConfig config, String configError, List<Audience.Member> members) {
        this.campusId = campusId;
        this.configError = configError;
        this.config = config;
        this.members = members;
    }

    /** The loaded context if it is recent, otherwise null. */
    public static AudienceContext fresh() {
        return cache != null && System.currentTimeMillis() - cache.loadedAt < FRESH_MS ? cache : null;
    }

    public static void invalidate() {
        cache = null;
    }

    /** Loads the roster and the campus setup. A missing or unreadable setup is not fatal (nobody gets skipped). */
    public static void load(String campusId, AlertTracker.Callback<AudienceContext> cb) {
        AlertTracker.countRoster(new AlertTracker.Callback<Integer>() {
            @Override public void onSuccess(Integer n) {
                if (n > LARGE_ROSTER) loadWith(campusId, new ArrayList<Audience.Member>(), true, cb);
                else loadSmall(campusId, cb);
            }
            @Override public void onError(String message) { loadSmall(campusId, cb); }
        });
    }

    private static void loadSmall(String campusId, AlertTracker.Callback<AudienceContext> cb) {
        AlertTracker.loadRoster(new AlertTracker.Callback<List<Audience.Member>>() {
            @Override public void onSuccess(List<Audience.Member> members) { loadWith(campusId, members, false, cb); }
            @Override public void onError(String message) { cb.onError(message); }
        });
    }

    private static void loadWith(String campusId, List<Audience.Member> members, boolean large, AlertTracker.Callback<AudienceContext> cb) {
        CampusStore.load(campusId, new AlertTracker.Callback<CampusConfig>() {
            @Override public void onSuccess(CampusConfig c) { finish(c, ""); }
            @Override public void onError(String message) { finish(null, message); }
            private void finish(CampusConfig c, String error) {
                String id = c != null ? c.id : (campusId == null ? "" : campusId);
                cache = new AudienceContext(id, c, error, members);
                cache.large = large;
                cb.onSuccess(cache);
            }
        });
    }
}
