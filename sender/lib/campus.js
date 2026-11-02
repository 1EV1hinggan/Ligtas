// Campus check: an admin who belongs to a campus may only alert that campus. Pure logic, no Firebase, easy to test.
//
// The phones join push topics named from the roster ("campus_alangilan", "role_student", "teach_bsit_2" ...).
// So the target of an alert tells us which campus it is aimed at.

// Same rule as Slug.of() in the Android app: "BSU Alangilan" -> "bsu_alangilan"
const slug = (s) => String(s == null ? "" : s).toLowerCase().replace(/[^a-z0-9]+/g, "_").replace(/^_+|_+$/g, "");

/** All topic names mentioned in a target: "/topics/x" or "condition:'a' in topics && 'b' in topics". */
function topicsOf(target) {
  if (target.startsWith("/topics/")) return [target.slice(8)];
  const found = [], re = /'([^']+)'/g; let m;
  while ((m = re.exec(target))) found.push(m[1]);
  return found;
}

const LEGACY_ALL = "ligtas_campus";   // older builds: one topic for everybody

/**
 * Returns "" when the admin may send this, or a message saying why not.
 *  adminCampus: the roster "campus" value of the admin ("" = not tied to a campus yet, so no limit, as before)
 *  type: ALERT, ALL_CLEAR or FALSE_ALARM
 */
function campusProblem(adminCampus, target, type) {
  const mine = slug(adminCampus);
  if (!mine) return "";
  const topics = topicsOf(target);
  const campusTopics = topics.filter((t) => t.startsWith("campus_"));
  if (campusTopics.some((t) => t !== "campus_" + mine)) return "You can only alert your own campus.";
  if (campusTopics.length) return "";
  // No campus topic at all. One case is still allowed: the older all-campus topic, only for All clear / False alarm
  // (the app falls back to it for old alerts). Teacher targets now always carry the campus topic too.
  if (topics.length === 1 && topics[0] === LEGACY_ALL && type !== "ALERT") return "";
  return "This alert is not limited to your campus.";
}

/**
 * Same rule for an alert that was already sent: every place it was pushed to ("targets" on the alert record) must be
 * inside the admin's campus. Old alerts with no saved targets cannot be judged, so they are allowed.
 */
function alertProblem(adminCampus, targets) {
  if (!slug(adminCampus) || !Array.isArray(targets)) return "";
  for (const t of targets) {
    const why = campusProblem(adminCampus, String(t), "ALL_CLEAR");   // "ALL_CLEAR": the older all-campus topic stays allowed
    if (why) return why;
  }
  return "";
}

/**
 * When the server setting REQUIRE_ADMIN_CAMPUS is "true", an admin with no campus is refused (returns a message).
 * Leave the setting off until every admin has a campus (console > Admins), then turn it on.
 */
function missingCampus(adminCampus, requireSetting) {
  if (String(requireSetting || "").toLowerCase() !== "true") return "";
  return slug(adminCampus) ? "" : "Your admin account is not assigned to a campus yet. Ask an admin to set it in the console.";
}

module.exports = { slug, topicsOf, campusProblem, alertProblem, missingCampus };
