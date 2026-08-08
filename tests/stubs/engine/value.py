"""engine 最小 stub：身价模型（契约与 engine/value.py 一致）。"""
import math

POS_FACTOR = {"ST":1.3,"LW":1.2,"RW":1.2,"CAM":1.15,"CM":1.15,"CDM":1.0,"LB":0.9,"RB":0.9,"CB":1.0,"GK":0.85}
LEAGUE_FACTOR = {"epl":1.5,"laliga":1.4,"bundesliga":1.3,"seriea":1.25,"ligue1":1.15,"eredivisie":1.0,"primeira":0.95,"cs":0.7,"csl2":0.45}


def _age_factor(age: int) -> float:
    if age <= 16: return 0.6
    if age == 17: return 0.7
    if age == 18: return 0.85
    if 19 <= age <= 23: return 1.0
    if 24 <= age <= 28: return 1.1
    if 29 <= age <= 32: return 0.85
    return 0.6


def _contract_factor(years: int) -> float:
    if years >= 3: return 1.1
    if years == 2: return 1.0
    if years == 1: return 0.75
    return 0.55


def base_value(ovr: int) -> float:
    if ovr <= 85:
        return 300_000 * (1.27 ** (ovr - 60))
    return 119_000_000 * (1.16 ** (ovr - 85)) * 0.7


def market_value(ovr, age, position="CM", league="epl", contract_years=3, form=1.0, potential_premium=1.0) -> int:
    v = base_value(ovr) * _age_factor(age) * POS_FACTOR.get(position, 1.0) \
        * LEAGUE_FACTOR.get(league, 1.0) * _contract_factor(contract_years) * max(0.85, min(1.2, form)) * potential_premium
    return int(v)


def apply_under18_caps(ovr: int, value: int, age: int):
    if age < 18:
        ovr = min(ovr, 80)
        value = min(value, 80_000_000)
    return ovr, value
