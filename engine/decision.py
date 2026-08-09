"""决策骨架生成：事件库 + 引擎预生成的合法效果（防幻觉核心）。

防幻觉原则：所有选项的数值效果（effects）由本模块预生成并限定合法键与取值范围，
LLM 只能改写选项文案，后端强制合并引擎效果。

事件选择是有上下文的（v2）：
- 按位置过滤：门将不会遇到"比赛单刀"，前锋不会遇到"门将出击"
- 伤病状态机：受伤（injury_risk 累积触发）→ 复出决策 → 恢复，形成闭环
- 防连续重复：相邻决策点不出现同一事件
- 位置转型：动态生成合法目标位置，选择后真正改变位置（路由结算）
"""
import random

# effects 合法键：attr_delta（属性点，±5）/ morale / form / injury_risk / reputation
#                  / value_factor / transfer / retire / position_change / get_injured / recover
EFFECT_KEYS = {"attr_delta", "morale", "form", "injury_risk", "reputation", "value_factor",
               "transfer", "retire", "position_change", "get_injured", "recover"}

# 位置 → 合法转型目标（引擎预定义，防止 LLM 编造）
POSITION_TRANSFORMS = {
    "ST": ["CAM", "LW"],
    "LW": ["ST", "RW"],
    "RW": ["ST", "LW"],
    "CAM": ["CM", "ST"],
    "CM": ["CDM", "CAM"],
    "CDM": ["CM", "CB"],
    "LB": ["CB", "CDM"],
    "RB": ["CB", "CDM"],
    "CB": ["CDM"],
    # GK 不可转型
}

# 季前备战专属训练选项（不进常规随机池，由 engine.season 使用）
TRAINING_OPTIONS = [
    {"label": "加练速度", "hint": "冲刺与启动变强，消耗体能",
     "effects": {"attr_delta": {"acceleration": 2, "sprint_speed": 2}, "morale": -1, "injury_risk": 0.08}},
    {"label": "加练射门", "hint": "射术精进",
     "effects": {"attr_delta": {"finishing": 2, "shot_power": 1}, "morale": 1}},
    {"label": "加练体能", "hint": "耐力提升，恢复",
     "effects": {"attr_delta": {"stamina": 2}, "morale": 1, "injury_risk": -0.05}},
]

# 常规事件池（季前训练、伤病、转型由专门生成器处理）
EVENT_LIBRARY = {
    "比赛单刀": {
        "positions": ["ST", "LW", "RW", "CAM", "CM"],  # 只有进攻/中场会遇到单刀
        "options": [
            {"label": "冷静推射远角", "hint": "稳妥但可能被扑",
             "effects": {"attr_delta": {"finishing": 1, "composure": 1}, "morale": 3, "form": 0.03}},
            {"label": "过掉门将再打空门", "hint": "更华丽，失误则丢机会",
             "effects": {"attr_delta": {"dribbling": 1, "agility": 1}, "morale": 2, "form": 0.02, "injury_risk": 0.05}},
            {"label": "横传队友吃饼", "hint": "无私，刷助攻",
             "effects": {"attr_delta": {"vision": 1}, "morale": 1, "form": 0.02, "reputation": 1}},
        ]},
    "更衣室矛盾": {
        "positions": None,  # 全位置
        "options": [
            {"label": "当面调解", "hint": "敢说话，队内声望升",
             "effects": {"morale": 4, "reputation": 2, "injury_risk": 0.02}},
            {"label": "私下劝和", "hint": "稳妥，少树敌",
             "effects": {"morale": 2, "reputation": 1}},
            {"label": "保持沉默", "hint": "不掺和",
             "effects": {"morale": -2, "reputation": -1}},
        ]},
    "媒体采访": {
        "positions": None,
        "options": [
            {"label": "豪言夺冠", "hint": "关注度↑，压力↑",
             "effects": {"reputation": 3, "morale": 2, "form": -0.02}},
            {"label": "低调谦逊", "hint": "稳妥",
             "effects": {"morale": 1, "reputation": 1}},
            {"label": "避谈媒体", "hint": "省心但无曝光",
             "effects": {"reputation": -1, "morale": 1}},
        ]},
    "新型训练法": {
        "positions": None,
        "options": [
            {"label": "尝试认知训练", "hint": "反应与沉着提升",
             "effects": {"attr_delta": {"reactions": 1, "composure": 1}, "injury_risk": 0.03}},
            {"label": "传统力量训练", "hint": "身体对抗增强",
             "effects": {"attr_delta": {"strength": 2}, "injury_risk": 0.05}},
            {"label": "拒绝折腾", "hint": "维持现状",
             "effects": {"morale": 1}},
        ]},
    "国家队竞争": {
        "positions": None,
        "min_ovr": 60,  # 具备一定实力才谈得上国家队位置竞争
        "options": [
            {"label": "主动请缨", "hint": "展现自信",
             "effects": {"morale": 2, "reputation": 3}},
            {"label": "做好自己", "hint": "不争不抢",
             "effects": {"morale": 1}},
            {"label": "消极心态", "hint": "情绪低落",
             "effects": {"morale": -3, "form": -0.03}},
        ]},
    "门将出击": {
        "positions": ["GK"],  # 门将专属
        "options": [
            {"label": "果断出击化解单刀", "hint": "门将第一属性：扑救与勇气",
             "effects": {"attr_delta": {"diving": 1, "reflexes": 1}, "morale": 3, "form": 0.03, "injury_risk": 0.06}},
            {"label": "固守门线封角度", "hint": "站好位置，稳",
             "effects": {"attr_delta": {"gk_positioning": 1, "composure": 1}, "morale": 2, "form": 0.02}},
            {"label": "出击到大禁区边缘解围", "hint": "冒险但能开大脚",
             "effects": {"attr_delta": {"kicking": 1}, "morale": 1, "form": 0.02, "injury_risk": 0.1}},
        ]},
}

# 伤病状态机阈值
INJURY_RISK_TRIGGER = 0.30  # 累积超过该值，下一次决策可能触发受伤
INJURY_CHANCE = 0.6         # 触发后的受伤概率


def _clone_options(options: list) -> list:
    """深拷贝选项（effects 独立，调用方修改不影响事件库）。"""
    return [{"label": o["label"], "hint": o["hint"], "effects": dict(o["effects"])}
            for o in options]


def season_skeleton(season: int) -> list:
    """每赛季 8 决策点骨架：夏窗/季前备战/上半程×2/冬窗/下半程×2/赛季末。"""
    return [
        {"season": season, "stage": "夏窗", "type": "转会窗口"},
        {"season": season, "stage": "季前备战", "type": "训练加练"},
        {"season": season, "stage": "上半程", "type": None},
        {"season": season, "stage": "上半程", "type": None},
        {"season": season, "stage": "冬窗", "type": "转会窗口"},
        {"season": season, "stage": "下半程", "type": None},
        {"season": season, "stage": "下半程", "type": None},
        {"season": season, "stage": "赛季末", "type": "赛季总结"},
    ]


def _eligible_events(player: dict, last_type: str = None) -> list:
    """按位置/实力过滤事件池，并排除上次事件（防连续重复）。"""
    pool = []
    for name, ev in EVENT_LIBRARY.items():
        if last_type and name == last_type:
            continue
        if ev.get("positions") and player["position"] not in ev["positions"]:
            continue
        if ev.get("min_ovr") and player["ovr"] < ev["min_ovr"]:
            continue
        pool.append(name)
    if not pool:  # 兜底：全部过滤时放宽到位置过滤
        pool = [n for n, ev in EVENT_LIBRARY.items()
                if not ev.get("positions") or player["position"] in ev["positions"]]
    return pool


def _injury_skeleton(player: dict, season: int, stage: str) -> dict:
    """受伤触发事件：高强度对抗中拉伤（injury_risk 高时出现）。"""
    return {
        "season": season, "stage": stage, "type": "伤病来袭",
        "narrative_hook": "一次高强度的对抗后，你的大腿传来刺痛——是硬撑下去，还是示意换人？",
        "options": [
            {"label": "咬牙坚持比赛", "hint": "留下硬汉印象，但可能拉伤加重",
             "effects": {"morale": 2, "form": 0.03, "injury_risk": 0.30, "get_injured": True}},
            {"label": "示意换人下场", "hint": "保护自己，缺席两轮",
             "effects": {"injury_risk": -0.25, "morale": -1}},
            {"label": "硬撑到半场再评估", "hint": "折中方案",
             "effects": {"injury_risk": 0.15, "form": 0.01, "morale": 1}},
        ],
    }


def _injury_recovery_skeleton(player: dict, season: int, stage: str) -> dict:
    """受伤恢复事件：受伤状态下的复出决策。"""
    return {
        "season": season, "stage": stage, "type": "伤病复出",
        "narrative_hook": "队医评估后说你的伤已无大碍，但复出时机由你决定。",
        "options": [
            {"label": "保守复出", "hint": "多休两周，零风险",
             "effects": {"morale": -1, "injury_risk": -0.1, "recover": True}},
            {"label": "提前复出", "hint": "赶上关键战，风险高",
             "effects": {"morale": 3, "injury_risk": 0.25, "form": 0.03, "recover": True}},
            {"label": "循序渐进复出", "hint": "先随队训练再上场",
             "effects": {"morale": 1, "injury_risk": 0.05, "form": 0.02, "recover": True}},
        ],
    }


def _position_change_skeleton(player: dict, season: int, stage: str, rng: random.Random) -> dict:
    """位置转型事件：引擎预定义合法目标位置，接受后由路由真正改变位置。"""
    targets = POSITION_TRANSFORMS.get(player["position"], [])
    if not targets:
        return None  # 门将/无可转型位置：不生成该事件
    target = rng.choice(targets)
    return {
        "season": season, "stage": stage, "type": "位置转型",
        "narrative_hook": f"教练组找你谈话：以你的特点，或许 {target} 位置能发挥更大价值。",
        "options": [
            {"label": f"接受转型到 {target}", "hint": "新位置潜力更大，转型期 OVR 波动",
             "effects": {"attr_delta": {"reactions": 2}, "morale": 2, "reputation": 2,
                         "value_factor": 0.05, "position_change": target}},
            {"label": "婉拒转型", "hint": "坚持原位置",
             "effects": {"morale": -1, "reputation": -1}},
            {"label": "先试训新位置再决定", "hint": "感受后再定",
             "effects": {"attr_delta": {"reactions": 1}, "morale": 1}},
        ],
    }


def next_decision(player: dict, season: int, stage: str, rng_seed: int = 0,
                  last_type: str = None) -> dict:
    """生成一个决策骨架：按位置/状态过滤 + 防重复 + 伤病状态机 + 位置转型。"""
    rng = random.Random(rng_seed)
    # 1. 伤病状态机：受伤中 → 强制复出决策
    if player.get("injured"):
        return _injury_recovery_skeleton(player, season, stage)
    # 2. 伤病风险高 → 可能触发受伤事件
    if player.get("injury_risk", 0) >= INJURY_RISK_TRIGGER and rng.random() < INJURY_CHANCE:
        return _injury_skeleton(player, season, stage)
    # 3. 位置转型（低概率，门将除外）
    if player["position"] != "GK" and rng.random() < 0.15:
        sk = _position_change_skeleton(player, season, stage, rng)
        if sk:
            return sk
    # 4. 常规事件池（位置过滤 + 防重复）
    pool = _eligible_events(player, last_type)
    ev_name = rng.choice(pool)
    ev = EVENT_LIBRARY[ev_name]
    return {
        "season": season, "stage": stage, "type": ev_name,
        "narrative_hook": ev_name,
        "options": _clone_options(ev["options"]),
    }
