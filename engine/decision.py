"""决策骨架生成：事件库 + 引擎预生成的合法效果（防幻觉核心）。

防幻觉原则：所有选项的数值效果（effects）由本模块预生成并限定合法键与取值范围，
LLM 只能改写选项文案，后端强制合并引擎效果。
"""
import random

# effects 合法键：attr_delta（属性点，±5）/ morale / form / injury_risk / reputation / value_factor
EFFECT_KEYS = {"attr_delta", "morale", "form", "injury_risk", "reputation", "value_factor"}

EVENT_LIBRARY = {
    "比赛单刀": {
        "options": [
            {"label": "冷静推射远角", "hint": "稳妥但可能被扑",
             "effects": {"attr_delta": {"finishing": 1, "composure": 1}, "morale": 3, "form": 0.03}},
            {"label": "过掉门将再打空门", "hint": "更华丽，失误则丢机会",
             "effects": {"attr_delta": {"dribbling": 1, "agility": 1}, "morale": 2, "form": 0.02, "injury_risk": 0.05}},
            {"label": "横传队友吃饼", "hint": "无私，刷助攻",
             "effects": {"attr_delta": {"vision": 1}, "morale": 1, "form": 0.02, "reputation": 1}},
        ]},
    "训练加练": {
        "options": [
            {"label": "加练速度", "hint": "冲刺与启动变强，消耗体能",
             "effects": {"attr_delta": {"acceleration": 2, "sprint_speed": 2}, "morale": -1, "injury_risk": 0.08}},
            {"label": "加练射门", "hint": "射术精进",
             "effects": {"attr_delta": {"finishing": 2, "shot_power": 1}, "morale": 1}},
            {"label": "加练体能", "hint": "耐力提升，恢复",
             "effects": {"attr_delta": {"stamina": 2}, "morale": 1, "injury_risk": -0.05}},
        ]},
    "更衣室矛盾": {
        "options": [
            {"label": "当面调解", "hint": "敢说话，队内声望升",
             "effects": {"morale": 4, "reputation": 2, "injury_risk": 0.02}},
            {"label": "私下劝和", "hint": "稳妥，少树敌",
             "effects": {"morale": 2, "reputation": 1}},
            {"label": "保持沉默", "hint": "不掺和",
             "effects": {"morale": -2, "reputation": -1}},
        ]},
    "媒体采访": {
        "options": [
            {"label": "豪言夺冠", "hint": "关注度↑，压力↑",
             "effects": {"reputation": 3, "morale": 2, "form": -0.02}},
            {"label": "低调谦逊", "hint": "稳妥",
             "effects": {"morale": 1, "reputation": 1}},
            {"label": "避谈媒体", "hint": "省心但无曝光",
             "effects": {"reputation": -1, "morale": 1}},
        ]},
    "伤病复出": {
        "options": [
            {"label": "保守复出", "hint": "缺阵 2 周，零风险",
             "effects": {"morale": -1, "injury_risk": -0.1}},
            {"label": "提前复出", "hint": "赶上关键战，风险高",
             "effects": {"morale": 3, "injury_risk": 0.25, "form": 0.03}},
            {"label": "循序渐进复出", "hint": "先随队训练再上场",
             "effects": {"morale": 1, "injury_risk": 0.05, "form": 0.02}},
        ]},
    "新型训练法": {
        "options": [
            {"label": "尝试认知训练", "hint": "反应与沉着提升",
             "effects": {"attr_delta": {"reactions": 1, "composure": 1}, "injury_risk": 0.03}},
            {"label": "传统力量训练", "hint": "身体对抗增强",
             "effects": {"attr_delta": {"strength": 2}, "injury_risk": 0.05}},
            {"label": "拒绝折腾", "hint": "维持现状",
             "effects": {"morale": 1}},
        ]},
    "位置转型": {
        "options": [
            {"label": "接受转型", "hint": "新位置潜力更大，转型期 OVR 波动",
             "effects": {"attr_delta": {"reactions": 2}, "morale": 2, "reputation": 2, "value_factor": 0.05}},
            {"label": "婉拒转型", "hint": "坚持原位置",
             "effects": {"morale": -1, "reputation": -1}},
            {"label": "先试训再决定", "hint": "感受新位置后再定",
             "effects": {"attr_delta": {"reactions": 1}, "morale": 1}},
        ]},
    "国家队竞争": {
        "options": [
            {"label": "主动请缨", "hint": "展现自信",
             "effects": {"morale": 2, "reputation": 3}},
            {"label": "做好自己", "hint": "不争不抢",
             "effects": {"morale": 1}},
            {"label": "消极心态", "hint": "情绪低落",
             "effects": {"morale": -3, "form": -0.03}},
        ]},
}


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


def next_decision(player: dict, season: int, stage: str, rng_seed: int = 0) -> dict:
    """生成一个决策骨架：随机事件 + 选项（效果深拷贝，调用方篡改不影响事件库）。"""
    rng = random.Random(rng_seed)
    ev_name = rng.choice(list(EVENT_LIBRARY.keys()))
    ev = EVENT_LIBRARY[ev_name]
    return {
        "season": season,
        "stage": stage,
        "type": ev_name,
        "narrative_hook": ev_name,
        "options": [{"label": o["label"], "hint": o["hint"], "effects": dict(o["effects"])}
                    for o in ev["options"]],
    }
