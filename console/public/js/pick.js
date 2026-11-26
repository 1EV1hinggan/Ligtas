// Custom pickers. The native control stays in the page (hidden) and remains the single source of truth:
// choosing something sets its value and fires the same input/change events the console already listens to.
(() => {
  const NS = "http://www.w3.org/2000/svg";
  const CHEV = '<svg class="chev" width="12" height="8" viewBox="0 0 12 8" aria-hidden="true"><path d="M1 1.5l5 5 5-5" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>';
  const CAL = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="5" width="18" height="16" rx="3"/><path d="M8 3v4M16 3v4M3 10h18"/></svg>';
  const CLK = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>';
  const CK = '<svg class="ck" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 12.5l4.5 4.5L19 7"/></svg>';
  const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
  const p2 = (n) => String(n).padStart(2, "0");
  const MONTHS = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"];
  let pop = null, owner = null, cleanup = null;

  function fire(el) { el.dispatchEvent(new Event("input", { bubbles: true })); el.dispatchEvent(new Event("change", { bubbles: true })); }
  function close(focus) {
    if (!pop) return; const p = pop, o = owner, c = cleanup; pop = owner = cleanup = null;
    if (c) c();
    p.classList.add("out"); setTimeout(() => p.remove(), 130);
    if (o) { o.setAttribute("aria-expanded", "false"); if (focus && document.contains(o)) o.focus(); }
  }
  function place(el, trig, minW) {
    const r = trig.getBoundingClientRect(), vw = innerWidth, vh = innerHeight, m = 8;
    el.style.minWidth = Math.max(minW || 0, r.width) + "px";
    el.style.left = "0px"; el.style.top = "0px";
    const w = el.offsetWidth, h = el.offsetHeight;
    let left = Math.min(Math.max(m, r.left), vw - w - m), top = r.bottom + 6, up = false;
    if (top + h > vh - m && r.top - 6 - h > m) { top = r.top - 6 - h; up = true; }
    else if (top + h > vh - m) { top = Math.max(m, vh - h - m); }
    el.style.left = left + "px"; el.style.top = top + "px"; el.classList.toggle("up", up);
    el.style.setProperty("--ox", Math.max(0, r.left - left + 24) + "px");
  }
  function openPop(trig, build, minW) {
    if (owner === trig) { close(true); return; }
    close(); const el = document.createElement("div"); el.className = "fx-pop";
    const api = build(el) || {}; document.body.appendChild(el); pop = el; owner = trig; trig.setAttribute("aria-expanded", "true");
    place(el, trig, minW);
    const onDown = (e) => { if (!el.contains(e.target) && !trig.contains(e.target)) { api.beforeClose && api.beforeClose(); close(false); } };
    const onScroll = (e) => { if (!el.contains(e.target)) { api.beforeClose && api.beforeClose(); close(false); } };
    const onKey = (e) => { if (e.key === "Escape") { e.preventDefault(); close(true); } else if (api.key) api.key(e); };
    const onResize = () => place(el, trig, minW);
    document.addEventListener("pointerdown", onDown, true); document.addEventListener("scroll", onScroll, true);
    document.addEventListener("keydown", onKey, true); addEventListener("resize", onResize);
    cleanup = () => { document.removeEventListener("pointerdown", onDown, true); document.removeEventListener("scroll", onScroll, true); document.removeEventListener("keydown", onKey, true); removeEventListener("resize", onResize); };
    api.after && api.after();
  }
  const labelOf = (el) => { const a = el.getAttribute("aria-label"); if (a) return a; const l = el.closest("label"); if (l) { const n = [...l.childNodes].find((x) => x.nodeType === 3 && x.textContent.trim()); if (n) return n.textContent.trim(); } const td = el.closest("td"); return (td && td.dataset.label) || ""; };
  function makeTrigger(native, icon) {
    const b = document.createElement("button"); b.type = "button"; b.className = "fx-dd sec"; b.setAttribute("aria-haspopup", icon === CHEV ? "listbox" : "dialog"); b.setAttribute("aria-expanded", "false");
    b.innerHTML = `<span class="v"></span>${icon}`; b.className = "fx-dd";
    native.classList.add("fx-native"); native.tabIndex = -1; native.setAttribute("aria-hidden", "true");
    native.insertAdjacentElement("afterend", b); b._native = native; native._trig = b;
    const lab = native.closest("label"); if (lab) lab.addEventListener("click", (e) => { if (!e.target.closest("button")) { e.preventDefault(); b.focus(); } });
    return b;
  }
  function syncCommon(native, b, text, empty) {
    b.querySelector(".v").textContent = text; b.classList.toggle("ph", !!empty);
    b.disabled = native.disabled; const hid = native.classList.contains("hide") || native.hidden; b.classList.toggle("hide", hid);
    const l = labelOf(native); if (l) b.setAttribute("aria-label", l + ": " + text);
  }

  /* ---------- select ---------- */
  function enhanceSelect(sel) {
    if (sel.dataset.fx) return; sel.dataset.fx = "1";
    const b = makeTrigger(sel, CHEV);
    const sync = () => { const longest = Math.max(0, ...[...sel.options].map((o) => o.textContent.trim().length)); b.style.minWidth = Math.min(420, Math.max(92, longest * 8 + 52)) + "px"; const o = sel.options[sel.selectedIndex]; syncCommon(sel, b, o ? o.textContent.trim() : "", !sel.value && sel.options.length > 0); };
    b._sync = sync; sync();
    b.addEventListener("click", () => openList(b, sel));
    b.addEventListener("keydown", (e) => { if (!pop && ["ArrowDown", "ArrowUp"].includes(e.key)) { e.preventDefault(); openList(b, sel); } });
    new MutationObserver(sync).observe(sel, { childList: true, subtree: true, attributes: true, characterData: true });
    return b;
  }
  function openList(b, sel) {
    openPop(b, (el) => {
      const opts = [...sel.options]; let act = Math.max(0, sel.selectedIndex), typed = "", tt = 0;
      el.innerHTML = `<div class="fx-list" role="listbox">${opts.map((o, i) => `<div class="fx-opt${!o.value ? " dim" : ""}" role="option" id="fxo${i}" data-i="${i}" aria-selected="${i === sel.selectedIndex}"${o.disabled ? ' aria-disabled="true"' : ""}><span class="t">${esc(o.textContent.trim())}</span>${CK}</div>`).join("") || '<div class="fx-empty">Nothing to choose</div>'}</div>`;
      const list = el.firstChild, items = () => [...list.querySelectorAll(".fx-opt")];
      const mark = () => { items().forEach((n, i) => n.classList.toggle("act", i === act)); const n = items()[act]; if (n) { b.setAttribute("aria-activedescendant", n.id); n.scrollIntoView({ block: "nearest" }); } };
      const pick = (i) => { const o = opts[i]; if (!o || o.disabled) return; close(true); if (sel.value !== o.value || sel.selectedIndex !== i) { sel.selectedIndex = i; b._sync(); fire(sel); } };
      list.addEventListener("click", (e) => { const n = e.target.closest(".fx-opt"); if (n) pick(+n.dataset.i); });
      list.addEventListener("pointermove", (e) => { const n = e.target.closest(".fx-opt"); if (n && +n.dataset.i !== act) { act = +n.dataset.i; items().forEach((x, i) => x.classList.toggle("act", i === act)); } });
      return {
        after: () => { list.style.maxHeight = Math.min(340, innerHeight * .5) + "px"; mark(); const s = items()[sel.selectedIndex]; if (s) s.scrollIntoView({ block: "center" }); },
        key: (e) => {
          if (e.key === "ArrowDown") { e.preventDefault(); act = Math.min(opts.length - 1, act + 1); mark(); }
          else if (e.key === "ArrowUp") { e.preventDefault(); act = Math.max(0, act - 1); mark(); }
          else if (e.key === "Home") { e.preventDefault(); act = 0; mark(); } else if (e.key === "End") { e.preventDefault(); act = opts.length - 1; mark(); }
          else if (e.key === "Enter" || e.key === " ") { e.preventDefault(); pick(act); }
          else if (e.key === "Tab") { close(false); }
          else if (e.key.length === 1) { clearTimeout(tt); typed += e.key.toLowerCase(); tt = setTimeout(() => typed = "", 700); const i = opts.findIndex((o) => o.textContent.trim().toLowerCase().startsWith(typed)); if (i >= 0) { act = i; mark(); } }
        },
        beforeClose: () => b.removeAttribute("aria-activedescendant")
      };
    }, 180);
  }

  /* ---------- date ---------- */
  const pd = (v) => { const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(v || ""); return m ? { y: +m[1], m: +m[2] - 1, d: +m[3] } : null; };
  const fmtD = (v) => { const d = pd(v); return d ? `${MONTHS[d.m].slice(0, 3)} ${d.d}, ${d.y}` : ""; };
  function enhanceDate(inp) {
    if (inp.dataset.fx) return; inp.dataset.fx = "1";
    const b = makeTrigger(inp, CAL);
    const sync = () => syncCommon(inp, b, fmtD(inp.value) || "Select a date", !inp.value);
    b._sync = sync; sync();
    new MutationObserver(sync).observe(inp, { attributes: true });
    b.addEventListener("click", () => openDate(b, inp));
    b.addEventListener("keydown", (e) => { if (!pop && e.key === "ArrowDown") { e.preventDefault(); openDate(b, inp); } });
  }
  function openDate(b, inp) {
    openPop(b, (el) => {
      const cur = pd(inp.value), now = new Date(), today = { y: now.getFullYear(), m: now.getMonth(), d: now.getDate() };
      let vy = (cur || today).y, vm = (cur || today).m, mode = "days", focusD = (cur || today).d;
      const iso = (y, m, d) => `${y}-${p2(m + 1)}-${p2(d)}`, inRange = (s) => (!inp.min || s >= inp.min) && (!inp.max || s <= inp.max);
      const commit = (v) => { close(true); if (inp.value !== v) { inp.value = v; b._sync(); fire(inp); } };
      el.classList.add("fx-cal"); el.setAttribute("role", "dialog"); el.setAttribute("aria-label", "Choose a date");
      const draw = () => {
        const head = `<div class="fx-ch"><button type="button" class="ttl" aria-expanded="${mode === "months"}" data-a="mode">${mode === "months" ? vy : MONTHS[vm] + " " + vy}${CHEV}</button>
          <button type="button" class="fx-ib" data-a="prev" aria-label="${mode === "months" ? "Previous year" : "Previous month"}"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M15 5l-7 7 7 7"/></svg></button>
          <button type="button" class="fx-ib" data-a="next" aria-label="${mode === "months" ? "Next year" : "Next month"}"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M9 5l7 7-7 7"/></svg></button></div>`;
        let body;
        if (mode === "months") body = `<div class="fx-grid fx-mg">${MONTHS.map((n, i) => `<button type="button" class="fx-d m${i === vm ? " sel" : ""}" data-m="${i}">${n.slice(0, 3)}</button>`).join("")}</div>`;
        else {
          const first = new Date(vy, vm, 1).getDay(), dim = new Date(vy, vm + 1, 0).getDate(), prevDim = new Date(vy, vm, 0).getDate(); let cells = "";
          for (let i = 0; i < 42; i++) {
            let d = i - first + 1, y = vy, m = vm, o = "";
            if (d < 1) { d += prevDim; m--; o = " o"; if (m < 0) { m = 11; y--; } } else if (d > dim) { d -= dim; m++; o = " o"; if (m > 11) { m = 0; y++; } }
            const s = iso(y, m, d), isSel = cur && cur.y === y && cur.m === m && cur.d === d, isNow = today.y === y && today.m === m && today.d === d;
            cells += `<button type="button" class="fx-d${o}${isSel ? " sel" : ""}${isNow ? " now" : ""}" data-v="${s}" tabindex="${!o && d === focusD ? 0 : -1}"${inRange(s) ? "" : " disabled"} aria-label="${MONTHS[m]} ${d}, ${y}"${isSel ? ' aria-pressed="true"' : ""}>${d}</button>`;
          }
          body = `<div class="fx-grid">${["Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"].map((w) => `<div class="fx-wd">${w}</div>`).join("")}${cells}</div>`;
        }
        el.innerHTML = head + body + `<div class="fx-foot"><button type="button" class="ghost" data-a="clear">Clear</button><button type="button" class="ghost" data-a="today">Today</button></div>`;
      };
      draw();
      el.addEventListener("click", (e) => {
        const t = e.target.closest("button"); if (!t) return;
        if (t.dataset.v) return commit(t.dataset.v);
        if (t.dataset.m !== undefined) { vm = +t.dataset.m; mode = "days"; return draw(); }
        const a = t.dataset.a;
        if (a === "mode") { mode = mode === "days" ? "months" : "days"; draw(); }
        else if (a === "prev") { if (mode === "months") vy--; else { vm--; if (vm < 0) { vm = 11; vy--; } } draw(); }
        else if (a === "next") { if (mode === "months") vy++; else { vm++; if (vm > 11) { vm = 0; vy++; } } draw(); }
        else if (a === "clear") commit("");
        else if (a === "today") commit(iso(today.y, today.m, today.d));
      });
      const focusDay = () => { const n = el.querySelector(`.fx-d[data-v="${iso(vy, vm, focusD)}"]`); if (n) n.focus(); };
      return {
        after: () => { const n = el.querySelector('.fx-d[tabindex="0"]'); if (n) n.focus({ preventScroll: true }); },
        key: (e) => {
          if (mode !== "days" || !e.target.closest || !e.target.closest(".fx-d")) return;
          const mv = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7 }[e.key]; if (!mv) return; e.preventDefault();
          const d = new Date(vy, vm, focusD + mv); vy = d.getFullYear(); vm = d.getMonth(); focusD = d.getDate(); draw(); focusDay();
        }
      };
    });
  }

  /* ---------- time ---------- */
  const fmtT = (v) => { const m = /^(\d{2}):(\d{2})/.exec(v || ""); if (!m) return ""; const h = +m[1]; return `${p2(h % 12 || 12)}:${m[2]} ${h < 12 ? "AM" : "PM"}`; };
  function enhanceTime(inp) {
    if (inp.dataset.fx) return; inp.dataset.fx = "1";
    const b = makeTrigger(inp, CLK);
    const sync = () => syncCommon(inp, b, fmtT(inp.value) || "Select a time", !inp.value);
    b._sync = sync; sync();
    new MutationObserver(sync).observe(inp, { attributes: true });
    b.addEventListener("click", () => openTime(b, inp));
    b.addEventListener("keydown", (e) => { if (!pop && e.key === "ArrowDown") { e.preventDefault(); openTime(b, inp); } });
  }
  function openTime(b, inp) {
    openPop(b, (el) => {
      const m = /^(\d{2}):(\d{2})/.exec(inp.value || ""); const h24 = m ? +m[1] : 8, min0 = m ? +m[2] : 0;
      let h = h24 % 12 || 12, mi = min0, pm = h24 >= 12, dirty = false;
      const mins = [...new Set([...Array(12).keys()].map((i) => i * 5).concat(mi))].sort((a, b) => a - b);
      el.classList.add("fx-time"); el.setAttribute("role", "dialog"); el.setAttribute("aria-label", "Choose a time");
      const col = (lab, arr, sel, k, cls = "") => `<div class="fx-col ${cls}" aria-label="${lab}"><div class="lab">${lab}</div>${arr.map(([v, t]) => `<button type="button" class="fx-t${v === sel ? " sel" : ""}" data-k="${k}" data-v="${v}">${t}</button>`).join("")}</div>`;
      const draw = () => {
        const keep = [...el.querySelectorAll(".fx-col")].map((c) => c.scrollTop);
        el.innerHTML = `<div class="fx-cols">${col("Hour", [...Array(12).keys()].map((i) => [i + 1, p2(i + 1)]), h, "h")}${col("Min", mins.map((v) => [v, p2(v)]), mi, "m")}${col("", [[0, "AM"], [1, "PM"]], pm ? 1 : 0, "p", "ampm")}</div>
          <div class="fx-foot"><button type="button" class="ghost" data-a="now">Now</button><button type="button" data-a="done">Done</button></div>`;
        [...el.querySelectorAll(".fx-col")].forEach((c, i) => { c.scrollTop = keep[i] || 0; });
      };
      const value = () => p2((h % 12) + (pm ? 12 : 0)) + ":" + p2(mi);
      const commit = () => { if (dirty && inp.value !== value()) { inp.value = value(); b._sync(); fire(inp); } dirty = false; };
      draw();
      el.addEventListener("click", (e) => {
        const t = e.target.closest("button"); if (!t) return;
        if (t.dataset.k) { const v = +t.dataset.v; if (t.dataset.k === "h") h = v; else if (t.dataset.k === "m") mi = v; else pm = !!v; dirty = true; draw(); return; }
        if (t.dataset.a === "now") { const n = new Date(); h = n.getHours() % 12 || 12; mi = n.getMinutes(); pm = n.getHours() >= 12; if (!mins.includes(mi)) mins.push(mi), mins.sort((a, b) => a - b); dirty = true; draw(); el.querySelectorAll(".fx-t.sel").forEach((x) => x.scrollIntoView({ block: "center" })); return; }
        if (t.dataset.a === "done") { commit(); close(true); }
      });
      return {
        after: () => el.querySelectorAll(".fx-t.sel").forEach((x) => x.scrollIntoView({ block: "center" })),
        beforeClose: commit,
        key: (e) => { if (e.key === "Enter") { e.preventDefault(); commit(); close(true); } }
      };
    });
  }

  /* ---------- text input with suggestions (replaces the browser's datalist popup) ---------- */
  function enhanceSuggest(inp) {
    if (inp.dataset.fx) return; inp.dataset.fx = "1";
    const id = inp.getAttribute("list"), dl = document.getElementById(id); if (!dl) return;
    inp.dataset.fxlist = id; inp.removeAttribute("list"); inp.setAttribute("autocomplete", "off");
    inp.setAttribute("role", "combobox"); inp.setAttribute("aria-autocomplete", "list"); inp.setAttribute("aria-expanded", "false");
    let act = -1;
    const items = () => { const q = inp.value.trim().toLowerCase(); return [...dl.options].map((o) => o.value).filter((v) => v && (!q || v.toLowerCase().includes(q)) && v !== inp.value).slice(0, 40); };
    const show = () => {
      const list = items(); if (!list.length) { if (owner === inp) close(false); return; }
      if (owner === inp && pop) { fill(list); return; }
      close(); const el = document.createElement("div"); el.className = "fx-pop"; document.body.appendChild(el); pop = el; owner = inp; inp.setAttribute("aria-expanded", "true");
      fill(list); place(el, inp, 160);
      const onDown = (e) => { if (!el.contains(e.target) && e.target !== inp) close(false); };
      const onScroll = (e) => { if (!el.contains(e.target)) close(false); };
      document.addEventListener("pointerdown", onDown, true); document.addEventListener("scroll", onScroll, true);
      cleanup = () => { document.removeEventListener("pointerdown", onDown, true); document.removeEventListener("scroll", onScroll, true); inp.setAttribute("aria-expanded", "false"); };
    };
    const fill = (list) => {
      act = -1; pop.innerHTML = `<div class="fx-list" role="listbox">${list.map((v, i) => `<div class="fx-opt" role="option" data-i="${i}"><span class="t">${esc(v)}</span></div>`).join("")}</div>`;
      pop.firstChild.addEventListener("pointerdown", (e) => { const n = e.target.closest(".fx-opt"); if (!n) return; e.preventDefault(); choose(list[+n.dataset.i]); });
      pop._list = list; place(pop, inp, 160);
    };
    const choose = (v) => { close(false); inp.value = v; fire(inp); };
    inp.addEventListener("focus", show); inp.addEventListener("input", () => { if (document.activeElement === inp) show(); });
    inp.addEventListener("keydown", (e) => {
      if (owner !== inp || !pop) return; const n = [...pop.querySelectorAll(".fx-opt")];
      if (e.key === "ArrowDown" || e.key === "ArrowUp") { e.preventDefault(); act = (act + (e.key === "ArrowDown" ? 1 : -1) + n.length) % n.length; n.forEach((x, i) => x.classList.toggle("act", i === act)); n[act].scrollIntoView({ block: "nearest" }); }
      else if (e.key === "Enter" && act >= 0) { e.preventDefault(); choose(pop._list[act]); }
      else if (e.key === "Escape") { e.stopPropagation(); close(false); }
    });
    inp.addEventListener("blur", () => setTimeout(() => { if (owner === inp) close(false); }, 120));
  }

  /* ---------- wire up: now, and whenever the console redraws ---------- */
  function scan(root) {
    root.querySelectorAll("select:not([data-fx])").forEach(enhanceSelect);
    root.querySelectorAll("input[type=date]:not([data-fx])").forEach(enhanceDate);
    root.querySelectorAll("input[type=time]:not([data-fx])").forEach(enhanceTime);
    root.querySelectorAll("input[list]:not([data-fx])").forEach(enhanceSuggest);
  }
  const app = document.getElementById("app") || document.body;
  let q = 0; new MutationObserver(() => { cancelAnimationFrame(q); q = requestAnimationFrame(() => scan(document)); }).observe(document.body, { childList: true, subtree: true });
  // values set by the console's own code (not by a click) don't mutate the DOM, so refresh the labels after any change
  document.addEventListener("change", () => setTimeout(() => document.querySelectorAll("[data-fx]").forEach((n) => n._trig && n._trig._sync && n._trig._sync()), 0), true);
  scan(document);
})();
