// POST /api/send   Authorization: Bearer <Firebase ID token of a signed-in Ligtas admin>
// Body: { "target": "/topics/x" | "condition:...", "data": { title, body, incoming_alert_id, type, ... }, "ttl": 1800 }
const admin = require("firebase-admin");
const { validate } = require("../lib/validate");

if (!admin.apps.length) {
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT;
  if (!raw) throw new Error("FIREBASE_SERVICE_ACCOUNT is not set");
  admin.initializeApp({ credential: admin.credential.cert(JSON.parse(raw)) });
}

// Best-effort brake against a stolen admin login spamming everyone (resets when the server restarts).
const recent = new Map();
function tooMany(email) {
  const now = Date.now();
  const list = (recent.get(email) || []).filter((t) => now - t < 60000);
  list.push(now);
  recent.set(email, list);
  return list.length > 40;
}

module.exports = async (req, res) => {
  if (req.method !== "POST") return res.status(405).json({ error: "POST only" });

  // 1. Who is calling? Must be a verified Google sign-in for this Firebase project.
  const header = req.headers.authorization || "";
  const idToken = header.startsWith("Bearer ") ? header.slice(7) : "";
  if (!idToken) return res.status(401).json({ error: "Not signed in" });
  let decoded;
  try {
    decoded = await admin.auth().verifyIdToken(idToken);
  } catch (e) {
    return res.status(401).json({ error: "Invalid sign-in" });
  }
  const email = (decoded.email || "").toLowerCase();
  if (!email || decoded.email_verified !== true) return res.status(403).json({ error: "Email not verified" });

  // 2. Are they an admin? Same rule as the Firestore rules: roster/<email>.role == ADMIN
  const snap = await admin.firestore().doc("roster/" + email).get();
  if (!snap.exists || snap.get("role") !== "ADMIN") return res.status(403).json({ error: "Admins only" });
  if (tooMany(email)) return res.status(429).json({ error: "Too many alerts, slow down" });

  // 3. Is the request one we are willing to send?
  const v = validate(typeof req.body === "string" ? safeJson(req.body) : req.body);
  if (v.error) return res.status(400).json({ error: v.error });

  // Recipients (students, teachers) only ever see "Admin", never which admin sent it, and the phone cannot choose
  // otherwise. Who really sent it is kept in the alert record, which only admins can read.
  v.data.by = "Admin";

  // 4. Send.
  const message = {
    data: v.data,
    android: { priority: "high", ttl: v.ttl * 1000 },
  };
  if (v.target.startsWith("condition:")) message.condition = v.target.slice("condition:".length);
  else message.topic = v.target.slice("/topics/".length);

  try {
    const id = await admin.messaging().send(message);
    return res.status(200).json({ ok: true, id });
  } catch (e) {
    console.error("send failed", e.code, e.message);
    return res.status(502).json({ error: "FCM refused the message", code: e.code || "" });
  }
};

function safeJson(s) { try { return JSON.parse(s); } catch (e) { return null; } }
