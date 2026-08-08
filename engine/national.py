"""国家队线：征召判定与级别推进。"""

NATIONAL_LEVELS = {
    "u17": {"min_age": 16, "max_age": 18, "min_ovr": 58, "event": "U17亚洲杯"},
    "u20": {"min_age": 18, "max_age": 20, "min_ovr": 63, "event": "U20亚洲杯"},
    "u23": {"min_age": 20, "max_age": 23, "min_ovr": 68, "event": "U23亚洲杯/奥预赛"},
    "senior": {"min_age": 18, "min_ovr": 65, "event": "世预赛/亚洲杯/世界杯"},
}
NATIONAL_LEVEL_ORDER = ["u17", "u20", "u23", "senior"]

# 联赛级别加成：留洋平台越高，国家队入选门槛越低
LEAGUE_BONUS = {"epl": 6, "laliga": 5, "bundesliga": 5, "seriea": 5, "ligue1": 4,
                "eredivisie": 3, "primeira": 3, "cs": 0, "csl2": -3}


def national_callup(age: int, ovr: int, league: str, form: float = 1.0, level: str = "senior") -> bool:
    """征召判定 = OVR + 联赛加成 + 状态加成，达到该级别门槛（+2 容差）即入选。"""
    cfg = NATIONAL_LEVELS[level]
    if age < cfg["min_age"]:
        return False
    if "max_age" in cfg and age > cfg["max_age"]:
        return False
    score = ovr + LEAGUE_BONUS.get(league, 0) + int(round((form - 1.0) * 10))
    return score >= cfg["min_ovr"] + 2


def next_level(current: str, age: int) -> str:
    """随年龄自动推进的级别（只升不降）。"""
    if age >= 23:
        want = "senior"
    elif age >= 20:
        want = "u23"
    elif age >= 18:
        want = "u20"
    elif age >= 16:
        want = "u17"
    else:
        return ""
    cur_idx = NATIONAL_LEVEL_ORDER.index(current) if current in NATIONAL_LEVEL_ORDER else -1
    want_idx = NATIONAL_LEVEL_ORDER.index(want)
    return current if cur_idx >= want_idx else want
