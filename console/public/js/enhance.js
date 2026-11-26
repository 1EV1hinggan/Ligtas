// Accessibility and interaction layer. It only adds attributes and reveals text; it never changes the console's data or logic.
(() => {
  const $ = (id) => document.getElementById(id), panel = $("panel");
  $("gateIn").addEventListener("click", () => $("btnIn").click());
  const NAMES = { locBulk: "Location names, one per line", impPaste: "Pasted rows, for example copied from Excel", impFile: "Spreadsheet or CSV file to import" };
  const cellText = (c) => { const f = c && c.querySelector("input,select"); return (f ? (f.value || "") : (c ? c.textContent : "")).trim(); };
  function enhance() {
    panel.querySelectorAll("table").forEach((t) => {
      const head = t.rows[0]; if (!head) return;
      const hasHead = [...head.cells].every((c) => c.tagName === "TH");
      const names = hasHead ? [...head.cells].map((c) => c.textContent.trim()) : [];
      if (hasHead) { head.classList.add("hdr"); [...head.cells].forEach((c) => c.setAttribute("scope", "col")); }
      const hasFields = !!t.querySelector("td input, td select");
      const editable = hasFields || !!t.querySelector("td button");
      if (editable) t.classList.add("stack");           // on phones these tables become one labelled block per row
      if (editable && !hasFields) t.classList.add("stack-ro");   // read-only lists with row actions: compact "label  value" lines
      [...t.rows].slice(hasHead ? 1 : 0).forEach((r) => {
        const rowName = cellText(r.cells[0]);
        [...r.cells].forEach((c, i) => {
          if (names[i] && !c.dataset.label && c.tagName === "TD" && !(t.classList.contains("stack-ro") && i === 0)) c.dataset.label = names[i];
          if (c.tagName === "TD" && !c.querySelector("input,select,button,a") && !c.textContent.trim()) c.dataset.empty = "";
          c.querySelectorAll("input,select,textarea").forEach((f) => {
            if (f.type === "hidden" || f.getAttribute("aria-label") || f.closest("label")) return;
            const base = names[i] || (rowName && i > 0 ? "Choice" : "Value");
            f.setAttribute("aria-label", base + (i > 0 && rowName ? " for " + rowName : ""));
          });
        });
      });
      const cells = [...t.querySelectorAll("td[title]")];
      cells.forEach((c, i) => { c.tabIndex = i === 0 ? 0 : -1; });
    });
    Object.entries(NAMES).forEach(([id, name]) => { const f = $(id); if (f && !f.getAttribute("aria-label")) f.setAttribute("aria-label", name); });
    panel.querySelectorAll('a[data-act="delTeach"]').forEach((a) => { const chip = a.closest(".tag"); a.setAttribute("aria-label", "Remove " + (chip ? chip.firstChild.textContent.trim() : "")); });
  }
  const reveal = (td) => {
    const card = td.closest(".card"), out = card && card.querySelector(".why-out"); if (!out) return;
    const t = td.closest("table"), col = t.rows[0].cells[td.cellIndex];
    const rowName = [...td.parentElement.cells].filter((c) => !c.hasAttribute("title")).map((c) => c.textContent.trim()).join(" ");
    out.textContent = rowName + (col ? " · " + col.textContent.trim() : "") + ": " + td.title;
  };
  panel.addEventListener("click", (e) => { const td = e.target.closest("td[title]"); if (td) reveal(td); });
  panel.addEventListener("focusin", (e) => { const td = e.target.closest && e.target.closest("td[title]"); if (td) reveal(td); });
  panel.addEventListener("keydown", (e) => {   // arrow keys move through the preview like a grid
    const td = e.target.closest && e.target.closest("td[title]"); if (!td) return;
    const d = { ArrowRight: [0, 1], ArrowLeft: [0, -1], ArrowDown: [1, 0], ArrowUp: [-1, 0] }[e.key]; if (!d) return;
    const rows = [...td.closest("table").rows], ri = rows.indexOf(td.parentElement) + d[0]; if (ri < 1 || ri >= rows.length) return;
    const mine = [...td.parentElement.cells].filter((c) => c.hasAttribute("title")).indexOf(td);
    const row = [...rows[ri].cells].filter((c) => c.hasAttribute("title")); const target = row[Math.max(0, Math.min(row.length - 1, mine + d[1]))];
    if (target) { e.preventDefault(); td.tabIndex = -1; target.tabIndex = 0; target.focus(); }
  });
  let queued = false;
  new MutationObserver(() => { if (queued) return; queued = true; requestAnimationFrame(() => { queued = false; enhance(); }); }).observe(panel, { childList: true, subtree: true });
  enhance();
})();
