// Re-alerts the people who have not acknowledged an active alert. Pure logic: Firebase objects are passed in, so it can be tested.
//
// Rules (all enforced here, on the server, never trusted from the phone):
//   - the alert must exist, still be ACTIVE, and have a named recipient list (large audiences keep counts only)
//   - manual: at most one round per 45 seconds, at most 5 rounds in total
//   - auto:   only when the alert's autoEscalateMin has elapsed since the last round (or the send), at most 3 rounds
//   - only people who have NOT acknowledged are contacted, and only phones that still have a token
//   - the round is claimed inside a transaction, so two callers at the same moment cannot both send it

const MAX_MANUAL = 5, MAX_AUTO = 3, COOLDOWN_MS = 45 * 1000, TTL_SECONDS = 15 * 60, MAX_AGE_MS = 6 * 60 * 60 * 1000;

class EscalationError extends Error {
  constructor(status, message) { super(message); this.status = status; }
}

const millis = (t) => (t && typeof t.toMillis === "function" ? t.toMillis() : typeof t === "number" ? t : 0);
const clip = (s, n) => String(s == null ? "" : s).slice(0, n);

/** When the next automatic round is due, or 0 if none is scheduled. Shared rule: the app shows the same countdown. */
function nextAutoDue(a) {
  const min = Number(a.autoEscalateMin) || 0, rounds = Number(a.escalations) || 0;
  if (min <= 0 || rounds >= MAX_AUTO || a.status !== "ACTIVE") return 0;
  const base = rounds === 0 ? millis(a.sentAt) : millis(a.lastEscalatedAt);
  return base > 0 ? base + min * 60 * 1000 : 0;
}

async function escalateAlert(env, alertId, opts) {
  const { db, messaging, now = Date.now, stamp = (ms) => ms } = env;
  const by = opts.by || "", mode = opts.mode === "auto" ? "auto" : "manual";
  const alertRef = db.doc("alerts/" + alertId);
  const t = now();
  let alert, pending, round;

  await db.runTransaction(async (tx) => {
    const snap = await tx.get(alertRef);
    if (!snap.exists) throw new EscalationError(404, "Alert not found.");
    alert = snap.data();
    if ((alert.status || "ACTIVE") !== "ACTIVE") throw new EscalationError(409, "This alert has already ended.");
    const recips = Array.isArray(alert.recipients) ? alert.recipients : [];
    if (!recips.length) throw new EscalationError(422, "This alert has no named recipient list (large audiences keep counts only), so people cannot be re-alerted one by one.");
    const sentAt = millis(alert.sentAt);
    if (sentAt && t - sentAt > MAX_AGE_MS) throw new EscalationError(409, "This alert is more than 6 hours old.");
    const rounds = Number(alert.escalations) || 0, last = millis(alert.lastEscalatedAt);
    if (mode === "manual") {
      if (rounds >= MAX_MANUAL) throw new EscalationError(409, "Already re-alerted " + rounds + " times. Time to call or send someone.");
      if (last && t - last < COOLDOWN_MS) throw new EscalationError(429, "Wait a moment: people were re-alerted " + Math.round((t - last) / 1000) + " seconds ago.");
    } else {
      const due = nextAutoDue({ ...alert, status: "ACTIVE" });
      if (!due || t < due) throw new EscalationError(409, "Not due yet.");
    }
    const acks = await tx.get(alertRef.collection("acks"));
    const acked = new Set();
    acks.forEach((d) => { if (d.get("ackedAt")) acked.add(String(d.id).toLowerCase()); });
    pending = recips.filter((r) => r && r.email && !acked.has(String(r.email).toLowerCase()));
    if (!pending.length) return;                         // everyone answered: nothing to claim
    round = rounds + 1;
    tx.update(alertRef, { escalations: round, lastEscalatedAt: stamp(t), lastEscalatedBy: by || "auto", lastEscalatedCount: 0 });
  });

  if (!pending.length) return { round: Number(alert.escalations) || 0, targeted: 0, sent: 0, failed: 0, nobody: true };

  // Phones: tokens live on the roster, readable only by the server
  const refs = pending.map((p) => db.doc("roster/" + String(p.email).toLowerCase()));
  const docs = await db.getAll(...refs);
  const withToken = [];
  docs.forEach((d, i) => { const tok = d.exists ? d.get("fcmToken") : ""; if (tok) withToken.push({ email: pending[i].email, token: tok }); });

  const data = {
    title: clip("REMINDER · " + (alert.title || "Ligtas alert"), 200),
    body: clip((alert.message || "") + "\n\nYou have not acknowledged this alert yet. Open Ligtas and tap Acknowledge.", 600),
    incoming_alert_id: alertId,
    type: "ALERT",
    sent_at: String(millis(alert.sentAt) || t),
    by: "Admin",                       // recipients never see which admin sent it
    esc: String(round),
    td: "1",
  };
  // Taglish version, when the alert was saved with one: phones set to Taglish show this instead
  if (alert.titleTl && alert.messageTl) {
    data.title_tl = clip("PAALALA \u00B7 " + alert.titleTl, 200);
    data.body_tl = clip(alert.messageTl + "\n\nHindi mo pa na-a-acknowledge ang alert na ito. Buksan ang Ligtas at i-tap ang Acknowledge.", 600);
  }
  let sent = 0, failed = 0;
  for (let i = 0; i < withToken.length; i += 500) {
    const chunk = withToken.slice(i, i + 500);
    try {
      const res = await messaging.sendEach(chunk.map((c) => ({ token: c.token, data, android: { priority: "high", ttl: TTL_SECONDS * 1000 } })));
      sent += res.successCount; failed += res.failureCount;
    } catch (e) { failed += chunk.length; }
  }
  const noPhone = pending.length - withToken.length;

  // The record: one document per round, so every re-alert is accounted for
  await db.doc("alerts/" + alertId + "/escalations/" + round).set({
    round, at: stamp(t), by: by || "auto", mode, targeted: pending.length, noPhone, sent, failed,
    emails: pending.slice(0, 500).map((p) => String(p.email).toLowerCase()),
  });
  await alertRef.update({ lastEscalatedCount: sent });
  return { round, targeted: pending.length, sent, failed, noPhone };
}

module.exports = { escalateAlert, nextAutoDue, EscalationError, MAX_MANUAL, MAX_AUTO, COOLDOWN_MS };
