// GET or POST /api/escalate-due   Authorization: Bearer <CRON_SECRET>
// Called every minute or two by a free pinger (see README). Sends any automatic re-alert that has come due.
// It only does what an admin already allowed: alerts whose autoEscalateMin is set, ACTIVE, within the round limits.
const crypto = require("crypto");
const { init } = require("../lib/auth");
const { escalateAlert, nextAutoDue, EscalationError } = require("../lib/escalate");

function sameSecret(a, b) {
  const x = Buffer.from(String(a)), y = Buffer.from(String(b));
  return x.length === y.length && crypto.timingSafeEqual(x, y);
}

module.exports = async (req, res) => {
  const secret = process.env.CRON_SECRET;
  if (!secret || secret.length < 16) return res.status(503).json({ error: "CRON_SECRET is not set (use at least 16 characters)." });
  const header = req.headers.authorization || "";
  if (!header.startsWith("Bearer ") || !sameSecret(header.slice(7), secret)) return res.status(401).json({ error: "Not allowed" });
  const admin = init();
  const db = admin.firestore();
  const env = { db, messaging: admin.messaging(), stamp: (ms) => admin.firestore.Timestamp.fromMillis(ms) };
  const out = [];
  try {
    const snap = await db.collection("alerts").where("status", "==", "ACTIVE").limit(25).get();
    for (const d of snap.docs) {
      const a = d.data(), due = nextAutoDue(a);
      if (!due || Date.now() < due) continue;
      try { out.push({ alertId: d.id, ...(await escalateAlert(env, d.id, { by: "auto", mode: "auto" })) }); }
      catch (e) { if (!(e instanceof EscalationError)) console.error("auto escalate failed", d.id, e); }
    }
    return res.status(200).json({ ok: true, checked: snap.size, escalated: out });
  } catch (e) {
    console.error("escalate-due failed", e);
    return res.status(500).json({ error: "Check failed." });
  }
};
