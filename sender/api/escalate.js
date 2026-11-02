// POST /api/escalate   Authorization: Bearer <Firebase ID token of a signed-in Ligtas admin>
// Body: { "alertId": "A-1760000000000", "mode": "manual" | "auto" }
// Re-alerts the people on that alert's recipient list who have not acknowledged it yet.
const { init, requireAdmin } = require("../lib/auth");
const { escalateAlert, EscalationError } = require("../lib/escalate");

module.exports = async (req, res) => {
  if (req.method !== "POST") return res.status(405).json({ error: "POST only" });
  const admin = init();
  const who = await requireAdmin(req, res);
  if (!who) return;
  const body = typeof req.body === "string" ? safeJson(req.body) : req.body;
  const alertId = body && typeof body.alertId === "string" ? body.alertId : "";
  if (!/^[A-Za-z0-9_-]{1,80}$/.test(alertId)) return res.status(400).json({ error: "Missing or invalid alertId." });
  const mode = body.mode === "auto" ? "auto" : "manual";
  try {
    const env = { db: admin.firestore(), messaging: admin.messaging(), stamp: (ms) => admin.firestore.Timestamp.fromMillis(ms) };
    const r = await escalateAlert(env, alertId, { by: who.email, mode, adminCampus: who.campus });
    return res.status(200).json({ ok: true, ...r });
  } catch (e) {
    if (e instanceof EscalationError) return res.status(e.status).json({ error: e.message });
    console.error("escalate failed", e);
    return res.status(500).json({ error: "Could not re-alert." });
  }
};

function safeJson(s) { try { return JSON.parse(s); } catch (e) { return null; } }
