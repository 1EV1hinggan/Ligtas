// Pure checks for what the app is allowed to ask the server to send. No Firebase here, so it is easy to test.

const TYPES = new Set(["ALERT", "ALL_CLEAR", "FALSE_ALARM"]);
// Only these data keys are forwarded to phones (everything the app sends today).
const KEYS = { title: 200, body: 600, title_tl: 200, body_tl: 600, incoming_alert_id: 80, type: 20, sent_at: 20, by: 120, ol: 2000, td: 2 };
const TOPIC = /^\/topics\/[A-Za-z0-9\-_.~%]{1,100}$/;
// "condition:'a' in topics && 'b' in topics", also with || and ( ). Nothing else.
const CONDITION = /^condition:[A-Za-z0-9\-_.~%'\s&|()]{1,500}$/;
const MAX_TTL = 2 * 60 * 60; // seconds

function validate(body) {
  if (!body || typeof body !== "object") return { error: "Body must be JSON." };
  const { target, data, ttl } = body;
  if (typeof target !== "string" || !(TOPIC.test(target) || CONDITION.test(target))) {
    return { error: "Target must be a topic or a topic condition." }; // single phone tokens are refused on purpose
  }
  if (!data || typeof data !== "object" || Array.isArray(data)) return { error: "Missing data." };
  const clean = {};
  for (const [k, v] of Object.entries(data)) {
    if (!(k in KEYS)) return { error: "Field not allowed: " + k };
    const s = v == null ? "" : String(v);
    if (s.length > KEYS[k]) return { error: "Field too long: " + k };
    clean[k] = s;
  }
  if (!clean.incoming_alert_id) return { error: "Missing incoming_alert_id." };
  if (!TYPES.has(clean.type || "ALERT")) return { error: "Unknown type." };
  if (!clean.title) return { error: "Missing title." };
  const t = Math.min(Math.max(parseInt(ttl, 10) || 0, 60), MAX_TTL);
  return { target, data: clean, ttl: t };
}

module.exports = { validate, MAX_TTL };
