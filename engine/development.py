"""成长曲线、衰退与决策效果结算。"""
import random

from engine.attributes import ALL_ATTRS, POSITION_WEIGHTS


def growth_points(age: int) -> int:
    """按年龄的基础年成长点（正=成长，0=横盘，负=衰退）。"""
    if age <= 18:
        return random.randint(8, 12)
    if age <= 21:
        return random.randint(5, 8)
    if age <= 25:
        return random.randint(3, 5)
    if age <= 28:
        return random.randint(1, 2)
    if age <= 31:
        return 0
    return -random.randint(1, 3)  # 32+ 衰退


# 衰退优先级：速度/敏捷/耐力先退，站位/射术/沉着保值
DECLINE_FIRST = ["acceleration", "sprint_speed", "agility", "stamina", "balance"]
PRESERVE = ["positioning", "finishing", "composure", "short_passing", "vision"]


def _position_core(position: str) -> list:
    """位置核心属性按权重降序。"""
    w = POSITION_WEIGHTS.get(position, POSITION_WEIGHTS["CM"])
    return sorted(w.items(), key=lambda kv: -kv[1])


def allocate_growth(growth: int, position: str) -> dict:
    """把成长点按位置权重分配给属性。返回 {属性: 增量}，总和=growth。

    权重大的属性分得多；非核心属性保持 0。负成长（衰退）同样按权重分配。
    """
    core = _position_core(position)
    deltas = {a: 0 for a in ALL_ATTRS}
    total_weight = sum(w for _, w in core)
    shares = [round(growth * w / total_weight) for _, w in core]
    # 修正取整误差：差值补给权重最大的属性，保证总和精确
    shares[0] += growth - sum(shares)
    for (attr, _), share in zip(core, shares):
        deltas[attr] = share
    return deltas


def apply_decision_effects(attrs: dict, effects: dict) -> dict:
    """应用决策效果。

    effects: {attr_delta: {attr: n}, morale: ±, form: ±, injury_risk: 0-1}
    仅结算属性增量（0-99 钳制）；morale/form/injury_risk 由调用方写入状态。
    """
    out = dict(attrs)
    for a, d in effects.get("attr_delta", {}).items():
        if a in out:
            out[a] = max(0, min(99, out[a] + d))
    return out


def age_player_attributes(attrs: dict, age: int) -> dict:
    """按年龄推进属性（跨赛季结算；玩家属性由 growth_points+allocate_growth 处理）。

    32 岁起速度/敏捷/耐力先退，下限 50；站位/射术/沉着保值。
    """
    out = dict(attrs)
    if age >= 32:
        n = 1 if age <= 33 else 2
        for a in DECLINE_FIRST:
            if a in out and out[a] > 50:
                out[a] = max(50, out[a] - n)
    return out
