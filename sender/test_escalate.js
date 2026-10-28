// Runs lib/escalate.js against an in-memory fake of Firestore and Messaging (no network).
const assert = require("assert");
const { escalateAlert, nextAutoDue, EscalationError } = require("./lib/escalate");

function makeDb(docs) {
  const ts = (ms) => ({ toMillis: () => ms });
  const snapOf = (path) => ({ id: path.split("/").pop(), exists: path in docs, data: () => docs[path], get: (k) => (docs[path] || {})[k] });
  const ref = (path) => ({ path, collection: (n) => ({ path: path + "/" + n }), set: async (d) => { docs[path] = d; }, update: async (d) => { docs[path] = { ...docs[path], ...d }; } });
  const db = {
    doc: ref,
    getAll: async (...refs) => refs.map((r) => snapOf(r.path)),
    runTransaction: async (fn) => fn({
      get: async (r) => {
        if (docs[r.path] !== undefined || r.path.split("/").length % 2 === 0) return snapOf(r.path);
        const list = Object.keys(docs).filter((p) => p.startsWith(r.path + "/")).map(snapOf);
        return { forEach: (f) => list.forEach(f) };
      },
      update: (r, d) => { docs[r.path] = { ...docs[r.path], ...d }; },
    }),
  };
  return { db, ts };
}
const NOW = 1_800_000_000_000;
function setup(over = {}) {
  const docs = {
    "alerts/A1": { status: "ACTIVE", title: "FIRE", message: "Evacuate.", sentBy: "boss@x.com", sentByName: "Boss", sentAt: { toMillis: () => NOW - 6 * 60000 }, autoEscalateMin: 5,
      titleTl: "SUNOG ALERT", messageTl: "May sunog. Lumabas na.", recipients: [{ email: "a@s.edu", name: "A" }, { email: "b@s.edu", name: "B" }, { email: "c@s.edu", name: "C" }, { email: "d@s.edu", name: "D" }], ...over },
    "alerts/A1/acks/a@s.edu": { ackedAt: 1 },
    "alerts/A1/acks/b@s.edu": { deliveredAt: 1 },
    "roster/b@s.edu": { fcmToken: "tokB" }, "roster/c@s.edu": { fcmToken: "tokC" }, "roster/d@s.edu": { name: "no phone" },
  };
  const { db } = makeDb(docs); const sent = [];
  const messaging = { sendEach: async (m) => { sent.push(...m); return { successCount: m.length, failureCount: 0 }; } };
  return { docs, env: { db, messaging, now: () => NOW }, sent };
}
const fails = async (p, status) => { try { await p; assert.fail("should have thrown"); } catch (e) { assert(e instanceof EscalationError, String(e)); assert.strictEqual(e.status, status, e.message); } };

(async () => {
  // manual round: A acknowledged, so B, C and D are targeted; D has no phone
  let s = setup();
  let r = await escalateAlert(s.env, "A1", { by: "boss@x.com", mode: "manual" });
  assert.deepStrictEqual([r.round, r.targeted, r.sent, r.noPhone], [1, 3, 2, 1]);
  assert.deepStrictEqual(s.sent.map((m) => m.token).sort(), ["tokB", "tokC"]);
  assert.strictEqual(s.sent[0].data.esc, "1"); assert(s.sent[0].data.title.startsWith("REMINDER"));
  assert.strictEqual(s.sent[0].data.by, "Admin");
  assert(s.sent[0].data.title_tl.startsWith("PAALALA")); assert(s.sent[0].data.body_tl.includes("May sunog."));
  assert.strictEqual(s.sent[0].data.incoming_alert_id, "A1"); assert.strictEqual(s.sent[0].data.type, "ALERT");
  assert.strictEqual(s.docs["alerts/A1"].escalations, 1); assert.strictEqual(s.docs["alerts/A1"].lastEscalatedCount, 2);
  assert.strictEqual(s.docs["alerts/A1/escalations/1"].mode, "manual");
  // a second tap right away is refused, and nothing more is sent
  await fails(escalateAlert(s.env, "A1", { by: "x", mode: "manual" }), 429);
  assert.strictEqual(s.sent.length, 2);
  // auto before it is due is refused; once due it works, and only 3 auto rounds exist
  s = setup({ autoEscalateMin: 10 });
  await fails(escalateAlert(s.env, "A1", { mode: "auto" }), 409);
  s = setup();
  r = await escalateAlert(s.env, "A1", { mode: "auto" }); assert.strictEqual(r.round, 1);
  await fails(escalateAlert(s.env, "A1", { mode: "auto" }), 409);           // next one is 5 min after the last round
  s = setup({ escalations: 3, lastEscalatedAt: { toMillis: () => NOW - 3600000 } });
  await fails(escalateAlert(s.env, "A1", { mode: "auto" }), 409);           // auto stops after 3 rounds
  r = await escalateAlert(s.env, "A1", { mode: "manual" }); assert.strictEqual(r.round, 4);   // the admin still can
  s = setup({ escalations: 5, lastEscalatedAt: { toMillis: () => NOW - 3600000 } });
  await fails(escalateAlert(s.env, "A1", { mode: "manual" }), 409);         // manual stops after 5
  // ended alert, missing alert, no named list, too old
  await fails(escalateAlert(setup({ status: "ALL_CLEAR" }).env, "A1", { mode: "manual" }), 409);
  await fails(escalateAlert(setup().env, "ZZ", { mode: "manual" }), 404);
  await fails(escalateAlert(setup({ recipients: [] }).env, "A1", { mode: "manual" }), 422);
  await fails(escalateAlert(setup({ sentAt: { toMillis: () => NOW - 7 * 3600000 } }).env, "A1", { mode: "manual" }), 409);
  // everyone answered: nothing is sent and no round is used
  s = setup(); ["b", "c", "d"].forEach((k) => { s.docs[`alerts/A1/acks/${k}@s.edu`] = { ackedAt: 1 }; });
  r = await escalateAlert(s.env, "A1", { mode: "manual" });
  assert.strictEqual(r.targeted, 0); assert.strictEqual(s.sent.length, 0); assert.strictEqual(s.docs["alerts/A1"].escalations, undefined);
  // the shared countdown rule
  assert.strictEqual(nextAutoDue({ status: "ACTIVE", autoEscalateMin: 5, sentAt: { toMillis: () => 1000 } }), 301000);
  assert.strictEqual(nextAutoDue({ status: "ACTIVE", autoEscalateMin: 0, sentAt: { toMillis: () => 1000 } }), 0);
  assert.strictEqual(nextAutoDue({ status: "ALL_CLEAR", autoEscalateMin: 5, sentAt: { toMillis: () => 1000 } }), 0);
  console.log("escalate: all checks passed");
})().catch((e) => { console.error(e); process.exit(1); });
