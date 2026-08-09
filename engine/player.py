"""玩家档案：建档、初始属性、潜力。"""
import random
import zlib

from engine.attributes import ALL_FIELD, GK_ATTRIBUTES, POSITION_WEIGHTS, clamp_attr

ACADEMIES = [
    {"name": "山东鲁能足校", "style": "身体对抗", "region": "山东",
     "bias": {"strength": 3, "stamina": 3, "aggression": 2}},
    {"name": "广州恒大足校", "style": "技术流", "region": "广东",
     "bias": {"ball_control": 3, "dribbling": 3, "agility": 2}},
    {"name": "上海根宝基地", "style": "技术流", "region": "上海",
     "bias": {"short_passing": 3, "vision": 3, "composure": 2}},
    {"name": "浙江绿城足校", "style": "青训摇篮", "region": "浙江",
     "bias": {"vision": 2, "short_passing": 2, "ball_control": 2}},
    {"name": "北京国安青训", "style": "整体战术", "region": "北京",
     "bias": {"def_awareness": 2, "short_passing": 2, "stamina": 2}},
    {"name": "万达留洋计划", "style": "海外视野", "region": "大连",
     "bias": {"reactions": 2, "composure": 2, "long_passing": 2}},
]
REGIONS = ["东北", "华北", "华东", "华南", "西南", "西北"]

# 青训入营赛季（设计文档：2023 年夏天，16 岁）
_ACADEMY_YEAR = 2023


def _name_seed(name: str) -> int:
    """玩家名 → 确定性随机种子（存档可复现，不依赖 PYTHONHASHSEED）。"""
    return zlib.crc32(name.encode("utf-8")) % 10000


def create_player(name, birth_year, position, foot, height, weight, region, academy):
    """创建 16 岁青训球员档案。"""
    rng = random.Random(_name_seed(name))
    return {
        "name": name, "birth_year": birth_year, "age": 16,
        "position": position, "foot": foot, "height": height, "weight": weight,
        "region": region, "academy": academy, "club": academy + " U17",
        "national_level": "无",
        "attributes": initial_attributes(position, seed=_name_seed(name + ":attrs")),
        "hidden": {"skill_moves": rng.randint(1, 5), "weak_foot": rng.randint(1, 5),
                   "playstyles": []},
        "potential_rating": roll_potential(seed=_name_seed(name + ":pa")),
        "ovr": 0, "value": 0,
        "injured": False, "injury_risk": 0.0,
        "contract": {"years": 0, "weekly_wage": 0, "release_clause": 0, "signing_bonus": 0},
        "career_stats": {}, "milestones": [], "honors": [],
        "national_stats": {"u17": {"apps": 0, "goals": 0}, "u20": {"apps": 0, "goals": 0},
                           "u23": {"apps": 0, "goals": 0}, "senior": {"apps": 0, "goals": 0}},
        "coach_attributes": None,
    }


def initial_attributes(position, seed=0):
    """16 岁青训初始属性：按位置核心偏高，整体 48-72 区间。"""
    rng = random.Random(seed)
    attrs = {}
    for a in ALL_FIELD:
        attrs[a] = clamp_attr(rng.randint(48, 62))
    # 先初始化 GK 专属属性（GK 位置的核心提升会作用到它们）
    for g in GK_ATTRIBUTES:
        attrs[g] = clamp_attr(rng.randint(40, 50) if position != "GK" else rng.randint(55, 70))
    core = list(POSITION_WEIGHTS.get(position, POSITION_WEIGHTS["CM"]).keys())
    for a in core:
        attrs[a] = clamp_attr(attrs[a] + rng.randint(4, 10))
    return attrs


def roll_potential(seed=0):
    """潜力评级掷骰：青训可造之才 / 国脚级 / 洲际级 / 世界级。"""
    rng = random.Random(seed)
    r = rng.random()
    if r < 0.45:
        return "青训可造之才"
    if r < 0.75:
        return "国脚级"
    if r < 0.95:
        return "洲际级"
    return "世界级"
