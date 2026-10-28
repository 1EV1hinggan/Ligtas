// Runs api/send.js against a fake firebase-admin (no network).
const Module = require("module");
let sent = [];
const roster = { "boss@gmail.com": { role: "ADMIN", name: "Real Name" }, "kid@g.batstate-u.edu.ph": { role: "STUDENT" } };
const tokens = { good: { email: "boss@gmail.com", email_verified: true }, kid: { email: "kid@g.batstate-u.edu.ph", email_verified: true }, unver: { email: "boss@gmail.com", email_verified: false } };
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
  const body = { target: "/topics/ligtas_campus", data: { title: "FIRE", body: "go", incoming_alert_id: "a1", by: "FAKE" }, ttl: 1800 };
  assert.strictEqual((await call("GET", "good", body)).c, 405);
  assert.strictEqual((await call("POST", null, body)).c, 401);
  assert.strictEqual((await call("POST", "nope", body)).c, 401);
  assert.strictEqual((await call("POST", "unver", body)).c, 403);
  assert.strictEqual((await call("POST", "kid", body)).c, 403);
  assert.strictEqual((await call("POST", "good", { ...body, target: "tok123" })).c, 400);
  const r = await call("POST", "good", body);
  assert.strictEqual(r.c, 200);
  assert.strictEqual(sent[0].topic, "ligtas_campus");
  assert.strictEqual(sent[0].data.by, "Admin"); // recipients only ever see "Admin", and the phone cannot choose otherwise
  assert.strictEqual(sent[0].android.ttl, 1800000);
  assert.strictEqual(sent[0].android.priority, "high");
  console.log("handler tests: all passed (" + sent.length + " message sent, 6 refused)");
})();
