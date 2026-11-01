// Runs api/send.js against a fake firebase-admin (no network).
const Module = require("module");
let sent = [];
const roster = { "boss@gmail.com": { role: "ADMIN", name: "Real Name", campus: "alangilan" }, "free@gmail.com": { role: "ADMIN", name: "No Campus" }, "kid@g.batstate-u.edu.ph": { role: "STUDENT" } };
const tokens = { good: { email: "boss@gmail.com", email_verified: true }, free: { email: "free@gmail.com", email_verified: true }, kid: { email: "kid@g.batstate-u.edu.ph", email_verified: true }, unver: { email: "boss@gmail.com", email_verified: false } };
const fake = {
  apps: [1], initializeApp() {}, credential: { cert: () => ({}) },
  auth: () => ({ verifyIdToken: async (t) => { if (!tokens[t]) throw new Error("bad"); return tokens[t]; } }),
  firestore: () => ({ doc: (p) => ({ get: async () => { const r = roster[p.split("/")[1]]; return { exists: !!r, get: (k) => r && r[k] }; } }) }),
  messaging: () => ({ send: async (m) => { sent.push(m); return "id1"; } }),
};
const orig = Module._load;
Module._load = function (req, ...a) { return req === "firebase-admin" ? fake : orig.call(this, req, ...a); };
const handler = require("./api/send.js");
const call = async (method, auth, body) => { let out; const res = { status(c) { out = { c }; return this; }, json(j) { out.j = j; return out; } };
  await handler({ method, headers: auth ? { authorization: "Bearer " + auth } : {}, body }, res); return out; };
const assert = require("assert");
(async () => {
  const d = { title: "FIRE", body: "go", incoming_alert_id: "a1" };
  const mk = (target, type) => ({ target, data: { ...d, type }, ttl: 1800 });
  assert.strictEqual((await call("POST", "good", mk("/topics/campus_alangilan", "ALERT"))).c, 200);
  assert.strictEqual((await call("POST", "good", mk("/topics/campus_lipa", "ALERT"))).c, 403);
  assert.strictEqual((await call("POST", "good", mk("/topics/role_student", "ALERT"))).c, 403);
  assert.strictEqual((await call("POST", "good", mk("/topics/ligtas_campus", "ALL_CLEAR"))).c, 200);
  assert.strictEqual((await call("POST", "free", mk("/topics/campus_lipa", "ALERT"))).c, 200);   // no campus set: not limited yet
  assert.strictEqual(sent.length, 3);
  console.log("handler campus tests: all passed (3 sent, 2 blocked)");
})();
