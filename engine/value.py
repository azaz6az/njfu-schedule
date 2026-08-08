"""身价模型：基础曲线 × 年龄/位置/联赛/合同/状态系数（欧元）。"""
import math

# ---- 系数表（设计文档 6.2）----

def _age_factor(age: int) -> float:
    if age <= 16:
        return 0.6
    if age == 17:
        return 0.7
    if age == 18:
        return 0.85
    if 19 <= age <= 23:
        return 1.0
    if 24 <= age <= 28:
        return 1.1
    if 29 <= age <= 32:
        return 0.85
    return 0.6


POS_FACTOR = {
    "ST": 1.3, "LW": 1.2, "RW": 1.2, "CAM": 1.15, "CM": 1.15,
    "CDM": 1.0, "CB": 1.0, "LB": 0.9, "RB": 0.9, "GK": 0.85,
}

LEAGUE_FACTOR = {
    "epl": 1.5, "laliga": 1.4, "bundesliga": 1.3, "seriea": 1.25,
    "ligue1": 1.15, "eredivisie": 1.0, "primeira": 0.95, "cs": 0.7,
    # 次级联赛（0.45-0.8）
    "csl2": 0.45, "efl": 0.8, "laliga2": 0.75, "serieb": 0.7,
    "bundes2": 0.7, "ligue2": 0.65, "eerst": 0.6, "ligaporta": 0.6,
}


def _contract_factor(years: int) -> float:
    if years >= 3:
        return 1.1
    if years == 2:
        return 1.0
    if years == 1:
        return 0.75
    return 0.55


def base_value(ovr: int) -> float:
    """基础身价（欧元）。OVR≤85 指数增长；>85 斜率放缓。"""
    if ovr <= 85:
        return 300_000 * (1.27 ** (ovr - 60))
    return 119_000_000 * (1.16 ** (ovr - 85)) * 0.7


def market_value(ovr, age, position="CM", league="epl", contract_years=3,
                 form=1.0, potential_premium=1.0) -> int:
    """市场身价 = 基础身价 × 年龄 × 位置 × 联赛 × 合同 × 状态 × 潜力溢价。"""
    v = (base_value(ovr)
         * _age_factor(age)
         * POS_FACTOR.get(position, 1.0)
         * LEAGUE_FACTOR.get(league, 1.0)
         * _contract_factor(contract_years)
         * max(0.85, min(1.2, form))
         * potential_premium)
    return int(v)


def apply_under18_caps(ovr: int, value: int, age: int) -> tuple:
    """18 岁前硬约束：OVR≤80、身价≤8000 万欧元。返回 (ovr, value)。"""
    if age < 18:
        ovr = min(ovr, 80)
        value = min(value, 80_000_000)
    return ovr, value
