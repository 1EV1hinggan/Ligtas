package com.ligtas.app;

import android.text.TextUtils;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The admin's live alert status sheet and the "All clear / False alarm" flow that ends an alert.
 * Moved out of MainActivity; the behaviour is unchanged.
 */
final class StatusController {
    private final AppCompatActivity activity;
    private final Supplier<String> userEmail;
    private final Supplier<String> userName;

    StatusController(AppCompatActivity activity, Supplier<String> userEmail, Supplier<String> userName) {
        this.activity = activity;
        this.userEmail = userEmail;
        this.userName = userName;
    }

    /** Live view for the admin: delivered, acknowledged, who is still waiting, average response time. */
    void show(String alertId) {
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.getBehavior().setSkipCollapsed(true);

        // Compose draws the sheet from this state; the live Firestore watchers below just keep filling it
        final AlertStatusState st = new AlertStatusState();
        final String[] titleHolder = {""};

        AlertTracker.Watcher watcher = new AlertTracker.Watcher(alertId, new AlertTracker.Callback<AlertTracker.Status>() {
            @Override public void onSuccess(AlertTracker.Status s) {
                st.setTitle(s.title.isEmpty() ? "Alert status" : s.title);
                titleHolder[0] = s.title;
                st.setMessage(s.message);
                StringBuilder meta = new StringBuilder();
                if (s.sentAtMillis > 0) meta.append("Sent ").append(TimeText.when(activity, s.sentAtMillis));
                if (!s.sentBy.isEmpty()) meta.append(meta.length() > 0 ? " by " : "Sent by ").append(s.sentBy);
                if (!s.audience.isEmpty()) meta.append("\nTo: ").append(s.audience);
                if ("ALL_CLEAR".equals(s.state) || "FALSE_ALARM".equals(s.state)) {
                    meta.append("\n").append("ALL_CLEAR".equals(s.state) ? "Ended: all clear" : "Ended: false alarm");
                    if (s.resolvedAtMillis > 0) meta.append(" at ").append(TimeText.when(activity, s.resolvedAtMillis));
                    st.setCanResolve(false);
                } else {
                    meta.append("\nStill active");
                    st.setCanResolve(true);
                }
                st.setMeta(meta.toString());
                st.setExpected(s.expected);
                st.setDelivered(s.delivered);
                st.setAcknowledged(s.acknowledged);
                st.setAverage(s.avgSeconds >= 0
                        ? "Average response time: " + TimeText.seconds(s.avgSeconds)
                        : "Average response time: waiting for the first acknowledgment");
                if (s.expected == 0) {
                    st.setPending("No registered recipients yet.");
                } else if (s.pending.isEmpty()) {
                    st.setPending("Everyone has acknowledged.");
                } else {
                    st.setPending(TextUtils.join("\n", s.pending));
                }
            }
            @Override public void onError(String message) {
                st.setPending("Live status unavailable: " + message);
            }
        });
        // Room reports from teachers, live, most urgent first
        final com.google.firebase.firestore.ListenerRegistration roomsReg = AlertTracker.watchRooms(alertId, new AlertTracker.Callback<List<RoomReport>>() {
            @Override public void onSuccess(List<RoomReport> list) {
                StringBuilder sb = new StringBuilder(RoomReport.summary(list));
                for (RoomReport r : list) sb.append("\n\n").append(r.line());
                st.setRooms(sb.toString());
            }
            @Override public void onError(String message) { st.setRooms("Room reports unavailable: " + message); }
        });
        final EscalationFeed feed = new EscalationFeed(alertId, st);
        feed.start();
        dialog.setOnDismissListener(d -> { watcher.stop(); roomsReg.remove(); feed.stop(); });

        AlertStatusListener listener = new AlertStatusListener() {
            @Override public void onAllClear() { confirmResolve(alertId, "ALL_CLEAR", titleHolder[0], dialog); }
            @Override public void onFalseAlarm() { confirmResolve(alertId, "FALSE_ALARM", titleHolder[0], dialog); }
            @Override public void onClose() { dialog.dismiss(); }
            @Override public void onEscalate() { confirmEscalate(alertId, st); }
            @Override public void onAutoChange(int minutes) {
                feed.setAuto(minutes, new AlertTracker.Callback<Void>() {
                    @Override public void onSuccess(Void value) { }
                    @Override public void onError(String message) {
                        Toast.makeText(activity, "Could not change the timer: " + message, Toast.LENGTH_LONG).show();
                    }
                });
            }
            @Override public void onAutoDue() { escalate(alertId, st, true); }
        };
        AlertStatusBridge.attach(dialog, activity, st, listener);
        SheetUi.makeTransparent(dialog); // keep the dark glass look: no white box behind the Compose sheet
        dialog.show();
    }

    /** The admin taps "Re-alert": confirm, then the server contacts only the people who have not answered. */
    private void confirmEscalate(String alertId, AlertStatusState st) {
        if (st.getEscBusy()) return;
        final int waiting = Math.max(0, st.getExpected() - st.getAcknowledged());
        SheetUi.prompt(activity, "Re-alert " + waiting + (waiting == 1 ? " person?" : " people?"),
                "They get a REMINDER notification that buzzes again. People who already acknowledged are not contacted.",
                new String[]{"Re-alert", "Cancel"}, new int[]{PromptKind.PRIMARY, PromptKind.GHOST}, true,
                i -> { if (i == 0) escalate(alertId, st, false); }, null);
    }

    private void escalate(String alertId, AlertStatusState st, boolean auto) {
        if (st.getEscBusy()) return;
        st.setEscBusy(true);
        if (!auto) st.setEscNote("");
        EscalationClient.escalate(activity, alertId, auto, new AlertTracker.Callback<EscalationClient.Result>() {
            @Override public void onSuccess(EscalationClient.Result r) {
                st.setEscBusy(false);
                st.setEscNoteError(false);
                if (r.targeted == 0) {
                    st.setEscNote("Everyone has answered. Nobody to re-alert.");
                } else {
                    StringBuilder sb = new StringBuilder(auto ? "Auto re-alert sent to " : "Re-alert sent to ").append(r.sent)
                            .append(r.sent == 1 ? " person" : " people");
                    if (r.noPhone > 0) sb.append(". ").append(r.noPhone).append(r.noPhone == 1 ? " has" : " have").append(" no phone signed in");
                    if (r.failed > 0) { sb.append(". ").append(r.failed).append(" could not be reached"); st.setEscNoteError(true); }
                    st.setEscNote(sb.toString());
                }
            }
            @Override public void onError(String message) {
                st.setEscBusy(false);
                // an automatic attempt that lost the race to the server's own timer is not worth showing
                if (auto) return;
                st.setEscNoteError(true);
                st.setEscNote(message);
            }
        });
    }

    /** Ends an alert for everyone: the message goes to the whole campus, and the alert cannot be ended twice. */
    private void confirmResolve(String alertId, String state, String alertTitle, BottomSheetDialog statusDialog) {
        final boolean falseAlarm = "FALSE_ALARM".equals(state);
        final String label = alertTitle == null || alertTitle.isEmpty() ? "The earlier alert" : alertTitle.toLowerCase(Locale.US);
        final String title = falseAlarm ? "FALSE ALARM" : "ALL CLEAR";
        final String body = falseAlarm
                ? "The earlier alert (" + label + ") was sent in error. There is no emergency. Please disregard it and resume normal activities."
                : "The emergency (" + label + ") has ended. It is safe to resume normal activities unless school officials tell you otherwise.";

        final String bodyTl = falseAlarm
                ? "Nagkamali ang naunang alert (" + label + "). Walang emergency. Huwag na itong pansinin at ituloy ang normal na activities."
                : "Tapos na ang emergency (" + label + "). Ligtas nang bumalik sa normal na activities maliban kung iba ang sabihin ng school officials.";

        SheetUi.prompt(activity, falseAlarm ? "Send false-alarm message?" : "Send all-clear message?",
                "This goes to everyone who received the alert and ends it. It cannot be undone.\n\n" + title + "\n" + body,
                new String[]{"Send", "Cancel"}, new int[]{PromptKind.DANGER, PromptKind.GHOST}, true, choice -> {
                    if (choice != 0) return;
                    AlertTracker.resolveAlert(alertId, state, userEmail.get(), body, new AlertTracker.Callback<Void>() {
                            @Override public void onSuccess(Void value) {
                                Map<String, String> data = new HashMap<>();
                                data.put("title", title);
                                data.put("body", body);
                                data.put("title_tl", title);          // "ALL CLEAR" / "FALSE ALARM" read the same in Taglish
                                data.put("body_tl", bodyTl);
                                data.put("incoming_alert_id", alertId);
                                data.put("type", state);
                                data.put("sent_at", String.valueOf(System.currentTimeMillis()));
                                data.put("by", userName.get());
                                // Same people who got the alert (older alerts without a saved audience go campus-wide)
                                AlertTracker.getRouting(alertId, new AlertTracker.Callback<AlertTracker.Routing>() {
                                    @Override public void onSuccess(AlertTracker.Routing r) {
                                        if (r.targets.isEmpty()) r.targets.add("/topics/" + Topics.CAMPUS);
                                        if (!r.onlineGroups.isEmpty()) data.put("ol", r.onlineGroups);
                                        for (String target : r.targets) {
                                            new FcmSender().send(activity, target, data,
                                                    FcmSender.RESOLUTION_TTL_SECONDS);
                                        }
                                    }
                                    @Override public void onError(String message) {
                                        new FcmSender().send(activity, "/topics/" + Topics.CAMPUS, data,
                                                FcmSender.RESOLUTION_TTL_SECONDS);
                                    }
                                });
                                Toast.makeText(activity, title + " sent.", Toast.LENGTH_SHORT).show();
                            }
                            @Override public void onError(String message) {
                                Toast.makeText(activity,
                                        "Not sent: " + message + " (it may already have been ended)", Toast.LENGTH_LONG).show();
                            }
                        });
                }, null);
    }
}
