"""engine 最小 stub：决策骨架生成器（契约与 engine/decision.py 一致）。"""
import random

EVENT_LIBRARY = {
    "比赛单刀": {
        "options": [
            {"label": "冷静推射远角", "hint": "稳妥但可能被扑", "effects": {"attr_delta": {"finishing": 1, "composure": 1}, "morale": 3, "form": 0.03}},
            {"label": "过掉门将再打空门", "hint": "更华丽，失误则丢机会", "effects": {"attr_delta": {"dribbling": 1, "agility": 1}, "morale": 2, "form": 0.02, "injury_risk": 0.05}},
            {"label": "横传队友吃饼", "hint": "无私，刷助攻", "effects": {"attr_delta": {"vision": 1}, "morale": 1, "form": 0.02, "reputation": 1}},
        ]},
    "训练加练": {
        "options": [
            {"label": "加练速度", "hint": "冲刺与启动变强，消耗体能", "effects": {"attr_delta": {"acceleration": 2, "sprint_speed": 2}, "morale": -1, "injury_risk": 0.08}},
            {"label": "加练射门", "hint": "射术精进", "effects": {"attr_delta": {"finishing": 2, "shot_power": 1}, "morale": 1}},
            {"label": "加练体能", "hint": "耐力提升，恢复", "effects": {"attr_delta": {"stamina": 2}, "morale": 1, "injury_risk": -0.05}},
        ]},
    "媒体采访": {
        "options": [
            {"label": "豪言夺冠", "hint": "关注度↑，压力↑", "effects": {"reputation": 3, "morale": 2, "form": -0.02}},
            {"label": "低调谦逊", "hint": "稳妥", "effects": {"morale": 1, "reputation": 1}},
            {"label": "避谈媒体", "hint": "省心但无曝光", "effects": {"reputation": -1, "morale": 1}},
        ]},
}


def season_skeleton(season: int) -> list:
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
    rng = random.Random(rng_seed)
    ev_name = rng.choice(list(EVENT_LIBRARY.keys()))
    ev = EVENT_LIBRARY[ev_name]
    return {
        "season": season, "stage": stage, "type": ev_name,
        "narrative_hook": ev_name,
        "options": [dict(o) for o in ev["options"]],
    }
