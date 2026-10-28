// The two escalation endpoints against a fake firebase-admin: who may call them.
const Module = require("module"), assert = require("assert");
const roster = { "boss@gmail.com": { role: "ADMIN", name: "Boss" }, "kid@s.edu": { role: "STUDENT" } };
const tokens = { good: { email: "boss@gmail.com", email_verified: true }, kid: { email: "kid@s.edu", email_verified: true } };
let queried = 0;
const fs = () => ({
  doc: (p) => ({ get: async () => { const r = roster[p.split("/")[1]]; return { exists: !!r, get: (k) => r && r[k] }; } }),
  collection: () => ({ where: () => ({ limit: () => ({ get: async () => { queried++; return { size: 0, docs: [] }; } }) }) }),
});
fs.Timestamp = { fromMillis: (m) => m };
const fake = { apps: [1], initializeApp() {}, credential: { cert: () => ({}) },
  auth: () => ({ verifyIdToken: async (t) => { if (!tokens[t]) throw new Error("bad"); return tokens[t]; } }),
  firestore: fs, messaging: () => ({ sendEach: async () => ({ successCount: 0, failureCount: 0 }) }) };
const orig = Module._load; Module._load = function (r, ...a) { return r === "firebase-admin" ? fake : orig.call(this, r, ...a); };
const esc = require("./api/escalate.js"), due = require("./api/escalate-due.js");
const call = async (h, method, auth, body) => { let out; const res = { status(c) { out = { c }; return this; }, json(j) { out.j = j; return out; } };
  await h({ method, headers: auth ? { authorization: "Bearer " + auth } : {}, body }, res); return out; };
(async () => {
  assert.strictEqual((await call(esc, "GET", "good", {})).c, 405);
  assert.strictEqual((await call(esc, "POST", null, { alertId: "A-1" })).c, 401);
  assert.strictEqual((await call(esc, "POST", "kid", { alertId: "A-1" })).c, 403);
  assert.strictEqual((await call(esc, "POST", "good", { alertId: "../x" })).c, 400);
  delete process.env.CRON_SECRET;
  assert.strictEqual((await call(due, "GET", "x", null)).c, 503);
  process.env.CRON_SECRET = "0123456789abcdef-secret";
  assert.strictEqual((await call(due, "GET", null, null)).c, 401);
  assert.strictEqual((await call(due, "GET", "wrong", null)).c, 401);
  const ok = await call(due, "GET", "0123456789abcdef-secret", null);
  assert.strictEqual(ok.c, 200); assert.strictEqual(queried, 1);
  console.log("escalate http: all checks passed");
})().catch((e) => { console.error(e); process.exit(1); });
