"""engine 最小 stub：属性定义与 OVR（契约与 engine/attributes.py 一致，供会话 A/B/C 合并前使用）。"""
POSITION_WEIGHTS = {
    "ST":  {"positioning":30,"finishing":25,"composure":15,"reactions":10,"acceleration":5,"shot_power":5,"strength":5,"jumping":5},
    "LW":  {"acceleration":25,"agility":15,"dribbling":15,"ball_control":10,"balance":10,"crossing":10,"composure":10,"long_shots":5},
    "RW":  {"acceleration":25,"agility":15,"dribbling":15,"ball_control":10,"balance":10,"crossing":10,"composure":10,"long_shots":5},
    "CAM": {"short_passing":20,"vision":20,"composure":20,"reactions":10,"long_shots":10,"ball_control":10,"dribbling":5,"finishing":5},
    "CM":  {"short_passing":20,"vision":15,"composure":15,"reactions":10,"stamina":10,"interceptions":10,"ball_control":10,"long_shots":5,"def_awareness":5},
    "CDM": {"interceptions":20,"def_awareness":20,"strength":15,"stamina":15,"short_passing":10,"aggression":10,"composure":10},
    "LB":  {"sprint_speed":20,"stamina":15,"interceptions":15,"standing_tackle":15,"crossing":15,"def_awareness":10,"acceleration":10},
    "RB":  {"sprint_speed":20,"stamina":15,"interceptions":15,"standing_tackle":15,"crossing":15,"def_awareness":10,"acceleration":10},
    "CB":  {"def_awareness":25,"interceptions":20,"strength":15,"jumping":15,"reactions":10,"standing_tackle":10,"sprint_speed":5},
    "GK":  {"reflexes":25,"gk_positioning":20,"diving":15,"handling":15,"kicking":10,"reactions":10,"strength":5},
}
FIELD_GROUPS = {
    "pace":     ["acceleration", "sprint_speed"],
    "shooting": ["positioning", "finishing", "shot_power", "long_shots", "volleys", "penalties"],
    "passing":  ["vision", "short_passing", "long_passing", "crossing", "curve", "fk_accuracy"],
    "dribbling":["agility", "balance", "reactions", "ball_control", "dribbling", "composure"],
    "defending":["def_awareness", "interceptions", "heading", "standing_tackle", "sliding_tackle"],
    "physical": ["strength", "stamina", "jumping", "aggression"],
}
GK_ATTRIBUTES = ["reflexes", "handling", "diving", "gk_positioning", "kicking"]
ALL_FIELD = [a for g in FIELD_GROUPS.values() for a in g]
ALL_ATTRS = ALL_FIELD + GK_ATTRIBUTES


def clamp_attr(v: float) -> int:
    return max(0, min(99, int(round(v))))


def calc_ovr(attrs: dict, position: str) -> int:
    w = POSITION_WEIGHTS[position]
    num = sum(attrs.get(a, 50) * wt for a, wt in w.items())
    return clamp_attr(num / sum(w.values()))
