// Backdrop + section-change cue. Purely visual: no data, no logic of the console is touched.
(() => {
  const cv = document.createElement("canvas"); cv.id = "fx"; cv.setAttribute("aria-hidden", "true");
  document.body.prepend(cv);
  const ctx = cv.getContext("2d"); if (!ctx) return;
  const mq = matchMedia("(prefers-reduced-motion: reduce)");
  let W = 0, H = 0, dpr = 1, raf = 0, t0 = performance.now();
  const size = () => { dpr = Math.min(devicePixelRatio || 1, 1.5); W = innerWidth; H = innerHeight; cv.width = W * dpr; cv.height = H * dpr; ctx.setTransform(dpr, 0, 0, dpr, 0, 0); draw(0); };
  const PERIOD = 9, RINGS = 3, GAP = 52;
  function draw(sec) {
    ctx.clearRect(0, 0, W, H);
    ctx.fillStyle = "#090F1C"; ctx.fillRect(0, 0, W, H);
    // drifting orbs
    const orbs = [[.12, .18, 520, "46,84,170", .30, 0], [.88, .78, 600, "36,70,150", .26, 2.1], [.55, .52, 420, "138,181,255", .07, 4.2]];
    for (const [x, y, r, c, a, ph] of orbs) {
      const cx = x * W + Math.sin(sec / 14 + ph) * 60, cy = y * H + Math.cos(sec / 17 + ph) * 46;
      const g = ctx.createRadialGradient(cx, cy, 0, cx, cy, r);
      g.addColorStop(0, `rgba(${c},${a})`); g.addColorStop(1, `rgba(${c},0)`);
      ctx.fillStyle = g; ctx.fillRect(cx - r, cy - r, r * 2, r * 2);
    }
    // alert broadcast: rings leave the top right, the dot grid lights up as each ring passes
    const ox = W * .9, oy = -H * .06, maxR = Math.hypot(Math.max(ox, W - ox), H - oy) + 80;
    const radii = []; for (let i = 0; i < RINGS; i++) radii.push((((sec / PERIOD) + i / RINGS) % 1) * maxR);
    ctx.lineWidth = 1.2;
    for (const r of radii) {
      const k = 1 - r / maxR;
      ctx.strokeStyle = `rgba(138,181,255,${.16 * k})`; ctx.beginPath(); ctx.arc(ox, oy, r, 0, 7); ctx.stroke();
      ctx.strokeStyle = `rgba(138,181,255,${.05 * k})`; ctx.lineWidth = 9; ctx.beginPath(); ctx.arc(ox, oy, r, 0, 7); ctx.stroke(); ctx.lineWidth = 1.2;
    }
    for (let y = GAP / 2; y < H; y += GAP) for (let x = ((y / GAP) % 2 ? GAP / 2 : 0) + 8; x < W; x += GAP) {
      const d = Math.hypot(x - ox, y - oy); let glow = 0;
      for (const r of radii) { const e = Math.abs(d - r); if (e < 70) glow = Math.max(glow, (1 - e / 70) * (1 - r / maxR)); }
      ctx.fillStyle = `rgba(150,185,255,${.10 + glow * .75})`;
      ctx.beginPath(); ctx.arc(x, y, 1.1 + glow * 1.9, 0, 7); ctx.fill();
    }
  }
  const loop = (now) => { draw(Math.max(0, now - t0) / 1000); raf = requestAnimationFrame(loop); };
  const start = () => { cancelAnimationFrame(raf); if (!mq.matches && !document.hidden) raf = requestAnimationFrame(loop); else draw(0); };
  addEventListener("resize", size); document.addEventListener("visibilitychange", start); mq.addEventListener && mq.addEventListener("change", start);
  size(); start();

  // cascade the panel in only when the section itself changes
  const panel = document.getElementById("panel"), nav = document.getElementById("tabs"); let last = "";
  const cue = () => {
    const cur = nav.querySelector('[aria-current="page"]'), key = cur ? cur.textContent : "";
    if (key && key !== last) { last = key; panel.classList.remove("enter"); void panel.offsetWidth; panel.classList.add("enter"); setTimeout(() => panel.classList.remove("enter"), 1100); }
  };
  new MutationObserver(cue).observe(panel, { childList: true }); cue();
})();
