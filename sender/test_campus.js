const { campusProblem, slug } = require("./lib/campus");
const assert = require("assert");
const ok = (c, t, ty = "ALERT") => assert.strictEqual(campusProblem(c, t, ty), "", `should allow ${c} -> ${t}`);
const no = (c, t, ty = "ALERT") => assert(campusProblem(c, t, ty), `should block ${c} -> ${t}`);
assert.strictEqual(slug("BSU Alangilan"), "bsu_alangilan");
ok("", "/topics/campus_lipa");                                   // admin with no campus: not limited (as before)
ok("alangilan", "/topics/campus_alangilan");
ok("Alangilan", "/topics/campus_alangilan");                     // case does not matter
ok("alangilan", "condition:'campus_alangilan' in topics && 'col_cics' in topics && 'prog_bsit' in topics");
no("alangilan", "/topics/campus_lipa");                          // other campus
no("alangilan", "condition:'campus_lipa' in topics && 'bld_x' in topics");
no("alangilan", "condition:'campus_alangilan' in topics || 'campus_lipa' in topics");   // mixing is blocked
no("alangilan", "/topics/role_student");                         // no campus at all
no("alangilan", "/topics/ligtas_campus");                        // old all-campus topic cannot send a new ALERT
ok("alangilan", "/topics/ligtas_campus", "ALL_CLEAR");           // but the app's all-clear fallback still works
ok("alangilan", "/topics/ligtas_campus", "FALSE_ALARM");
ok("alangilan", "condition:'teach_bsit_2' in topics || 'teach_bsit_all' in topics");   // teacher topics (known gap)
ok("alangilan", "/topics/teachp_bsit");
no("alangilan", "condition:'teach_bsit_2' in topics && 'role_student' in topics");
console.log("campus tests: all passed");
