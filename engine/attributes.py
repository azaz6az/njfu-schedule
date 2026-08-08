"""FIFA 式属性定义、位置权重与 OVR 计算。"""
import random

# ---- 属性定义 ----
FIELD_GROUPS = {
    "pace":     ["acceleration", "sprint_speed"],
    "shooting": ["positioning", "finishing", "shot_power", "long_shots", "volleys", "penalties"],
    "passing":  ["vision", "short_passing", "long_passing", "crossing", "curve", "fk_accuracy"],
    "dribbling": ["agility", "balance", "reactions", "ball_control", "dribbling", "composure"],
    "defending": ["def_awareness", "interceptions", "heading", "standing_tackle", "sliding_tackle"],
    "physical": ["strength", "stamina", "jumping", "aggression"],
}
GK_ATTRIBUTES = ["reflexes", "handling", "diving", "gk_positioning", "kicking"]
HIDDEN_FIELDS = ["skill_moves", "weak_foot", "playstyles"]

ALL_FIELD = [a for g in FIELD_GROUPS.values() for a in g]
ALL_ATTRS = ALL_FIELD + GK_ATTRIBUTES

# ---- 位置权重（OVR = Σ(属性×权重) / Σ权重）----
POSITION_WEIGHTS = {
    "ST":  {"positioning": 30, "finishing": 25, "composure": 15, "reactions": 10,
            "acceleration": 5, "shot_power": 5, "strength": 5, "jumping": 5},
    "LW":  {"acceleration": 25, "agility": 15, "dribbling": 15, "ball_control": 10,
            "balance": 10, "crossing": 10, "composure": 10, "long_shots": 5},
    "RW":  {"acceleration": 25, "agility": 15, "dribbling": 15, "ball_control": 10,
            "balance": 10, "crossing": 10, "composure": 10, "long_shots": 5},
    "CAM": {"short_passing": 20, "vision": 20, "composure": 20, "reactions": 10,
            "long_shots": 10, "ball_control": 10, "dribbling": 5, "finishing": 5},
    "CM":  {"short_passing": 20, "vision": 15, "composure": 15, "reactions": 10,
            "stamina": 10, "interceptions": 10, "ball_control": 10, "long_shots": 5,
            "def_awareness": 5},
    "CDM": {"interceptions": 20, "def_awareness": 20, "strength": 15, "stamina": 15,
            "short_passing": 10, "aggression": 10, "composure": 10},
    "LB":  {"sprint_speed": 20, "stamina": 15, "interceptions": 15, "standing_tackle": 15,
            "crossing": 15, "def_awareness": 10, "acceleration": 10},
    "RB":  {"sprint_speed": 20, "stamina": 15, "interceptions": 15, "standing_tackle": 15,
            "crossing": 15, "def_awareness": 10, "acceleration": 10},
    "CB":  {"def_awareness": 25, "interceptions": 20, "strength": 15, "jumping": 15,
            "reactions": 10, "standing_tackle": 10, "sprint_speed": 5},
    "GK":  {"reflexes": 25, "gk_positioning": 20, "diving": 15, "handling": 15,
            "kicking": 10, "reactions": 10, "strength": 5},
}
POSITIONS = list(POSITION_WEIGHTS.keys())
# LW/RW/LB/RB 归属到位置类（用于面板展示）
POSITION_LABEL = {
    "LW": "边锋", "RW": "边锋", "ST": "中锋", "CAM": "前腰", "CM": "中场",
    "CDM": "后腰", "LB": "边后卫", "RB": "边后卫", "CB": "中后卫", "GK": "门将",
}


def clamp_attr(v: float) -> int:
    """属性钳制到 0-99 整数。"""
    return max(0, min(99, int(round(v))))


def calc_ovr(attrs: dict, position: str) -> int:
    """按位置权重加权计算 OVR。"""
    w = POSITION_WEIGHTS[position]
    num = sum(attrs.get(a, 50) * wt for a, wt in w.items())
    den = sum(w.values())
    return clamp_attr(num / den)


def ovr_for_position(attrs: dict) -> dict:
    """返回 {位置: OVR} 全部位置快照。"""
    return {p: calc_ovr(attrs, p) for p in POSITIONS}


def template_attributes(target_ovr: int, position: str, rng_seed: int = 0, deviation: int = 3) -> dict:
    """由目标 OVR 按位置模板反推生成属性（AI 球员展开用）。

    核心属性在 target±deviation 内波动，非核心与门将属性低于 OVR，
    使加权 OVR 逼近目标值。
    """
    rng = random.Random(rng_seed)
    w = POSITION_WEIGHTS.get(position, POSITION_WEIGHTS["CM"])
    core = set(w.keys())
    attrs = {}
    for a in ALL_FIELD:
        if a in core:
            attrs[a] = clamp_attr(target_ovr + rng.randint(-deviation, deviation))
        else:
            attrs[a] = clamp_attr(target_ovr - rng.randint(0, 6))
    for g in GK_ATTRIBUTES:
        attrs[g] = clamp_attr(target_ovr - rng.randint(2, 8))
    return attrs


def expand_attributes(row: dict) -> dict:
    """从数据集行（ovr/position/seed）展开属性字典。"""
    pos = row.get("position", "CM")
    return template_attributes(int(row["ovr"]), pos, rng_seed=int(row.get("seed", 0)))
