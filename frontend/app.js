// 足球人生模拟器前端 —— 纯原生 JS，零依赖
// API 契约见设计文档 §16；后端会话按此实现
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
    const e = new Error((data && data.detail) || r.statusText);
    e.status = r.status;
    throw e;
  }
  return r.json();
}

// 启动分流：未配置(403 / configured=false) → 设置页；已配置 → 尝试载入存档
async function init() {
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

async function choose(i) {
  try {
    const r = await api("/api/game/decision", "POST", { choice_id: i });
    state = r;
    cacheStory(r);
    renderAll();
    window.scrollTo({ top: 0, behavior: "smooth" });
  } catch (e) { alert(e.message); }
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

function renderAll() {
  const p = state.player || {};
  $("g_name").textContent = [p.name, p.age ? p.age + "岁" : "", p.position, p.club].filter(Boolean).join(" · ");
  $("g_ovr").textContent = p.ovr != null ? p.ovr : "-";
  $("g_value").textContent = p.value != null ? `${(p.value / 1e4).toFixed(0)}万欧` : "-";
  renderStory();
  renderRadar();
  $("g_stats").textContent = JSON.stringify(p.career_stats || {}, null, 2);
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
