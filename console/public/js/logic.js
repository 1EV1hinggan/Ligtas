// ---- Ligtas campus logic (same rules as the Android app's CampusConfig.java) ----
const DAY_MS = 86400000, WEEK_MS = 7 * DAY_MS;
const slug = (s) => String(s || "").toLowerCase().replace(/[^a-z0-9]+/g, "_").replace(/^_+|_+$/g, "");

function mondayOf(ms) {
  const d = new Date(ms);
  const dow = (d.getDay() + 6) % 7; // Monday = 0
  return new Date(d.getFullYear(), d.getMonth(), d.getDate() - dow).getTime();
}
function weekNumber(c, when) {
  if (!c.semesterStart || when < mondayOf(c.semesterStart)) return 0;
  if (c.semesterEnd && when >= c.semesterEnd + DAY_MS) return 0;
  return Math.round((mondayOf(when) - mondayOf(c.semesterStart)) / WEEK_MS) + 1;
}
const DAY_NAMES = ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"];
function normMode(m) {
  const t = String(m || "").trim().toUpperCase();
  if (t === "OL" || t === "ONLINE" || t === "REMOTE") return "OL";
  if (t === "NC" || t === "NONE" || t === "HOLIDAY" || t === "VACANT") return "NC";
  return "OS";
}
// "FRI", "MON,TUE", "WEEKDAYS", "" (= every day) -> 7 flags (Monday first)
function parseDays(text) {
  const d = [false, false, false, false, false, false, false];
  for (const part of String(text || "").toUpperCase().split(/[,; ]+/)) {
    if (part === "WEEKDAYS") { for (let i = 0; i < 5; i++) d[i] = true; continue; }
    if (part === "WEEKEND") { d[5] = d[6] = true; continue; }
    DAY_NAMES.forEach((n, i) => { if (part.length >= 3 && part.startsWith(n)) d[i] = true; });
  }
  return d;
}
function overrideCovers(o, when) {
  const days = parseDays(o.days), any = days.some(Boolean);
  if (!(o.start > 0) && !(o.end > 0) && !any) return false;
  if (o.start > 0 && when < o.start) return false;
  if (o.end > 0 && when >= o.end + DAY_MS) return false;
  return !any || days[(new Date(when).getDay() + 6) % 7];
}
function findBy(list, key, val) { return list.find((x) => slug(x[key]) === slug(val)); }
function policyMode(policy, year, odd) {
  if (!policy) return "OS";
  if (policy.type === "FIXED_ONLINE") return "OL";
  if (policy.type === "GRID") {
    const row = (policy.grid || []).find((r) => Number(r.year) === year);
    return row ? (odd ? row.odd : row.even) || "OS" : "OS";
  }
  return "OS";
}
// Returns {mode, why}
function modeFor(c, collegeCode, programCode, year, when) {
  const prog = programCode ? findBy(c.programs, "code", programCode) : null;
  const collegeKey = collegeCode || (prog ? prog.college : "");
  let forced = null;
  for (const o of c.overrides || []) {
    if (overrideCovers(o, when) && (o.college === "*" || !o.college || slug(o.college) === slug(collegeKey))) forced = o;
  }
  if (forced) return { mode: normMode(forced.mode), why: "Rule" + (forced.reason ? ": " + forced.reason : "") };
  const week = weekNumber(c, when);
  if (week === 0) return { mode: "OS", why: "Semester dates not set / outside" };
  let policyId = prog && prog.policy ? prog.policy : "";
  if (!policyId) { const col = findBy(c.colleges, "code", collegeKey); if (col) policyId = col.policy; }
  const policy = (c.policies || []).find((p) => p.id === policyId);
  if (!policy) return { mode: "OS", why: "No policy set" };
  return { mode: policyMode(policy, year, week % 2 === 1), why: "Week " + week + (week % 2 === 1 ? " (ODD)" : " (EVEN)") };
}

// Who moves up a year and who graduates at the start of a new semester (pure: no database calls).
function planRollover(roster, c, campusId, label) {
  const out = { promote: [], graduate: [], skipped: [] };
  for (const r of roster || []) {
    if (r.role !== "STUDENT") continue;
    if (label && r.rolloverTag === label) continue;   // already moved in this run
    if (r.campus && slug(r.campus) !== slug(campusId)) continue;
    const p = (c.programs || []).find((x) => slug(x.code) === slug(r.program));
    const y = Number(r.yearLevel) || 0;
    if (!p) { out.skipped.push({ email: r.email, why: 'program "' + (r.program || "") + '" is not in the setup' }); continue; }
    if (y < 1) { out.skipped.push({ email: r.email, why: "no year level" }); continue; }
    if (y >= p.years) out.graduate.push(r.email); else out.promote.push(r.email);
  }
  return out;
}

// ---- roster CSV import (pure, so it can be tested) ----
const CORE_COLS = ["email", "name", "firstName", "lastName", "role", "studentNo", "college", "program", "yearLevel", "building", "campus", "teaches"];
const COL_ALIASES = {
  email: ["email", "email_address", "e_mail", "school_email", "g_suite_email", "gsuite_email", "gmail"],
  firstName: ["first_name", "firstname", "given_name", "first"],
  lastName: ["last_name", "lastname", "surname", "family_name", "last"],
  name: ["name", "full_name", "fullname", "faculty_name", "employee_name", "teacher_name"],
  role: ["role", "user_type", "type", "account_type"],
  studentNo: ["studentno", "student_no", "student_number", "student_id", "sr_code", "srcode", "id_number"],
  college: ["college", "department", "dept", "college_department"],
  program: ["program", "course"],
  yearLevel: ["yearlevel", "year_level", "year"],
  building: ["building", "office", "office_building"],
  campus: ["campus"],
  teaches: ["teaches", "handles", "teaching", "handled_programs", "programs_handled", "teaching_load"],
};
const ROLE_WORDS = { ADMIN: "ADMIN", ADMINISTRATOR: "ADMIN", TEACHER: "TEACHER", FACULTY: "TEACHER", PROFESSOR: "TEACHER", INSTRUCTOR: "TEACHER", STUDENT: "STUDENT" };

/** Splits CSV or pasted-from-Excel (tab separated) text into rows of cells. Handles "quoted, cells". */
function splitCsv(text) {
  const t = String(text || "").replace(/^﻿/, "");
  const firstLine = t.split(/\r?\n/).find((l) => l.trim()) || "";
  const delim = firstLine.includes("\t") ? "\t" : ",";
  const rows = []; let row = [], cell = "", q = false;
  for (let i = 0; i < t.length; i++) {
    const ch = t[i];
    if (q) { if (ch === '"') { if (t[i + 1] === '"') { cell += '"'; i++; } else q = false; } else cell += ch; }
    else if (ch === '"') q = true;
    else if (ch === delim) { row.push(cell.trim()); cell = ""; }
    else if (ch === "\n" || ch === "\r") { if (ch === "\r" && t[i + 1] === "\n") i++; row.push(cell.trim()); cell = ""; if (row.some((c) => c)) rows.push(row); row = []; }
    else cell += ch;
  }
  row.push(cell.trim()); if (row.some((c) => c)) rows.push(row);
  return rows;
}

/** "BSIT-2; BSME" -> [{text:"BSIT-2", program:"BSIT", year:2}, {text:"BSME", program:"BSME", year:0}] (year 0 = every year). */
function parseTeaches(raw, programs) {
  const out = [], warn = [];
  for (const part of String(raw || "").split(/[;|\/]/)) {
    const tok = part.trim(); if (!tok) continue;
    const m = tok.match(/^(.*?)[-_\s]+(\d{1,2})$/);
    const progIn = (m ? m[1] : tok).trim(), year = m ? Number(m[2]) : 0;
    const hit = (programs || []).find((p) => slug(p.code) === slug(progIn));
    if (programs && programs.length && !hit) warn.push(`"${tok}": no program with code ${progIn}`);
    else if (hit && year && (year < 1 || year > (Number(hit.years) || 4))) warn.push(`"${tok}": ${hit.code} has only ${hit.years || 4} years`);
    const code = hit ? hit.code : progIn.toUpperCase();
    const text = year ? code + "-" + year : code;
    if (!out.some((o) => o.text === text)) out.push({ text, program: code, year });
  }
  return { slots: out, warnings: warn };
}

const DEFAULT_ORDER = ["email", "name", "role", "studentNo", "college", "program", "yearLevel", "building", "campus", "teaches"];
const looksEmail = (v) => /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(String(v || "").trim());
const STOP_WORDS = ["of", "in", "and", "the", "a", "major", "program", "course", "college", "department"];

/** Finds the {code,name} item a value refers to, by code or by full name. */
function matchItem(v, items) {
  const k = slug(v); if (!k) return null;
  return (items || []).find((i) => slug(i.code) === k || (i.name && slug(i.name) === k)) || null;
}
/** Best guess of the known code for text like "Bachelor of Science in Information Technology" (BSIT) or "Informatics". "" if unsure. */
function suggestMatch(raw, items) {
  const hit = matchItem(raw, items); if (hit) return hit.code;
  const k = slug(raw); if (!k) return "";
  const words = String(raw).toLowerCase().split(/[^a-z0-9]+/).filter((w) => w && !STOP_WORDS.includes(w));
  const initials = words.map((w) => (w.length <= 2 ? w : w[0])).join("");   // "BS" stays whole, long words give their first letter
  const byInit = (items || []).find((i) => slug(i.code).replace(/_/g, "") === initials);
  if (byInit) return byInit.code;
  const hits = (items || []).filter((i) => (slug(i.code).length >= 3 && k.includes(slug(i.code))) || (i.name && slug(i.name).length >= 4 && (k.includes(slug(i.name)) || slug(i.name).includes(k) && k.length >= 4)));
  return hits.length === 1 ? hits[0].code : "";
}

/**
 * Looks at a table (array of rows) and guesses which column is which: first from the heading, then from the values
 * (emails, roles, college and program names, year numbers, student numbers), then by the old fixed order when there
 * is no heading row. Returns { hasHeader, mapping:[fieldKey|"extra:key"|""], how:["heading"|"values"|"position"|""] }.
 * forceHeader: true/false overrides the automatic detection of a heading row.
 */
function guessColumns(grid, ctx, forceHeader) {
  ctx = ctx || {};
  const extras = ctx.extraFields || [];
  const out = { hasHeader: false, mapping: [], how: [] };
  if (!grid.length) return out;
  const ncols = Math.max.apply(null, grid.map((r) => r.length));
  const first = grid[0];
  out.hasHeader = forceHeader === undefined ? !first.some(looksEmail) : !!forceHeader;
  const body = out.hasHeader ? grid.slice(1) : grid;
  const aliasKey = (h) => {
    const s = slug(h); if (!s) return "";
    for (const k of CORE_COLS) if ((COL_ALIASES[k] || []).includes(s) || slug(k) === s) return k;
    const x = extras.find((e) => e.key === s || slug(e.label) === s);
    return x ? "extra:" + x.key : "";
  };
  const pc = (t) => t.split(/[;|\/]/).map((x) => x.trim()).filter(Boolean);
  const sniff = (c) => {
    const vals = body.map((r) => String(r[c] || "").trim()).filter(Boolean).slice(0, 200);
    if (!vals.length) return "";
    const frac = (f) => vals.filter(f).length / vals.length;
    if (frac(looksEmail) >= 0.8) return "email";
    if (frac((v) => !!ROLE_WORDS[v.toUpperCase()]) >= 0.8) return "role";
    if (frac((v) => /^\d{2,4}-\d{3,6}$/.test(v)) >= 0.8) return "studentNo";
    if (frac((v) => /^[1-8](st|nd|rd|th)?( year)?$/i.test(v)) >= 0.8) return "yearLevel";
    if ((ctx.programs || []).length && frac((v) => pc(v).length && pc(v).every((t) => (ctx.programs || []).some((p) => slug(t.replace(/[-_\s]+\d{1,2}$/, "")) === slug(p.code)))) >= 0.6
        && vals.some((v) => /[-_\s]\d{1,2}$|[;|\/]/.test(v))) return "teaches";
    if ((ctx.collegeList || []).length && frac((v) => !!matchItem(v, ctx.collegeList)) >= 0.6) return "college";
    if ((ctx.programs || []).length && frac((v) => !!matchItem(v, ctx.programs)) >= 0.6) return "program";
    if (frac((v) => /^[^\d@]+$/.test(v) && /[ ,]/.test(v) && !/\b(college|school|department|bachelor|science|engineering|building|hall|campus|institute|university)\b/i.test(v)) >= 0.8) return "name";
    return "";
  };
  const used = new Set();
  for (let c = 0; c < ncols; c++) {
    let key = out.hasHeader ? aliasKey(first[c]) : "", how = key ? "heading" : "";
    if (!key) { key = sniff(c); how = key ? "values" : ""; }
    if (key && !key.startsWith("extra:") && used.has(key)) { key = ""; how = ""; }
    if (key) used.add(key);
    out.mapping.push(key); out.how.push(how);
  }
  if (!out.hasHeader) for (let c = 0; c < ncols; c++) if (!out.mapping[c] && DEFAULT_ORDER[c] && !used.has(DEFAULT_ORDER[c])) { out.mapping[c] = DEFAULT_ORDER[c]; out.how[c] = "position"; used.add(DEFAULT_ORDER[c]); }
  return out;
}

/** College and program values in the file that are not an exact known code or name, with a best guess for each. */
function unmatchedValues(grid, mapping, hasHeader, ctx) {
  const body = hasHeader ? grid.slice(1) : grid, res = { college: [], program: [] };
  for (const [kind, items] of [["college", ctx.collegeList || []], ["program", ctx.programs || []]]) {
    const c = mapping.indexOf(kind); if (c < 0) continue;
    const seen = new Set();
    for (const r of body) { const raw = String(r[c] || "").trim(); if (!raw || seen.has(raw)) continue; seen.add(raw);
      if (!matchItem(raw, items)) res[kind].push({ raw, suggest: suggestMatch(raw, items) }); }
  }
  return res;
}

/**
 * Turns a table plus a column mapping into roster entries, without touching Firestore.
 * opts: { mapping, hasHeader, how, valueMaps:{college:{raw:code},program:{raw:code}}, extraFields, allowedDomains,
 *         defaultRole, programs, collegeList, colleges:fn }
 * Only mapped columns are returned, so importing a short file never blanks other fields.
 */
function parseRosterGrid(grid, opts) {
  opts = opts || {};
  const extras = opts.extraFields || [], allowed = (opts.allowedDomains || []).map((d) => d.toLowerCase()), vm = opts.valueMaps || {};
  const result = { rows: [], errors: [], warnings: [], unknown: [], mapped: [], hasHeader: !!opts.hasHeader, total: 0 };
  const mapping = opts.mapping || [], how = opts.how || [];
  if (!grid.length) return result;
  const header = mapping.map((k) => (!k ? null : k.startsWith("extra:") ? { kind: "extra", key: k.slice(6) } : { kind: "core", key: k }));
  const body = opts.hasHeader ? grid.slice(1) : grid;
  const first = opts.hasHeader ? grid[0] : [];
  const HOW = { heading: "from the heading", values: "guessed from the values", position: "by position", you: "your choice" };
  mapping.forEach((k, c) => { const nm = (first[c] || "column " + (c + 1)); if (k) result.mapped.push(`${nm} → ${k.replace("extra:", "")}${how[c] ? " (" + HOW[how[c]] + ")" : ""}`); else if (opts.hasHeader && (first[c] || "").trim()) result.unknown.push(first[c]); });
  if (!header.some((h) => h && h.kind === "core" && h.key === "email")) { result.errors.push("No column is set to Email, so nobody can be saved. Pick the email column above."); return result; }
  if (!opts.hasHeader) result.warnings.push("There is no row of column names, so the columns were guessed from their values or position. Check the column choices above.");
  const seen = new Set();
  body.forEach((cells, n) => {
    const get = {}, ex = {}, present = new Set();
    header.forEach((h, i) => { if (!h) return; const v = String(cells[i] == null ? "" : cells[i]).trim(); if (h.kind === "core") { get[h.key] = v; present.add(h.key); } else ex[h.key] = v; });
    if (!Object.values(get).some(Boolean) && !Object.values(ex).some(Boolean)) return;   // blank line
    const email = (get.email || "").toLowerCase(), label = email || "row " + (n + 1 + (opts.hasHeader ? 1 : 0));
    result.total++;
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) { result.errors.push(`${label}: not an email address`); return; }
    if (seen.has(email)) { result.errors.push(`${email}: listed twice (the first one is used)`); return; }
    let role = ROLE_WORDS[String(get.role || "").trim().toUpperCase()] || "";
    if (!role && opts.defaultRole) role = opts.defaultRole;
    if (!role) { result.errors.push(`${email}: no valid role (use ADMIN, TEACHER or STUDENT, or choose "Everyone in this file is..." above)`); return; }
    if (role !== "ADMIN" && !allowed.includes((email.split("@")[1] || ""))) { result.errors.push(`${email}: ${(email.split("@")[1] || "")} is not an allowed email domain for students and teachers`); return; }
    const missing = extras.filter((e) => e.required && (e.key in ex) && !(ex[e.key] || "").trim());
    if (missing.length) { result.errors.push(`${email}: missing required ${missing.map((e) => e.label).join(", ")}`); return; }
    if (!get.name && (get.firstName || get.lastName)) { get.name = [get.firstName, get.lastName].filter(Boolean).join(" "); present.add("name"); }
    const data = {}, warnings = [];
    for (const k of present) {
      if (k === "email" || k === "role" || k === "teaches" || k === "firstName" || k === "lastName") continue;
      let v = get[k];
      if (k === "yearLevel") { const m = String(v).match(/\d+/); v = m ? Number(m[0]) : 0; }
      if (k === "college" && v) v = (vm.college && vm.college[v]) || (opts.colleges ? opts.colleges(v) : ((matchItem(v, opts.collegeList) || {}).code || v));
      if (k === "program" && v) v = (vm.program && vm.program[v]) || (matchItem(v, opts.programs) || {}).code || v;
      data[k] = v;
    }
    data.role = role;
    if (data.program) { const ph = matchItem(data.program, opts.programs); if (!ph && (opts.programs || []).length) warnings.push(`${email}: program "${data.program}" is not in the campus setup (saved as written)`); else if (ph && !data.college && ph.college) data.college = ph.college; }
    if (data.college && (opts.collegeList || []).length && !matchItem(data.college, opts.collegeList)) warnings.push(`${email}: college "${data.college}" is not in the campus setup (saved as written)`);
    if (present.has("teaches")) {
      if (role !== "TEACHER") { if (get.teaches) warnings.push(`${email}: "teaches" is only used for teachers, ignored`); }
      else { const t = parseTeaches(get.teaches, opts.programs); data.teaches = t.slots.map((s) => s.text); t.warnings.forEach((w) => warnings.push(email + " " + w)); }
    }
    const extra = {}; for (const e of extras) if (e.key in ex) extra[e.key] = ex[e.key];
    seen.add(email);
    result.rows.push({ email, role, data, extra });
    result.warnings.push(...warnings);
  });
  return result;
}

/** Convenience for pasted text: read it as a table, guess the columns, and parse. */
function parseRosterCsv(text, opts) {
  opts = opts || {};
  const grid = splitCsv(text), g = guessColumns(grid, opts);
  return parseRosterGrid(grid, Object.assign({}, opts, { mapping: g.mapping, hasHeader: g.hasHeader, how: g.how }));
}

/** Turns a label such as "Employee No." into a safe field key ("employee_no"). */
function extraKeyOf(label) { return slug(label).slice(0, 30); }
