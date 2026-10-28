// Shared by the endpoints added after send.js: who is calling, and are they an admin in the roster?
const admin = require("firebase-admin");

function init() {
  if (!admin.apps.length) {
    const raw = process.env.FIREBASE_SERVICE_ACCOUNT;
    if (!raw) throw new Error("FIREBASE_SERVICE_ACCOUNT is not set");
    admin.initializeApp({ credential: admin.credential.cert(JSON.parse(raw)) });
  }
  return admin;
}

/** Returns { email, name } for a verified roster admin, or sends the error response and returns null. */
async function requireAdmin(req, res) {
  const header = req.headers.authorization || "";
  const idToken = header.startsWith("Bearer ") ? header.slice(7) : "";
  if (!idToken) { res.status(401).json({ error: "Not signed in" }); return null; }
  let decoded;
  try { decoded = await admin.auth().verifyIdToken(idToken); }
  catch (e) { res.status(401).json({ error: "Invalid sign-in" }); return null; }
  const email = (decoded.email || "").toLowerCase();
  if (!email || decoded.email_verified !== true) { res.status(403).json({ error: "Email not verified" }); return null; }
  const snap = await admin.firestore().doc("roster/" + email).get();
  if (!snap.exists || snap.get("role") !== "ADMIN") { res.status(403).json({ error: "Admins only" }); return null; }
  return { email, name: snap.get("name") || email };
}

module.exports = { init, requireAdmin };
