// 足球人生模拟器前端 —— 纯原生 JS，零依赖
// API 契约见设计文档 §16；决策端点为 SSE 流式（meta → narrative* → done | error）
const $ = (id) => document.getElementById(id);
let state = null; // 最近一次后端返回的完整状态（player / narrative / options）

const STORY_KEY = "footboll_last_story"; // 刷新恢复用的叙事缓存

async function api(path, method = "GET", body = null) {
  const opt = { method, headers: { "Content-Type": "application/json" } };
  if (body) opt.body = JSON.stringify(body);
  let r;
  try {
    r = await fetch(path, opt);
  } catch {
    const e = new Error("无法连接后端，请确认服务已启动");
    e.status = 0;
    throw e;
  }
  if (!r.ok) {
    const data = await r.json().catch(() => null);
    // 截断后端 detail（可能包含完整 URL 与状态码细节）
    let msg = (data && data.detail) || r.statusText;
    if (typeof msg === "string" && msg.length > 120) msg = msg.slice(0, 120) + "…";
    const e = new Error(msg);
    e.status = r.status;
    throw e;
  }
  return r.json();
}

// 启动分流：未配置(403 / configured=false) → 设置页；已配置 → 尝试载入存档
async function init() {
  // 防踩坑：直接双击 index.html 打开时（file:// 协议）API 无法访问
  if (location.protocol === "file:") {
    document.body.innerHTML =
      '<div style="max-width:600px;margin:80px auto;padding:32px;background:#1a212b;' +
      'border-radius:12px;color:#e5e7eb;line-height:2">' +
      '<h2>⚠️ 请通过服务地址访问</h2>' +
      '<p>不能直接双击打开 index.html（file:// 协议下无法连接后端）。</p>' +
      '<p>请先启动服务：<code>python -m uvicorn app.main:app --port 8000</code></p>' +
      '<p>然后访问：<b>http://127.0.0.1:8000/</b></p></div>';
    return;
  }
  let s;
  try {
    s = await api("/api/setup/status");
  } catch (e) {
    if (e.status === 403) { show("setup"); return; }
    show("setup");
    setMsg("cfg_msg", e.message);
    return;
  }
  if (!s.configured) { show("setup"); return; }
  try {
    await refreshState();
    show("game");
    renderAll(); // 恢复存档后渲染面板/叙事/选项
  } catch {
    show("create"); // 已配置但未建档
  }
}

function show(id) {
  ["setup", "create", "game"].forEach((s) => $(s).classList.add("hidden"));
  $(id).classList.remove("hidden");
}

function setMsg(id, text, ok = false) {
  const el = $(id);
  el.textContent = text;
  el.className = "msg" + (ok ? " ok" : "");
}

async function saveConfig() {
  try {
    const r = await api("/api/setup/config", "POST", {
      api_key: $("cfg_key").value.trim(),
      base_url: $("cfg_base").value.trim(),
      model: $("cfg_model").value.trim(),
    });
    if (r.ok || r.configured) {
      setMsg("cfg_msg", "配置成功！", true);
      show("create");
    } else {
      setMsg("cfg_msg", "配置失败，请重试");
    }
  } catch (e) {
    setMsg("cfg_msg", e.message);
  }
}

async function createGame() {
  const name = $("p_name").value.trim();
  if (!name) { alert("请填写姓名"); return; }
  try {
    const r = await api("/api/game/new", "POST", {
      name,
      birth_year: 2007,
      position: $("p_position").value,
      foot: $("p_foot").value,
      height: +$("p_height").value,
      weight: +$("p_weight").value,
      region: $("p_region").value.trim(),
      academy: $("p_academy").value,
    });
    state = r;
    cacheStory(r);
    show("game");
    renderAll();
  } catch (e) { alert(e.message); }
}

// ---- SSE 流式决策 ----

// 解析 fetch 响应流中的 SSE 事件，逐事件回调
async function consumeStream(resp, handlers) {
  const reader = resp.body.getReader();
  const decoder = new TextDecoder();
  let buf = "";
  let eventName = "message";
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buf += decoder.decode(value, { stream: true });
    // SSE 事件以空行分隔
    let idx;
    while ((idx = buf.indexOf("\n\n")) >= 0) {
      const rawEvent = buf.slice(0, idx);
      buf = buf.slice(idx + 2);
      let data = "";
      for (const line of rawEvent.split("\n")) {
        if (line.startsWith("event:")) eventName = line.slice(6).trim();
        else if (line.startsWith("data:")) data += line.slice(5).trim();
      }
      if (!data) continue;
      let payload = null;
      try { payload = JSON.parse(data); } catch { /* 忽略坏数据 */ }
      if (payload && handlers[eventName]) await handlers[eventName](payload);
    }
  }
}

async function choose(i) {
  const storyEl = $("story");
  // 禁用选项防连点
  storyEl.querySelectorAll(".opt").forEach((b) => (b.disabled = true));
  try {
    const resp = await fetch("/api/game/decision", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ choice_id: i }),
    });
    if (!resp.ok) {
      const e = await resp.json().catch(() => null);
      throw new Error((e && e.detail) || resp.statusText);
    }
    const narrativeEl = document.createElement("p");
    narrativeEl.className = "narrative streaming";
    narrativeEl.textContent = "—— 叙事生成中 ——";
    storyEl.prepend(narrativeEl);
    let fullText = "";
    await consumeStream(resp, {
      meta: (m) => {
        state = { player: m.player };
        renderAll();
        if (m.career_over) renderCareerEnd(m);
      },
      narrative: async (n) => {
        fullText += n.delta;
        narrativeEl.textContent = fullText;
        narrativeEl.classList.remove("streaming");
        storyEl.scrollTop = storyEl.scrollHeight;
      },
      done: (d) => {
        state.player = d.player || state.player;
        state.narrative = d.narrative;
        state.options = d.options;
        state.decision = d.decision;
        cacheStory({ narrative: d.narrative, options: d.options });
        renderAll();
        window.scrollTo({ top: 0, behavior: "smooth" });
      },
      error: (e) => { throw new Error(e.detail || "叙事生成失败"); },
    });
  } catch (e) {
    alert(e.message);
    renderStory(); // 恢复选项按钮供重试
  }
}

// 刷新恢复：player 等来自 /api/game/state，叙事/选项来自本地缓存
async function refreshState() {
  const s = await api("/api/game/state");
  const saved = loadStory();
  state = {
    player: s.player,
    narrative: saved ? saved.narrative : null,
    options: saved ? saved.options : null,
  };
}

function cacheStory(r) {
  if (!r.narrative) return;
  try {
    localStorage.setItem(STORY_KEY, JSON.stringify({ narrative: r.narrative, options: r.options || null }));
  } catch { /* localStorage 不可用时忽略 */ }
}

function loadStory() {
  try { return JSON.parse(localStorage.getItem(STORY_KEY)); } catch { return null; }
}

// ---- 渲染 ----

function renderAll() {
  const p = state.player || {};
  $("g_name").textContent = [p.name, p.age ? p.age + "岁" : "", p.position, p.club].filter(Boolean).join(" · ");
  $("g_ovr").textContent = p.ovr != null ? p.ovr : "-";
  $("g_value").textContent = p.value != null ? `${(p.value / 1e4).toFixed(0)}万欧` : "-";
  $("g_stage").textContent = (state.decision || {}).season
    ? `${state.decision.season}赛季 · ${state.decision.stage}`
    : (p.national_level ? `国字号：${p.national_level}` : "");
  renderStory();
  renderRadar();
  renderStats();
}

function renderStory() {
  const el = $("story");
  el.innerHTML = "";
  if (state.narrative) {
    const p = document.createElement("p");
    p.textContent = state.narrative;
    el.appendChild(p);
  } else {
    const p = document.createElement("p");
    p.className = "narrative muted";
    p.textContent = "—— 叙事已归档，点击下方选项继续你的旅程 ——";
    el.appendChild(p);
  }
  (state.decision?.options || state.options || []).forEach((o, i) => {
    const b = document.createElement("button");
    b.className = "opt";
    b.innerHTML = `${o.label}${o.hint ? `<small>${o.hint}</small>` : ""}`;
    b.onclick = () => choose(i);
    el.appendChild(b);
  });
}

function renderCareerEnd(m) {
  // 退役/转教练结局：展示荣誉、里程碑、教练属性
  const el = $("story");
  el.innerHTML = "";
  const div = document.createElement("div");
  div.className = "career-end";
  let html = `<h3>🏁 职业生涯结束（${m.ended}）</h3>`;
  html += "<h3>🏆 生涯荣誉</h3><p>" + (m.honors.length ? m.honors.join("<br>") : "无") + "</p>";
  html += "<h3>📜 生涯里程碑</h3><p>" + (m.milestones.length ? m.milestones.join("<br>") : "无") + "</p>";
  if (m.coach) {
    html += "<h3>🧑‍🏫 教练属性</h3><p>" +
      Object.entries(m.coach).map(([k, v]) => `${k} ${v}`).join(" / ") + "</p>";
  }
  if (m.career_stats) {
    const rows = Object.entries(m.career_stats)
      .map(([s, v]) => `${s}赛季：${v.apps}场 ${v.goals}球 ${v.assists}助攻`)
      .join("<br>");
    html += "<h3>📊 生涯数据</h3><p>" + rows + "</p>";
  }
  div.innerHTML = html;
  el.appendChild(div);
}

function renderStats() {
  const el = $("g_stats");
  const stats = (state.player && state.player.career_stats) || {};
  const seasons = Object.keys(stats).sort();
  if (!seasons.length) {
    el.innerHTML = '<p class="empty">首个赛季结束后显示赛季数据</p>';
    return;
  }
  const rows = seasons.map((s) => {
    const v = stats[s];
    return `<tr><td>${s}</td><td>${v.age ?? "-"}</td><td>${v.apps ?? 0}</td>` +
      `<td>${v.goals ?? 0}</td><td>${v.assists ?? 0}</td><td>${v.clean_sheets ?? "-"}</td>` +
      `<td class="club">${v.club ?? ""}</td></tr>`;
  }).join("");
  el.innerHTML = `<table><thead><tr><th>赛季</th><th>年龄</th><th>出场</th>` +
    `<th>进球</th><th>助攻</th><th>零封</th><th>俱乐部</th></tr></thead><tbody>${rows}</tbody></table>`;
}

// 六大项均值（键名与设计文档 §4.1 一致）
const RADAR_GROUPS = [
  { name: "速度", keys: ["acceleration", "sprint_speed"] },
  { name: "射门", keys: ["positioning", "finishing", "shot_power", "long_shots", "volleys", "penalties"] },
  { name: "传球", keys: ["vision", "short_passing", "long_passing", "crossing", "curve", "fk_accuracy"] },
  { name: "盘带", keys: ["agility", "balance", "reactions", "ball_control", "dribbling", "composure"] },
  { name: "防守", keys: ["def_awareness", "interceptions", "heading", "standing_tackle", "sliding_tackle"] },
  { name: "身体", keys: ["strength", "stamina", "jumping", "aggression"] },
];

// 原生 SVG 六边形雷达图，含数值标签
function renderRadar() {
  const attrs = (state.player && state.player.attributes) || {};
  const vals = RADAR_GROUPS.map((g) => {
    const vs = g.keys.map((k) => attrs[k] ?? 50);
    return Math.round(vs.reduce((a, b) => a + b, 0) / vs.length);
  });
  const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  svg.setAttribute("viewBox", "0 0 300 270");
  svg.setAttribute("width", "100%");
  const cx = 150, cy = 140, R = 90;
  let s = `<text x="150" y="18" text-anchor="middle" fill="#9ca3af" font-size="13">属性雷达图</text>`;
  // 四层参考环
  for (let ring = 1; ring <= 4; ring++) {
    const pts = [];
    for (let i = 0; i < 6; i++) {
      const a = (Math.PI * 2 * i) / 6 - Math.PI / 2;
      pts.push(`${cx + Math.cos(a) * R * ring / 4},${cy + Math.sin(a) * R * ring / 4}`);
    }
    s += `<polygon points="${pts.join(" ")}" fill="none" stroke="#2c3644"/>`;
  }
  // 数据多边形
  const pts = vals.map((v, i) => {
    const a = (Math.PI * 2 * i) / 6 - Math.PI / 2;
    return `${cx + Math.cos(a) * R * v / 100},${cy + Math.sin(a) * R * v / 100}`;
  });
  s += `<polygon points="${pts.join(" ")}" fill="rgba(74,222,128,.25)" stroke="#4ade80" stroke-width="2"/>`;
  // 顶点数值标签
  vals.forEach((v, i) => {
    const a = (Math.PI * 2 * i) / 6 - Math.PI / 2;
    const x = cx + Math.cos(a) * (R + 22), y = cy + Math.sin(a) * (R + 22);
    s += `<text x="${x}" y="${y}" text-anchor="middle" fill="#e5e7eb" font-size="12">${RADAR_GROUPS[i].name} ${v}</text>`;
  });
  svg.innerHTML = s;
  $("radar").innerHTML = "";
  $("radar").appendChild(svg);
}

init();
