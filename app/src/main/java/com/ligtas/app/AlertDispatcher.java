package com.ligtas.app;

import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sending an alert: the repeat-send cooldown, the audience lookup, saving the record and pushing the message.
 * Moved out of MainActivity; the behaviour is unchanged. The one rule that matters most is kept as it was:
 * an emergency is never held up by record-keeping or by a slow network lookup.
 */
final class AlertDispatcher {

    /** What the dispatcher needs from the screen that owns it. */
    interface Host {
        boolean hasTemplate();                   // is an emergency type selected?
        Alert buildAlert(String alertId);
        Alert buildAlertTl(String alertId);      // the same alert in Taglish
        Audience audience();
        User user();
        String userEmail();
        String campusId();
        void onRecorded(String alertId);         // remember it as "the last alert" and refresh the Status button
        void showStatus(String alertId);         // open the live status sheet
        void onDispatched();                     // the screen resets its selection, preventing accidental repeats
    }

    /** Minimum gap before a second alert is sent without asking "are you sure?". */
    private static final long COOLDOWN_MS = 60_000;

    private final AppCompatActivity activity;
    private final Host host;
    private long lastSentLocal = 0; // when THIS phone last sent an alert (for the send cooldown)

    AlertDispatcher(AppCompatActivity activity, Host host) {
        this.activity = activity;
        this.host = host;
    }

    /**
     * Send cooldown: a second alert within one minute of the last one (from this phone or any other admin)
     * asks for confirmation first. It never blocks: a real second emergency can still go out.
     */
    void dispatch() {
        if (!host.hasTemplate()) return;

        long sinceLocal = System.currentTimeMillis() - lastSentLocal;
        if (lastSentLocal > 0 && sinceLocal < COOLDOWN_MS) {
            confirmRepeat("you", sinceLocal);
            return;
        }

        // Check what other admins sent, but never hold up an emergency for it: go after 1.5 s at the latest
        final AtomicBoolean decided = new AtomicBoolean(false);
        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable go = () -> { if (decided.compareAndSet(false, true)) sendNow(); };
        handler.postDelayed(go, 1500);
        AlertTracker.latestAlert(new AlertTracker.Callback<AlertTracker.Latest>() {
            @Override public void onSuccess(AlertTracker.Latest l) {
                if (decided.get()) return;
                long age = l == null || l.sentAtMillis <= 0 ? Long.MAX_VALUE : System.currentTimeMillis() - l.sentAtMillis;
                if (age < COOLDOWN_MS) {
                    if (decided.compareAndSet(false, true)) {
                        handler.removeCallbacks(go);
                        confirmRepeat(l.sentBy, age);
                    }
                } else {
                    handler.removeCallbacks(go);
                    go.run();
                }
            }
            @Override public void onError(String message) {
                handler.removeCallbacks(go);
                go.run();
            }
        });
    }

    private void confirmRepeat(String who, long ageMs) {
        long seconds = Math.max(0, ageMs / 1000);
        SheetUi.prompt(activity, "Another alert was just sent",
                "An alert was sent " + seconds + " seconds ago by " + who
                        + ". Sending again so soon may be a duplicate.\n\nSend anyway?",
                new String[]{"Send anyway", "Cancel"}, new int[]{PromptKind.DANGER, PromptKind.GHOST}, true,
                i -> { if (i == 0) sendNow(); }, null);
    }

    /** Gets the roster and weekly schedule (if the audience needs them) without ever holding up an emergency. */
    private void sendNow() {
        if (!host.hasTemplate()) return;
        final Audience aud = host.audience();
        AudienceContext ready = AudienceContext.fresh();
        if (ready != null || !aud.usesSchedule()) {
            sendWith(aud, ready);
            return;
        }
        // Not loaded yet: wait at most 1.2 s, then send to the whole campus rather than delay
        final AtomicBoolean fired = new AtomicBoolean(false);
        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable fallback = () -> { if (fired.compareAndSet(false, true)) sendWith(aud, null); };
        handler.postDelayed(fallback, 1200);
        AudienceContext.load(host.campusId(), new AlertTracker.Callback<AudienceContext>() {
            @Override public void onSuccess(AudienceContext ctx) {
                handler.removeCallbacks(fallback);
                if (fired.compareAndSet(false, true)) sendWith(aud, ctx);
            }
            @Override public void onError(String message) {
                handler.removeCallbacks(fallback);
                if (fired.compareAndSet(false, true)) sendWith(aud, null);
            }
        });
    }

    private void sendWith(Audience aud, AudienceContext ctx) {
        if (!host.hasTemplate()) return;

        final User user = host.user();
        final String userEmail = host.userEmail();
        AlertService service = new AlertService();
        final long sentAt = System.currentTimeMillis();
        final Alert newAlert = host.buildAlert("A-" + sentAt);
        final Alert newAlertTl = host.buildAlertTl(newAlert.getAlertId());

        if (!service.sendAlert(user, newAlert)) {
            Toast.makeText(activity, "DENIED: Insufficient permissions.", Toast.LENGTH_SHORT).show();
            return;
        }
        lastSentLocal = sentAt;

        // Where to push and which groups are online right now (no campus setup: skip nobody)
        final AudiencePlan plan = aud.plan(ctx == null ? null : ctx.config, sentAt);
        final List<String> targets = plan.targets;
        final boolean trackDelivery = ctx != null && !ctx.large
                && AlertTracker.countAudience(ctx.members, plan, userEmail).reachable <= 1000;
        final List<Audience.Member> recipientSource = ctx != null && !ctx.large ? ctx.members : null;

        // The alert itself must never be held up by record-keeping:
        // push right after the record is saved, or after 1.5 s at the latest.
        final AtomicBoolean pushed = new AtomicBoolean(false);
        Runnable pushNow = () -> {
            if (pushed.compareAndSet(false, true)) {
                Map<String, String> data = new HashMap<>();
                data.put("title", newAlert.getTitle());
                data.put("body", newAlert.getMessage());
                data.put("title_tl", newAlertTl.getTitle());       // each phone shows the language its owner chose
                data.put("body_tl", newAlertTl.getMessage());
                data.put("incoming_alert_id", newAlert.getAlertId());
                data.put("type", LigtasMessagingService.TYPE_ALERT);
                data.put("sent_at", String.valueOf(sentAt));
                data.put("by", user.getName());
                if (!plan.onlineCsv().isEmpty()) data.put("ol", plan.onlineCsv()); // phones in these groups stay quiet
                if (trackDelivery) data.put("td", "1");   // small audiences only: phones report "delivered" (one write each)
                for (String target : targets) {
                    new FcmSender().send(activity, target, data, FcmSender.ALERT_TTL_SECONDS);
                }
            }
        };

        AlertTracker.saveAlert(newAlert, userEmail, user.getName(), plan,
                new AlertTracker.Callback<Void>() {
            @Override public void onSuccess(Void value) {
                pushNow.run();
                host.onRecorded(newAlert.getAlertId());
                AlertTracker.storeRecipients(newAlert.getAlertId(), userEmail, plan, recipientSource);
                EscalationFeed.applyDefault(newAlert.getAlertId(), newAlertTl.getTitle(), newAlertTl.getMessage());   // re-alert people who have not answered (admin can change it on the status sheet)
                host.showStatus(newAlert.getAlertId());
            }
            @Override public void onError(String message) {
                pushNow.run();
                Toast.makeText(activity,
                        "Alert sent, but tracking is unavailable: " + message, Toast.LENGTH_LONG).show();
            }
        });
        new Handler(Looper.getMainLooper()).postDelayed(pushNow, 1500);

        Toast.makeText(activity, "DISPATCHED: " + newAlert.getTitle(), Toast.LENGTH_SHORT).show();
        host.onDispatched();
    }
}
