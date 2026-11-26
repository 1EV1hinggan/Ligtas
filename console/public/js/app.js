import { initializeApp } from "https://www.gstatic.com/firebasejs/10.14.1/firebase-app.js";
import { getAuth, GoogleAuthProvider, signInWithPopup, signOut, onAuthStateChanged } from "https://www.gstatic.com/firebasejs/10.14.1/firebase-auth.js";
import { getFirestore, doc, getDoc, getDocs, setDoc, collection, writeBatch, deleteDoc, serverTimestamp, Timestamp, query, where, orderBy, limit, startAfter, documentId, getCountFromServer } from "https://www.gstatic.com/firebasejs/10.14.1/firebase-firestore.js";

// STEP: paste the SAME firebaseConfig block you used in roster.html
const firebaseConfig = {
  apiKey: "AIzaSyClJvlO0zvrxX3b1YhLXylOutkyhQUV_H0",
  authDomain: "ligtas-eb260.firebaseapp.com",
  projectId: "ligtas-eb260",
  appId: "1:499346860850:web:eacd2e8fe97473a620452b"
};
const SCHOOL_DOMAIN = "g.batstate-u.edu.ph";
const $ = (id) => document.getElementById(id);
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
let sayTimer = null;
const say = (text, cls = "") => { const m = $("msg"); m.className = "status " + cls; m.textContent = text; clearTimeout(sayTimer); if (text && cls !== "bad") sayTimer = setTimeout(() => { if (m.textContent === text) m.textContent = ""; }, 7000); };

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app);

let campusId = null;     // id of the campus being edited
let campus = null;       // the document being edited
let tab = "basics";
let dirty = false;
let stats = null;        // counts for the People tab, made with database count queries (no people are downloaded)
let statsNote = "";
let pg = { filter: "", q: "", rows: [], cursor: null, more: false, loading: false, err: "", started: false };   // the People list, 50 at a time
let adminRows = null;    // the admins (a short list)
let probList = null;     // the "Show them" list in the roster check: {kind, title, rows}
let formExisting = null; // the roster entry for the email typed in the form, if any
const PAGE = 50, CONSENT_VERSION = 2;
let codes = null;        // setup codes (Admins tab)
let lastCode = "";       // the code just created, shown once
let rolloverMsg = "";
let rolloverLabel = "";
let rolloverBudget = 15000;
let allowedDomains = [SCHOOL_DOMAIN];   // email domains students/teachers may have (settings/app); admins may use any
let knownCampusIds = [];
let extraFields = [];   // admin-defined extra columns for people (settings/app.extraFields): [{key,label,required}]
let peopleFilter = "";
let picked = new Set();   // emails ticked in the People list (for "Delete selected")
let bulkBusy = false;
const MAX_EXTRA = 5;
const PUBLIC_DOMAINS = ["gmail.com", "yahoo.com", "outlook.com", "hotmail.com", "icloud.com", "proton.me", "protonmail.com"];
async function loadSettings() {
  try { const s = await getDoc(doc(db, "settings", "app"));
    if (s.exists() && Array.isArray(s.data().allowedDomains) && s.data().allowedDomains.length) allowedDomains = s.data().allowedDomains.map((d) => String(d).toLowerCase());
    if (s.exists() && Array.isArray(s.data().extraFields)) extraFields = s.data().extraFields.filter((f) => f && f.key && f.label).map((f) => ({ key: String(f.key), label: String(f.label), required: !!f.required })); }
  catch (e) { /* keep the default */ }
}
const domainOf = (addr) => String(addr || "").toLowerCase().split("@")[1] || "";       // loaded when the People tab opens
let previewDate = Date.now();

const TABS = [["basics", "Campus"], ["org", "Colleges & programs"], ["policies", "Schedule set-ups"], ["overrides", "Calendar rules"],
  ["locations", "Locations"], ["times", "Class times"], ["people", "People"], ["preview", "Preview"], ["rollover", "New semester"], ["admins", "Admins & email"]];

const blank = (name) => ({
  name, semesterStart: 0, semesterEnd: 0, hours: { open: "06:00", close: "21:00", days: "Mon-Sat" },
  colleges: [], programs: [], policies: [], overrides: [], locations: [], periods: []
});

function uid(prefix) { return prefix + "_" + Math.random().toString(36).slice(2, 7); }
function markDirty() { dirty = true; $("dirty").classList.remove("hide"); $("btnSave").disabled = false; }
function clean() { dirty = false; $("dirty").classList.add("hide"); $("btnSave").disabled = true; }

// ---------- the BSU Alangilan example from the diagram ----------
function seed() {
  const cics = { id: "p_cics", name: "CICS set-up (odd/even by year level)", type: "GRID", grid: [
    { year: 1, odd: "OL", even: "OS" }, { year: 2, odd: "OS", even: "OL" },
    { year: 3, odd: "OL", even: "OS" }, { year: 4, odd: "OS", even: "OL" }] };
  const coe = JSON.parse(JSON.stringify(cics)); coe.id = "p_coe"; coe.name = "COE set-up (copied from CICS: CHECK THIS)";
  const always = { id: "p_onsite", name: "Always on-site", type: "FIXED_ONSITE", grid: [] };
  const c = blank("BSU - Alangilan Campus");
  c.policies = [cics, coe, always];
  c.colleges = [{ code: "CICS", name: "College of Informatics and Computing Sciences", policy: "p_cics" },
    { code: "COE", name: "College of Engineering", policy: "p_coe" },
    { code: "CAFAD", name: "College of Architecture, Fine Arts and Design (different set-up: edit me)", policy: "p_onsite" }];
  c.programs = [{ code: "BSIT", name: "Information Technology", college: "CICS", years: 4, policy: "" },
    { code: "BSCS", name: "Computer Science", college: "CICS", years: 4, policy: "" },
    { code: "BSME", name: "Mechanical Engineering", college: "COE", years: 4, policy: "" },
    { code: "BSEE", name: "Electrical Engineering", college: "COE", years: 4, policy: "" }];
  c.locations = ["Main Building", "Science Building", "Engineering Building", "Library", "Gymnasium", "Cafeteria"].map((n) => ({ name: n, type: "Building", note: "" }));
  return c;
}

// ---------- load / save ----------
function normalize(d) {
  const c = Object.assign(blank(""), d || {});
  c.hours = Object.assign({ open: "06:00", close: "21:00", days: "Mon-Sat" }, c.hours || {});
  for (const k of ["colleges", "programs", "policies", "overrides", "locations", "periods"]) if (!Array.isArray(c[k])) c[k] = [];
  c.policies.forEach((p) => { if (!Array.isArray(p.grid)) p.grid = []; });
  return c;
}
async function listCampuses() {
  const snap = await getDocs(collection(db, "campuses"));
  const sel = $("campusSel");
  sel.innerHTML = snap.docs.map((d) => `<option value="${esc(d.id)}">${esc(d.data().name || d.id)}</option>`).join("");
  knownCampusIds = snap.docs.map((d) => d.id);
  return knownCampusIds;
}
async function openCampus(id) {
  const snap = await getDoc(doc(db, "campuses", id));
  campusId = id; campus = normalize(snap.exists() ? snap.data() : {}); resetRoster(); clean();
  $("campusSel").value = id; render();
}
async function saveCampus() {
  $("btnSave").disabled = true;
  try {
    const data = JSON.parse(JSON.stringify(campus));
    data.updatedBy = auth.currentUser.email.toLowerCase();
    data.updatedAt = serverTimestamp();
    await setDoc(doc(db, "campuses", campusId), data);
    clean(); say("Saved. Phones pick the new setup up the next time they send or open the app.", "ok");
  } catch (e) { markDirty(); say("Save REJECTED: " + (e.code || "") + " " + (e.message || e) + " (only admins can save)", "bad"); }
}

// ---------- auth ----------
$("btnIn").onclick = async () => {
  const p = new GoogleAuthProvider(); p.setCustomParameters({ prompt: "select_account" });
  try { await signInWithPopup(auth, p); } catch (e) { say("Sign-in failed: " + (e.code || e.message), "bad"); }
};
$("btnOut").onclick = () => signOut(auth);
$("btnSave").onclick = saveCampus;
window.addEventListener("beforeunload", (e) => { if (dirty) { e.preventDefault(); e.returnValue = ""; } });

async function isAdminUser(email) {
  try { const me = await getDoc(doc(db, "roster", email)); return me.exists() && me.data().role === "ADMIN"; } catch (e) { return false; }
}
onAuthStateChanged(auth, async (user) => {
  const email = user && user.email ? user.email.toLowerCase() : "";
  const school = !!(user && email);   // any Google account may sign in; only the roster makes you an admin
  const ok = school && await isAdminUser(email);
  $("btnIn").classList.toggle("hide", !!user); $("btnOut").classList.toggle("hide", !user);
  $("who").textContent = user ? email : "Not signed in";
  for (const id of ["campusSel", "btnNew", "btnSave"]) $(id).classList.toggle("hide", !ok);
  $("gate").classList.toggle("hide", school); $("app").classList.toggle("hide", !ok);
  $("claim").classList.toggle("hide", !(school && !ok));
  if (!ok) { campus = null; return; }
  await loadSettings();
  try {
    const ids = await listCampuses();
    if (ids.length) await openCampus(ids[0]);
    else { campusId = null; campus = null; render(); }
    say("");
  } catch (e) { say("Could not read campuses: " + (e.code || "") + " " + (e.message || e), "bad"); }
});
$("btnClaim").onclick = async () => {
  const out = $("claimMsg"); const user = auth.currentUser; if (!user) return;
  const email = user.email.toLowerCase(), code = $("claimCode").value.trim().toLowerCase(), name = $("claimName").value.trim();
  if (!name || !code) { out.className = "status warn"; out.textContent = "Enter your name and the setup code."; return; }
  try {
    const batch = writeBatch(db);
    batch.set(doc(db, "roster", email), { name, role: "ADMIN", setupCode: code, updatedBy: email, updatedAt: serverTimestamp() });
    batch.update(doc(db, "setup", code), { used: true, usedBy: email, usedAt: serverTimestamp() });
    await batch.commit();
    out.className = "status ok"; out.textContent = "You are now an admin. Loading...";
    setTimeout(() => location.reload(), 600);
  } catch (e) {
    out.className = "status bad";
    out.textContent = "Not accepted. The code may be wrong, already used or expired, or you may already be in the roster (ask an admin to promote you). " + (e.code || "");
  }
};
$("campusSel").onchange = async (e) => {
  if (dirty && !confirm("Discard unsaved changes?")) { e.target.value = campusId; return; }
  await openCampus(e.target.value);
};
$("btnNew").onclick = async () => {
  const name = prompt("Name of the new campus (for example: BSU - Pablo Borbon Campus)");
  if (!name) return;
  const id = slug(name).replace(/_/g, "-") || uid("campus");
  if ([...$("campusSel").options].some((o) => o.value === id)) { say("A campus with that id already exists.", "bad"); return; }
  campusId = id; campus = blank(name); resetRoster();
  $("campusSel").insertAdjacentHTML("beforeend", `<option value="${esc(id)}">${esc(name)}</option>`); $("campusSel").value = id;
  markDirty(); render();
};

// ---------- rendering ----------
function policyOptions(sel, allowNone) {
  return (allowNone ? `<option value="">(use the college's)</option>` : `<option value="">(none)</option>`) +
    campus.policies.map((p) => `<option value="${esc(p.id)}" ${p.id === sel ? "selected" : ""}>${esc(p.name)}</option>`).join("");
}
const collegeOptions = (sel, star) => (star ? `<option value="*" ${sel === "*" ? "selected" : ""}>Whole campus</option>` : "") +
  campus.colleges.map((c) => `<option value="${esc(c.code)}" ${slug(c.code) === slug(sel) ? "selected" : ""}>${esc(c.code)}</option>`).join("");
const dateVal = (ms) => { if (!ms) return ""; const d = new Date(ms); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`; };
const parseDate = (v) => { if (!v) return 0; const [y, m, d] = v.split("-").map(Number); return new Date(y, m - 1, d).getTime(); };
const modePill = (m, why) => `<span class="pill ${m}">${m === "OL" ? "Online" : m === "NC" ? "No class" : "On-site"}${why ? `<span class="sr">. ${esc(why)}</span>` : ""}</span>`;

function render() {
  const nav = $("tabs"), hadFocus = nav.contains(document.activeElement) ? document.activeElement.dataset.v : null;
  const NAV = [["Campus setup", ["basics", "org", "policies", "overrides", "locations", "times"]], ["People and access", ["people", "rollover", "admins"]], ["Check", ["preview"]]];
  const tabLabel = Object.fromEntries(TABS);
  nav.innerHTML = NAV.map(([head, keys]) => `<div class="navgroup" role="group" aria-label="${head}"><p class="navhead" aria-hidden="true">${head}</p>${keys.map((k) => `<button type="button" ${tab === k ? 'aria-current="page"' : ""} data-act="tab" data-v="${k}">${tabLabel[k]}</button>`).join("")}</div>`).join("");
  if (hadFocus) { const kept = nav.querySelector(`[data-v="${hadFocus}"]`); if (kept) kept.focus({ preventScroll: true }); }   // re-rendering must not drop keyboard focus
  const panel = $("panel");
  if (!campus) {
    panel.innerHTML = `<div class="card"><h2>No campus yet</h2><p class="hint">Start with the BSU example from your diagram, or an empty campus.</p>
      <button data-act="seed">Load the BSU Alangilan example</button> <button class="sec" data-act="empty">Start empty</button></div>`;
    return;
  }
  const fn = { basics, org, policies, overrides, locations, times, people, preview, rollover, admins }[tab];
  panel.innerHTML = fn();
  if ((tab === "people" || tab === "rollover") && !stats) loadStats();
  if (tab === "people" && !pg.started) loadPage(true);
  if (tab === "admins" && !adminRows) loadAdmins();
  if (tab === "admins" && !codes) loadCodes();
}

function basics() {
  const now = Date.now(), w = weekNumber(campus, now);
  return `<div class="card"><h2>Campus</h2>
    <div class="row"><label class="f">Name<input data-bind="name" value="${esc(campus.name)}" size="34"></label>
      <label class="f">Campus id<input value="${esc(campusId)}" disabled size="18"></label></div></div>
  <div class="card"><h2>Semester</h2>
    <p class="hint">Week 1 of the semester counts as an <b>ODD</b> week, then EVEN, ODD... Pick the first day of Week 1 (the app uses the Monday of that week).</p>
    <div class="row"><label class="f">Week 1 starts<input type="date" data-bind="semesterStart" data-kind="date" value="${dateVal(campus.semesterStart)}"></label>
      <label class="f">Semester ends (optional)<input type="date" data-bind="semesterEnd" data-kind="date" value="${dateVal(campus.semesterEnd)}"></label></div>
    <p class="status ${w ? "ok" : "warn"}">${w ? `Today is Week ${w} (${w % 2 ? "ODD" : "EVEN"}).` : "Today is outside the semester dates (or they are not set). Until they are, nobody is treated as online and alerts go to everyone."}</p></div>
  <div class="card"><h2>Operating hours</h2><p class="hint">Stored for later features (drills, "campus in session" checks).</p>
    <div class="row"><label class="f">Opens<input type="time" data-bind="hours.open" value="${esc(campus.hours.open)}"></label>
      <label class="f">Closes<input type="time" data-bind="hours.close" value="${esc(campus.hours.close)}"></label>
      <label class="f">Days<input data-bind="hours.days" value="${esc(campus.hours.days)}" size="14"></label></div></div>
  <div class="card"><h2>Backup</h2><div class="row"><button class="sec" data-act="export">Download as JSON</button>
    <button class="sec" data-act="importBtn">Import JSON</button><input type="file" id="importFile" accept=".json" class="hide"></div></div>`;
}

function org() {
  const colleges = campus.colleges.map((c, i) => {
    const progs = campus.programs.map((p, j) => ({ p, j })).filter((x) => slug(x.p.college) === slug(c.code));
    return `<div class="card"><div class="row"><label class="f">Code<input data-bind="colleges.${i}.code" value="${esc(c.code)}" size="8"></label>
      <label class="f">Full name<input data-bind="colleges.${i}.name" value="${esc(c.name)}" size="38"></label>
      <label class="f">Schedule set-up<select data-bind="colleges.${i}.policy">${policyOptions(c.policy, false)}</select></label>
      <button class="ghost" data-act="delCollege" data-i="${i}">Remove college</button></div>
      <h3>Programs</h3>
      <table><tr><th>Code</th><th>Name</th><th>Years</th><th>Set-up override</th><th></th></tr>
      ${progs.map(({ p, j }) => `<tr><td><input data-bind="programs.${j}.code" value="${esc(p.code)}" size="8"></td>
        <td><input data-bind="programs.${j}.name" value="${esc(p.name)}" size="26"></td>
        <td><input type="number" min="1" max="8" data-bind="programs.${j}.years" data-kind="num" value="${esc(p.years)}"></td>
        <td><select data-bind="programs.${j}.policy">${policyOptions(p.policy, true)}</select></td>
        <td><button class="ghost" data-act="delProgram" data-i="${j}">Remove</button></td></tr>`).join("") || `<tr><td colspan="5" class="hint">No programs yet.</td></tr>`}</table>
      <button class="sec" data-act="addProgram" data-code="${esc(c.code)}">+ Add program</button></div>`;
  }).join("");
  return `<p class="hint">Campus &gt; College &gt; Program &gt; Year level. Each college has a schedule set-up; a single program can override it.</p>
    ${colleges}<button data-act="addCollege">+ Add college</button>`;
}

function policies() {
  const cards = campus.policies.map((p, i) => {
    let body = "";
    if (p.type === "GRID") {
      body = `<table class="grid-edit"><tr><th>Year level</th><th>ODD week</th><th>EVEN week</th><th></th></tr>
        ${p.grid.map((r, k) => `<tr><td>${esc(r.year)}</td>
          <td><button class="tog ${r.odd}" data-act="flip" data-i="${i}" data-k="${k}" data-w="odd">${r.odd}</button></td>
          <td><button class="tog ${r.even}" data-act="flip" data-i="${i}" data-k="${k}" data-w="even">${r.even}</button></td>
          <td>${k === p.grid.length - 1 ? `<button class="ghost" data-act="delYear" data-i="${i}">Remove year</button>` : ""}</td></tr>`).join("")}</table>
        <button class="sec" data-act="addYear" data-i="${i}">+ Add year level</button>
        <p class="hint">OL = online, OS = on-site. A year level that is not in the table counts as on-site.</p>`;
    } else body = `<p class="hint">${p.type === "FIXED_ONLINE" ? "Always online." : "Always on-site."}</p>`;
    return `<div class="card"><div class="row"><label class="f">Name<input data-bind="policies.${i}.name" value="${esc(p.name)}" size="40"></label>
      <label class="f">Type<select data-bind="policies.${i}.type" data-rerender="1">
        ${[["GRID", "Odd/even table by year level"], ["FIXED_ONSITE", "Always on-site"], ["FIXED_ONLINE", "Always online"]].map(([v, l]) => `<option value="${v}" ${p.type === v ? "selected" : ""}>${l}</option>`).join("")}</select></label>
      <button class="sec" data-act="dupPolicy" data-i="${i}">Duplicate</button><button class="ghost" data-act="delPolicy" data-i="${i}">Remove</button></div>${body}</div>`;
  }).join("");
  return `<p class="hint">A set-up is the rule a college follows. Create one per way of scheduling (CICS has its own table; COE may copy it; CAFAD is different), then pick it on the Colleges tab.</p>
    ${cards}<button data-act="addPolicy">+ Add set-up</button>`;
}

function overrides() {
  const sel = (o, v, label) => `<option value="${v}" ${normMode(o.mode) === v ? "selected" : ""}>${label}</option>`;
  const rows = campus.overrides.map((o, i) => `<tr><td><input type="date" data-bind="overrides.${i}.start" data-kind="date" value="${dateVal(o.start)}"></td>
    <td><input type="date" data-bind="overrides.${i}.end" data-kind="date" value="${dateVal(o.end)}"></td>
    <td><input data-bind="overrides.${i}.days" value="${esc(o.days || "")}" size="10" placeholder="every day" list="dayList"></td>
    <td><select data-bind="overrides.${i}.college">${collegeOptions(o.college || "*", true)}</select></td>
    <td><select data-bind="overrides.${i}.mode">${sel(o, "OL", "Online / remote")}${sel(o, "OS", "On-site")}${sel(o, "NC", "No classes (holiday)")}</select></td>
    <td><input data-bind="overrides.${i}.reason" value="${esc(o.reason)}" size="20"></td><td><button class="ghost" data-act="delOverride" data-i="${i}">Remove</button></td></tr>`).join("");
  return `<div class="card"><h2>Calendar rules</h2>
    <p class="hint">Exceptions to the normal odd/even week. Leave the dates empty for a rule that always applies, leave the days empty for every day. The last matching row wins.</p>
    <ul class="hint"><li><b>Every Friday remote:</b> no dates, days <code>FRI</code>, Online.</li>
    <li><b>Typhoon suspension:</b> the two dates, no days, Online.</li>
    <li><b>Holiday:</b> one date (From = To), No classes.</li>
    <li><b>Days can be</b> <code>MON</code>, <code>FRI,SAT</code>, <code>WEEKDAYS</code> or <code>WEEKEND</code>.</li></ul>
    <datalist id="dayList"><option value="MON"><option value="TUE"><option value="WED"><option value="THU"><option value="FRI"><option value="SAT"><option value="WEEKDAYS"><option value="WEEKEND"></datalist>
    <table><tr><th>From</th><th>To (last day)</th><th>Weekdays</th><th>Applies to</th><th>Mode</th><th>Reason</th><th></th></tr>${rows || `<tr><td colspan="7" class="hint">None.</td></tr>`}</table>
    <button class="sec" data-act="addOverride">+ Add rule</button>
    <p class="hint">Alerts marked "on-site only" skip people who are <b>online</b> that day. A "No classes" day is not skipped, because students and staff may still be on campus.</p></div>`;
}

let locFilter = "";
function locations() {
  const q = locFilter.trim().toLowerCase();
  const all = campus.locations.map((l, i) => [l, i]).filter(([l]) => !q || (l.name || "").toLowerCase().includes(q) || (l.type || "").toLowerCase().includes(q));
  const shown = all.slice(0, 100);
  const rows = shown.map(([l, i]) => `<tr><td><input data-bind="locations.${i}.name" value="${esc(l.name)}" size="24"></td>
    <td><select data-bind="locations.${i}.type">${["Building", "Room", "Gym", "Library", "Cafeteria", "Grounds", "Other"].map((t) => `<option ${l.type === t ? "selected" : ""}>${t}</option>`).join("")}</select></td>
    <td><input data-bind="locations.${i}.note" value="${esc(l.note)}" size="30"></td><td><button class="ghost" data-act="delLocation" data-i="${i}">Remove</button></td></tr>`).join("");
  const nRooms = campus.locations.filter((l) => l.type === "Room").length;
  return `<div class="card"><h2>Locations</h2><p class="hint">Buildings and other sites fill the "Affected area" list when an admin sends an alert, and match the Building column in the roster. <b>Rooms</b> are only kept as a record: they are left out of that list, so a campus with thousands of rooms stays usable. ${campus.locations.length} saved (${nRooms} rooms).</p>
    <div class="row"><label class="f">Search<input id="locSearch" size="24" value="${esc(locFilter)}" placeholder="name or type"></label></div>
    <table><tr><th>Name</th><th>Type</th><th>Note</th><th></th></tr>${rows || `<tr><td colspan="4" class="hint">None.</td></tr>`}
      ${all.length > shown.length ? `<tr><td colspan="4" class="hint">Showing the first ${shown.length} of ${all.length}. Type in the search box to narrow it.</td></tr>` : ""}</table>
    <button class="sec" data-act="addLocation">+ Add location</button>
    <h3>Add many at once</h3>
    <p class="hint">Paste one name per line (from a spreadsheet column, for example).</p>
    <div class="row"><textarea id="locBulk" rows="4" cols="34" placeholder="CICS 101&#10;CICS 102"></textarea>
      <label class="f">Type<select id="locBulkType">${["Room", "Building", "Gym", "Library", "Cafeteria", "Grounds", "Other"].map((t) => `<option>${t}</option>`).join("")}</select></label>
      <button class="sec" data-act="addLocBulk">Add these</button></div></div>`;
}

function times() {
  const rows = campus.periods.map((p, i) => `<tr><td><input data-bind="periods.${i}.label" value="${esc(p.label)}" size="16"></td>
    <td><input type="time" data-bind="periods.${i}.start" value="${esc(p.start)}"></td><td><input type="time" data-bind="periods.${i}.end" value="${esc(p.end)}"></td>
    <td><button class="ghost" data-act="delPeriod" data-i="${i}">Remove</button></td></tr>`).join("");
  return `<div class="card"><h2>Class times</h2><p class="hint">The campus's class periods. Saved now; used by later features (drills at a safe time, "who is in class right now").</p>
    <table><tr><th>Period</th><th>Starts</th><th>Ends</th><th></th></tr>${rows || `<tr><td colspan="4" class="hint">None.</td></tr>`}</table>
    <button class="sec" data-act="addPeriod">+ Add period</button></div>`;
}

// ---------- people: counts and paging (built for thousands of people) ----------
// Nothing here downloads the whole roster. Totals come from database count queries (about 1 read per 1000 people
// counted) and the list is fetched 50 at a time, so the free plan's 50,000 reads a day are not used up.
const rosterCol = () => collection(db, "roster");
const cnt = async (...c) => (await getCountFromServer(query(rosterCol(), ...c))).data().count;
async function pool(thunks, n, onTick) {
  const res = new Array(thunks.length); let i = 0, done = 0;
  await Promise.all(Array.from({ length: Math.min(n, thunks.length) }, async () => {
    while (i < thunks.length) { const k = i++; res[k] = await thunks[k](); done++; if (onTick) onTick(done, thunks.length); }
  }));
  return res;
}
function afterChange(full) {   // a person was saved, removed or imported: reload the list, keep the counts (press Count again)
  picked.clear(); adminRows = null; probList = null; formExisting = null; if (full) stats = null;
  pg = { filter: pg.filter, q: pg.q || "", rows: [], cursor: null, more: false, loading: false, err: "", started: false };
}
async function lookupExisting(email) {
  email = String(email || "").trim().toLowerCase(); formExisting = null;
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) return;
  try { const d = await getDoc(doc(db, "roster", email)); if (d.exists() && form && form.email.trim().toLowerCase() === email) { formExisting = Object.assign({ email }, d.data()); render(); } } catch (e) {}
}
function resetRoster() {
  picked.clear(); stats = null; adminRows = null; probList = null; formExisting = null;
  pg = { filter: pg ? pg.filter : "", q: "", rows: [], cursor: null, more: false, loading: false, err: "", started: false };
}
async function loadStats() {
  if (stats === "loading" || !campus) return;
  stats = "loading"; statsNote = "";
  const S = "STUDENT", T = "TEACHER", W = where, keys = [], jobs = [];
  const add = (k, f) => { keys.push(k); jobs.push(f); };
  add("total", () => cnt()); add("students", () => cnt(W("role", "==", S))); add("teachers", () => cnt(W("role", "==", T))); add("admins", () => cnt(W("role", "==", "ADMIN")));
  add("phones", () => cnt(W("fcmToken", ">", ""))); add("agreed", () => cnt(W("consentVersion", ">=", CONSENT_VERSION)));
  for (const c of campus.colleges) { add("cS:" + c.code, () => cnt(W("role", "==", S), W("college", "==", c.code))); add("cT:" + c.code, () => cnt(W("role", "==", T), W("college", "==", c.code))); }
  add("blankS", () => cnt(W("role", "==", S), W("college", "==", ""))); add("blankT", () => cnt(W("role", "==", T), W("college", "==", "")));
  for (const p of campus.programs) for (let y = 1; y <= (Number(p.years) || 4); y++) add(`m:${p.code}:${y}`, () => cnt(W("role", "==", S), W("program", "==", p.code), W("yearLevel", "==", y)));
  add("noYear", () => cnt(W("role", "==", S), W("yearLevel", "==", 0))); add("noProgram", () => cnt(W("role", "==", S), W("program", "==", "")));
  add("noTeaches", () => cnt(W("role", "==", T), W("teaches", "==", [])));
  add("strays", () => cnt(W("campus", "not-in", ["", ...knownCampusIds.slice(0, 9)])));
  for (const c of campus.colleges) if (c.name && slug(c.name) !== slug(c.code)) add("fix:" + c.code, () => cnt(W("college", "==", c.name)));
  try {
    const res = await pool(jobs, 8, (d, n) => { statsNote = `Counting... ${d} of ${n}`; const el = $("statsNote"); if (el) el.textContent = statsNote; });
    const o = {}; keys.forEach((k, i) => { o[k] = res[i]; }); o.queries = keys.length; o.at = Date.now(); stats = o;
  } catch (e) { stats = null; say("Could not count the roster: " + (e.code || e.message), "bad"); return; }
  if (tab === "people" || tab === "rollover") render();
}
const mcount = (p) => { let n = 0; for (let y = 1; y <= (Number(p.years) || 4); y++) n += stats[`m:${p.code}:${y}`] || 0; return n; };

async function loadPage(reset) {
  if (pg.loading) return;
  pg.loading = true; pg.started = true; pg.err = "";
  if (reset) { pg.rows = []; pg.cursor = null; pg.more = false; picked.clear(); }
  try {
    const q = pg.q.trim(); let docs = [];
    if (q) {                                                  // search: by email or student number (start of it), or by name (start of it)
      const lo = q.toLowerCase(), seen = new Set();
      const a = await getDocs(query(rosterCol(), where(documentId(), ">=", lo), where(documentId(), "<", lo + ""), orderBy(documentId()), limit(PAGE)));
      a.docs.forEach((d) => { seen.add(d.id); docs.push(d); });
      if (!/[@\d]/.test(q)) {
        for (const nm of new Set([q[0].toUpperCase() + q.slice(1), q.replace(/\b\w/g, (c) => c.toUpperCase())])) {
          const b2 = await getDocs(query(rosterCol(), where("name", ">=", nm), where("name", "<", nm + ""), orderBy("name"), limit(PAGE)));
          b2.docs.forEach((d) => { if (!seen.has(d.id)) { seen.add(d.id); docs.push(d); } });
        }
      }
      if (pg.filter) docs = docs.filter((d) => d.data().role === pg.filter);
      pg.more = false;
    } else {
      const cons = []; if (pg.filter) cons.push(where("role", "==", pg.filter));
      cons.push(orderBy(documentId())); if (pg.cursor) cons.push(startAfter(pg.cursor)); cons.push(limit(PAGE));
      const snap = await getDocs(query(rosterCol(), ...cons)); docs = snap.docs;
      if (docs.length) pg.cursor = docs[docs.length - 1]; pg.more = docs.length === PAGE;
    }
    pg.rows = pg.rows.concat(docs.map((d) => Object.assign({ email: d.id }, d.data())));
  } catch (e) { pg.err = (e.code || "") + " " + (e.message || e); }
  pg.loading = false;
  const w = $("plistWrap"); if (w) w.innerHTML = plistTable();
}
async function loadAdmins() {
  adminRows = [];
  try { const snap = await getDocs(query(rosterCol(), where("role", "==", "ADMIN"), limit(200))); adminRows = snap.docs.map((d) => Object.assign({ email: d.id }, d.data())); }
  catch (e) { say("Could not read the admins: " + (e.code || e.message), "bad"); }
  if (tab === "admins") render();
}
const findRow = (email) => [].concat(pg.rows, probList ? probList.rows : [], adminRows || []).find((r) => r.email === email);

// A roster may say "College of Informatics and Computing Sciences" instead of "CICS": match by code or by name.
function collegeCodeOf(v) {
  if (!v || !campus) return v || "";
  const hit = campus.colleges.find((c) => slug(c.code) === slug(v) || (c.name && slug(c.name) === slug(v)));
  return hit ? hit.code : v;
}

function people() {
  const s = stats && stats !== "loading" ? stats : null;
  if (!s) return `<div class="card"><h2>Counting the roster...</h2><p class="hint">Counting does not download people, so it stays quick and cheap even with thousands of students. <span id="statsNote">${esc(statsNote)}</span></p></div>`
    + personForm() + peopleListCard() + importCard();
  const maxY = Math.max(4, ...campus.programs.map((p) => Number(p.years) || 4));
  const stOther = s.students - campus.colleges.reduce((n, c) => n + (s["cS:" + c.code] || 0), 0) - s.blankS;
  const tcOther = s.teachers - campus.colleges.reduce((n, c) => n + (s["cT:" + c.code] || 0), 0) - s.blankT;
  const collegeRows = campus.colleges.map((c) => `<tr><td>${esc(c.code)}</td><td>${s["cS:" + c.code] || 0}</td><td>${s["cT:" + c.code] || 0}</td></tr>`).join("")
    + (s.blankS || s.blankT ? `<tr><td>(no college)</td><td>${s.blankS}</td><td>${s.blankT}</td></tr>` : "")
    + (stOther > 0 || tcOther > 0 ? `<tr><td>(college not in setup)</td><td>${Math.max(0, stOther)}</td><td>${Math.max(0, tcOther)}</td></tr>` : "");
  const matrix = campus.programs.map((p) => {
    const cells = []; for (let y = 1; y <= maxY; y++) cells.push(y <= (Number(p.years) || 4) ? (s[`m:${p.code}:${y}`] || 0) : "");
    return `<tr><td>${esc(p.code)}</td>${cells.map((n) => `<td>${n}</td>`).join("")}</tr>`;
  }).join("");
  const sumMatrix = campus.programs.reduce((n, p) => n + mcount(p), 0);
  const stRest = Math.max(0, s.students - sumMatrix - s.noYear - s.noProgram);
  const fixable = campus.colleges.reduce((n, c) => n + (s["fix:" + c.code] || 0), 0);
  const probs = [];
  const li = (txt, kind, label) => `<li>${txt}${kind ? ` <button class="ghost" data-act="showProb" data-k="${kind}">${label || "Show them"}</button>` : ""}</li>`;
  if (s.noYear) probs.push(li(`${s.noYear} student${s.noYear === 1 ? " has" : "s have"} no year level (they cannot be targeted by year or moved up at rollover)`, "noYear"));
  if (s.noProgram) probs.push(li(`${s.noProgram} student${s.noProgram === 1 ? " has" : "s have"} no program`, "noProgram"));
  if (stRest) probs.push(li(`${stRest} student${stRest === 1 ? "'s" : "s'"} program or year does not match your campus setup (misspelled program, or a year the program does not have)`));
  if (fixable) probs.push(li(`${fixable} ${fixable === 1 ? "person has" : "people have"} the college written as a name instead of the code`) + `<p><button data-act="fixColleges">Fix ${fixable} college name${fixable === 1 ? "" : "s"} automatically</button> <span id="fixMsg" class="status"></span></p>`);
  else if (stOther > 0 || tcOther > 0) probs.push(li(`${Math.max(0, stOther) + Math.max(0, tcOther)} people have a college that is not in your setup`));
  if (s.strays) probs.push(li(`${s.strays} ${s.strays === 1 ? "person has" : "people have"} a campus value that is not a campus in this system (usually a CSV column that landed in the wrong place)`, "strays")
    + `<p><button data-act="fixStrays">Clear the campus value for them</button> <span id="strayMsg" class="status"></span></p>`);
  if (s.noTeaches) probs.push(li(`${s.noTeaches} teacher${s.noTeaches === 1 ? " has" : "s have"} an empty <code>teaches</code> list, so they only get campus-wide and college alerts`, "noTeaches"));
  const prob = probList ? `<h3>${esc(probList.title)} (first ${probList.rows.length})</h3><ul class="hint">${probList.rows.map((r) => `<li>${esc(r.name || "")} <code>${esc(r.email)}</code> <button class="ghost" data-act="editPerson" data-e="${esc(r.email)}">Edit</button></li>`).join("") || "<li>None found.</li>"}</ul>` : "";
  return `<details class="card"><summary>Roster summary: ${s.total.toLocaleString()} people (${s.students.toLocaleString()} students, ${s.teachers.toLocaleString()} teachers, ${s.admins.toLocaleString()} admins)</summary><h3>Faculty and college breakdown</h3>
    <p class="hint">${s.total.toLocaleString()} people in the roster: ${s.students.toLocaleString()} students, ${s.teachers.toLocaleString()} teachers, ${s.admins.toLocaleString()} admins.
      Counted ${new Date(s.at).toLocaleTimeString()} with ${s.queries} quick count queries (about ${s.queries} of the 50,000 free reads for the day). <button class="ghost" data-act="recount">Count again</button></p>
    <table><tr><th>College</th><th>Students</th><th>Faculty</th></tr>${collegeRows}</table>
    <p class="hint">${s.phones.toLocaleString()} of ${s.total.toLocaleString()} have signed in on a phone and can receive alerts. ${s.agreed.toLocaleString()} have agreed to the current privacy notice.</p>
  <h3>Program breakdown (students per year level)</h3>
    <table><tr><th>Program</th>${Array.from({ length: maxY }, (_, i) => `<th>Year ${i + 1}</th>`).join("")}</tr>${matrix || `<tr><td class="hint">No programs.</td></tr>`}</table></details>
  <div class="card"><h2>${probs.length ? "Roster problems" : "Roster check"}</h2>
    ${probs.length ? `<ul>${probs.join("")}</ul>${prob}<p class="hint">Fix people one by one with Edit, or correct a CSV and import it again. Required extra columns are checked when people are added or imported.</p>` : `<p class="status ok">Nothing wrong found.</p>`}</div>
`
    + personForm() + peopleListCard() + importCard();
}

async function showProblem(kind) {
  const S = "STUDENT", T = "TEACHER", W = where;
  const defs = {
    noYear: ["Students with no year level", [W("role", "==", S), W("yearLevel", "==", 0)]],
    noProgram: ["Students with no program", [W("role", "==", S), W("program", "==", "")]],
    noTeaches: ["Teachers with an empty teaches list", [W("role", "==", T), W("teaches", "==", [])]],
    strays: ["People with an unknown campus value", [W("campus", "not-in", ["", ...knownCampusIds.slice(0, 9)])]],
  };
  const d = defs[kind]; if (!d) return;
  try { const snap = await getDocs(query(rosterCol(), ...d[1], limit(100))); probList = { kind, title: d[0], rows: snap.docs.map((x) => Object.assign({ email: x.id }, x.data())) }; render(); }
  catch (e) { say("Could not load the list: " + (e.code || e.message), "bad"); }
}

// ---------- people: add or edit one person (form) ----------
const newForm = () => ({ editing: false, email: "", name: "", role: "STUDENT", college: "", program: "", yearLevel: "", studentNo: "", building: "", teaches: [], extra: {} });
let form = null, imp = null;
const nameFromEmail = (email) => { const l = String(email || "").split("@")[0]; if (!/^[a-z]+([._-][a-z]+)+$/i.test(l)) return ""; return l.split(/[._-]/).map((w) => w[0].toUpperCase() + w.slice(1).toLowerCase()).join(" "); };
const optHtml = (v, label, sel) => `<option value="${esc(v)}" ${sel ? "selected" : ""}>${esc(label)}</option>`;
const ROLE_LABEL = { STUDENT: "Student", TEACHER: "Teacher / faculty", ADMIN: "Admin" };

function personForm() {
  const f = form || (form = newForm());
  const key = f.email.trim().toLowerCase();
  const existing = !f.editing && key && formExisting && formExisting.email === key ? formExisting : null;
  const progs = campus.programs.filter((p) => !f.college || slug(p.college) === slug(f.college));
  const prog = campus.programs.find((p) => slug(p.code) === slug(f.program));
  const years = prog ? Number(prog.years) || 4 : 4, maxY = Math.max(4, ...campus.programs.map((p) => Number(p.years) || 4));
  const domainNote = f.role === "ADMIN" ? "Admins can use any email address." : "Needs an email ending in " + allowedDomains.map((d) => "@" + d).join(" or ") + ".";
  const collegeSel = `<label class="f">College<select data-form="college">${optHtml("", "(choose)", !f.college)}${campus.colleges.map((c) => optHtml(c.code, c.code + (c.name ? " - " + c.name : ""), slug(c.code) === slug(f.college))).join("")}</select></label>`;
  const buildingIn = `<label class="f">Building / office (optional)<input data-form="building" list="bldList" size="22" value="${esc(f.building)}"><datalist id="bldList">${(campus.locations || []).map((l) => `<option value="${esc(l.name)}">`).join("")}</datalist></label>`;
  const roleBlock = f.role === "STUDENT" ? `<div class="row">${collegeSel}
      <label class="f">Program<select data-form="program">${optHtml("", "(choose)", !f.program)}${progs.map((p) => optHtml(p.code, p.code, slug(p.code) === slug(f.program))).join("")}</select></label>
      <label class="f">Year level<select data-form="yearLevel">${optHtml("", "(choose)", !f.yearLevel)}${Array.from({ length: years }, (_, i) => optHtml(String(i + 1), "Year " + (i + 1), String(f.yearLevel) === String(i + 1))).join("")}</select></label>
      <label class="f">Student number<input data-form="studentNo" size="14" value="${esc(f.studentNo)}"></label>${buildingIn}</div>`
    : f.role === "TEACHER" ? `<div class="row">${collegeSel}${buildingIn}<label class="f">Employee / ID number (optional)<input data-form="studentNo" size="14" value="${esc(f.studentNo)}"></label></div>
      <div class="row"><label class="f">Teaches: program<select id="tchProg">${campus.programs.map((p) => optHtml(p.code, p.code, false)).join("")}</select></label>
        <label class="f">Year<select id="tchYear">${optHtml("", "every year", true)}${Array.from({ length: maxY }, (_, i) => optHtml(String(i + 1), "Year " + (i + 1), false)).join("")}</select></label>
        <button class="sec" data-act="addTeach">Add</button></div>
      <p class="hint">${f.teaches.length ? f.teaches.map((t, i) => `<span class="tag">${esc(t)} <a href="#" data-act="delTeach" data-i="${i}" role="button">&times;</a></span>`).join("") : "Nothing added yet. Alerts for a program and year also reach the teachers listed for it. Without any, this teacher only gets campus-wide and college alerts."}</p>`
    : `<p class="hint">Admins can send alerts and change everything in this console. No class details needed.</p>`;
  const extras = f.role === "ADMIN" ? "" : extraFields.length ? `<div class="row">${extraFields.map((x) => `<label class="f">${esc(x.label)}${x.required ? " *" : ""}<input data-form-extra="${esc(x.key)}" size="18" value="${esc(f.extra[x.key] || "")}"></label>`).join("")}</div>` : "";
  return `<div class="card" id="personCard"><h2>${f.editing ? "Edit person" : "Add a person"}</h2>
    <div class="row">${["STUDENT", "TEACHER", "ADMIN"].map((r) => `<button class="${f.role === r ? "" : "sec"}" data-act="formRole" data-v="${r}">${ROLE_LABEL[r]}</button>`).join("")}</div>
    <div class="row"><label class="f">Email<input data-form="email" size="34" value="${esc(f.email)}" ${f.editing ? "readonly" : ""} placeholder="name@${esc(allowedDomains[0] || "")}"></label>
      <label class="f">Full name<input data-form="name" size="26" value="${esc(f.name)}" placeholder="${esc(nameFromEmail(f.email) || "First Last")}"></label></div>
    <p class="hint">${esc(domainNote)}${existing ? ` <b>This person is already in the roster as ${esc(ROLE_LABEL[existing.role] || existing.role)}; saving will update them.</b>` : ""}</p>
    ${roleBlock}${extras}
    <div class="row"><button data-act="savePerson">${f.editing ? "Save changes" : "Add person"}</button>${f.editing || f.email ? `<button class="sec" data-act="formClear">${f.editing ? "Cancel" : "Clear"}</button>` : ""}<span id="formMsg" class="status"></span></div></div>`;
}

function formDoc() {
  const f = form, me = auth.currentUser.email.toLowerCase();
  const email = f.email.trim().toLowerCase(), name = f.name.trim() || nameFromEmail(email);
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) return { error: "Type a valid email address." };
  if (f.role !== "ADMIN" && !allowedDomains.includes(domainOf(email))) return { error: `${domainOf(email)} is not an allowed email domain for students and teachers (allowed: ${allowedDomains.join(", ")}). Change the allowed domains on the Admins & email tab, or add this person as an admin.` };
  if (f.role === "STUDENT" && (!f.program || !f.yearLevel)) return { error: "Choose the student's program and year level (needed to know which alerts reach them)." };
  if (email === me && f.role !== "ADMIN") return { error: "You cannot take away your own admin role." };
  const missing = f.role === "ADMIN" ? [] : extraFields.filter((x) => x.required && !String(f.extra[x.key] || "").trim());
  if (missing.length) return { error: "Fill in: " + missing.map((x) => x.label).join(", ") };
  const p = campus.programs.find((x) => slug(x.code) === slug(f.program));
  const college = f.role === "ADMIN" ? "" : (collegeCodeOf(f.college) || (p ? p.college : ""));
  const doc_ = { name, role: f.role, college, program: f.role === "STUDENT" ? (p ? p.code : f.program) : "", yearLevel: f.role === "STUDENT" ? Number(f.yearLevel) || 0 : 0,
    studentNo: f.role === "ADMIN" ? "" : f.studentNo.trim(), building: f.role === "ADMIN" ? "" : f.building.trim(), teaches: f.role === "TEACHER" ? f.teaches : [], updatedBy: me, updatedAt: serverTimestamp() };
  const extra = {}; if (f.role !== "ADMIN") for (const x of extraFields) extra[x.key] = String(f.extra[x.key] || "").trim();
  if (Object.keys(extra).length) doc_.extra = extra;
  return { email, doc: doc_ };
}

const myEmail = () => (auth && auth.currentUser && auth.currentUser.email ? auth.currentUser.email.toLowerCase() : "");
function plistTable() {
  const cols = 7 + extraFields.length, me = myEmail();
  const pickable = pg.rows.filter((r) => r.email !== me);
  const allOn = pickable.length > 0 && pickable.every((r) => picked.has(r.email));
  const bar = picked.size ? `<div class="row" role="status"><span><b>${picked.size}</b> selected</span><button class="sec" data-act="pickClear">Clear selection</button><button class="ghost" data-act="delPicked"${bulkBusy ? " disabled" : ""}>Delete selected (${picked.size})</button></div>` : "";
  const rows = pg.rows.map((r) => `<tr><td><input type="checkbox" data-pick="${esc(r.email)}" aria-label="Select ${esc(r.email)}"${picked.has(r.email) ? " checked" : ""}${r.email === me ? ' disabled title="You cannot remove yourself"' : ""}></td><td>${esc(r.name || "")}<div class="hint">${esc(r.email)}</div></td><td>${esc(r.role || "")}</td><td>${esc(collegeCodeOf(r.college || r.department || ""))}</td>
    <td>${r.role === "TEACHER" ? esc(Array.isArray(r.teaches) && r.teaches.length ? r.teaches.join(", ") : "(none listed)") : esc([r.program, r.yearLevel ? "Year " + r.yearLevel : ""].filter(Boolean).join(" · "))}</td>
    ${extraFields.map((f) => `<td>${esc((r.extra || {})[f.key] || "")}</td>`).join("")}<td>${r.fcmToken ? "yes" : ""}</td>
    <td class="acts"><button class="ghost" data-act="editPerson" data-e="${esc(r.email)}">Edit</button><button class="ghost" data-act="delPerson" data-e="${esc(r.email)}">Remove</button></td></tr>`).join("");
  const foot = pg.err ? `<tr><td colspan="${cols}" class="status bad">${esc(pg.err)}</td></tr>`
    : pg.loading ? `<tr><td colspan="${cols}" class="hint">Loading...</td></tr>`
    : !pg.rows.length ? `<tr><td colspan="${cols}" class="hint">No one found.</td></tr>`
    : pg.more ? `<tr><td colspan="${cols}"><button class="sec" data-act="pgMore">Show the next ${PAGE}</button></td></tr>`
    : pg.q ? `<tr><td colspan="${cols}" class="hint">Showing up to ${PAGE} matches. Type more of the email or name to narrow down.</td></tr>` : "";
  return `${bar}<table><thead><tr><th><input type="checkbox" data-pickall aria-label="Select everyone shown below"${allOn ? " checked" : ""}${pickable.length ? "" : " disabled"} title="Select everyone shown below (not yourself)"></th><th>Person</th><th>Role</th><th>College</th><th>Program / teaches</th>${extraFields.map((f) => `<th>${esc(f.label)}</th>`).join("")}<th>Phone</th><th></th></tr></thead><tbody>${rows}${foot}</tbody></table>`;
}
function peopleListCard() {
  return `<div class="card"><h2>People list</h2>
    <div class="row"><label class="f">Show<select id="pgFilter">${[["", "Everyone"], ["STUDENT", "Students"], ["TEACHER", "Teachers"], ["ADMIN", "Admins"]].map(([v, l]) => optHtml(v, l, pg.filter === v)).join("")}</select></label>
      <label class="f">Search by email, student number or name<input id="pgSearch" size="32" value="${esc(pg.q)}" placeholder="25-06830, glenn.guinoban or Glenn"></label>
      <button class="sec" data-act="exportRoster">Download roster (CSV)</button></div>
    <div id="plistWrap">${plistTable()}</div></div>`;
}

// ---------- people: add many from a file ----------
const IMP_FIELDS = [["", "(don't import this column)"], ["email", "Email"], ["name", "Full name"], ["firstName", "First name"], ["lastName", "Last name"], ["role", "Role"], ["studentNo", "Student / ID number"],
  ["college", "College"], ["program", "Program"], ["yearLevel", "Year level"], ["building", "Building / office"], ["teaches", "Teaches (programs and years)"], ["campus", "Campus"]];
const impCtx = () => ({ programs: campus.programs, collegeList: campus.colleges, extraFields });
function impStart(grid) {
  if (!grid.length) return false;
  const g = guessColumns(grid, impCtx());
  imp = { grid, mapping: g.mapping, how: g.how, hasHeader: g.hasHeader, role: "", valueMaps: { college: {}, program: {} } };
  impSync(); return true;
}
// remember a best guess for every college/program value that is not an exact known code or name
function impSync() {
  const um = unmatchedValues(imp.grid, imp.mapping, imp.hasHeader, impCtx());
  for (const kind of ["college", "program"]) for (const u of um[kind]) if (!(u.raw in imp.valueMaps[kind])) imp.valueMaps[kind][u.raw] = u.suggest;
  return um;
}
function importCard() {
  const head = `<div class="card"><h2>Add many people from a file</h2>`;
  if (!imp) return head + `<p class="hint">Use a spreadsheet you already have: a class list, a faculty list, anything (Excel or CSV). The console works out which column is which and shows you before anything is saved, so you never type rows by hand.</p>
    <div id="dropZone" class="drop">Drop a file here, or choose one: <input type="file" id="impFile" accept=".csv,.tsv,.txt,.xlsx,.xls"></div>
    <details><summary>Or paste rows (for example copied from Excel)</summary><textarea id="impPaste" rows="5" class="mono"></textarea><div class="row"><button class="sec" data-act="readPaste">Read the pasted rows</button></div></details>
    <div id="impMsg" class="status"></div></div>`;
  const g = imp, um = impSync();
  const plan = g.plan = parseRosterGrid(g.grid, Object.assign({ allowedDomains, defaultRole: g.role, colleges: collegeCodeOf, mapping: g.mapping, hasHeader: g.hasHeader, how: g.how, valueMaps: g.valueMaps }, impCtx()));
  const fieldOpts = IMP_FIELDS.concat(extraFields.map((f) => ["extra:" + f.key, f.label + " (extra column)"]));
  const HOW = { heading: "from the heading", values: "guessed from the values", position: "by position", you: "your choice" };
  const first = g.hasHeader ? g.grid[0] : [], body = g.hasHeader ? g.grid.slice(1) : g.grid;
  const sample = (c) => body.map((r) => String(r[c] == null ? "" : r[c]).trim()).filter(Boolean).slice(0, 3).join("  |  ");
  const colRows = g.mapping.map((m, c) => `<tr><td>${esc(first[c] || "(column " + (c + 1) + ")")}</td><td class="hint">${esc(sample(c).slice(0, 80))}</td>
    <td><select data-imp-col="${c}">${fieldOpts.map(([v, l]) => optHtml(v, l, v === m)).join("")}</select> <span class="hint">${m ? esc(HOW[g.how[c]] || "") : ""}</span></td></tr>`).join("");
  const fixes = ["college", "program"].map((kind) => um[kind].length ? `<p class="hint"><b>These ${kind === "college" ? "colleges" : "programs"} are not written exactly like your setup.</b> Choose what each one means:</p>
    <table>${um[kind].map((u) => `<tr><td>${esc(u.raw)}</td><td><select data-imp-vm="${kind}" data-raw="${esc(u.raw)}">${optHtml("", "(keep as written)", !g.valueMaps[kind][u.raw])}${(kind === "college" ? campus.colleges : campus.programs).map((x) => optHtml(x.code, x.code + (x.name ? " - " + x.name : ""), g.valueMaps[kind][u.raw] === x.code)).join("")}</select></td></tr>`).join("")}</table>` : "").join("");
  const n = plan.rows.length;
  const prevRows = plan.rows.slice(0, 12).map((r) => `<tr><td>${esc(r.data.name || "")}<div class="hint">${esc(r.email)}</div></td><td>${esc(r.role)}</td><td>${esc(r.data.college || "")}</td>
    <td>${r.role === "TEACHER" ? esc((r.data.teaches || []).join(", ")) : esc([r.data.program, r.data.yearLevel ? "Year " + r.data.yearLevel : ""].filter(Boolean).join(" · "))}</td></tr>`).join("");
  return head + `<p class="hint">File: <b>${esc(g.fileName || "pasted rows")}</b> &mdash; ${body.length} row${body.length === 1 ? "" : "s"}. <button class="ghost" data-act="impReset">Choose another file</button></p>
    <p><label><input type="checkbox" data-imp-header ${g.hasHeader ? "checked" : ""}> The first row contains column names</label></p>
    <h3>1. Which column is which?</h3>
    <table><tr><th>Column in your file</th><th>Example values</th><th>Save it as</th></tr>${colRows}</table>
    <p class="hint">Everyone in this file is: <select data-imp-role><option value="">(use the role column)</option><option value="TEACHER" ${g.role === "TEACHER" ? "selected" : ""}>a teacher / faculty</option><option value="STUDENT" ${g.role === "STUDENT" ? "selected" : ""}>a student</option></select>
      ${g.mapping.some((k) => k && k.startsWith("extra:")) || !extraFields.length ? "" : ""}</p>
    ${fixes ? `<h3>2. Match the names</h3>${fixes}` : ""}
    <h3>${fixes ? "3" : "2"}. Check, then save</h3>
    <p class="status ${n ? "ok" : "warn"}">${n} of ${plan.total} row${plan.total === 1 ? "" : "s"} can be saved${plan.errors.length ? `, ${plan.errors.length} will be skipped` : ""}.</p>
    ${plan.errors.length ? `<p class="hint"><b>Skipped:</b></p><ul class="hint">${plan.errors.slice(0, 40).map((e) => `<li>${esc(e)}</li>`).join("")}${plan.errors.length > 40 ? `<li>...and ${plan.errors.length - 40} more</li>` : ""}</ul>` : ""}
    ${plan.warnings.length ? `<p class="hint"><b>Check these (saved anyway):</b></p><ul class="hint">${plan.warnings.slice(0, 30).map((e) => `<li>${esc(e)}</li>`).join("")}</ul>` : ""}
    ${plan.unknown.length ? `<p class="hint">Not saved (set to "don't import"): ${esc(plan.unknown.join(", "))}. ${extraFields.length < MAX_EXTRA ? "To keep one, add it under Extra information columns on the Admins &amp; email tab first." : ""}</p>` : ""}
    ${n ? `<table><tr><th>Person</th><th>Role</th><th>College</th><th>Program / teaches</th></tr>${prevRows}</table>${n > 12 ? `<p class="hint">Showing the first 12 of ${n}.</p>` : ""}<p><button data-act="importCsv">Save ${n} ${n === 1 ? "person" : "people"}</button> <span id="impMsg" class="status"></span></p>` : `<span id="impMsg" class="status"></span>`}</div>`;
}

async function readImportFile(file) {
  const out = $("impMsg"); if (!file) return;
  try {
    let grid;
    if (/\.xlsx?$/i.test(file.name)) {
      if (!window.XLSX) { out.className = "status warn"; out.textContent = "Excel files need the internet to load a reader. Save the sheet as CSV (File > Save As > CSV) and choose that, or connect and reload."; return; }
      const wb = window.XLSX.read(await file.arrayBuffer(), { type: "array" });
      grid = window.XLSX.utils.sheet_to_json(wb.Sheets[wb.SheetNames[0]], { header: 1, raw: false, defval: "" }).map((r) => r.map((c) => String(c == null ? "" : c).trim()));
      grid = grid.filter((r) => r.some(Boolean));
    } else grid = splitCsv(await file.text());
    if (!impStart(grid)) { out.className = "status warn"; out.textContent = "That file looks empty."; return; }
    imp.fileName = file.name; render();
  } catch (e) { out.className = "status bad"; out.textContent = "Could not read that file: " + (e.message || e); }
}

async function savePerson() {
  const out = $("formMsg"); const r = formDoc();
  if (r.error) { out.className = "status warn"; out.textContent = r.error; return; }
  try { await setDoc(doc(db, "roster", r.email), r.doc, { merge: true });
    const nm = r.doc.name || r.email, wasEdit = form.editing; form = newForm(); afterChange(); render(); say(`${nm} ${wasEdit ? "updated" : "saved"}.`, "ok"); }
  catch (e) { out.className = "status bad"; out.textContent = "Rejected: " + (e.code || e.message); }
}

// ---------- admins and setup codes ----------
const CODE_CHARS = "abcdefghjkmnpqrstuvwxyz23456789";
function randomCode(len) {
  const buf = new Uint32Array(len); crypto.getRandomValues(buf);
  return Array.from(buf, (n) => CODE_CHARS[n % CODE_CHARS.length]).join("");
}
async function loadCodes() {
  codes = "loading";
  try { const snap = await getDocs(collection(db, "setup")); codes = snap.docs.map((d) => Object.assign({ code: d.id }, d.data())); }
  catch (e) { codes = []; say("Could not read setup codes: " + (e.code || e.message), "bad"); }
  if (tab === "admins") render();
}
const msOf = (t) => (t && typeof t.toMillis === "function" ? t.toMillis() : Number(t) || 0);
function admins() {
  const adminList = Array.isArray(adminRows) ? adminRows : [];
  const list = Array.isArray(codes) ? codes : [];
  const rows = list.map((c) => { const exp = msOf(c.expiresAt); const state = c.used ? `used by ${esc(c.usedBy || "?")}` : exp && exp < Date.now() ? "expired" : "unused";
    return `<tr><td><code>${esc(c.code)}</code></td><td>${state}</td><td>${exp ? new Date(exp).toLocaleString() : ""}</td><td>${esc(c.note || "")}</td><td><button class="ghost" data-act="delCode" data-c="${esc(c.code)}">Delete</button></td></tr>`; }).join("");
  const noCampus = adminList.filter((r) => !String(r.campus || "").trim()).length;
  const adminTable = !adminRows ? `<p class="hint">Loading...</p>` : `<table><tr><th>Admin</th><th>Campus</th><th></th></tr>${adminList.map((r) => {
    const c = String(r.campus || "").trim(), same = c && campusId && c.toLowerCase() === String(campusId).toLowerCase();
    return `<tr><td>${esc(r.name || "")}<div class="hint">${esc(r.email)}</div></td><td>${c ? esc(c) : '<span class="hint">none: not limited to one campus</span>'}</td>
      <td>${same || !campusId ? "" : `<button class="sec" data-act="setAdminCampus" data-e="${esc(r.email)}">Set campus to ${esc(campusId)}</button>`}</td></tr>`; }).join("")}</table>
    ${noCampus ? `<p class="hint"><b>${noCampus}</b> admin${noCampus === 1 ? " has" : "s have"} no campus. Until you set one, ${noCampus === 1 ? "that admin is" : "they are"} not limited to a single campus.</p>` : ""}`;
  return `<div class="card"><h2>Admins</h2>${adminTable}
    <p class="hint">An admin can use <b>any</b> email address, including a personal Gmail. What makes someone an admin is their roster entry, not their email domain.</p>
    <div class="row"><label class="f">Add an admin by email<input id="newAdminEmail" size="30" placeholder="name@anything.com"></label>
      <label class="f">Name<input id="newAdminName" size="20"></label><button data-act="addAdmin">Make admin</button></div>
    <div id="adminMsg" class="status"></div></div>
  <div class="card"><h2>Email domains for students and teachers</h2>
    <p class="hint">Students and teachers can only be added with an email on these domains (the database enforces this too). Admins are not limited. Separate several domains with commas. Example: <code>g.batstate-u.edu.ph, batstate-u.edu.ph</code></p>
    <div class="row"><label class="f">Allowed domains<input id="domainsBox" size="46" value="${esc(allowedDomains.join(", "))}"></label><button data-act="saveDomains">Save domains</button></div>
    <div id="domainMsg" class="status"></div></div>
  <div class="card"><h2>Extra information columns</h2>
    <p class="hint">Ligtas only needs email, name, role, college and program/year (or what a teacher teaches) to work. If your university wants other details on file, such as an employee number or position, add up to ${MAX_EXTRA} columns here. They show in the People list and the CSV import and export, and never change who gets an alert. Keep it to what you need: please do not store sensitive data such as birth dates or ID card numbers, and note that people see the list of kept details in the privacy notice.</p>
    <table><tr><th>Column</th><th>CSV name</th><th>Required</th><th></th></tr>${extraFields.map((f, k) => `<tr><td>${esc(f.label)}</td><td><code>${esc(f.key)}</code></td><td>${f.required ? "yes" : "no"}</td><td><button class="ghost" data-act="delExtra" data-i="${k}">Remove</button></td></tr>`).join("") || `<tr><td colspan="4" class="hint">None yet.</td></tr>`}</table>
    ${extraFields.length < MAX_EXTRA ? `<div class="row"><label class="f">New column name<input id="extraLabel" size="24" placeholder="Employee no."></label>
      <label class="f"><span>Required</span><input id="extraReq" type="checkbox"></label><button data-act="addExtra">Add column</button></div>` : `<p class="hint">You have reached the limit of ${MAX_EXTRA}.</p>`}
    <div id="extraMsg" class="status"></div>
    <p class="hint">Removing a column hides it but does not delete what was already saved. "Required" only checks CSV imports that contain that column and the Roster check.</p></div>
  <div class="card"><h2>Add another admin with a setup code</h2>
    <p class="hint">Create a one-time code and send it privately to the person. They sign in to this console with their school account, enter the code and their name, and become an admin. The code works once and then expires.</p>
    <div class="row"><label class="f">Note (who is it for?)<input id="codeNote" size="24"></label>
      <label class="f">Valid for (hours, max 120)<input id="codeHours" type="number" value="48" min="1" max="120"></label>
      <button data-act="genCode">Create setup code</button></div>
    ${lastCode ? `<p class="banner">New code (send it privately, it works once): <b><code>${esc(lastCode)}</code></b></p>` : ""}
    <table><tr><th>Code</th><th>Status</th><th>Expires</th><th>Note</th><th></th></tr>${rows || `<tr><td colspan="5" class="hint">${codes === "loading" ? "Loading..." : "No codes yet."}</td></tr>`}</table>
    <p class="hint">Very first admin of a brand-new project: in the Firebase console open Firestore, create a collection <code>setup</code>, a document whose ID is a long random code (for example 16 letters and digits), with fields <code>used</code> (boolean) = false, <code>createdBy</code> (string) = your email, <code>expiresAt</code> (timestamp) = a time within the next 7 days. Then sign in here with that code.</p></div>`;
}

// ---------- new semester ----------
// Runs one program at a time. Each moved student is stamped with rolloverTag = the label, so a stopped run can be
// continued without moving anyone twice. Graduates are archived and removed, so they cannot be moved twice either.
const defaultLabel = () => { const y = new Date().getFullYear(), m = new Date().getMonth(); const a = m >= 5 ? y : y - 1; return a + "-" + (a + 1); };
const planFor = (p) => { const yrs = Number(p.years) || 4; let grad = stats[`m:${p.code}:${yrs}`] || 0, up = 0; for (let y = 1; y < yrs; y++) up += stats[`m:${p.code}:${y}`] || 0; return { up, grad, writes: up + grad * 2 }; };
function rollover() {
  if (!stats || stats === "loading") return `<div class="card"><p class="hint">Counting the roster... <span id="statsNote">${esc(statsNote)}</span></p></div>`;
  const ro = campus.rollover || null, label = rolloverLabel || (ro && !ro.complete ? ro.label : defaultLabel());
  const doneSet = ro && ro.label === label ? ro.done || [] : [];
  let tUp = 0, tGrad = 0, tW = 0;
  const rows = campus.programs.map((p) => { const f = planFor(p), isDone = doneSet.includes(p.code); if (!isDone) { tUp += f.up; tGrad += f.grad; tW += f.writes; }
    return `<tr><td>${esc(p.code)}</td><td>${f.up}</td><td>${f.grad}</td><td>${f.writes}</td><td>${isDone ? "done" : ""}</td></tr>`; }).join("");
  const skipped = (stats.noYear || 0) + (stats.noProgram || 0);
  const last = campus.lastRollover;
  return `<div class="card"><h2>New semester: move everyone up a year</h2>
    <p class="hint">Run this once at the start of the new school year. Students move up one year level. Students already in their program's last year graduate: they are copied to an archive and removed from the roster, so they stop being counted. Teachers and admins are not touched. It runs one program at a time and remembers what is finished, so if it stops (internet, daily limit) you press Run again and it carries on without moving anyone twice.</p>
    ${last ? `<p class="status warn">Last finished run: ${new Date(last.at).toLocaleString()} by ${esc(last.by)} (${last.promoted} promoted, ${last.graduated} graduated).</p>` : ""}
    ${ro && !ro.complete ? `<p class="status warn">An unfinished run "${esc(ro.label)}" is saved: ${(ro.done || []).length} of ${campus.programs.length} programs finished. Press Run to continue.</p>` : ""}
    <table><tr><th>Program</th><th>Move up</th><th>Graduate</th><th>Writes</th><th></th></tr>${rows}
      <tr><th>Total left to do</th><th>${tUp}</th><th>${tGrad}</th><th>${tW}</th><th></th></tr></table>
    <p class="hint">Each move is one database write; each graduate is two (archive and remove). The free plan allows 20,000 writes per day for everything, so with a big school do it in parts: set the daily limit below and run again tomorrow. ${skipped ? `${skipped} students with no program or year are skipped; fix them on the People tab.` : ""}</p>
    <h3>Before you start</h3>
    <ol class="hint"><li>Download a backup: <button class="sec" data-act="exportRoster">Download roster (CSV)</button></li>
      <li>Set the new semester's Week 1 date on the Campus tab and press Save.</li>
      <li>Add the new freshmen on the People tab (CSV) if you have the list. Do this <b>after</b> the rollover, or they would move up too.</li></ol>
    <div class="row"><label class="f">Name of this run<input id="rolloverLabel" size="12" value="${esc(label)}"></label>
      <label class="f">Stop after about (writes)<input id="rolloverBudget" type="number" value="${rolloverBudget}" min="100" step="500"></label>
      <label class="f">Type ROLLOVER to confirm<input id="rolloverConfirm" size="12" autocomplete="off"></label>
      <button data-act="runRollover" ${tW ? "" : "disabled"}>Run rollover</button></div>
    <div class="status ${rolloverMsg.startsWith("Done") ? "ok" : "warn"}" id="rolloverStatus">${esc(rolloverMsg)}</div>
    <p class="hint">Phones of removed students keep any alert subscriptions until the app is opened next. Alerts are still limited by the roster whenever they sign in again.</p></div>`;
}
async function runRollover() {
  if ($("rolloverConfirm").value.trim() !== "ROLLOVER") { rolloverMsg = "Type ROLLOVER in the box first."; render(); return; }
  const label = $("rolloverLabel").value.trim().replace(/[^\w.-]/g, "") || defaultLabel(); rolloverLabel = label;
  rolloverBudget = Math.max(100, Number($("rolloverBudget").value) || 15000);
  const me = auth.currentUser.email.toLowerCase();
  let ro = campus.rollover && campus.rollover.label === label ? campus.rollover : { label, done: [], promoted: 0, graduated: 0, complete: false };
  const last = campus.lastRollover;
  if (!ro.done.length && last && Date.now() - last.at < 90 * 86400000 && !confirm("A rollover finished less than 90 days ago. Running it again moves everyone up another year. Continue?")) return;
  const say2 = (m) => { rolloverMsg = m; const el = $("rolloverStatus"); if (el) el.textContent = m; };
  const save = async () => { campus.rollover = Object.assign({}, ro, { at: Date.now(), by: me }); await setDoc(doc(db, "campuses", campusId), { rollover: campus.rollover }, { merge: true }); };
  let writes = 0;
  const okCampus = (r) => !r.campus || slug(r.campus) === slug(campusId);
  try {
    for (const p of campus.programs) {
      if (ro.done.includes(p.code)) continue;
      const f = planFor(p);
      if (writes && writes + f.writes > rolloverBudget) { await save(); say2(`Paused after ${writes} writes (your limit). Run again later to continue with the remaining programs.`); render(); return; }
      const yrs = Number(p.years) || 4, base = [where("role", "==", "STUDENT"), where("program", "==", p.code)];
      // 1) graduates leave the roster (so there is nothing to page past)
      for (let guard = 0; guard < 400; guard++) {
        const snap = await getDocs(query(rosterCol(), ...base, where("yearLevel", "==", yrs), limit(200)));
        const docs = snap.docs.filter((d) => okCampus(d.data())); if (!docs.length) break;
        const batch = writeBatch(db);
        for (const d of docs) { const copy = Object.assign({}, d.data()); delete copy.fcmToken; delete copy.tokenUpdatedAt; delete copy.hasToken;
          batch.set(doc(db, "rosterArchive", d.id), Object.assign(copy, { archivedAt: serverTimestamp(), archivedBy: me, reason: "graduated" })); batch.delete(doc(db, "roster", d.id)); }
        await batch.commit(); writes += docs.length * 2; ro.graduated += docs.length; say2(`${p.code}: ${ro.graduated} graduated so far...`);
        if (docs.length < snap.docs.length) break;   // rows of another campus would never leave: stop instead of looping
      }
      // 2) the other years move up, highest year first so nobody is moved twice
      for (let y = yrs - 1; y >= 1; y--) {
        let cursor = null;
        for (let guard = 0; guard < 400; guard++) {
          const cons = [...base, where("yearLevel", "==", y), orderBy(documentId())]; if (cursor) cons.push(startAfter(cursor)); cons.push(limit(400));
          const snap = await getDocs(query(rosterCol(), ...cons)); if (!snap.docs.length) break;
          cursor = snap.docs[snap.docs.length - 1];
          const docs = snap.docs.filter((d) => okCampus(d.data()) && d.data().rolloverTag !== label);
          if (docs.length) { const batch = writeBatch(db);
            for (const d of docs) batch.set(doc(db, "roster", d.id), { yearLevel: y + 1, rolloverTag: label, updatedBy: me, updatedAt: serverTimestamp() }, { merge: true });
            await batch.commit(); writes += docs.length; ro.promoted += docs.length; say2(`${p.code}: ${ro.promoted} moved up so far...`); }
          if (snap.docs.length < 400) break;
        }
      }
      ro.done.push(p.code); await save();
    }
    ro.complete = true; await save();
    campus.lastRollover = { at: Date.now(), by: me, promoted: ro.promoted, graduated: ro.graduated };
    await setDoc(doc(db, "campuses", campusId), { lastRollover: campus.lastRollover }, { merge: true });
    rolloverMsg = `Done: ${ro.promoted} moved up, ${ro.graduated} graduated.`; rolloverLabel = "";
  } catch (e) { try { await save(); } catch (e2) {} rolloverMsg = "Stopped part-way: " + (e.code || e.message) + ". Progress is saved. Press Run again to continue (nobody is moved twice)."; }
  afterChange(true); render();
}
async function exportRosterCsv() {
  const n = stats && stats.total;
  if (n > 3000 && !confirm(`The backup downloads all ${n.toLocaleString()} people, which uses about ${n.toLocaleString()} of the free plan's 50,000 daily reads. Continue?`)) return;
  const cols = ["email", "name", "role", "studentNo", "college", "program", "yearLevel", "building", "campus", "teaches"];
  const val = (r, c) => c === "teaches" ? (Array.isArray(r.teaches) ? r.teaches.join("; ") : "") : r[c];
  const lines = [cols.concat(extraFields.map((f) => f.key)).join(",")]; let cursor = null;
  try {
    for (let guard = 0; guard < 500; guard++) {
      const cons = [orderBy(documentId())]; if (cursor) cons.push(startAfter(cursor)); cons.push(limit(1000));
      const snap = await getDocs(query(rosterCol(), ...cons)); if (!snap.docs.length) break; cursor = snap.docs[snap.docs.length - 1];
      for (const d of snap.docs) { const r = Object.assign({ email: d.id }, d.data());
        lines.push(cols.map((c) => String(val(r, c) ?? "").replace(/,/g, " ")).concat(extraFields.map((f) => String((r.extra || {})[f.key] ?? "").replace(/,/g, " "))).join(",")); }
      say(`Preparing backup... ${lines.length - 1} people`, "warn");
      if (snap.docs.length < 1000) break;
    }
  } catch (e) { say("Could not read the roster for the backup: " + (e.code || e.message), "bad"); return; }
  const u = URL.createObjectURL(new Blob([lines.join("\n")], { type: "text/csv" }));
  const l = document.createElement("a"); l.href = u; l.download = "roster-backup-" + new Date().toISOString().slice(0, 10) + ".csv"; l.click(); URL.revokeObjectURL(u);
  say(`Backup ready (${lines.length - 1} people).`, "ok");
}

// ---------- preview ----------
function dayByDay(d) {
  const mon = mondayOf(d), names = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
  const rows = campus.programs.flatMap((p) => { const out = [];
    for (let y = 1; y <= p.years; y++) {
      const cells = names.map((_, k) => { const t = new Date(mon); t.setDate(t.getDate() + k); t.setHours(12); const r = modeFor(campus, p.college, p.code, y, t.getTime()); return `<td title="${esc(r.why)}">${modePill(r.mode, r.why)}</td>`; }).join("");
      out.push(`<tr><td>${esc(p.code)} ${y}</td>${cells}</tr>`);
    } return out; }).join("");
  return `<div class="card"><h2>This week, day by day</h2><p class="hint">Includes your calendar rules. Select a cell for the reason. Only Online is skipped by an on-site-only alert.</p>
    <table><tr><th>Group</th>${names.map((n) => `<th>${n}</th>`).join("")}</tr>${rows || `<tr><td class="hint">Add programs first.</td></tr>`}</table><p class="why-out" aria-live="polite">Select a cell to see why.</p></div>`;
}

function preview() {
  const d = previewDate, w = weekNumber(campus, d);
  const rows = campus.colleges.map((c) => {
    const progs = campus.programs.filter((p) => slug(p.college) === slug(c.code));
    return progs.map((p) => {
      const cells = []; for (let y = 1; y <= p.years; y++) { const r = modeFor(campus, c.code, p.code, y, d); cells.push(`<td title="${esc(r.why)}">${modePill(r.mode, r.why)}</td>`); }
      return `<tr><td>${esc(c.code)}</td><td>${esc(p.code)}</td>${cells.join("")}</tr>`;
    }).join("");
  }).join("");
  const maxY = Math.max(4, ...campus.programs.map((p) => p.years));
  return `<div class="card"><h2>Preview</h2><p class="hint">Exactly what the app will use to decide who is online. Select a cell for the reason.</p>
    <div class="row"><label class="f">Date<input type="date" id="pvDate" value="${dateVal(d)}"></label>
      <button class="sec" data-act="pvShift" data-v="-7">Previous week</button><button class="sec" data-act="pvShift" data-v="7">Next week</button><button class="sec" data-act="pvShift" data-v="0">Today</button></div>
    <p class="status ${w ? "ok" : "warn"}">${w ? `Week ${w} (${w % 2 ? "ODD" : "EVEN"})` : "Outside the semester dates: everyone counts as on-site."}</p>
    <table><tr><th>College</th><th>Program</th>${Array.from({ length: maxY }, (_, i) => `<th>Year ${i + 1}</th>`).join("")}</tr>${rows || `<tr><td class="hint">Add colleges and programs first.</td></tr>`}</table><p class="why-out" aria-live="polite">Select a cell to see why.</p></div>
    ${dayByDay(d)}`;
}

// ---------- events ----------
function setPath(obj, path, val) {
  const parts = path.split("."); let o = obj;
  for (let i = 0; i < parts.length - 1; i++) o = o[parts[i]];
  o[parts[parts.length - 1]] = val;
}
function onBind(e) {
  const t = e.target; if (!t.dataset || !t.dataset.bind || !campus) return;
  let v = t.value;
  if (t.dataset.kind === "num") v = Math.max(1, Number(v) || 1);
  if (t.dataset.kind === "date") { v = parseDate(v); if (t.dataset.bind === "semesterStart" && v) v = mondayOf(v); }
  setPath(campus, t.dataset.bind, v);
  // renaming a college code keeps its programs attached
  const m = t.dataset.bind.match(/^colleges\.(\d+)\.code$/);
  if (m && t.dataset.old !== undefined) { campus.programs.forEach((p) => { if (slug(p.college) === slug(t.dataset.old)) p.college = v; }); t.dataset.old = v; }
  markDirty();
  if (t.dataset.rerender || t.dataset.kind === "date" && t.dataset.bind.startsWith("semester")) render();
}
document.addEventListener("input", onBind);
document.addEventListener("change", (e) => {
  onBind(e);
  if (e.target.id === "pvDate") { previewDate = parseDate(e.target.value) || Date.now(); render(); }
  if (e.target.id === "importFile") importJson(e.target.files[0]);
  if (e.target.id === "impFile") readImportFile(e.target.files[0]);
  const t = e.target;
  if (t.dataset && t.dataset.form) formChanged(t, true);
  if (t.dataset && "formExtra" in t.dataset) { form.extra[t.dataset.formExtra] = t.value; }
  if (t.dataset && t.dataset.impCol !== undefined && imp) {
    const c = Number(t.dataset.impCol), v = t.value;
    if (v && !v.startsWith("extra:")) imp.mapping.forEach((m, k) => { if (m === v && k !== c) { imp.mapping[k] = ""; imp.how[k] = ""; } });
    imp.mapping[c] = v; imp.how[c] = "you"; render();
  }
  if (t.dataset && t.dataset.impVm && imp) { imp.valueMaps[t.dataset.impVm][t.dataset.raw] = t.value; render(); }
  if (t.dataset && t.dataset.pick !== undefined) { if (t.checked) picked.add(t.dataset.pick); else picked.delete(t.dataset.pick); const w = $("plistWrap"); if (w) w.innerHTML = plistTable(); return; }
  if (t.dataset && "pickall" in t.dataset) { const me = myEmail(); pg.rows.forEach((r) => { if (r.email === me) return; if (t.checked) picked.add(r.email); else picked.delete(r.email); }); const w = $("plistWrap"); if (w) w.innerHTML = plistTable(); return; }
  if (t.id === "pgFilter") { pg.filter = t.value; pg.rows = []; pg.cursor = null; pg.more = false; loadPage(true); }
  if (t.dataset && "impRole" in t.dataset && imp) { imp.role = t.value; render(); }
  if (t.dataset && "impHeader" in t.dataset && imp) { const g = guessColumns(imp.grid, impCtx(), t.checked); imp.hasHeader = g.hasHeader; imp.mapping = g.mapping; imp.how = g.how; imp.valueMaps = { college: {}, program: {} }; render(); }
});
let pgTimer = null;
document.addEventListener("input", (e) => {
  if (e.target.dataset && e.target.dataset.form) formChanged(e.target, false);
  if (e.target.dataset && "formExtra" in e.target.dataset && form) form.extra[e.target.dataset.formExtra] = e.target.value;
  if (e.target.id === "locSearch") { locFilter = e.target.value; const pos = e.target.selectionStart; render(); const el = $("locSearch"); if (el) { el.focus(); el.setSelectionRange(pos, pos); } return; }
  if (e.target.id === "pgSearch") { clearTimeout(pgTimer); const v = e.target.value; pgTimer = setTimeout(() => { pg.q = v.trim(); pg.rows = []; pg.cursor = null; pg.more = false; loadPage(true); }, 450); }
});
document.addEventListener("focusin", (e) => { const t = e.target; if (t.dataset && /^colleges\.\d+\.code$/.test(t.dataset.bind || "")) t.dataset.old = t.value; });

// the add-a-person form: typing just stores the value; choosing from a list may also fill or reset other fields
function formChanged(t, committed) {
  form = form || newForm(); const k = t.dataset.form; form[k] = t.value;
  if (!committed) return;
  if (k === "email") lookupExisting(t.value);
  if (k === "college") { const p = campus.programs.find((x) => slug(x.code) === slug(form.program)); if (p && form.college && slug(p.college) !== slug(form.college)) { form.program = ""; form.yearLevel = ""; } render(); }
  else if (k === "program") { const p = campus.programs.find((x) => slug(x.code) === slug(form.program)); if (p) { if (!form.college) form.college = p.college; if (Number(form.yearLevel) > Number(p.years)) form.yearLevel = ""; } render(); }
  else if (k === "email" && !form.name.trim()) { const g = nameFromEmail(form.email); if (g) form.name = g; render(); }
  else if (k === "email") render();
}
document.addEventListener("dragover", (e) => { if (e.target.closest && e.target.closest("#dropZone")) e.preventDefault(); });
document.addEventListener("drop", (e) => { const z = e.target.closest && e.target.closest("#dropZone"); if (!z) return; e.preventDefault(); readImportFile(e.dataTransfer.files[0]); });

document.addEventListener("click", async (e) => {
  const b = e.target.closest("[data-act]"); if (!b) return;
  const a = b.dataset.act, i = Number(b.dataset.i);
  const C = campus;
  switch (a) {
    case "tab": tab = b.dataset.v; render(); return;
    case "seed": campus = normalize(seed()); campusId = campusId || "alangilan"; if (!$("campusSel").options.length) $("campusSel").innerHTML = `<option value="${campusId}">${esc(campus.name)}</option>`; markDirty(); break;
    case "empty": campusId = campusId || "main"; campus = blank("My campus"); markDirty(); break;
    case "addCollege": C.colleges.push({ code: "NEW", name: "", policy: C.policies[0] ? C.policies[0].id : "" }); break;
    case "delCollege": if (!confirm("Remove this college and its programs?")) return; C.programs = C.programs.filter((p) => slug(p.college) !== slug(C.colleges[i].code)); C.colleges.splice(i, 1); break;
    case "addProgram": C.programs.push({ code: "NEW", name: "", college: b.dataset.code, years: 4, policy: "" }); break;
    case "delProgram": C.programs.splice(i, 1); break;
    case "addPolicy": C.policies.push({ id: uid("p"), name: "New set-up", type: "GRID", grid: [1, 2, 3, 4].map((y) => ({ year: y, odd: "OS", even: "OS" })) }); break;
    case "dupPolicy": { const c = JSON.parse(JSON.stringify(C.policies[i])); c.id = uid("p"); c.name += " (copy)"; C.policies.splice(i + 1, 0, c); break; }
    case "delPolicy": { const id = C.policies[i].id; const used = C.colleges.some((c) => c.policy === id) || C.programs.some((p) => p.policy === id);
      if (used && !confirm("This set-up is in use. Colleges using it will have no set-up (treated as on-site). Remove anyway?")) return;
      C.colleges.forEach((c) => { if (c.policy === id) c.policy = ""; }); C.programs.forEach((p) => { if (p.policy === id) p.policy = ""; }); C.policies.splice(i, 1); break; }
    case "flip": { const r = C.policies[i].grid[Number(b.dataset.k)]; r[b.dataset.w] = r[b.dataset.w] === "OL" ? "OS" : "OL"; break; }
    case "addYear": { const g = C.policies[i].grid; g.push({ year: g.length + 1, odd: "OS", even: "OS" }); break; }
    case "delYear": C.policies[i].grid.pop(); break;
    case "addOverride": { const t = mondayOf(Date.now()); C.overrides.push({ start: t, end: t, days: "", college: "*", mode: "OL", reason: "" }); break; }
    case "delOverride": C.overrides.splice(i, 1); break;
    case "addLocBulk": {
      const type = $("locBulkType").value, have = new Set(campus.locations.map((l) => (l.name || "").trim().toLowerCase()));
      let n = 0; for (const line of $("locBulk").value.split(/\r?\n/)) { const name = line.trim(); if (!name || have.has(name.toLowerCase())) continue; have.add(name.toLowerCase()); campus.locations.push({ name, type, note: "" }); n++; }
      say(n + " added. Press Save to keep them.", "ok"); break; }
    case "addLocation": C.locations.push({ name: "", type: "Building", note: "" }); break;
    case "delLocation": C.locations.splice(i, 1); break;
    case "addPeriod": C.periods.push({ label: "Period " + (C.periods.length + 1), start: "07:00", end: "08:30" }); break;
    case "delPeriod": C.periods.splice(i, 1); break;
    case "pvShift": previewDate = Number(b.dataset.v) === 0 ? Date.now() : previewDate + Number(b.dataset.v) * 86400000; render(); return;
    case "export": { const blob = new Blob([JSON.stringify(C, null, 2)], { type: "application/json" }); const u = URL.createObjectURL(blob);
      const l = document.createElement("a"); l.href = u; l.download = campusId + ".json"; l.click(); URL.revokeObjectURL(u); return; }
    case "importBtn": $("importFile").click(); return;
    case "formRole": { form = form || newForm(); form.role = b.dataset.v; render(); return; }
    case "addTeach": {
      form = form || newForm(); const p = $("tchProg").value, y = $("tchYear").value, t = y ? p + "-" + y : p;
      if (p && !form.teaches.includes(t)) form.teaches.push(t); render(); return; }
    case "delTeach": { e.preventDefault(); form.teaches.splice(i, 1); render(); return; }
    case "savePerson": await savePerson(); return;
    case "formClear": form = newForm(); render(); return;
    case "editPerson": {
      const r = findRow(b.dataset.e); if (!r) return; formExisting = r;
      form = { editing: true, email: r.email, name: r.name || "", role: r.role || "STUDENT", college: collegeCodeOf(r.college || r.department || ""), program: r.program || "", yearLevel: r.yearLevel ? String(r.yearLevel) : "",
        studentNo: r.studentNo || "", building: r.building || "", teaches: Array.isArray(r.teaches) ? r.teaches.slice() : [], extra: Object.assign({}, r.extra || {}) };
      render(); $("personCard").scrollIntoView({ behavior: "smooth", block: "start" }); return; }
    case "delPerson": {
      const em = b.dataset.e, me = auth.currentUser.email.toLowerCase();
      if (em === me) { say("You cannot remove yourself.", "warn"); return; }
      if (!confirm(`Remove ${em} from the roster? They will stop receiving alerts the next time their phone checks in, and cannot sign in to the app.`)) return;
      try { await deleteDoc(doc(db, "roster", em)); afterChange(); render(); say(`${em} removed.`, "ok"); } catch (err) { say("Could not remove: " + (err.code || err.message), "bad"); }
      return; }
    case "readPaste": { const g = splitCsv($("impPaste").value); if (!impStart(g)) { $("impMsg").className = "status warn"; $("impMsg").textContent = "Paste some rows first."; return; } render(); return; }
    case "impReset": imp = null; render(); return;
    case "importCsv": await importCsv(); return;
    case "addExtra": {
      const out = $("extraMsg"), label = $("extraLabel").value.trim(), key = extraKeyOf(label);
      if (!key) { out.className = "status warn"; out.textContent = "Type a name for the column."; return; }
      if (CORE_COLS.includes(key) || extraFields.some((f) => f.key === key)) { out.className = "status warn"; out.textContent = "That name is already used."; return; }
      if (extraFields.length >= MAX_EXTRA) return;
      await saveExtraFields(extraFields.concat([{ key, label, required: $("extraReq").checked }]), out); return; }
    case "delExtra": {
      const f = extraFields[i]; if (!f || !confirm(`Remove the column "${f.label}"? Saved values stay in the database but are no longer shown.`)) return;
      await saveExtraFields(extraFields.filter((_, k) => k !== i), $("extraMsg")); return; }
    case "genCode": {
      const code = randomCode(16), me = auth.currentUser.email.toLowerCase();
      const hours = Math.min(120, Math.max(1, Number($("codeHours").value) || 48));
      try { await setDoc(doc(db, "setup", code), { used: false, createdBy: me, createdAt: serverTimestamp(), expiresAt: Timestamp.fromMillis(Date.now() + hours * 3600000), note: $("codeNote").value.trim() });
        lastCode = code; codes = null; } catch (e) { say("Could not create the code: " + (e.code || e.message), "bad"); }
      render(); return; }
    case "delCode": { try { await deleteDoc(doc(db, "setup", b.dataset.c)); codes = null; } catch (e) { say("Could not delete: " + (e.code || e.message), "bad"); } render(); return; }
    case "addAdmin": {
      const out = $("adminMsg"), email = $("newAdminEmail").value.trim().toLowerCase(), name = $("newAdminName").value.trim();
      if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) { out.className = "status warn"; out.textContent = "Enter a valid email address."; return; }
      if (!confirm(`Make ${email} an admin? Admins can send alerts and change everything here.`)) return;
      try { await setDoc(doc(db, "roster", email), { role: "ADMIN", name: name || "", updatedBy: auth.currentUser.email.toLowerCase(), updatedAt: serverTimestamp() }, { merge: true });
        let note = "";
        try { if (campusId) await setDoc(doc(db, "roster", email), { campus: campusId, updatedBy: auth.currentUser.email.toLowerCase(), updatedAt: serverTimestamp() }, { merge: true }); }
        catch (e2) { note = " Their campus could not be set (" + (e2.code || e2.message) + "): use the Set campus button in the list."; }
        out.className = "status ok"; out.textContent = `${email} is now an admin${campusId && !note ? " of " + campusId : ""}. They can sign in to this console and the app with that email.` + note; afterChange(); render(); }
      catch (e) { out.className = "status bad"; out.textContent = "Rejected: " + (e.code || e.message); }
      return; }
    case "setAdminCampus": {
      const em = b.dataset.e;
      if (!campusId || !confirm(`Set ${em} to campus "${campusId}"? From then on this admin can only alert and re-alert that campus.`)) return;
      try { await setDoc(doc(db, "roster", em), { campus: campusId, updatedBy: auth.currentUser.email.toLowerCase(), updatedAt: serverTimestamp() }, { merge: true });
        adminRows = null; render(); say(`${em} is now an admin of ${campusId}.`, "ok"); }
      catch (err) { say("Could not set the campus: " + (err.code || err.message), "bad"); }
      return; }
    case "saveDomains": {
      const out = $("domainMsg");
      const list = $("domainsBox").value.split(/[,;\s]+/).map((d) => d.trim().toLowerCase().replace(/^@/, "")).filter(Boolean);
      const bad = list.filter((d) => !/^[a-z0-9-]+(\.[a-z0-9-]+)+$/.test(d));
      if (!list.length || bad.length) { out.className = "status warn"; out.textContent = bad.length ? "Not a valid domain: " + bad.join(", ") : "Enter at least one domain."; return; }
      const pub = list.filter((d) => PUBLIC_DOMAINS.includes(d));
      if (pub.length && !confirm(pub.join(", ") + " is a public email provider. Anyone could then be added as a student with such an address (only by an admin, one by one). Continue?")) return;
      try { await setDoc(doc(db, "settings", "app"), { allowedDomains: list, extraFields, updatedBy: auth.currentUser.email.toLowerCase(), updatedAt: serverTimestamp() });
        allowedDomains = list; out.className = "status ok"; out.textContent = "Saved."; }
      catch (e) { out.className = "status bad"; out.textContent = "Rejected: " + (e.code || e.message); }
      return; }
    case "runRollover": await runRollover(); return;
    case "exportRoster": exportRosterCsv(); return;
    case "pgMore": loadPage(false); return;
    case "pickClear": picked.clear(); { const w = $("plistWrap"); if (w) w.innerHTML = plistTable(); } return;
    case "delPicked": {
      if (bulkBusy) return;
      const me = myEmail(), emails = [...picked].filter((x) => x !== me), n = emails.length;
      if (!n) return;
      const label = (x) => { const r = findRow(x); return r && r.name ? `${r.name} (${x})` : x; };
      const sample = emails.slice(0, 8).map((x) => " - " + label(x)).join("\n") + (n > 8 ? `\n ...and ${n - 8} more` : "");
      if (!confirm(`Remove ${n} ${n === 1 ? "person" : "people"} from the roster?\n\n${sample}\n\nThey will stop receiving alerts the next time their phone checks in, and cannot sign in to the app. This cannot be undone here (download a roster backup first if you are not sure).`)) return;
      bulkBusy = true; let done = 0;
      try {
        for (let i = 0; i < n; i += 100) {                       // 100 per database batch, to stay inside Firestore's limits
          const chunk = emails.slice(i, i + 100), batch = writeBatch(db);
          chunk.forEach((x) => batch.delete(doc(db, "roster", x)));
          await batch.commit(); done += chunk.length;
          say(`Removing... ${done} of ${n}`, "warn");
        }
        bulkBusy = false; afterChange(true); render(); say(`${done} ${done === 1 ? "person" : "people"} removed.`, "ok");
      } catch (err) {
        bulkBusy = false; afterChange(true); render();
        say(`Removed ${done} of ${n}, then stopped: ${err.code || err.message}. The rest are still in the roster.`, "bad");
      }
      return; }
    case "recount": stats = null; render(); return;
    case "showProb": showProblem(b.dataset.k); return;
    case "fixStrays": {
      const me = auth.currentUser.email.toLowerCase(); let n = 0;
      try { for (let g = 0; g < 100; g++) {
        const snap = await getDocs(query(rosterCol(), where("campus", "not-in", ["", ...knownCampusIds.slice(0, 9)]), limit(450))); if (!snap.docs.length) break;
        const batch = writeBatch(db); snap.docs.forEach((d) => batch.set(doc(db, "roster", d.id), { campus: "", updatedBy: me, updatedAt: serverTimestamp() }, { merge: true })); await batch.commit(); n += snap.docs.length; }
        afterChange(true); render(); say(`Cleared the campus value for ${n} ${n === 1 ? "person" : "people"}. Check their other details in the People list.`, "ok"); } catch (e) { say("Could not fix: " + (e.code || e.message), "bad"); }
      return; }
    case "fixColleges": {
      const me = auth.currentUser.email.toLowerCase(); let n = 0;
      try { for (const c of campus.colleges) { if (!c.name || slug(c.name) === slug(c.code)) continue;
          for (let g = 0; g < 100; g++) { const snap = await getDocs(query(rosterCol(), where("college", "==", c.name), limit(450))); if (!snap.docs.length) break;
            const batch = writeBatch(db); snap.docs.forEach((d) => batch.set(doc(db, "roster", d.id), { college: c.code, updatedBy: me, updatedAt: serverTimestamp() }, { merge: true })); await batch.commit(); n += snap.docs.length; } }
        afterChange(true); render(); say(`Fixed ${n} roster ${n === 1 ? "entry" : "entries"}.`, "ok"); } catch (e) { say("Could not fix: " + (e.code || e.message), "bad"); }
      return; }
    default: return;
  }
  if (a !== "seed" && a !== "empty") markDirty();
  render();
});

function importJson(file) {
  if (!file) return;
  const r = new FileReader();
  r.onload = () => { try { campus = normalize(JSON.parse(r.result)); markDirty(); render(); say("Imported. Check it, then press Save.", "ok"); } catch (e) { say("That is not a valid campus file.", "bad"); } };
  r.readAsText(file);
}

async function saveExtraFields(list, out) {
  try { await setDoc(doc(db, "settings", "app"), { allowedDomains, extraFields: list, updatedBy: auth.currentUser.email.toLowerCase(), updatedAt: serverTimestamp() });
    extraFields = list; render(); }
  catch (e) { out.className = "status bad"; out.textContent = "Rejected: " + (e.code || e.message); }
}

async function importCsv() {
  const out = $("impMsg"); const p = imp && imp.plan;
  if (!p || !p.rows.length) { out.className = "status warn"; out.textContent = "Nothing to save yet."; return; }
  const me = auth.currentUser.email.toLowerCase();
  if (p.rows.length > 5000 && !confirm(`${p.rows.length} people means ${p.rows.length} database writes. The free plan allows 20,000 writes a day in total (alerts also use some). Continue?`)) return;
  try {
    for (let k = 0; k < p.rows.length; k += 200) {            // a batch holds at most 500 writes; stay well below
      const batch = writeBatch(db);
      for (const r of p.rows.slice(k, k + 200)) {
        const body = Object.assign({}, r.data, { updatedBy: me, updatedAt: serverTimestamp() });
        if (Object.keys(r.extra).length) body.extra = r.extra;
        batch.set(doc(db, "roster", r.email), body, { merge: true });
      }
      await batch.commit();
    }
    const saved = p.rows.length; imp = null; afterChange(true); render(); say(`Saved ${saved} ${saved === 1 ? "person" : "people"}.`, "ok");
  } catch (e) { out.className = "status bad"; out.textContent = "REJECTED: " + (e.code || "") + " " + (e.message || e); }
}
